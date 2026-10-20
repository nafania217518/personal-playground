package dev.portfolio.kafkaorders;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
class OrderListener {

	private final StatsAggregator stats;

	OrderListener(StatsAggregator stats) {
		this.stats = stats;
	}

	@KafkaListener(topics = "orders", groupId = "stats")
	void on(OrderEvent event) {
		stats.apply(event);
	}
}
