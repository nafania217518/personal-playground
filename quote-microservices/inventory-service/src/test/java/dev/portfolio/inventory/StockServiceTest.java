package dev.portfolio.inventory;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class StockServiceTest {
    private final StockService service = new StockService();

    @Test
    void checkStockReportsEnoughWhenAvailable() {
        var reply = service.check("SKU-1", 10);
        assertEquals(100, reply.getAvailable());
        assertTrue(reply.getEnough());
    }

    @Test
    void checkStockReportsNotEnoughWhenShort() {
        var reply = service.check("SKU-2", 10);
        assertEquals(5, reply.getAvailable());
        assertFalse(reply.getEnough());
    }

    @Test
    void checkStockUnknownSkuReturnsNotFound() {
        var e = assertThrows(StatusRuntimeException.class, () -> service.check("NOPE", 1));
        assertEquals(Status.Code.NOT_FOUND, e.getStatus().getCode());
    }

    @Test
    void checkStockZeroQuantityReturnsInvalidArgument() {
        var e = assertThrows(StatusRuntimeException.class, () -> service.check("SKU-1", 0));
        assertEquals(Status.Code.INVALID_ARGUMENT, e.getStatus().getCode());
    }

    @Test
    void reserveDecrementsStock() {
        var reply = service.reserve("SKU-1", 30, "o-1");
        assertEquals("o-1", reply.getOrderId());
        assertEquals(70, reply.getRemaining());
        assertEquals(70, service.get("SKU-1"));
    }

    @Test
    void reserveInsufficientReturnsFailedPrecondition() {
        var e = assertThrows(StatusRuntimeException.class, () -> service.reserve("SKU-2", 10, "o-2"));
        assertEquals(Status.Code.FAILED_PRECONDITION, e.getStatus().getCode());
        assertEquals("not enough stock for SKU-2: requested 10, available 5", e.getStatus().getDescription());
        assertEquals(5, service.get("SKU-2"));
    }

    @Test
    void reserveUnknownSkuReturnsNotFound() {
        var e = assertThrows(StatusRuntimeException.class, () -> service.reserve("NOPE", 1, "o-x"));
        assertEquals(Status.Code.NOT_FOUND, e.getStatus().getCode());
        assertThrows(StatusRuntimeException.class, () -> service.reserve("SKU-1", 0, "o-y"));
    }

    @Test
    void reserveIsIdempotentPerOrderId() {
        var first = service.reserve("SKU-1", 10, "o-3");
        var second = service.reserve("SKU-1", 10, "o-3");
        assertEquals(first, second);
        assertEquals(90, service.get("SKU-1"));
    }

    @Test
    void concurrentReservesNeverOversell() throws Exception {
        service.set("SKU-C", 20);
        AtomicInteger ok = new AtomicInteger();
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 50; i++) {
                String orderId = "c-" + i;
                pool.submit(() -> {
                    try {
                        service.reserve("SKU-C", 1, orderId);
                        ok.incrementAndGet();
                    } catch (StatusRuntimeException ignored) {
                    }
                });
            }
        }
        assertEquals(20, ok.get());
        assertEquals(0, service.get("SKU-C"));
    }

    @Test
    void setRejectsNegativeAndAllIsSnapshot() {
        assertThrows(IllegalArgumentException.class, () -> service.set("SKU-1", -1));
        assertEquals(3, service.all().size());
    }
}
