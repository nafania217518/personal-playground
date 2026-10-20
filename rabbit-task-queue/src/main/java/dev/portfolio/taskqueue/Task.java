package dev.portfolio.taskqueue;

import java.util.UUID;

public record Task(UUID id, TaskType type, String payload, int attempts) {
}
