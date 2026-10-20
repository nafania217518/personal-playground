package dev.portfolio.inventory;

import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HttpApiTest {
    private final io.javalin.Javalin app = HttpApi.create(new StockService());

    @Test
    void httpGetStockReturnsSeededMap() {
        JavalinTest.test(app, (server, client) -> {
            var res = client.get("/stock");
            assertEquals(200, res.code());
            String body = res.body().string();
            assertTrue(body.contains("\"SKU-1\":100"), body);
            assertTrue(body.contains("\"SKU-3\":0"), body);
        });
    }

    @Test
    void httpHealthIsUp() {
        JavalinTest.test(app, (server, client) ->
                assertEquals("{\"status\":\"UP\"}", client.get("/health").body().string()));
    }

    @Test
    void httpPutUpdatesStockAndGetReflectsIt() {
        JavalinTest.test(app, (server, client) -> {
            var put = client.put("/stock/SKU-9", "{\"quantity\":42}");
            assertEquals(200, put.code());
            var get = client.get("/stock/SKU-9");
            assertEquals(200, get.code());
            assertTrue(get.body().string().contains("\"available\":42"));
        });
    }

    @Test
    void httpPutNegativeReturns400() {
        JavalinTest.test(app, (server, client) ->
                assertEquals(400, client.put("/stock/SKU-1", "{\"quantity\":-1}").code()));
    }

    @Test
    void httpGetUnknownSkuReturns404() {
        JavalinTest.test(app, (server, client) -> assertEquals(404, client.get("/stock/NOPE").code()));
    }

    @Test
    void httpEchoesCorrelationIdHeader() {
        JavalinTest.test(app, (server, client) -> {
            var res = client.get("/health", req -> req.header(HttpApi.HEADER, "abc"));
            assertEquals("abc", res.headers().get(HttpApi.HEADER).getFirst());
            var generated = client.get("/health").headers().get(HttpApi.HEADER).getFirst();
            assertNotNull(generated);
            assertEquals(36, generated.length());
        });
    }
}
