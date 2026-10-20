package dev.portfolio.pricing

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HttpTest {
    private fun app(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application { module(PriceBook()) }
        block()
    }

    @Test fun httpGetPricesReturnsSeededMap() = app {
        val r = client.get("/prices")
        assertEquals(HttpStatusCode.OK, r.status)
        assertEquals("""{"SKU-1":1999,"SKU-2":500,"SKU-3":12000}""", r.bodyAsText())
    }

    @Test fun httpPutUpdatesPriceAndGetReflectsIt() = app {
        val put = client.put("/prices/SKU-2") {
            contentType(ContentType.Application.Json); setBody("""{"unitPriceCents":700}""")
        }
        assertEquals(HttpStatusCode.OK, put.status)
        assertEquals("""{"sku":"SKU-2","unitPriceCents":700,"currency":"EUR"}""", client.get("/prices/SKU-2").bodyAsText())
    }

    @Test fun httpGetUnknownSkuReturns404() = app {
        assertEquals(HttpStatusCode.NotFound, client.get("/prices/NOPE").status)
    }

    @Test fun httpNegativePriceReturns400() = app {
        val r = client.put("/prices/SKU-1") {
            contentType(ContentType.Application.Json); setBody("""{"unitPriceCents":-1}""")
        }
        assertEquals(HttpStatusCode.BadRequest, r.status)
    }

    @Test fun httpEchoesCorrelationIdHeader() = app {
        val r = client.get("/health") { header(HTTP_CORRELATION_HEADER, "corr-9") }
        assertEquals("corr-9", r.headers[HTTP_CORRELATION_HEADER])
        assertEquals("""{"status":"UP"}""", r.bodyAsText())
        assertTrue(client.get("/health").headers[HTTP_CORRELATION_HEADER]!!.length == 36)
    }
}
