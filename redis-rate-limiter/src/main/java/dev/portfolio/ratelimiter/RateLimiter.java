package dev.portfolio.ratelimiter;

import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/** Token bucket in Redis. One atomic Lua script per call. State: hash rl:{key} with fields tokens and ts. */
@Component
public class RateLimiter {

	public record Decision(boolean allowed, long remaining, long retryAfterSeconds) {
	}

	private static final DefaultRedisScript<List> SCRIPT = new DefaultRedisScript<>("""
			local capacity = tonumber(ARGV[1])
			local refill = tonumber(ARGV[2])
			local now = tonumber(ARGV[3])
			local tokens = tonumber(redis.call('HGET', KEYS[1], 'tokens'))
			local ts = tonumber(redis.call('HGET', KEYS[1], 'ts'))
			if tokens == nil then
			  tokens = capacity
			  ts = now
			end
			tokens = math.min(capacity, tokens + (now - ts) / 1000 * refill)
			local allowed = 0
			local retry = 0
			if tokens >= 1 then
			  tokens = tokens - 1
			  allowed = 1
			else
			  retry = math.ceil((1 - tokens) / refill)
			end
			redis.call('HSET', KEYS[1], 'tokens', tokens, 'ts', now)
			redis.call('EXPIRE', KEYS[1], math.max(1, math.ceil(capacity / refill)))
			return {allowed, math.floor(tokens), retry}
			""", List.class);

	private final StringRedisTemplate redis;
	private final RateLimitProperties props;

	public RateLimiter(StringRedisTemplate redis, RateLimitProperties props) {
		this.redis = redis;
		this.props = props;
	}

	public Decision tryAcquire(String key) {
		List<?> r = redis.execute(SCRIPT, List.of("rl:" + key), String.valueOf(props.capacity()),
				String.valueOf(props.refillPerSecond()), String.valueOf(System.currentTimeMillis()));
		return new Decision((Long) r.get(0) == 1, (Long) r.get(1), (Long) r.get(2));
	}
}
