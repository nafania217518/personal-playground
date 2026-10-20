package dev.portfolio.pricing

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StartTest {
    @Test fun awaitBlocksUntilStop() {
        val h = start(httpPort = 0, grpcPort = 0)
        val t = Thread { h.await() }.apply { start() }
        t.join(300)
        assertTrue(t.isAlive, "await returned before stop")
        h.stop()
        t.join(5000)
        assertTrue(!t.isAlive, "await did not return after stop")
    }

    @Test fun startStartsAndStopsOnFreePorts() {
        val h = start(httpPort = 0, grpcPort = 0)
        try {
            assertTrue(h.httpPort > 0 && h.grpcPort > 0)
            val r = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI("http://localhost:${h.httpPort}/health")).build(),
                HttpResponse.BodyHandlers.ofString()
            )
            assertEquals(200, r.statusCode())
            assertEquals("""{"status":"UP"}""", r.body())
        } finally { h.stop() }
    }
}
