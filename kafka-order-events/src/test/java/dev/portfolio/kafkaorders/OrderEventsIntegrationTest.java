package dev.portfolio.kafkaorders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.kafka.autoconfigure.KafkaConnectionDetails;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.utils.KafkaTestUtils;

@Import(TestcontainersConfiguration.class)
@AutoConfigureRestTestClient
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderEventsIntegrationTest {

	@Autowired RestTestClient http;
	@Autowired KafkaConnectionDetails kafka;
	@Autowired KafkaAdmin admin;
	@Autowired KafkaTemplate<String, OrderEvent> template;

	@Test
	void postOrderPublishesOrderPlacedEventToOrdersTopic() {
		try (var consumer = consumerOf("orders")) {
			var body = place("c-post", 10.50);

			var orderId = (String) body.get("orderId");
			assertThat(orderId).isNotBlank();
			assertThat(body).containsEntry("customerId", "c-post").containsEntry("amount", 10.50);

			var records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10));
			assertThat(records).anySatisfy(r -> {
				assertThat(r.key()).isEqualTo(orderId);
				assertThat(r.value()).contains("\"customerId\":\"c-post\"");
			});
		}
	}

	@Test
	void invalidOrderReturns400() {
		http.post().uri("/orders").body(Map.of("customerId", "", "amount", -1)).exchange()
				.expectStatus().isBadRequest();
	}

	@Test
	void consumedOrderPlacedUpdatesCustomerRevenueStats() {
		place("c-stats", 10.50);
		place("c-stats", 4.50);

		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
			var stats = stats("c-stats");
			assertThat(stats.orderCount()).isEqualTo(2);
			assertThat(stats.totalRevenue()).isEqualByComparingTo("15.00");
		});
	}

	@Test
	void duplicateEventIsCountedOnce() {
		var event = new OrderEvent.OrderPlaced(UUID.randomUUID().toString(), "o-dup", "c-dup", new BigDecimal("5.00"));
		template.send("orders", event.orderId(), event);
		template.send("orders", event.orderId(), event);

		await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
				assertThat(stats("c-dup").orderCount()).isEqualTo(1));
		// give a possible second update time to land, then confirm it did not
		await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(5)).untilAsserted(() ->
				assertThat(stats("c-dup").orderCount()).isEqualTo(1));
	}

	@Test
	void orderCancelledSubtractsFromStats() {
		var orderId = (String) place("c-cancel", 20).get("orderId");
		await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
				assertThat(stats("c-cancel").orderCount()).isEqualTo(1));

		http.delete().uri("/orders/" + orderId).exchange().expectStatus().isNoContent();

		await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
				assertThat(stats("c-cancel")).satisfies(stats -> {
					assertThat(stats.orderCount()).isZero();
					assertThat(stats.totalRevenue()).isEqualByComparingTo("0");
				}));
	}

	@Test
	void deleteUnknownOrderReturns404() {
		http.delete().uri("/orders/nope").exchange().expectStatus().isNotFound();
	}

	@Test
	void poisonMessageGoesToDeadLetterTopicAfterRetries() {
		try (var dlt = consumerOf("orders.DLT")) {
			place("poison", 1);

			var records = KafkaTestUtils.getRecords(dlt, Duration.ofSeconds(20));
			assertThat(records).anySatisfy(r -> assertThat(r.value()).contains("\"customerId\":\"poison\""));
		}
	}

	@Test
	void topicsAreCreatedOnStartup() {
		var topics = admin.describeTopics("orders", "orders.DLT");

		assertThat(topics.get("orders").partitions()).hasSize(3);
		assertThat(topics).containsKey("orders.DLT");
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> place(String customerId, double amount) {
		return http.post().uri("/orders").body(Map.of("customerId", customerId, "amount", amount)).exchange()
				.expectStatus().isCreated().expectBody(Map.class).returnResult().getResponseBody();
	}

	private CustomerStats stats(String customerId) {
		return http.get().uri("/stats/" + customerId).exchange()
				.expectStatus().isOk().expectBody(CustomerStats.class).returnResult().getResponseBody();
	}

	private KafkaConsumer<String, String> consumerOf(String topic) {
		var props = KafkaTestUtils.consumerProps(String.join(",", kafka.getBootstrapServers()), "test-" + UUID.randomUUID(), true);
		props.put("key.deserializer", StringDeserializer.class);
		props.put("value.deserializer", StringDeserializer.class);
		var consumer = new KafkaConsumer<String, String>(props);
		consumer.subscribe(java.util.List.of(topic));
		consumer.poll(Duration.ofSeconds(2)); // join the group before producing
		return consumer;
	}
}
