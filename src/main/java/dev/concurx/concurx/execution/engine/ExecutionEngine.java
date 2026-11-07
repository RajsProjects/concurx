package dev.concurx.concurx.execution.engine;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ExecutionEngine {
    private final ThreadPoolExecutor executor;

    public ExecutionEngine(){
        this.executor = new ThreadPoolExecutor(
                2,                           // Core pool size
                5,                                      // Maximum pool size
                60,                                     // Keep-alive time
                TimeUnit.SECONDS,                       // Time unit
                new ArrayBlockingQueue<>(100),  // Bounded task queue
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    public void submit(Runnable task){
        executor.execute(task);
    }

    public void shutdown(){
        executor.shutdown();
        try {
            if (!executor.awaitTermination(120, TimeUnit.SECONDS)) {
                System.out.println("Graceful shutdown timed out. Forcing shutdown...");
                executor.shutdownNow();
            } else {
                System.out.println("Executor terminated successfully.");
            }
        } catch (InterruptedException e) {
            System.out.println("Shutdown process interrupted.");
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public List<Runnable> forceShutdown(){
        return executor.shutdownNow();
    }
}
