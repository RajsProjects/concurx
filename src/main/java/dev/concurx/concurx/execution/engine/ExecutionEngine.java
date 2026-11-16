package dev.concurx.concurx.execution.engine;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ExecutionEngine {
    private final ThreadPoolExecutor executor;
    private final Duration shutdownTimeout;

    public ExecutionEngine() {
        this(Duration.ofSeconds(120));
    }

    public ExecutionEngine(Duration shutdownTimeout) {
        this.shutdownTimeout = Objects.requireNonNull(shutdownTimeout);

        if (shutdownTimeout.isNegative() || shutdownTimeout.isZero()) {
            throw new IllegalArgumentException(
                    "Shutdown timeout must be positive"
            );
        }

        this.executor = new ThreadPoolExecutor(
                2,
                5,
                60,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(100),
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    public void submit(Runnable task){
        executor.execute(task);
    }

    public void shutdown() {
        System.out.println("Initiating graceful shutdown...");

        executor.shutdown();

        try {
            System.out.println("Waiting for running tasks...");

            if (!executor.awaitTermination(
                    shutdownTimeout.toNanos(),
                    TimeUnit.NANOSECONDS)) {

                System.out.println("Graceful shutdown timed out.");
                System.out.println("Initiating forced shutdown...");

                List<Runnable> unexecutedTasks = executor.shutdownNow();

                System.out.println(
                        "Unexecuted queued tasks: " + unexecutedTasks.size()
                );

            } else {
                System.out.println("Executor terminated successfully.");
            }

        } catch (InterruptedException e) {
            System.out.println("Shutdown caller was interrupted.");
            System.out.println("Initiating forced shutdown...");

            List<Runnable> unexecutedTasks = executor.shutdownNow();

            System.out.println(
                    "Unexecuted queued tasks: " + unexecutedTasks.size()
            );

            Thread.currentThread().interrupt();
        }
    }

    public List<Runnable> forceShutdown() {
        System.out.println("Force shutdown requested.");

        List<Runnable> unexecutedTasks = executor.shutdownNow();

        System.out.println(
                "Unexecuted queued tasks: " + unexecutedTasks.size()
        );

        return unexecutedTasks;
    }

    public boolean isShutdown() {
        return executor.isShutdown();
    }
}
