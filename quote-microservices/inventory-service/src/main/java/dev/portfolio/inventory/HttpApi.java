package dev.portfolio.inventory;

import io.javalin.Javalin;

import java.util.Map;
import java.util.UUID;

public final class HttpApi {
    public static final String HEADER = "X-Correlation-Id";

    private HttpApi() {}

    public record Quantity(int quantity) {}

    public static Javalin create(StockService stock) {
        return Javalin.create(cfg -> {
        cfg.jsonMapper(new io.javalin.json.JavalinJackson3()); // Jackson 3 is already on the classpath via logstash-encoder
        var app = cfg.routes;
        app.before(ctx -> {
            String id = ctx.header(HEADER);
            ctx.attribute(HEADER, id == null || id.isBlank() ? UUID.randomUUID().toString() : id);
            org.slf4j.MDC.put(CorrelationInterceptor.MDC_KEY, ctx.attribute(HEADER));
        });
        app.after(ctx -> {
            ctx.header(HEADER, ctx.attribute(HEADER));
            org.slf4j.MDC.remove(CorrelationInterceptor.MDC_KEY);
        });
        app.get("/health", ctx -> ctx.json(Map.of("status", "UP")));
        app.get("/stock", ctx -> ctx.json(stock.all()));
        app.get("/stock/{sku}", ctx -> {
            String sku = ctx.pathParam("sku");
            Integer available = stock.get(sku);
            if (available == null) ctx.status(404).json(Map.of("error", "unknown sku " + sku));
            else ctx.json(Map.of("sku", sku, "available", available));
        });
        app.put("/stock/{sku}", ctx -> {
            String sku = ctx.pathParam("sku");
            int quantity = ctx.bodyAsClass(Quantity.class).quantity();
            if (quantity < 0) {
                ctx.status(400).json(Map.of("error", "quantity must be >= 0"));
                return;
            }
            stock.set(sku, quantity);
            ctx.json(Map.of("sku", sku, "available", quantity));
        });
        });
    }
}
