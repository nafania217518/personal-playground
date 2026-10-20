package dev.portfolio.kafkaorders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class StatsAggregatorTest {

	private final StatsAggregator stats = new StatsAggregator();

	@Test
	void placedEventAddsOrderAndRevenue() {
		stats.apply(new OrderEvent.OrderPlaced("e1", "o1", "c1", new BigDecimal("10.50")));
		stats.apply(new OrderEvent.OrderPlaced("e2", "o2", "c1", new BigDecimal("2.00")));

		assertThat(stats.get("c1")).isEqualTo(new CustomerStats("c1", 2, new BigDecimal("12.50")));
	}

	@Test
	void duplicateEventIsCountedOnce() {
		var event = new OrderEvent.OrderPlaced("e1", "o1", "c1", new BigDecimal("10.50"));
		stats.apply(event);
		stats.apply(event);

		assertThat(stats.get("c1").orderCount()).isEqualTo(1);
	}

	@Test
	void cancelledEventSubtractsOrderAndRevenue() {
		stats.apply(new OrderEvent.OrderPlaced("e1", "o1", "c1", new BigDecimal("10.50")));
		stats.apply(new OrderEvent.OrderCancelled("e2", "o1", "c1", new BigDecimal("10.50")));

		assertThat(stats.get("c1")).isEqualTo(new CustomerStats("c1", 0, new BigDecimal("0.00")));
	}

	@Test
	void unknownCustomerHasZeroStats() {
		assertThat(stats.get("nobody")).isEqualTo(new CustomerStats("nobody", 0, BigDecimal.ZERO));
	}

	@Test
	void poisonCustomerThrows() {
		var poison = new OrderEvent.OrderPlaced("e1", "o1", "poison", BigDecimal.ONE);
		assertThatThrownBy(() -> stats.apply(poison)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void processedIdSetIsBounded() {
		for (int i = 0; i < StatsAggregator.MAX_PROCESSED_IDS + 1; i++) {
			stats.apply(new OrderEvent.OrderPlaced("e" + i, "o" + i, "c1", BigDecimal.ONE));
		}
		// the oldest id is evicted, so the first event is counted again
		stats.apply(new OrderEvent.OrderPlaced("e0", "o0", "c1", BigDecimal.ONE));

		assertThat(stats.get("c1").orderCount()).isEqualTo(StatsAggregator.MAX_PROCESSED_IDS + 2);
	}
}
