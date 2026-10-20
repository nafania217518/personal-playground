package dev.portfolio.ratelimiter;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ratelimit")
public record RateLimitProperties(int capacity, double refillPerSecond) {
}
