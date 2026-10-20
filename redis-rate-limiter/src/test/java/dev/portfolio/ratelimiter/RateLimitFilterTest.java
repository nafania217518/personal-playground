package dev.portfolio.ratelimiter;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = { "ratelimit.capacity=2", "ratelimit.refill-per-second=1" })
@AutoConfigureMockMvc
class RateLimitFilterTest {

	@Autowired
	MockMvc mvc;

	@Test
	void filterReturns429WithRetryAfterWhenLimited() throws Exception {
		var key = UUID.randomUUID().toString();
		mvc.perform(get("/api/ping").header("X-Api-Key", key))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.pong").value(true))
				.andExpect(header().string("X-RateLimit-Remaining", "1"));
		mvc.perform(get("/api/ping").header("X-Api-Key", key))
				.andExpect(status().isOk())
				.andExpect(header().string("X-RateLimit-Remaining", "0"));
		mvc.perform(get("/api/ping").header("X-Api-Key", key))
				.andExpect(status().isTooManyRequests())
				.andExpect(header().string("Retry-After", "1"))
				.andExpect(header().string("X-RateLimit-Remaining", "0"));
	}

	@Test
	void fallsBackToRemoteAddressWhenNoApiKey() throws Exception {
		var addr = "10.0.0." + (int) (Math.random() * 250);
		mvc.perform(get("/api/ping").with(r -> { r.setRemoteAddr(addr); return r; }))
				.andExpect(status().isOk())
				.andExpect(header().exists("X-RateLimit-Remaining"));
	}

	@Test
	void limitStatusEndpointShowsBucket() throws Exception {
		var key = UUID.randomUUID().toString();
		mvc.perform(get("/api/limits/" + key))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.key").value(key))
				.andExpect(jsonPath("$.tokens").value(2))
				.andExpect(jsonPath("$.capacity").value(2));
		mvc.perform(get("/api/ping").header("X-Api-Key", key));
		for (int i = 0; i < 5; i++) {
			mvc.perform(get("/api/limits/" + key))
					.andExpect(status().isOk())
					.andExpect(header().doesNotExist("X-RateLimit-Remaining"))
					.andExpect(jsonPath("$.tokens").value(1));
		}
	}
}
