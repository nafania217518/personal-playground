package dev.portfolio.taskqueue;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/tasks")
class TaskController {

	record NewTask(@NotNull TaskType type, @NotBlank String payload) {
	}

	private final TaskStatusStore store;
	private final RabbitTemplate rabbit;
	private final AmqpAdmin admin;

	TaskController(TaskStatusStore store, RabbitTemplate rabbit, AmqpAdmin admin) {
		this.store = store;
		this.rabbit = rabbit;
		this.admin = admin;
	}

	@PostMapping
	ResponseEntity<TaskStatus> submit(@Valid @RequestBody NewTask request) {
		var task = new Task(UUID.randomUUID(), request.type(), request.payload(), 0);
		var status = store.pending(task);
		rabbit.convertAndSend(RabbitConfig.EXCHANGE, task.type().routingKey(), task,
				new CorrelationData(task.id().toString()));
		return ResponseEntity.accepted().body(status);
	}

	@GetMapping("/{id}")
	ResponseEntity<TaskStatus> status(@PathVariable UUID id) {
		return ResponseEntity.of(store.find(id));
	}

	@PostMapping("/dlq/replay")
	Map<String, Integer> replay() {
		var replayed = 0;
		for (Message m = rabbit.receive(RabbitConfig.DLQ); m != null; m = rabbit.receive(RabbitConfig.DLQ)) {
			rabbit.send(RabbitConfig.EXCHANGE, originalRoutingKey(m), m);
			replayed++;
		}
		return Map.of("replayed", replayed);
	}

	@SuppressWarnings("unchecked")
	private static String originalRoutingKey(Message m) {
		var death = m.getMessageProperties().getXDeathHeader().getFirst();
		return ((List<String>) death.get("routing-keys")).getFirst();
	}

	@GetMapping("/queues")
	Map<String, Long> queues() {
		var counts = new LinkedHashMap<String, Long>();
		Stream.of("tasks.email", "tasks.report", RabbitConfig.DLQ)
				.forEach(q -> counts.put(q, admin.getQueueInfo(q).getMessageCount()));
		return counts;
	}
}
