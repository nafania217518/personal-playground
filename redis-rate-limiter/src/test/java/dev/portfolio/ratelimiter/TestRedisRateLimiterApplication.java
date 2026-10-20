package dev.portfolio.ratelimiter;

import org.springframework.boot.SpringApplication;

public class TestRedisRateLimiterApplication {

	public static void main(String[] args) {
		SpringApplication.from(RedisRateLimiterApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
