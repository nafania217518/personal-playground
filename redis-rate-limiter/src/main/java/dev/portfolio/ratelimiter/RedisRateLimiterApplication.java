package dev.portfolio.ratelimiter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class RedisRateLimiterApplication {

	public static void main(String[] args) {
		SpringApplication.run(RedisRateLimiterApplication.class, args);
	}

}
