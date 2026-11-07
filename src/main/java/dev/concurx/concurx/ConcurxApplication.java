package dev.concurx.concurx;

import dev.concurx.concurx.execution.engine.ExecutionEngine;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

@SpringBootApplication
public class ConcurxApplication {

	public static void main(String[] args) throws InterruptedException {
		SpringApplication.run(ConcurxApplication.class, args);

		ExecutionEngine engine = new ExecutionEngine();
		CountDownLatch started = new CountDownLatch(5);
		CountDownLatch release = new CountDownLatch(1);

		Runnable blockingTask = () -> {
			started.countDown();

			try{
				release.await();
			}catch(InterruptedException e){
				Thread.currentThread().interrupt();
				return;
			}
			System.out.println("Execueted by : " + Thread.currentThread().getName());
		};

		for (int i = 1; i <= 105; i++) {
			engine.submit(blockingTask);
		}

		boolean workersStarted = started.await(5, TimeUnit.SECONDS);

		if (!workersStarted) {
			release.countDown();
			engine.shutdown();
			throw new IllegalStateException("workers did not start in time.");
		}

		try{
			engine.submit(blockingTask);
			System.out.println("Task accepted by : " + Thread.currentThread().getName());
		}catch(RejectedExecutionException e){
			System.out.println("Task rejected by : " + Thread.currentThread().getName() +
					"executer capacity exhausted");
		}

		release.countDown();
		engine.shutdown();
	}
}
