package dev.portfolio.kafkaorders;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration(proxyBeanMethods = false)
class KafkaConfig {

	@Bean
	NewTopic ordersTopic() {
		return TopicBuilder.name("orders").partitions(3).build();
	}

	@Bean
	NewTopic ordersDltTopic() {
		// same partition count as orders: the recoverer publishes to the same partition number
		return TopicBuilder.name("orders.DLT").partitions(3).build();
	}

	/** Retry a failed record 2 times, then publish it to {@code orders.DLT}. Boot wires this into the listener container. */
	@Bean
	DefaultErrorHandler errorHandler(KafkaOperations<Object, Object> template) {
		// Spring Kafka 4 defaults the dead-letter suffix to "-dlt"; this project uses "orders.DLT"
		var recoverer = new DeadLetterPublishingRecoverer(template,
				(record, ex) -> new TopicPartition("orders.DLT", record.partition()));
		return new DefaultErrorHandler(recoverer, new FixedBackOff(0L, 2L));
	}
}
