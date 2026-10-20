package dev.portfolio.sensorstore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.datastax.oss.driver.api.core.CqlSession;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.cassandra.core.CassandraTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class SensorApiTest {

	@Autowired MockMvc mvc;
	@Autowired CassandraTemplate template;
	@Autowired CqlSession session;

	private static String id() {
		return "s-" + UUID.randomUUID();
	}

	private void postReading(String sensor, Instant ts, double value) throws Exception {
		mvc.perform(post("/sensors/{id}/readings", sensor).contentType(MediaType.APPLICATION_JSON)
				.content("{\"ts\":\"%s\",\"value\":%s,\"unit\":\"C\"}".formatted(ts, value)))
				.andExpect(status().isCreated());
	}

	@Test
	void postReadingStoresRowInDayPartition() throws Exception {
		var sensor = id();
		var ts = Instant.parse("2026-01-15T23:30:00Z");
		mvc.perform(post("/sensors/{id}/readings", sensor).contentType(MediaType.APPLICATION_JSON)
				.content("{\"ts\":\"%s\",\"value\":21.5,\"unit\":\"C\"}".formatted(ts)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.sensorId").value(sensor))
				.andExpect(jsonPath("$.ts").value(ts.toString()))
				.andExpect(jsonPath("$.value").value(21.5))
				.andExpect(jsonPath("$.unit").value("C"));

		var row = session.execute("SELECT value FROM readings WHERE sensor_id=? AND day=? AND ts=?",
				sensor, LocalDate.of(2026, 1, 15), ts).one();
		assertThat(row).isNotNull();
		assertThat(row.getDouble("value")).isEqualTo(21.5);
	}

	@Test
	void postReadingDefaultsTsToNow() throws Exception {
		mvc.perform(post("/sensors/{id}/readings", id()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"value\":1,\"unit\":\"C\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.ts").exists());
	}

	@Test
	void invalidReadingReturns400() throws Exception {
		mvc.perform(post("/sensors/{id}/readings", id()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"unit\":\"\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void readingsAreReturnedNewestFirst() throws Exception {
		var sensor = id();
		postReading(sensor, Instant.parse("2026-02-01T10:00:00Z"), 1);
		postReading(sensor, Instant.parse("2026-02-02T10:00:00Z"), 2);
		postReading(sensor, Instant.parse("2026-02-01T12:00:00Z"), 3);
		postReading(sensor, Instant.parse("2026-02-03T10:00:00Z"), 4); // outside range

		mvc.perform(get("/sensors/{id}/readings", sensor)
				.param("from", "2026-02-01T00:00:00Z").param("to", "2026-02-02T23:59:59Z"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content", hasSize(3)))
				.andExpect(jsonPath("$.content[0].value").value(2.0))
				.andExpect(jsonPath("$.content[1].value").value(3.0))
				.andExpect(jsonPath("$.content[2].value").value(1.0))
				.andExpect(jsonPath("$.pagingState").value((Object) null));
	}

	@Test
	void pagingReturnsNextPageWithPagingState() throws Exception {
		var sensor = id();
		var base = Instant.parse("2026-04-01T00:00:00Z");
		for (int i = 0; i < 10; i++) {
			postReading(sensor, base.plusSeconds(60L * i), i);
		}
		var from = "2026-04-01T00:00:00Z";
		var to = "2026-04-01T23:59:59Z";

		var page1 = mvc.perform(get("/sensors/{id}/readings", sensor).param("from", from).param("to", to).param("size", "4"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content", hasSize(4)))
				.andExpect(jsonPath("$.content[0].value").value(9.0))
				.andReturn().getResponse().getContentAsString();
		String state1 = JsonPath.read(page1, "$.pagingState");
		assertThat(state1).isNotBlank();

		var page2 = mvc.perform(get("/sensors/{id}/readings", sensor).param("from", from).param("to", to)
				.param("size", "4").param("pagingState", state1))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content", hasSize(4)))
				.andExpect(jsonPath("$.content[0].value").value(5.0))
				.andReturn().getResponse().getContentAsString();
		String state2 = JsonPath.read(page2, "$.pagingState");
		assertThat(state2).isNotBlank();

		mvc.perform(get("/sensors/{id}/readings", sensor).param("from", from).param("to", to)
				.param("size", "4").param("pagingState", state2))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content", hasSize(2)))
				.andExpect(jsonPath("$.content[0].value").value(1.0))
				.andExpect(jsonPath("$.pagingState").value((Object) null));
	}

	@Test
	void pagingContinuesIntoOlderDay() throws Exception {
		var sensor = id();
		postReading(sensor, Instant.parse("2026-08-01T10:00:00Z"), 1);
		postReading(sensor, Instant.parse("2026-08-02T10:00:00Z"), 2);
		postReading(sensor, Instant.parse("2026-08-02T11:00:00Z"), 3);
		var from = "2026-08-01T00:00:00Z";
		var to = "2026-08-02T23:59:59Z";

		var page1 = mvc.perform(get("/sensors/{id}/readings", sensor).param("from", from).param("to", to).param("size", "2"))
				.andExpect(jsonPath("$.content", hasSize(2)))
				.andExpect(jsonPath("$.content[1].value").value(2.0))
				.andReturn().getResponse().getContentAsString();
		String state = JsonPath.read(page1, "$.pagingState");
		assertThat(state).isNotBlank();

		mvc.perform(get("/sensors/{id}/readings", sensor).param("from", from).param("to", to)
				.param("size", "2").param("pagingState", state))
				.andExpect(jsonPath("$.content", hasSize(1)))
				.andExpect(jsonPath("$.content[0].value").value(1.0))
				.andExpect(jsonPath("$.pagingState").value((Object) null));
	}

	@Test
	void batchInsertStoresAllReadings() throws Exception {
		var sensor = id();
		var items = new StringBuilder("[");
		for (int i = 0; i < 5; i++) {
			if (i > 0) items.append(',');
			items.append("{\"ts\":\"2026-05-01T00:0%d:00Z\",\"value\":%d,\"unit\":\"C\"}".formatted(i, i));
		}
		items.append(']');
		mvc.perform(post("/sensors/{id}/readings/batch", sensor).contentType(MediaType.APPLICATION_JSON)
				.content(items.toString()))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.inserted").value(5));

		var count = session.execute("SELECT count(*) FROM readings WHERE sensor_id=? AND day=?",
				sensor, LocalDate.of(2026, 5, 1)).one().getLong(0);
		assertThat(count).isEqualTo(5);

		mvc.perform(get("/sensors/{id}", sensor))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.lastValue").value(4.0))
				.andExpect(jsonPath("$.lastTs").value("2026-05-01T00:04:00Z"));
	}

	@Test
	void batchOverFiftyReturns400() throws Exception {
		var items = new StringBuilder("[");
		for (int i = 0; i < 51; i++) {
			if (i > 0) items.append(',');
			items.append("{\"value\":1,\"unit\":\"C\"}");
		}
		items.append(']');
		mvc.perform(post("/sensors/{id}/readings/batch", id()).contentType(MediaType.APPLICATION_JSON)
				.content(items.toString()))
				.andExpect(status().isBadRequest());
	}

	@Test
	void sensorMetaReflectsLatestReading() throws Exception {
		var sensor = id();
		mvc.perform(get("/sensors/{id}", sensor)).andExpect(status().isNotFound());

		postReading(sensor, Instant.parse("2026-06-01T10:00:00Z"), 10);
		postReading(sensor, Instant.parse("2026-06-01T11:00:00Z"), 11);

		mvc.perform(get("/sensors/{id}", sensor))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.sensorId").value(sensor))
				.andExpect(jsonPath("$.lastValue").value(11.0))
				.andExpect(jsonPath("$.lastTs").value("2026-06-01T11:00:00Z"));

		mvc.perform(put("/sensors/{id}", sensor).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Kitchen\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Kitchen"))
				.andExpect(jsonPath("$.lastValue").value(11.0));

		postReading(sensor, Instant.parse("2026-06-01T12:00:00Z"), 12);
		mvc.perform(get("/sensors/{id}", sensor))
				.andExpect(jsonPath("$.name").value("Kitchen"))
				.andExpect(jsonPath("$.lastValue").value(12.0));
	}

	@Test
	void putNameOnUnknownSensorCreatesMeta() throws Exception {
		var sensor = id();
		mvc.perform(put("/sensors/{id}", sensor).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"New\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("New"));
	}

	@Test
	void schemaIsCreatedOnStartup() {
		var keyspace = session.getMetadata().getKeyspace("sensors").orElseThrow();
		assertThat(keyspace.getTable("readings")).isPresent();
		assertThat(keyspace.getTable("sensor_meta")).isPresent();
	}
}
