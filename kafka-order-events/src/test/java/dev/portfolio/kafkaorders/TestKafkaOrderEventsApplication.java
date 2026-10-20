package dev.portfolio.kafkaorders;

import org.springframework.boot.SpringApplication;

public class TestKafkaOrderEventsApplication {

	public static void main(String[] args) {
		SpringApplication.from(KafkaOrderEventsApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
