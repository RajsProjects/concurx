package dev.concurx.concurx.concurrencyTests;

import dev.concurx.concurx.execution.engine.ExecutionEngine;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ExecutionEngineTest {

    // =========================================================
    // TEST 1: Maximum concurrency
    // =========================================================

    @Test
    void shouldNeverExecuteMoreThanFiveTasksConcurrently()
            throws InterruptedException {

        ExecutionEngine engine = new ExecutionEngine();

        CountDownLatch workersStarted = new CountDownLatch(5);
        CountDownLatch releaseWorkers = new CountDownLatch(1);

        AtomicInteger activeTasks = new AtomicInteger(0);
        AtomicInteger maxActiveTasks = new AtomicInteger(0);

        Runnable blockingTask = () -> {

            int active = activeTasks.incrementAndGet();

            maxActiveTasks.accumulateAndGet(active, Math::max);

            workersStarted.countDown();

            try {
                releaseWorkers.await();

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;

            } finally {
                activeTasks.decrementAndGet();
            }
        };

        try {
            System.out.println("\n========== TEST 1 ==========");
            System.out.println("[Test 1] Submitting 105 tasks...");

            // 5 executing + 100 queued.
            for (int i = 0; i < 105; i++) {
                engine.submit(blockingTask);
            }

            System.out.println("[Test 1] Waiting for all 5 workers...");

            assertTrue(
                    workersStarted.await(5, TimeUnit.SECONDS),
                    "Expected all 5 workers to start"
            );

            System.out.println("[Test 1] All 5 workers started.");

            assertEquals(5, maxActiveTasks.get());

            System.out.println(
                    "[Test 1] Maximum concurrency: "
                            + maxActiveTasks.get()
            );

            System.out.println("[Test 1] Passed.");

        } finally {
            System.out.println("[Test 1] Releasing workers...");

            releaseWorkers.countDown();

            engine.shutdown();
        }
    }


    // =========================================================
    // TEST 2: Rejection when executor is saturated
    // =========================================================

    @Test
    void shouldRejectTaskWhenExecutorIsSaturated()
            throws InterruptedException {

        ExecutionEngine engine = new ExecutionEngine();

        CountDownLatch workersStarted = new CountDownLatch(5);
        CountDownLatch releaseWorkers = new CountDownLatch(1);

        Runnable blockingTask = () -> {

            workersStarted.countDown();

            try {
                releaseWorkers.await();

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        try {
            System.out.println("\n========== TEST 2 ==========");
            System.out.println("[Test 2] Filling executor capacity...");

            for (int i = 0; i < 105; i++) {
                engine.submit(blockingTask);
            }

            assertTrue(
                    workersStarted.await(5, TimeUnit.SECONDS),
                    "Expected all 5 workers to start"
            );

            System.out.println("[Test 2] Executor is saturated.");

            System.out.println("[Test 2] Submitting task 106...");

            assertThrows(
                    RejectedExecutionException.class,
                    () -> engine.submit(blockingTask),
                    "Expected executor to reject task when saturated"
            );

            System.out.println(
                    "[Test 2] Task 106 rejected as expected."
            );

            System.out.println("[Test 2] Passed.");

        } finally {
            System.out.println("[Test 2] Releasing workers...");

            releaseWorkers.countDown();

            engine.shutdown();
        }
    }


    // =========================================================
    // TEST 3: Complete accepted tasks during graceful shutdown
    // =========================================================

    @Test
    void shouldCompleteAcceptedTasksDuringGracefulShutdown() {

        ExecutionEngine engine = new ExecutionEngine();

        AtomicInteger completedTasks = new AtomicInteger(0);

        Runnable task = () -> {

            System.out.println(
                    "[Worker] Task completed by "
                            + Thread.currentThread().getName()
            );

            completedTasks.incrementAndGet();
        };

        System.out.println("\n========== TEST 3 ==========");
        System.out.println("[Test 3] Submitting 10 tasks...");

        for (int i = 0; i < 10; i++) {
            engine.submit(task);
        }

        System.out.println("[Test 3] Initiating graceful shutdown...");

        engine.shutdown();

        assertEquals(10, completedTasks.get());

        System.out.println(
                "[Test 3] Completed tasks: " + completedTasks.get()
        );

        System.out.println("[Test 3] Passed.");
    }


    // =========================================================
    // TEST 4: Graceful shutdown waits for running tasks
    // =========================================================

    @Test
    void shouldWaitForRunningTasksDuringGracefulShutdown()
            throws InterruptedException {

        CountDownLatch taskStarted = new CountDownLatch(1);
        CountDownLatch taskRelease = new CountDownLatch(1);
        CountDownLatch shutdownCompleted = new CountDownLatch(1);

        ExecutionEngine engine = new ExecutionEngine();

        Runnable task = () -> {

            System.out.println("[Worker] Task started.");

            taskStarted.countDown();

            try {
                taskRelease.await();

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            System.out.println("[Worker] Task completed.");
        };

        Thread shutdownThread = new Thread(() -> {

            System.out.println("[Test 4] Shutdown thread started.");

            try {
                engine.shutdown();

            } finally {
                shutdownCompleted.countDown();
            }
        });

        try {
            System.out.println("\n========== TEST 4 ==========");
            System.out.println("[Test 4] Submitting task...");

            engine.submit(task);

            assertTrue(
                    taskStarted.await(5, TimeUnit.SECONDS),
                    "Task did not start"
            );

            System.out.println("[Test 4] Task is running.");

            shutdownThread.start();

            long deadline =
                    System.nanoTime() + TimeUnit.SECONDS.toNanos(5);

            while (!engine.isShutdown()
                    && System.nanoTime() < deadline) {

                Thread.sleep(1);
            }

            assertTrue(
                    engine.isShutdown(),
                    "Executor did not enter shutdown state"
            );

            System.out.println("[Test 4] Executor entered shutdown state.");

            assertEquals(
                    1,
                    shutdownCompleted.getCount(),
                    "Shutdown should still be waiting"
            );

            System.out.println(
                    "[Test 4] Shutdown has not completed yet."
            );

            System.out.println("[Test 4] Releasing running task...");

            taskRelease.countDown();

            assertTrue(
                    shutdownCompleted.await(5, TimeUnit.SECONDS),
                    "Shutdown did not complete"
            );

            shutdownThread.join(5000);

            assertFalse(
                    shutdownThread.isAlive(),
                    "Shutdown thread did not terminate"
            );

            System.out.println("[Test 4] Graceful shutdown completed.");
            System.out.println("[Test 4] Passed.");

        } finally {
            taskRelease.countDown();

            engine.forceShutdown();

            shutdownThread.join(5000);
        }
    }


    // =========================================================
    // TEST 5: Force shutdown interrupts running tasks
    // =========================================================

    @Test
    void shouldInterruptRunningTasksAndReturnQueuedTasksOnForceShutdown()
            throws InterruptedException {

        CountDownLatch tasksStarted = new CountDownLatch(2);
        CountDownLatch interruptedTasks = new CountDownLatch(2);
        CountDownLatch releaseTasks = new CountDownLatch(1);

        AtomicInteger queuedTaskExecutions = new AtomicInteger(0);

        ExecutionEngine engine = new ExecutionEngine();

        Runnable runningTask = () -> {

            System.out.println(
                    "[Worker] Running task: "
                            + Thread.currentThread().getName()
            );

            tasksStarted.countDown();

            try {
                releaseTasks.await();

            } catch (InterruptedException e) {

                System.out.println("[Worker] Running task interrupted.");

                interruptedTasks.countDown();

                Thread.currentThread().interrupt();
            }
        };

        Runnable queuedTask = queuedTaskExecutions::incrementAndGet;

        try {
            System.out.println("\n========== TEST 5 ==========");

            System.out.println("[Test 5] Submitting two running tasks...");

            engine.submit(runningTask);
            engine.submit(runningTask);

            assertTrue(
                    tasksStarted.await(5, TimeUnit.SECONDS),
                    "Running tasks did not start"
            );

            System.out.println("[Test 5] Both workers are occupied.");

            System.out.println("[Test 5] Submitting one queued task...");

            engine.submit(queuedTask);

            System.out.println("[Test 5] Initiating force shutdown...");

            List<Runnable> unexecutedTasks = engine.forceShutdown();

            System.out.println(
                    "[Test 5] Unexecuted queued tasks: "
                            + unexecutedTasks.size()
            );

            assertEquals(
                    1,
                    unexecutedTasks.size(),
                    "Expected one queued task to be returned"
            );

            assertEquals(
                    0,
                    queuedTaskExecutions.get(),
                    "Queued task should not have executed"
            );

            assertTrue(
                    interruptedTasks.await(5, TimeUnit.SECONDS),
                    "Running tasks should receive interruption"
            );

            System.out.println("[Test 5] Both running tasks interrupted.");
            System.out.println("[Test 5] Passed.");

        } finally {
            releaseTasks.countDown();

            engine.forceShutdown();
        }
    }


    // =========================================================
    // TEST 6: Tasks that ignore interruption
    // =========================================================

    @Test
    void shouldNotForcefullyTerminateTasksThatIgnoreInterruption()
            throws InterruptedException {

        CountDownLatch taskStarted = new CountDownLatch(1);
        CountDownLatch interruptionReceived = new CountDownLatch(1);
        CountDownLatch releaseTask = new CountDownLatch(1);
        CountDownLatch taskCompleted = new CountDownLatch(1);

        ExecutionEngine engine = new ExecutionEngine();

        Runnable task = () -> {

            System.out.println("[Worker] Task started.");

            taskStarted.countDown();

            while (releaseTask.getCount() > 0) {

                try {
                    releaseTask.await();

                } catch (InterruptedException e) {

                    System.out.println(
                            "[Worker] Received interruption but ignoring it."
                    );

                    interruptionReceived.countDown();
                }
            }

            System.out.println("[Worker] Task completed.");

            taskCompleted.countDown();
        };

        try {
            System.out.println("\n========== TEST 6 ==========");

            System.out.println("[Test 6] Submitting task...");

            engine.submit(task);

            assertTrue(
                    taskStarted.await(5, TimeUnit.SECONDS),
                    "Task did not start"
            );

            System.out.println("[Test 6] Initiating force shutdown...");

            engine.forceShutdown();

            assertTrue(
                    interruptionReceived.await(5, TimeUnit.SECONDS),
                    "Task did not receive interruption"
            );

            assertEquals(
                    1,
                    taskCompleted.getCount(),
                    "Task should not have completed after interruption"
            );

            System.out.println(
                    "[Test 6] Task is still running despite interruption."
            );

            System.out.println("[Test 6] Manually releasing task...");

            releaseTask.countDown();

            assertTrue(
                    taskCompleted.await(5, TimeUnit.SECONDS),
                    "Task did not complete after being released"
            );

            System.out.println("[Test 6] Task completed after release.");
            System.out.println("[Test 6] Passed.");

        } finally {
            releaseTask.countDown();

            engine.forceShutdown();
        }
    }


    // =========================================================
    // TEST 7: Reject submissions after shutdown
    // =========================================================

    @Test
    void shouldRejectTasksSubmittedAfterShutdown() {

        ExecutionEngine engine = new ExecutionEngine();

        Runnable task = () ->
                System.out.println("[Worker] Task executed.");

        System.out.println("\n========== TEST 7 ==========");

        System.out.println("[Test 7] Shutting down executor...");

        engine.shutdown();

        System.out.println(
                "[Test 7] Attempting submission after shutdown..."
        );

        assertThrows(
                RejectedExecutionException.class,
                () -> engine.submit(task),
                "Executor should reject submissions after shutdown"
        );

        System.out.println(
                "[Test 7] Submission rejected as expected."
        );

        System.out.println("[Test 7] Passed.");
    }


    // =========================================================
    // TEST 8: Repeated shutdown calls
    // =========================================================

    @Test
    void shouldHandleRepeatedShutdownCallsSafely() {

        ExecutionEngine engine = new ExecutionEngine();

        System.out.println("\n========== TEST 8 ==========");

        System.out.println("[Test 8] First shutdown...");
        engine.shutdown();

        System.out.println("[Test 8] Second shutdown...");
        engine.shutdown();

        System.out.println("[Test 8] Third shutdown...");
        engine.shutdown();

        assertTrue(
                engine.isShutdown(),
                "Executor should remain in shutdown state"
        );

        System.out.println(
                "[Test 8] Repeated shutdown calls handled safely."
        );

        System.out.println("[Test 8] Passed.");
    }


    // =========================================================
    // TEST 9: Interrupt the thread calling shutdown()
    // =========================================================

    @Test
    void shouldForceShutdownWhenShutdownThreadIsInterrupted()
            throws InterruptedException {

        ExecutionEngine engine = new ExecutionEngine();

        CountDownLatch taskStarted = new CountDownLatch(1);
        CountDownLatch workerInterrupted = new CountDownLatch(1);
        CountDownLatch shutdownFinished = new CountDownLatch(1);

        AtomicBoolean shutdownThreadInterruptStatus =
                new AtomicBoolean(false);

        Runnable task = () -> {

            System.out.println("[Worker] Task started.");

            taskStarted.countDown();

            try {
                new CountDownLatch(1).await();

            } catch (InterruptedException e) {

                System.out.println(
                        "[Worker] Received interruption from shutdownNow()."
                );

                workerInterrupted.countDown();

                Thread.currentThread().interrupt();
            }
        };

        Thread shutdownThread = new Thread(() -> {

            System.out.println(
                    "[Test 9] Shutdown thread calling shutdown()..."
            );

            try {
                engine.shutdown();

            } finally {

                shutdownThreadInterruptStatus.set(
                        Thread.currentThread().isInterrupted()
                );

                shutdownFinished.countDown();
            }
        });

        try {
            System.out.println("\n========== TEST 9 ==========");

            System.out.println("[Test 9] Submitting task...");

            engine.submit(task);

            assertTrue(
                    taskStarted.await(5, TimeUnit.SECONDS),
                    "Task did not start"
            );

            System.out.println("[Test 9] Starting shutdown thread...");

            shutdownThread.start();

            long deadline =
                    System.nanoTime() + TimeUnit.SECONDS.toNanos(5);

            while (!engine.isShutdown()
                    && System.nanoTime() < deadline) {

                Thread.sleep(1);
            }

            assertTrue(
                    engine.isShutdown(),
                    "Executor did not enter shutdown state"
            );

            System.out.println(
                    "[Test 9] Executor entered shutdown state."
            );

            System.out.println(
                    "[Test 9] Interrupting shutdown thread..."
            );

            shutdownThread.interrupt();

            assertTrue(
                    shutdownFinished.await(5, TimeUnit.SECONDS),
                    "Shutdown thread did not finish"
            );

            assertTrue(
                    workerInterrupted.await(5, TimeUnit.SECONDS),
                    "Worker did not receive interruption"
            );

            assertTrue(
                    shutdownThreadInterruptStatus.get(),
                    "Shutdown thread interrupt status was not restored"
            );

            System.out.println(
                    "[Test 9] Shutdown thread interrupt status restored."
            );

            System.out.println("[Test 9] Passed.");

        } finally {
            engine.forceShutdown();

            shutdownThread.interrupt();

            shutdownThread.join(5000);
        }
    }


    // =========================================================
    // TEST 10: Graceful shutdown timeout
    // =========================================================

    @Test
    void shouldForceShutdownWhenGracefulShutdownTimesOut()
            throws InterruptedException {

        ExecutionEngine engine =
                new ExecutionEngine(Duration.ofMillis(500));

        CountDownLatch taskStarted = new CountDownLatch(1);
        CountDownLatch workerInterrupted = new CountDownLatch(1);

        Runnable task = () -> {

            System.out.println("[Worker] Task started.");

            taskStarted.countDown();

            try {
                new CountDownLatch(1).await();

            } catch (InterruptedException e) {

                System.out.println(
                        "[Worker] Received interruption after timeout."
                );

                workerInterrupted.countDown();

                Thread.currentThread().interrupt();
            }
        };

        try {
            System.out.println("\n========== TEST 10 ==========");

            System.out.println("[Test 10] Submitting task...");

            engine.submit(task);

            assertTrue(
                    taskStarted.await(5, TimeUnit.SECONDS),
                    "Task did not start"
            );

            System.out.println(
                    "[Test 10] Initiating graceful shutdown..."
            );

            System.out.println(
                    "[Test 10] Configured timeout: 500 milliseconds."
            );

            engine.shutdown();

            System.out.println(
                    "[Test 10] Waiting for worker interruption..."
            );

            assertTrue(
                    workerInterrupted.await(5, TimeUnit.SECONDS),
                    "Task was not interrupted after timeout"
            );

            assertTrue(
                    engine.isShutdown(),
                    "Executor should be in shutdown state"
            );

            System.out.println(
                    "[Test 10] Forced shutdown verified."
            );

            System.out.println("[Test 10] Passed.");

        } finally {
            System.out.println("[Test 10] Cleaning up...");

            engine.forceShutdown();
        }
    }
}