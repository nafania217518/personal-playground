package dev.portfolio.inventory;

import dev.portfolio.quote.proto.InventoryGrpc;
import dev.portfolio.quote.proto.ReserveReply;
import dev.portfolio.quote.proto.ReserveRequest;
import dev.portfolio.quote.proto.StockReply;
import dev.portfolio.quote.proto.StockRequest;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InventoryGrpcService extends InventoryGrpc.InventoryImplBase {
    private static final Logger log = LoggerFactory.getLogger(InventoryGrpcService.class);
    private final StockService stock;

    public InventoryGrpcService(StockService stock) {
        this.stock = stock;
    }

    @Override
    public void checkStock(StockRequest req, StreamObserver<StockReply> out) {
        log.info("CheckStock sku={} quantity={}", req.getSku(), req.getQuantity());
        reply(out, () -> stock.check(req.getSku(), req.getQuantity()));
    }

    @Override
    public void reserve(ReserveRequest req, StreamObserver<ReserveReply> out) {
        log.info("Reserve sku={} quantity={} orderId={}", req.getSku(), req.getQuantity(), req.getOrderId());
        reply(out, () -> stock.reserve(req.getSku(), req.getQuantity(), req.getOrderId()));
    }

    private static <T> void reply(StreamObserver<T> out, java.util.function.Supplier<T> call) {
        try {
            out.onNext(call.get());
            out.onCompleted();
        } catch (StatusRuntimeException e) {
            out.onError(e);
        }
    }
}
