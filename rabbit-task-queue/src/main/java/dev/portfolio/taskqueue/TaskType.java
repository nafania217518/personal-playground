package dev.portfolio.taskqueue;

public enum TaskType {
	EMAIL, REPORT;

	public String routingKey() {
		return "task." + name().toLowerCase();
	}
}
