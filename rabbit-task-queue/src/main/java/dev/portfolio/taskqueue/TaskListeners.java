package dev.portfolio.taskqueue;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
class TaskListeners {

	private final TaskStatusStore store;

	TaskListeners(TaskStatusStore store) {
		this.store = store;
	}

	@RabbitListener(queues = "tasks.email")
	void email(Task task) {
		process(task);
	}

	@RabbitListener(queues = "tasks.report")
	void report(Task task) {
		process(task);
	}

	private void process(Task task) {
		store.attempt(task);
		if ("fail".equals(task.payload())) {
			throw new IllegalStateException("task " + task.id() + " failed");
		}
		store.done(task);
	}
}
