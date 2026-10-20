package dev.portfolio.sensorstore;

import org.springframework.boot.SpringApplication;

public class TestCassandraSensorStoreApplication {

	public static void main(String[] args) {
		SpringApplication.from(CassandraSensorStoreApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
