package dev.portfolio.taskqueue;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class TaskStatusStore {

	// In-memory store. Replace with a database for durability.
	private final Map<UUID, TaskStatus> statuses = new ConcurrentHashMap<>();

	public TaskStatus pending(Task task) {
		return update(task, TaskStatus.Status.PENDING, 0);
	}

	public TaskStatus attempt(Task task) {
		return statuses.compute(task.id(), (id, cur) -> new TaskStatus(id, task.type(),
				cur == null ? TaskStatus.Status.PENDING : cur.status(), cur == null ? 1 : cur.attempts() + 1));
	}

	public TaskStatus done(Task task) {
		return update(task, TaskStatus.Status.DONE, 0);
	}

	public TaskStatus failed(Task task) {
		return update(task, TaskStatus.Status.FAILED, 0);
	}

	public Optional<TaskStatus> find(UUID id) {
		return Optional.ofNullable(statuses.get(id));
	}

	private TaskStatus update(Task task, TaskStatus.Status status, int delta) {
		return statuses.compute(task.id(),
				(id, cur) -> new TaskStatus(id, task.type(), status, (cur == null ? 0 : cur.attempts()) + delta));
	}
}
