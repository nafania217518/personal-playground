package dev.portfolio.taskqueue;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class TaskStatusStoreTest {

	private final TaskStatusStore store = new TaskStatusStore();
	private final Task task = new Task(UUID.randomUUID(), TaskType.EMAIL, "hello", 0);

	@Test
	void pendingTaskIsStoredWithZeroAttempts() {
		store.pending(task);
		assertThat(store.find(task.id())).contains(new TaskStatus(task.id(), TaskType.EMAIL, TaskStatus.Status.PENDING, 0));
	}

	@Test
	void attemptIncrementsAttemptsAndKeepsStatus() {
		store.pending(task);
		store.attempt(task);
		store.attempt(task);
		assertThat(store.find(task.id()).orElseThrow().attempts()).isEqualTo(2);
		assertThat(store.find(task.id()).orElseThrow().status()).isEqualTo(TaskStatus.Status.PENDING);
	}

	@Test
	void doneAndFailedChangeStatusAndKeepAttempts() {
		store.pending(task);
		store.attempt(task);
		store.done(task);
		assertThat(store.find(task.id()).orElseThrow().status()).isEqualTo(TaskStatus.Status.DONE);
		store.failed(task);
		var failed = store.find(task.id()).orElseThrow();
		assertThat(failed.status()).isEqualTo(TaskStatus.Status.FAILED);
		assertThat(failed.attempts()).isEqualTo(1);
	}

	@Test
	void unknownTaskIsAbsentAndAttemptOnUnknownTaskCreatesEntry() {
		assertThat(store.find(task.id())).isEmpty();
		store.attempt(task);
		assertThat(store.find(task.id()).orElseThrow().attempts()).isEqualTo(1);
	}
}
