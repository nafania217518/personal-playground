package dev.portfolio.sensorstore;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Stream;

/** Maps instants to UTC day partitions. */
final class DayBucket {

	private DayBucket() {
	}

	static LocalDate dayOf(Instant ts) {
		return ts.atZone(ZoneOffset.UTC).toLocalDate();
	}

	/** Every UTC day in [from, to], newest first. */
	static List<LocalDate> daysNewestFirst(Instant from, Instant to) {
		var first = dayOf(from);
		return Stream.iterate(dayOf(to), d -> !d.isBefore(first), d -> d.minusDays(1)).toList();
	}
}
