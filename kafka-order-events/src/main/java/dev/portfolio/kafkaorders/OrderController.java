package dev.portfolio.kafkaorders;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
class OrderController {

	record NewOrder(@NotBlank String customerId, @Positive BigDecimal amount) {}

	record Order(String orderId, String customerId, BigDecimal amount) {}

	private final KafkaTemplate<String, OrderEvent> kafka;
	private final StatsAggregator stats;
	// In-memory order store. Replace with a database in production.
	private final Map<String, Order> orders = new ConcurrentHashMap<>();

	OrderController(KafkaTemplate<String, OrderEvent> kafka, StatsAggregator stats) {
		this.kafka = kafka;
		this.stats = stats;
	}

	@PostMapping("/orders")
	@ResponseStatus(HttpStatus.CREATED)
	Order place(@Valid @RequestBody NewOrder body) {
		var order = new Order(UUID.randomUUID().toString(), body.customerId(), body.amount());
		orders.put(order.orderId(), order);
		publish(new OrderEvent.OrderPlaced(UUID.randomUUID().toString(), order.orderId(), order.customerId(), order.amount()));
		return order;
	}

	@DeleteMapping("/orders/{orderId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void cancel(@PathVariable String orderId) {
		var order = orders.remove(orderId);
		if (order == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND);
		}
		publish(new OrderEvent.OrderCancelled(UUID.randomUUID().toString(), order.orderId(), order.customerId(), order.amount()));
	}

	@GetMapping("/stats/{customerId}")
	CustomerStats stats(@PathVariable String customerId) {
		return stats.get(customerId);
	}

	private void publish(OrderEvent event) {
		kafka.send("orders", event.orderId(), event);
	}
}
