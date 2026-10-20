package dev.portfolio.kafkaorders;

import java.math.BigDecimal;

/** Events on the {@code orders} topic. The Kafka type header selects the record on deserialization. */
public sealed interface OrderEvent {

	String eventId();
	String orderId();
	String customerId();
	BigDecimal amount();

	record OrderPlaced(String eventId, String orderId, String customerId, BigDecimal amount) implements OrderEvent {}

	record OrderCancelled(String eventId, String orderId, String customerId, BigDecimal amount) implements OrderEvent {}
}
