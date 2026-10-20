package dev.portfolio.inventory;

import dev.portfolio.quote.proto.ReserveReply;
import dev.portfolio.quote.proto.StockReply;
import io.grpc.Status;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory stock. Thread-safe. Throws gRPC Status exceptions for domain errors. */
public final class StockService {
    private final ConcurrentHashMap<String, Integer> stock = new ConcurrentHashMap<>(
            Map.of("SKU-1", 100, "SKU-2", 5, "SKU-3", 0));
    private final ConcurrentHashMap<String, ReserveReply> reservations = new ConcurrentHashMap<>();

    public Map<String, Integer> all() {
        return Map.copyOf(stock);
    }

    public Integer get(String sku) {
        return stock.get(sku);
    }

    public void set(String sku, int quantity) {
        if (quantity < 0) throw new IllegalArgumentException("quantity must be >= 0");
        stock.put(sku, quantity);
    }

    public StockReply check(String sku, int quantity) {
        if (quantity < 1) throw Status.INVALID_ARGUMENT.withDescription("quantity must be >= 1").asRuntimeException();
        int available = require(sku);
        return StockReply.newBuilder().setSku(sku).setAvailable(available).setEnough(available >= quantity).build();
    }

    public ReserveReply reserve(String sku, int quantity, String orderId) {
        ReserveReply existing = reservations.get(orderId);
        if (existing != null) return existing;
        if (quantity < 1) throw Status.INVALID_ARGUMENT.withDescription("quantity must be >= 1").asRuntimeException();
        int available = require(sku);
        Integer remaining = stock.compute(sku, (k, v) -> v != null && v >= quantity ? v - quantity : v);
        if (remaining == null || remaining == available) {
            throw Status.FAILED_PRECONDITION
                    .withDescription("not enough stock for %s: requested %d, available %d".formatted(sku, quantity, available))
                    .asRuntimeException();
        }
        ReserveReply reply = ReserveReply.newBuilder().setOrderId(orderId).setRemaining(remaining).build();
        reservations.put(orderId, reply);
        return reply;
    }

    private int require(String sku) {
        Integer available = stock.get(sku);
        if (available == null) throw Status.NOT_FOUND.withDescription("unknown sku " + sku).asRuntimeException();
        return available;
    }
}
