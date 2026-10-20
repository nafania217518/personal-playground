package dev.portfolio.sensorstore;

import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

@Table("readings")
record Reading(@PrimaryKey ReadingKey key, double value, String unit) {
}
