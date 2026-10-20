package dev.portfolio.taskqueue;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.boot.amqp.autoconfigure.RabbitTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ParameterizedTypeReference;

@Configuration(proxyBeanMethods = false)
class RabbitConfig {

	static final String EXCHANGE = "tasks";
	static final String DLX = "tasks.dlx";
	static final String DLQ = "tasks.dlq";

	@Bean
	Declarables topology() {
		var tasks = new TopicExchange(EXCHANGE);
		var dlx = new FanoutExchange(DLX);
		var dlq = QueueBuilder.durable(DLQ).build();
		var email = QueueBuilder.durable("tasks.email").deadLetterExchange(DLX).build();
		var report = QueueBuilder.durable("tasks.report").deadLetterExchange(DLX).build();
		return new Declarables(tasks, dlx, dlq, email, report,
				BindingBuilder.bind(email).to(tasks).with(TaskType.EMAIL.routingKey()),
				BindingBuilder.bind(report).to(tasks).with(TaskType.REPORT.routingKey()),
				BindingBuilder.bind(dlq).to(dlx));
	}

	@Bean
	JacksonJsonMessageConverter messageConverter() {
		return new JacksonJsonMessageConverter("dev.portfolio.taskqueue");
	}

	@Bean
	Set<String> ackedCorrelationIds() {
		return ConcurrentHashMap.newKeySet();
	}

	@Bean
	RabbitTemplateCustomizer confirmCallback(Set<String> ackedCorrelationIds) {
		return template -> template.setConfirmCallback((correlation, ack, cause) -> {
			if (ack && correlation != null) {
				ackedCorrelationIds.add(correlation.getId());
			}
		});
	}

	/** Runs after retries are exhausted: mark FAILED, then reject so the broker dead-letters the message. */
	@Bean
	MessageRecoverer messageRecoverer(TaskStatusStore store, JacksonJsonMessageConverter converter) {
		var reject = new RejectAndDontRequeueRecoverer();
		return (message, cause) -> {
			store.failed((Task) converter.fromMessage(message, ParameterizedTypeReference.forType(Task.class)));
			reject.recover(message, cause);
		};
	}
}
