package dev.portfolio.sensorstore;

import java.time.Instant;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

@Table("sensor_meta")
record SensorMeta(
		@PrimaryKey("sensor_id") String sensorId,
		String name,
		@Column("last_value") Double lastValue,
		@Column("last_ts") Instant lastTs) {
}
