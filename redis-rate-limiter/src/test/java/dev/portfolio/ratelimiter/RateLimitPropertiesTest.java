package dev.portfolio.ratelimiter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = { "ratelimit.capacity=7", "ratelimit.refill-per-second=0.5" })
class RateLimitPropertiesTest {

	@Autowired
	RateLimitProperties properties;

	@Autowired
	RateLimiter limiter;

	@Test
	void limitsAreConfigurable() {
		assertThat(properties.capacity()).isEqualTo(7);
		assertThat(properties.refillPerSecond()).isEqualTo(0.5);
		assertThat(limiter.tryAcquire("props-key").remaining()).isEqualTo(6);
	}
}
