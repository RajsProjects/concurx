package dev.concurx.concurx.execution.task;

import dev.concurx.concurx.execution.engine.ExecutionEngine;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class TaskWrapperTests {

    @Test
    void shouldExecuteTaskSuccessfully() {

        AtomicBoolean executed = new AtomicBoolean(false);

        Runnable task = () -> executed.set(true);

        TaskWrapper wrapper = new TaskWrapper(task);

        wrapper.run();

        assertTrue(executed.get());
        assertEquals(TaskState.COMPLETED, wrapper.getState());

        Duration duration = wrapper.getExecutionDuration();

        assertNotNull(duration);
        assertFalse(duration.isNegative());
    }

    @Test
    void shouldCaptureFailureAndMarkTaskAsFailed() {

        RuntimeException exception =
                new RuntimeException("Task execution failed");

        Runnable task = () -> {
            throw exception;
        };

        TaskWrapper wrapper = new TaskWrapper(task);

        RuntimeException thrown = assertThrows(
                RuntimeException.class,
                wrapper::run
        );

        assertSame(exception, thrown);
        assertSame(exception, wrapper.getFailure());

        assertEquals(TaskState.FAILED, wrapper.getState());

        assertNotNull(wrapper.getExecutionDuration());
    }

    @Test
    void shouldPreventExecutingSameTaskTwice() {

        TaskWrapper wrapper = new TaskWrapper(
                () -> System.out.println("Task executed")
        );

        wrapper.run();

        assertEquals(TaskState.COMPLETED, wrapper.getState());

        assertThrows(
                IllegalStateException.class,
                wrapper::run
        );
    }

    @Test
    void shouldRejectNullTask() {

        assertThrows(
                NullPointerException.class,
                () -> new TaskWrapper(null)
        );
    }

    @Test
    void shouldNotExposeExecutionDurationWhileTaskIsRunning()
            throws InterruptedException {

        CountDownLatch taskStarted = new CountDownLatch(1);
        CountDownLatch allowTaskToFinish = new CountDownLatch(1);

        TaskWrapper wrapper = new TaskWrapper(() -> {
            taskStarted.countDown();

            try {
                allowTaskToFinish.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        });

        Thread worker = new Thread(wrapper);
        worker.start();

        try {
            assertTrue(taskStarted.await(1, TimeUnit.SECONDS));

            assertEquals(TaskState.RUNNING, wrapper.getState());

            assertThrows(
                    IllegalStateException.class,
                    wrapper::getExecutionDuration
            );

        } finally {
            allowTaskToFinish.countDown();
            worker.join();
        }

        assertEquals(TaskState.COMPLETED, wrapper.getState());
        assertNotNull(wrapper.getExecutionDuration());
    }

    @Test
    void shouldRecordExecutionDurationWhenTaskFails() {

        TaskWrapper wrapper = new TaskWrapper(() -> {
            throw new RuntimeException("Simulated failure");
        });

        assertThrows(
                RuntimeException.class,
                wrapper::run
        );

        assertEquals(TaskState.FAILED, wrapper.getState());

        Duration duration = wrapper.getExecutionDuration();

        assertNotNull(duration);
        assertFalse(duration.isNegative());
    }

    @Test
    void shouldPreventConcurrentExecutionOfSameTask()
            throws InterruptedException {

        AtomicInteger executionCount = new AtomicInteger(0);
        AtomicInteger rejectedCount = new AtomicInteger(0);
        AtomicReference<Throwable> unexpectedFailure =
                new AtomicReference<>();

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch startSignal = new CountDownLatch(1);
        CountDownLatch taskStarted = new CountDownLatch(1);
        CountDownLatch allowTaskToFinish = new CountDownLatch(1);

        TaskWrapper wrapper = new TaskWrapper(() -> {
            executionCount.incrementAndGet();
            taskStarted.countDown();

            try {
                allowTaskToFinish.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        });

        Runnable attemptExecution = () -> {
            ready.countDown();

            try {
                startSignal.await();
                wrapper.run();

            } catch (IllegalStateException e) {
                rejectedCount.incrementAndGet();

            } catch (Throwable e) {
                unexpectedFailure.set(e);
            }
        };

        Thread thread1 = new Thread(attemptExecution);
        Thread thread2 = new Thread(attemptExecution);

        thread1.start();
        thread2.start();

        try {
            assertTrue(ready.await(2, TimeUnit.SECONDS));

            startSignal.countDown();

            assertTrue(taskStarted.await(2, TimeUnit.SECONDS));

        } finally {
            allowTaskToFinish.countDown();
        }

        thread1.join();
        thread2.join();

        assertNull(unexpectedFailure.get());

        assertEquals(1, executionCount.get());
        assertEquals(1, rejectedCount.get());
        assertEquals(TaskState.COMPLETED, wrapper.getState());
    }

    @Test
    void shouldMeasureExecutionDurationCorrectly() {

        TaskWrapper wrapper = new TaskWrapper(() -> {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        });

        wrapper.run();

        Duration duration = wrapper.getExecutionDuration();

        assertTrue(
                duration.toMillis() >= 200,
                "Execution duration should be at least 200 ms"
        );
    }

    @Test
    void shouldPreserveOriginalExceptionDetails() {

        RuntimeException originalException =
                new RuntimeException("Database connection failed");

        TaskWrapper wrapper = new TaskWrapper(() -> {
            throw originalException;
        });

        RuntimeException thrown = assertThrows(
                RuntimeException.class,
                wrapper::run
        );

        assertSame(originalException, thrown);

        assertEquals(
                "Database connection failed",
                wrapper.getFailure().getMessage()
        );

        assertEquals(TaskState.FAILED, wrapper.getState());
    }

    @Test
    void shouldCaptureErrorAndMarkTaskAsFailed() {

        AssertionError originalError =
                new AssertionError("Unexpected assertion failure");

        TaskWrapper wrapper = new TaskWrapper(() -> {
            throw originalError;
        });

        AssertionError thrown = assertThrows(
                AssertionError.class,
                wrapper::run
        );

        assertSame(originalError, thrown);
        assertSame(originalError, wrapper.getFailure());

        assertEquals(TaskState.FAILED, wrapper.getState());
    }

    @Test
    void shouldMarkTaskAsFailedWhenInterrupted()
            throws InterruptedException {

        CountDownLatch taskStarted = new CountDownLatch(1);

        TaskWrapper wrapper = new TaskWrapper(() -> {
            taskStarted.countDown();

            try {
                Thread.sleep(10_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Task interrupted", e);
            }
        });

        Thread worker = new Thread(wrapper);
        worker.start();

        assertTrue(taskStarted.await(2, TimeUnit.SECONDS));

        worker.interrupt();
        worker.join(2_000);

        assertFalse(worker.isAlive());

        assertEquals(TaskState.FAILED, wrapper.getState());

        assertNotNull(wrapper.getFailure());
        assertInstanceOf(RuntimeException.class, wrapper.getFailure());

        assertTrue(
                wrapper.getFailure().getCause()
                        instanceof InterruptedException
        );
    }

    @Test
    void shouldTrackSubmittedTaskUntilCompletion()
            throws InterruptedException {

        CountDownLatch taskStarted = new CountDownLatch(1);
        CountDownLatch allowTaskToFinish = new CountDownLatch(1);

        ExecutionEngine engine = new ExecutionEngine();

        try {
            TaskWrapper wrapper = engine.submit(() -> {
                taskStarted.countDown();

                try {
                    allowTaskToFinish.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            });

            assertEquals(TaskState.QUEUED, wrapper.getState());

            assertTrue(taskStarted.await(2, TimeUnit.SECONDS));

            assertEquals(TaskState.RUNNING, wrapper.getState());

            allowTaskToFinish.countDown();

            awaitTerminalState(wrapper);

            assertEquals(TaskState.COMPLETED, wrapper.getState());
            assertNotNull(wrapper.getExecutionDuration());

        } finally {
            allowTaskToFinish.countDown();
            engine.shutdown();
        }
    }

    @Test
    void shouldTrackFailedSubmittedTask()
            throws InterruptedException {

        CountDownLatch taskStarted = new CountDownLatch(1);

        RuntimeException originalException =
                new RuntimeException("Simulated task failure");

        ExecutionEngine engine = new ExecutionEngine();

        try {
            TaskWrapper wrapper = engine.submit(() -> {
                taskStarted.countDown();
                throw originalException;
            });

            assertTrue(taskStarted.await(2, TimeUnit.SECONDS));

            awaitTerminalState(wrapper);

            assertEquals(TaskState.FAILED, wrapper.getState());

            assertSame(originalException, wrapper.getFailure());

            assertNotNull(wrapper.getExecutionDuration());

        } finally {
            engine.shutdown();
        }
    }

    @Test
    void shouldContinueProcessingTasksAfterAnotherTaskFails()
            throws InterruptedException {

        CountDownLatch failingTaskStarted = new CountDownLatch(1);
        CountDownLatch successfulTaskStarted = new CountDownLatch(1);

        ExecutionEngine engine = new ExecutionEngine();

        try {
            TaskWrapper failedTask = engine.submit(() -> {
                failingTaskStarted.countDown();
                throw new RuntimeException("Simulated failure");
            });

            assertTrue(
                    failingTaskStarted.await(2, TimeUnit.SECONDS)
            );

            TaskWrapper successfulTask = engine.submit(
                    successfulTaskStarted::countDown
            );

            assertTrue(
                    successfulTaskStarted.await(2, TimeUnit.SECONDS)
            );

            awaitTerminalState(failedTask);
            awaitTerminalState(successfulTask);

            assertEquals(TaskState.FAILED, failedTask.getState());
            assertEquals(TaskState.COMPLETED, successfulTask.getState());

        } finally {
            engine.shutdown();
        }
    }

    /**
     * Waits until a task reaches a terminal state.
     * Terminal states: COMPLETED and FAILED.
     */
    private void awaitTerminalState(TaskWrapper wrapper)
            throws InterruptedException {

        long deadline = System.nanoTime()
                + TimeUnit.SECONDS.toNanos(2);

        while (wrapper.getState() == TaskState.QUEUED
                || wrapper.getState() == TaskState.RUNNING) {

            if (System.nanoTime() >= deadline) {
                fail("Task did not reach a terminal state within 2 seconds");
            }

            Thread.sleep(5);
        }
    }
}