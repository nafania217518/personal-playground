package dev.portfolio.sensorstore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.datastax.oss.driver.api.core.CqlSession;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "sensors.ttl-seconds=2")
@AutoConfigureMockMvc
class TtlTest {

	@Autowired MockMvc mvc;
	@Autowired CqlSession session;

	@Test
	void writesHaveTtlAndExpire() throws Exception {
		var ts = Instant.parse("2026-07-01T00:00:00Z");
		mvc.perform(post("/sensors/ttl-sensor/readings").contentType(MediaType.APPLICATION_JSON)
				.content("{\"ts\":\"%s\",\"value\":1,\"unit\":\"C\"}".formatted(ts)))
				.andExpect(status().isCreated());

		var ttl = session.execute("SELECT TTL(value) FROM readings WHERE sensor_id='ttl-sensor' AND day=? AND ts=?",
				LocalDate.of(2026, 7, 1), ts).one();
		assertThat(ttl).isNotNull();
		assertThat(ttl.getInt(0)).isBetween(1, 2);

		Awaitility.await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(500)).untilAsserted(() ->
				assertThat(session.execute("SELECT value FROM readings WHERE sensor_id='ttl-sensor' AND day=? AND ts=?",
						LocalDate.of(2026, 7, 1), ts).one()).isNull());
	}
}
