package dev.concurx.concurx;

import dev.concurx.concurx.task.Task;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootApplication
public class ConcurxApplication {

	public static void main(String[] args) {
		SpringApplication.run(ConcurxApplication.class, args);

		ExecutorService executor = Executors.newFixedThreadPool(5);
		for (int i = 1; i <= 5; i++) {
			executor.submit(new Task("worker", i));
		}
		executor.shutdown();
	}
}
