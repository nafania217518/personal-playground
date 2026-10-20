package dev.portfolio.ratelimiter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class RateLimiterTest {

	@Autowired
	StringRedisTemplate redisTemplate;

	RateLimiter limiter(int capacity, double refillPerSecond) {
		return new RateLimiter(redisTemplate, new RateLimitProperties(capacity, refillPerSecond));
	}

	String key() {
		return UUID.randomUUID().toString();
	}

	@Test
	void allowsUpToCapacityThenRejects() {
		var limiter = limiter(5, 1);
		var key = key();
		for (int i = 4; i >= 0; i--) {
			var d = limiter.tryAcquire(key);
			assertThat(d.allowed()).isTrue();
			assertThat(d.remaining()).isEqualTo(i);
		}
		var rejected = limiter.tryAcquire(key);
		assertThat(rejected.allowed()).isFalse();
		assertThat(rejected.remaining()).isZero();
		assertThat(rejected.retryAfterSeconds()).isPositive();
	}

	@Test
	void refillsTokensOverTime() {
		var limiter = limiter(2, 10);
		var key = key();
		limiter.tryAcquire(key);
		limiter.tryAcquire(key);
		assertThat(limiter.tryAcquire(key).allowed()).isFalse();
		await().atMost(Duration.ofSeconds(2))
				.untilAsserted(() -> assertThat(limiter.tryAcquire(key).allowed()).isTrue());
	}

	@Test
	void keysAreIsolated() {
		var limiter = limiter(1, 1);
		var a = key();
		var b = key();
		limiter.tryAcquire(a);
		assertThat(limiter.tryAcquire(a).allowed()).isFalse();
		assertThat(limiter.tryAcquire(b).allowed()).isTrue();
	}

	@Test
	void bucketKeyExpiresWhenIdle() {
		var limiter = limiter(5, 1);
		var key = key();
		limiter.tryAcquire(key);
		assertThat(redisTemplate.getExpire("rl:" + key)).isPositive();
	}

	@Test
	void concurrentCallersNeverExceedCapacity() throws Exception {
		var limiter = limiter(20, 1);
		var key = key();
		var allowed = new AtomicInteger();
		try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
			IntStream.range(0, 50).forEach(i -> executor.submit(() -> {
				if (limiter.tryAcquire(key).allowed()) {
					allowed.incrementAndGet();
				}
			}));
		}
		assertThat(allowed.get()).isEqualTo(20);
	}
}
