package dev.portfolio.quote.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.LoggingEvent;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import net.logstash.logback.encoder.LogstashEncoder;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class LoggingFormatTest {

    @Test
    void jsonLogLineContainsServiceAndCorrelationId() {
        Logger logger = (Logger) LoggerFactory.getLogger("test");
        LogstashEncoder encoder = new LogstashEncoder();
        encoder.setContext(logger.getLoggerContext());
        encoder.setCustomFields("{\"service\":\"gateway\"}");
        encoder.start();

        LoggingEvent event = new LoggingEvent("test", logger, Level.INFO, "hello", null, null);
        event.setMDCPropertyMap(Map.of("correlationId", "xyz"));

        String json = new String(encoder.encode(event), StandardCharsets.UTF_8);
        assertThat(json).contains("\"service\":\"gateway\"").contains("\"correlationId\":\"xyz\"");
    }
}
