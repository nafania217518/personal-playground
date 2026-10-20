package dev.portfolio.quote.gateway;

import dev.portfolio.quote.gateway.Dtos.ItemRequest;
import dev.portfolio.quote.gateway.Dtos.OrderResponse;
import dev.portfolio.quote.gateway.Dtos.QuoteResponse;
import dev.portfolio.quote.proto.*;
import jakarta.validation.Valid;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
class QuoteController {

    private static final Logger log = LoggerFactory.getLogger(QuoteController.class);
    private final InventoryGrpc.InventoryBlockingStub inventory;
    private final PricingGrpc.PricingBlockingStub pricing;

    QuoteController(GrpcChannelFactory channels) {
        this.inventory = InventoryGrpc.newBlockingStub(channels.createChannel("inventory"));
        this.pricing = PricingGrpc.newBlockingStub(channels.createChannel("pricing"));
    }

    @PostMapping("/quotes")
    QuoteResponse quote(@Valid @RequestBody ItemRequest req) {
        log.info("POST /quotes sku={} quantity={}", req.sku(), req.quantity());
        StockReply stock = inventory.checkStock(
                StockRequest.newBuilder().setSku(req.sku()).setQuantity(req.quantity()).build());
        PriceReply price = pricing.getPrice(
                PriceRequest.newBuilder().setSku(req.sku()).setQuantity(req.quantity()).build());
        return new QuoteResponse(req.sku(), req.quantity(), stock.getAvailable(), stock.getEnough(),
                price.getUnitPriceCents(), price.getTotalCents(), price.getCurrency(), price.getDiscountPercent());
    }

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    OrderResponse order(@Valid @RequestBody ItemRequest req) {
        log.info("POST /orders sku={} quantity={}", req.sku(), req.quantity());
        PriceReply price = pricing.getPrice(
                PriceRequest.newBuilder().setSku(req.sku()).setQuantity(req.quantity()).build());
        String orderId = UUID.randomUUID().toString();
        ReserveReply reserved = inventory.reserve(ReserveRequest.newBuilder()
                .setSku(req.sku()).setQuantity(req.quantity()).setOrderId(orderId).build());
        return new OrderResponse(orderId, req.sku(), req.quantity(), price.getTotalCents(), price.getCurrency(),
                reserved.getRemaining());
    }
}
