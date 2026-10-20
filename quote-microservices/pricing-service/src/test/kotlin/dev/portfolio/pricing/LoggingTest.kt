package dev.portfolio.pricing

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.LoggingEvent
import net.logstash.logback.encoder.LogstashEncoder
import kotlin.test.Test
import kotlin.test.assertTrue

class LoggingTest {
    @Test fun jsonLogLineContainsServiceAndCorrelationId() {
        val ctx = LoggerContext()
        val encoder = LogstashEncoder().apply {
            context = ctx
            customFields = """{"service":"pricing-service"}"""
            start()
        }
        val event = LoggingEvent("x", ctx.getLogger("t"), Level.INFO, "hello", null, null)
        event.mdcPropertyMap = mapOf("correlationId" to "xyz")
        val json = String(encoder.encode(event))
        assertTrue(json.contains("\"service\":\"pricing-service\""), json)
        assertTrue(json.contains("\"correlationId\":\"xyz\""), json)
    }
}
