package dev.portfolio.inventory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import net.logstash.logback.encoder.LogstashEncoder;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LoggingTest {
    @Test
    void jsonLogLineContainsServiceAndCorrelationId() {
        LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
        LogstashEncoder encoder = new LogstashEncoder();
        encoder.setContext(ctx);
        encoder.setCustomFields("{\"service\":\"inventory-service\"}");
        encoder.start();

        LoggingEvent event = new LoggingEvent("x", ctx.getLogger("test"), Level.INFO, "hello", null, null);
        event.setMDCPropertyMap(Map.of("correlationId", "xyz"));
        String json = new String(encoder.encode(event), StandardCharsets.UTF_8);

        assertTrue(json.contains("\"service\":\"inventory-service\""), json);
        assertTrue(json.contains("\"correlationId\":\"xyz\""), json);
    }
}
