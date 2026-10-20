package dev.portfolio.taskqueue;

import java.util.UUID;

public record TaskStatus(UUID id, TaskType type, Status status, int attempts) {

	public enum Status {
		PENDING, DONE, FAILED
	}
}
