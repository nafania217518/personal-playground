package dev.portfolio.sensorstore;

import java.time.Instant;
import java.time.LocalDate;
import org.springframework.data.cassandra.core.cql.Ordering;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyClass;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;

@PrimaryKeyClass
record ReadingKey(
		@PrimaryKeyColumn(name = "sensor_id", type = PrimaryKeyType.PARTITIONED, ordinal = 0) String sensorId,
		@PrimaryKeyColumn(name = "day", type = PrimaryKeyType.PARTITIONED, ordinal = 1) LocalDate day,
		@PrimaryKeyColumn(name = "ts", type = PrimaryKeyType.CLUSTERED, ordinal = 2, ordering = Ordering.DESCENDING) Instant ts) {

	static ReadingKey of(String sensorId, Instant ts) {
		return new ReadingKey(sensorId, DayBucket.dayOf(ts), ts);
	}
}
