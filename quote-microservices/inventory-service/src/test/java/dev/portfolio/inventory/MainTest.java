package dev.portfolio.inventory;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

class MainTest {
    @Test
    void mainStartsAndStopsOnFreePorts() throws Exception {
        Main.Handle h = Main.start(0, 0);
        try {
            assertTrue(h.httpPort() > 0);
            assertTrue(h.grpcPort() > 0);
            var res = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + h.httpPort() + "/health")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals("{\"status\":\"UP\"}", res.body());
        } finally {
            h.stop();
        }
    }

    @Test
    void portReadsEnvOrFallback() {
        assertEquals(8081, Main.port("HTTP_PORT_DOES_NOT_EXIST", 8081));
        assertEquals(9091, Main.port("GRPC_PORT_DOES_NOT_EXIST", 9091));
    }
}
