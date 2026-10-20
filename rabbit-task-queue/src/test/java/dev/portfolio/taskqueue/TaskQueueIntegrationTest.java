package dev.portfolio.taskqueue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class TaskQueueIntegrationTest {

	@Autowired RestTestClient http;
	@Autowired TaskStatusStore store;
	@Autowired RabbitTemplate rabbit;
	@Autowired AmqpAdmin admin;
	@Autowired Set<String> ackedCorrelationIds;

	@SuppressWarnings("unchecked")
	private Map<String, Object> post(String path, String json) {
		return http.post().uri(path).contentType(MediaType.APPLICATION_JSON).body(json).exchange()
				.expectStatus().isAccepted().expectBody(Map.class).returnResult().getResponseBody();
	}

	private TaskStatus awaitStatus(String id, TaskStatus.Status expected) {
		return await().atMost(Duration.ofSeconds(10))
				.until(() -> store.find(UUID.fromString(id)).orElseThrow(), s -> s.status() == expected);
	}

	@Test
	void postTaskPublishesToTopicExchangeAndReturnsAccepted() {
		var body = post("/tasks", """
				{"type":"EMAIL","payload":"hello"}""");
		assertThat(body).containsEntry("type", "EMAIL").containsEntry("status", "PENDING").containsKey("id");
		// the only consumer of exchange "tasks" / key "task.email" is the email listener
		awaitStatus((String) body.get("id"), TaskStatus.Status.DONE);
	}

	@Test
	void invalidTaskReturns400() {
		http.post().uri("/tasks").contentType(MediaType.APPLICATION_JSON).body("""
				{"type":"EMAIL","payload":""}""").exchange().expectStatus().isBadRequest();
		http.post().uri("/tasks").contentType(MediaType.APPLICATION_JSON).body("""
				{"type":"SMS","payload":"x"}""").exchange().expectStatus().isBadRequest();
	}

	@Test
	void emailTaskIsProcessedByEmailListener() {
		var id = (String) post("/tasks", """
				{"type":"EMAIL","payload":"hello"}""").get("id");
		awaitStatus(id, TaskStatus.Status.DONE);
		http.get().uri("/tasks/" + id).exchange().expectStatus().isOk()
				.expectBody().jsonPath("type").isEqualTo("EMAIL").jsonPath("status").isEqualTo("DONE")
				.jsonPath("attempts").isEqualTo(1);
	}

	@Test
	void reportTaskIsProcessedByReportListener() {
		var id = (String) post("/tasks", """
				{"type":"REPORT","payload":"monthly"}""").get("id");
		assertThat(awaitStatus(id, TaskStatus.Status.DONE).type()).isEqualTo(TaskType.REPORT);
	}

	@Test
	void unknownTaskReturns404() {
		http.get().uri("/tasks/" + UUID.randomUUID()).exchange().expectStatus().isNotFound();
	}

	@Test
	void failingTaskIsRetriedThreeTimesThenDeadLettered() {
		var id = (String) post("/tasks", """
				{"type":"EMAIL","payload":"fail"}""").get("id");
		assertThat(awaitStatus(id, TaskStatus.Status.FAILED).attempts()).isEqualTo(3);
		var dead = (Task) rabbit.receiveAndConvert("tasks.dlq", 5_000);
		assertThat(dead).isNotNull();
		assertThat(dead.id()).isEqualTo(UUID.fromString(id));
	}

	@Test
	void dlqMessageCanBeRequeuedViaEndpoint() {
		var id = (String) post("/tasks", """
				{"type":"REPORT","payload":"fail"}""").get("id");
		awaitStatus(id, TaskStatus.Status.FAILED);
		await().atMost(Duration.ofSeconds(5)).until(() -> admin.getQueueInfo("tasks.dlq").getMessageCount() >= 1L);

		http.post().uri("/tasks/dlq/replay").exchange().expectStatus().isOk()
				.expectBody().jsonPath("replayed").isEqualTo(1);
		// replayed message is consumed by the report listener again: 3 more attempts, then DLQ again
		await().atMost(Duration.ofSeconds(10))
				.until(() -> store.find(UUID.fromString(id)).orElseThrow().attempts() == 6);
		assertThat((Task) rabbit.receiveAndConvert("tasks.dlq", 5_000)).isNotNull();
	}

	@Test
	void publisherConfirmIsReceived() {
		var id = (String) post("/tasks", """
				{"type":"EMAIL","payload":"hello"}""").get("id");
		await().atMost(Duration.ofSeconds(5)).until(() -> ackedCorrelationIds.contains(id));
	}

	@Test
	void queueDepthEndpointReportsCounts() {
		var body = http.get().uri("/tasks/queues").exchange().expectStatus().isOk()
				.expectBody(Map.class).returnResult().getResponseBody();
		assertThat(body).containsOnlyKeys("tasks.email", "tasks.report", "tasks.dlq");
		assertThat(body.values()).allSatisfy(v -> assertThat(((Number) v).longValue()).isGreaterThanOrEqualTo(0L));
	}
}
