package dev.concurx.concurx;

import dev.concurx.concurx.execution.engine.ExecutionEngine;
import dev.concurx.concurx.task.Task;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootApplication
public class ConcurxApplication {

	public static void main(String[] args) throws InterruptedException {
		SpringApplication.run(ConcurxApplication.class, args);

		ExecutionEngine engine = new ExecutionEngine();

		for (int i = 1; i <= 100; i++) {
			engine.submit(new Task("Task-" + i, i));
		}

		Thread.sleep(1000);

		engine.shutdown();
	}
}
