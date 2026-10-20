package dev.portfolio.kafkaorders;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** In-memory per-customer stats. Idempotent: an eventId is applied once. */
@Component
public class StatsAggregator {

	static final int MAX_PROCESSED_IDS = 10_000;

	private final Map<String, CustomerStats> stats = new ConcurrentHashMap<>();

	// Bounded in-memory set of processed ids. Replace with Redis or a database in production.
	private final Set<String> processedIds = Collections.newSetFromMap(Collections.synchronizedMap(
			new LinkedHashMap<>(16, 0.75f, true) {
				@Override
				protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
					return size() > MAX_PROCESSED_IDS;
				}
			}));

	public void apply(OrderEvent event) {
		if ("poison".equals(event.customerId())) {
			throw new IllegalStateException("poison event " + event.eventId());
		}
		if (!processedIds.add(event.eventId())) {
			return;
		}
		stats.merge(event.customerId(), delta(event), (old, d) -> old.plus(d.orderCount(), d.totalRevenue()));
	}

	public CustomerStats get(String customerId) {
		return stats.getOrDefault(customerId, CustomerStats.zero(customerId));
	}

	private static CustomerStats delta(OrderEvent event) {
		return switch (event) {
			case OrderEvent.OrderPlaced p -> new CustomerStats(p.customerId(), 1, p.amount());
			case OrderEvent.OrderCancelled c -> new CustomerStats(c.customerId(), -1, c.amount().negate());
		};
	}
}
