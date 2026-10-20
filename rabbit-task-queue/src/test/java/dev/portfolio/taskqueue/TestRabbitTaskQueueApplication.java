package dev.portfolio.taskqueue;

import org.springframework.boot.SpringApplication;

public class TestRabbitTaskQueueApplication {

	public static void main(String[] args) {
		SpringApplication.from(RabbitTaskQueueApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
