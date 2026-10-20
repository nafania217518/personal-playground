package dev.portfolio.sensorstore;

import static org.springframework.data.cassandra.core.query.Criteria.where;
import static org.springframework.data.cassandra.core.query.Query.query;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.cassandra.core.CassandraTemplate;
import org.springframework.data.cassandra.core.InsertOptions;
import org.springframework.data.cassandra.core.query.CassandraPageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/sensors/{sensorId}")
class SensorController {

	record ReadingRequest(Instant ts, @NotNull Double value, @NotBlank String unit) {
		Reading toReading(String sensorId) {
			return new Reading(ReadingKey.of(sensorId, ts == null ? Instant.now() : ts), value, unit);
		}
	}

	record ReadingResponse(String sensorId, Instant ts, double value, String unit) {
		static ReadingResponse of(Reading r) {
			return new ReadingResponse(r.key().sensorId(), r.key().ts(), r.value(), r.unit());
		}
	}

	record Page(List<ReadingResponse> content, String pagingState) {
	}

	record NameRequest(@NotBlank String name) {
	}

	private final CassandraTemplate template;
	private final InsertOptions ttl;

	SensorController(CassandraTemplate template, @Value("${sensors.ttl-seconds}") long ttlSeconds) {
		this.template = template;
		this.ttl = InsertOptions.builder().ttl(Duration.ofSeconds(ttlSeconds)).build();
	}

	@PostMapping("/readings")
	@ResponseStatus(HttpStatus.CREATED)
	ReadingResponse post(@PathVariable String sensorId, @Valid @RequestBody ReadingRequest request) {
		var reading = request.toReading(sensorId);
		template.insert(reading, ttl);
		template.insert(meta(sensorId, reading));
		return ReadingResponse.of(reading);
	}

	@PostMapping("/readings/batch")
	@ResponseStatus(HttpStatus.CREATED)
	Map<String, Integer> batch(@PathVariable String sensorId, @RequestBody @Size(max = 50) List<@Valid ReadingRequest> requests) {
		var readings = requests.stream().map(r -> r.toReading(sensorId)).toList();
		var latest = readings.stream().max((a, b) -> a.key().ts().compareTo(b.key().ts())).orElseThrow();
		// Logged batch: all rows share the sensor partition, so this stays a single-partition batch.
		template.batchOps().insert(readings, ttl).insert(meta(sensorId, latest)).execute();
		return Map.of("inserted", readings.size());
	}

	@GetMapping("/readings")
	Page list(@PathVariable String sensorId, @RequestParam Instant from, @RequestParam Instant to,
			@RequestParam(defaultValue = "100") int size, @RequestParam(required = false) String pagingState) {
		// One query per day partition, newest day first. Token = "<day>:<base64 driver state>".
		var days = DayBucket.daysNewestFirst(from, to);
		var i = 0;
		ByteBuffer state = null;
		if (pagingState != null) {
			var parts = pagingState.split(":", 2);
			i = days.indexOf(LocalDate.parse(parts[0]));
			state = parts[1].isEmpty() ? null : ByteBuffer.wrap(Base64.getDecoder().decode(parts[1]));
		}
		var content = new ArrayList<ReadingResponse>();
		while (i < days.size() && content.size() < size) {
			Pageable page = CassandraPageRequest.first(size - content.size());
			if (state != null) {
				page = CassandraPageRequest.of(page, state);
			}
			var query = query(where("sensor_id").is(sensorId), where("day").is(days.get(i)),
					where("ts").gte(from), where("ts").lte(to)).pageRequest(page);
			var slice = template.slice(query, Reading.class);
			slice.forEach(r -> content.add(ReadingResponse.of(r)));
			if (slice.hasNext()) {
				var next = ((CassandraPageRequest) slice.nextPageable()).getPagingState();
				return new Page(content, days.get(i) + ":" + encode(next));
			}
			state = null;
			i++;
		}
		return new Page(content, i < days.size() ? days.get(i) + ":" : null);
	}

	@GetMapping
	ResponseEntity<SensorMeta> get(@PathVariable String sensorId) {
		return ResponseEntity.ofNullable(template.selectOneById(sensorId, SensorMeta.class));
	}

	@PutMapping
	SensorMeta rename(@PathVariable String sensorId, @Valid @RequestBody NameRequest request) {
		// Read-modify-write. A lost update only affects the name, so last writer wins.
		var current = template.selectOneById(sensorId, SensorMeta.class);
		var updated = current == null ? new SensorMeta(sensorId, request.name(), null, null)
				: new SensorMeta(sensorId, request.name(), current.lastValue(), current.lastTs());
		return template.insert(updated);
	}

	private SensorMeta meta(String sensorId, Reading latest) {
		var current = template.selectOneById(sensorId, SensorMeta.class);
		var name = current == null ? null : current.name();
		return new SensorMeta(sensorId, name, latest.value(), latest.key().ts());
	}

	private static String encode(ByteBuffer state) {
		var bytes = new byte[state.remaining()];
		state.duplicate().get(bytes);
		return Base64.getEncoder().encodeToString(bytes);
	}
}
