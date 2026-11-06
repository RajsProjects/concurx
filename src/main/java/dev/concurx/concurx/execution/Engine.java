package dev.concurx.concurx.execution;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class Engine {
    private final ThreadPoolExecutor executor;

    public Engine(){
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
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
