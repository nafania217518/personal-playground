package dev.portfolio.inventory;

import dev.portfolio.quote.proto.InventoryGrpc;
import dev.portfolio.quote.proto.ReserveRequest;
import dev.portfolio.quote.proto.StockReply;
import dev.portfolio.quote.proto.StockRequest;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class InventoryGrpcServiceTest {
    private final AtomicReference<String> seenCorrelationId = new AtomicReference<>();
    private Server server;
    private ManagedChannel channel;
    private InventoryGrpc.InventoryBlockingStub stub;

    @BeforeEach
    void start() throws Exception {
        String name = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(name).directExecutor()
                .addService(new InventoryGrpcService(new StockService()) {
                    @Override
                    public void checkStock(StockRequest req, StreamObserver<StockReply> out) {
                        seenCorrelationId.set(MDC.get(CorrelationInterceptor.MDC_KEY));
                        super.checkStock(req, out);
                    }
                })
                .intercept(new CorrelationInterceptor())
                .build().start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();
        stub = InventoryGrpc.newBlockingStub(channel);
    }

    @AfterEach
    void stop() {
        channel.shutdownNow();
        server.shutdownNow();
    }

    @Test
    void checkStockWritesOneInfoLogLine() {
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(InventoryGrpcService.class);
        var list = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        list.start();
        logger.addAppender(list);
        try {
            stub.checkStock(StockRequest.newBuilder().setSku("SKU-1").setQuantity(10).build());
        } finally {
            logger.detachAppender(list);
        }
        assertEquals(1, list.list.size());
        assertEquals("CheckStock sku=SKU-1 quantity=10", list.list.getFirst().getFormattedMessage());
    }

    @Test
    void checkStockReportsEnoughWhenAvailable() {
        var reply = stub.checkStock(StockRequest.newBuilder().setSku("SKU-1").setQuantity(10).build());
        assertTrue(reply.getEnough());
        assertEquals(100, reply.getAvailable());
    }

    @Test
    void checkStockUnknownSkuReturnsNotFound() {
        var e = assertThrows(StatusRuntimeException.class,
                () -> stub.checkStock(StockRequest.newBuilder().setSku("NOPE").setQuantity(1).build()));
        assertEquals(Status.Code.NOT_FOUND, e.getStatus().getCode());
    }

    @Test
    void reserveDecrementsStock() {
        var reply = stub.reserve(ReserveRequest.newBuilder().setSku("SKU-1").setQuantity(1).setOrderId("o").build());
        assertEquals(99, reply.getRemaining());
    }

    @Test
    void reserveInsufficientReturnsFailedPrecondition() {
        var e = assertThrows(StatusRuntimeException.class,
                () -> stub.reserve(ReserveRequest.newBuilder().setSku("SKU-2").setQuantity(10).setOrderId("o").build()));
        assertEquals(Status.Code.FAILED_PRECONDITION, e.getStatus().getCode());
        assertEquals("not enough stock for SKU-2: requested 10, available 5", e.getStatus().getDescription());
    }

    @Test
    void correlationIdFromMetadataIsVisibleInMdcDuringCall() {
        Metadata md = new Metadata();
        md.put(CorrelationInterceptor.HEADER, "abc");
        stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(md))
                .checkStock(StockRequest.newBuilder().setSku("SKU-1").setQuantity(1).build());
        assertEquals("abc", seenCorrelationId.get());
        assertNull(MDC.get(CorrelationInterceptor.MDC_KEY));
    }

    @Test
    void missingCorrelationIdIsGenerated() {
        stub.checkStock(StockRequest.newBuilder().setSku("SKU-1").setQuantity(1).build());
        assertNotNull(seenCorrelationId.get());
        assertEquals(36, seenCorrelationId.get().length());
    }
}
