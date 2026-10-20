package dev.portfolio.securenotes;

import org.springframework.boot.SpringApplication;

public class TestSecureNotesApiApplication {

	public static void main(String[] args) {
		SpringApplication.from(SecureNotesApiApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
