package dev.portfolio.inventory;

import io.grpc.Grpc;
import io.grpc.InsecureServerCredentials;
import io.grpc.Server;
import io.javalin.Javalin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public final class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    private Main() {}

    public record Handle(Javalin http, Server grpc) {
        public int httpPort() { return http.port(); }
        public int grpcPort() { return grpc.getPort(); }
        public void stop() {
            grpc.shutdownNow();
            http.stop();
        }
    }

    public static Handle start(int httpPort, int grpcPort) throws IOException {
        StockService stock = new StockService();
        Server grpc = Grpc.newServerBuilderForPort(grpcPort, InsecureServerCredentials.create())
                .addService(new InventoryGrpcService(stock))
                .intercept(new CorrelationInterceptor())
                .build()
                .start();
        Javalin http = HttpApi.create(stock).start(httpPort);
        log.info("inventory-service started http={} grpc={}", http.port(), grpc.getPort());
        return new Handle(http, grpc);
    }

    public static void main(String[] args) throws IOException {
        Handle h = start(port("HTTP_PORT", 8081), port("GRPC_PORT", 9091));
        Runtime.getRuntime().addShutdownHook(new Thread(h::stop));
    }

    static int port(String env, int fallback) {
        String v = System.getenv(env);
        return v == null || v.isBlank() ? fallback : Integer.parseInt(v);
    }
}
