package dev.concurx.concurx.execution.task;

import lombok.Getter;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;


public class TaskWrapper implements Runnable {

    private final Runnable task;

    private long startTime;
    private long endTime;

    @Getter
    private volatile Throwable failure;

    private final AtomicReference<TaskState> state =
            new AtomicReference<>(TaskState.QUEUED);

    public TaskState getState() {
        return state.get();
    }

    public TaskWrapper(Runnable task) {
        this.task = Objects.requireNonNull(task);
    }

    @Override
    public void run() {

        if (!state.compareAndSet(TaskState.QUEUED, TaskState.RUNNING)) {
            throw new IllegalStateException("Task has already been executed");
        }

        startTime = System.nanoTime();

        try {
            task.run();

        } catch (RuntimeException | Error e) {
            this.failure = e;
            throw e;

        } finally {
            endTime = System.nanoTime();

            state.set((failure == null)
                    ? TaskState.COMPLETED
                    : TaskState.FAILED);
        }
    }

    public Duration getExecutionDuration() {
        TaskState currentState = state.get();

        if (currentState != TaskState.COMPLETED &&
                currentState != TaskState.FAILED) {

            throw new IllegalStateException(
                    "Task execution has not completed"
            );
        }

        return Duration.ofNanos(endTime - startTime);
    }
}