package dev.portfolio.sensorstore;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class DayBucketTest {

	@Test
	void instantMapsToUtcDay() {
		assertThat(DayBucket.dayOf(Instant.parse("2026-03-01T23:59:59Z"))).isEqualTo(LocalDate.of(2026, 3, 1));
		assertThat(DayBucket.dayOf(Instant.parse("2026-03-02T00:00:00Z"))).isEqualTo(LocalDate.of(2026, 3, 2));
	}

	@Test
	void daysInRangeAreEnumeratedNewestFirst() {
		var days = DayBucket.daysNewestFirst(Instant.parse("2026-03-01T10:00:00Z"), Instant.parse("2026-03-03T01:00:00Z"));
		assertThat(days).containsExactly(LocalDate.of(2026, 3, 3), LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 1));
	}

	@Test
	void sameDayRangeIsOneDay() {
		var t = Instant.parse("2026-03-01T10:00:00Z");
		assertThat(DayBucket.daysNewestFirst(t, t)).containsExactly(LocalDate.of(2026, 3, 1));
	}
}
