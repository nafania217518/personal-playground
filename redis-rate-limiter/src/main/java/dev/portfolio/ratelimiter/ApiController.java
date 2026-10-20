package dev.portfolio.ratelimiter;

import java.util.Map;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ApiController {

	public record LimitStatus(String key, long tokens, int capacity) {
	}

	private final StringRedisTemplate redis;
	private final RateLimitProperties props;

	public ApiController(StringRedisTemplate redis, RateLimitProperties props) {
		this.redis = redis;
		this.props = props;
	}

	@GetMapping("/ping")
	public Map<String, Boolean> ping() {
		return Map.of("pong", true);
	}

	/** Reads the stored token count. Returns capacity when no bucket exists. */
	@GetMapping("/limits/{key}")
	public LimitStatus limits(@PathVariable String key) {
		var tokens = (String) redis.opsForHash().get("rl:" + key, "tokens");
		return new LimitStatus(key, tokens == null ? props.capacity() : (long) Double.parseDouble(tokens),
				props.capacity());
	}
}
