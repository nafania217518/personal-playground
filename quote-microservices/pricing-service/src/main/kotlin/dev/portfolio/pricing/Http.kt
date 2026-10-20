package dev.portfolio.pricing

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import org.slf4j.MDC
import java.util.UUID

const val HTTP_CORRELATION_HEADER = "X-Correlation-Id"

@Serializable data class PriceDto(val sku: String, val unitPriceCents: Long, val currency: String = CURRENCY)
@Serializable data class PriceUpdate(val unitPriceCents: Long)

fun Application.module(book: PriceBook) {
    install(ContentNegotiation) { json() }
    intercept(ApplicationCallPipeline.Plugins) {
        val id = context.request.header(HTTP_CORRELATION_HEADER) ?: UUID.randomUUID().toString()
        context.response.header(HTTP_CORRELATION_HEADER, id)
        MDC.put(MDC_KEY, id)
        try { proceed() } finally { MDC.remove(MDC_KEY) }
    }
    routing {
        get("/health") { call.respond(mapOf("status" to "UP")) }
        get("/prices") { call.respond(book.prices.toMap()) }
        get("/prices/{sku}") {
            val sku = call.parameters["sku"]!!
            val unit = book.prices[sku]
            if (unit == null) call.respond(HttpStatusCode.NotFound, mapOf("error" to "unknown sku $sku"))
            else call.respond(PriceDto(sku, unit))
        }
        put("/prices/{sku}") {
            val sku = call.parameters["sku"]!!
            val body = call.receive<PriceUpdate>()
            if (body.unitPriceCents < 0) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "unitPriceCents must be >= 0"))
            } else {
                book.prices[sku] = body.unitPriceCents
                call.respond(PriceDto(sku, body.unitPriceCents))
            }
        }
    }
}
