package dev.portfolio.quote.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.portfolio.quote.proto.*;
import io.grpc.*;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.grpc.client.autoconfigure.GrpcChannelFactoryCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.grpc.client.InProcessGrpcChannelFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
        "spring.grpc.client.channel.inventory.target=in-process:gw-inv",
        "spring.grpc.client.channel.pricing.target=in-process:gw-price"})
@AutoConfigureMockMvc
class GatewayApiTest {

    /** spring-grpc 1.1.1 does not set virtual targets on the in-process factory; wire them so named channels resolve. */
    @TestConfiguration
    static class InProcessTargets {
        @Bean
        GrpcChannelFactoryCustomizer inProcessVirtualTargets(Environment env) {
            return f -> {
                if (f instanceof InProcessGrpcChannelFactory ip)
                    ip.setVirtualTargets(name -> env.getProperty("spring.grpc.client.channel." + name + ".target", name));
            };
        }
    }

    static final Metadata.Key<String> CID = Metadata.Key.of("x-correlation-id", Metadata.ASCII_STRING_MARSHALLER);
    static final AtomicReference<String> capturedCid = new AtomicReference<>();
    static Supplier<Status> failWith; // null = succeed
    static Server inv, price;

    static <T> boolean fail(StreamObserver<T> o) {
        if (failWith == null) return false;
        o.onError(failWith.get().asRuntimeException());
        return true;
    }

    static final ServerInterceptor capture = new ServerInterceptor() {
        public <Q, R> ServerCall.Listener<Q> interceptCall(ServerCall<Q, R> call, Metadata h, ServerCallHandler<Q, R> next) {
            capturedCid.set(h.get(CID));
            return next.startCall(call, h);
        }
    };

    @BeforeAll
    static void startFakes() throws Exception {
        inv = InProcessServerBuilder.forName("gw-inv").directExecutor().intercept(capture)
                .addService(new InventoryGrpc.InventoryImplBase() {
                    public void checkStock(StockRequest r, StreamObserver<StockReply> o) {
                        if (fail(o)) return;
                        o.onNext(StockReply.newBuilder().setSku(r.getSku()).setAvailable(10).setEnough(r.getQuantity() <= 10).build());
                        o.onCompleted();
                    }
                    public void reserve(ReserveRequest r, StreamObserver<ReserveReply> o) {
                        if (fail(o)) return;
                        o.onNext(ReserveReply.newBuilder().setOrderId(r.getOrderId()).setRemaining(10 - r.getQuantity()).build());
                        o.onCompleted();
                    }
                }).build().start();
        price = InProcessServerBuilder.forName("gw-price").directExecutor().intercept(capture)
                .addService(new PricingGrpc.PricingImplBase() {
                    public void getPrice(PriceRequest r, StreamObserver<PriceReply> o) {
                        if (fail(o)) return;
                        o.onNext(PriceReply.newBuilder().setSku(r.getSku()).setUnitPriceCents(500)
                                .setTotalCents(500L * r.getQuantity()).setCurrency("EUR").setDiscountPercent(0).build());
                        o.onCompleted();
                    }
                }).build().start();
    }

    @AfterAll
    static void stopFakes() {
        inv.shutdownNow();
        price.shutdownNow();
    }

    @Autowired MockMvc mvc;

    @BeforeEach
    void reset() {
        failWith = null;
        capturedCid.set(null);
    }

    @Test
    void postQuoteReturnsPriceAndAvailability() throws Exception {
        mvc.perform(post("/quotes").contentType(MediaType.APPLICATION_JSON).content("{\"sku\":\"SKU-1\",\"quantity\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("SKU-1"))
                .andExpect(jsonPath("$.quantity").value(3))
                .andExpect(jsonPath("$.available").value(10))
                .andExpect(jsonPath("$.enough").value(true))
                .andExpect(jsonPath("$.unitPriceCents").value(500))
                .andExpect(jsonPath("$.totalCents").value(1500))
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.discountPercent").value(0));
    }

    @Test
    void postQuoteWritesOneInfoLogLine() throws Exception {
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(QuoteController.class);
        var list = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        list.start();
        logger.addAppender(list);
        try {
            mvc.perform(post("/quotes").contentType(MediaType.APPLICATION_JSON).content("{\"sku\":\"SKU-1\",\"quantity\":3}"))
                    .andExpect(status().isOk());
        } finally {
            logger.detachAppender(list);
        }
        assertThat(list.list).extracting(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage)
                .containsExactly("POST /quotes sku=SKU-1 quantity=3");
    }

    @Test
    void postQuoteWithInvalidBodyReturns400() throws Exception {
        mvc.perform(post("/quotes").contentType(MediaType.APPLICATION_JSON).content("{\"sku\":\" \",\"quantity\":3}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        mvc.perform(post("/quotes").contentType(MediaType.APPLICATION_JSON).content("{\"sku\":\"SKU-1\",\"quantity\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void postOrderReservesStockAndReturns201() throws Exception {
        MvcResult res = mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content("{\"sku\":\"SKU-1\",\"quantity\":2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value("SKU-1"))
                .andExpect(jsonPath("$.quantity").value(2))
                .andExpect(jsonPath("$.totalCents").value(1000))
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.remaining").value(8))
                .andReturn();
        String orderId = com.jayway.jsonpath.JsonPath.read(res.getResponse().getContentAsString(), "$.orderId");
        assertThat(UUID.fromString(orderId)).isNotNull();
    }

    @Test
    void postOrderWhenStockInsufficientReturns409() throws Exception {
        failWith = () -> Status.FAILED_PRECONDITION.withDescription("only 1 left");
        mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content("{\"sku\":\"SKU-1\",\"quantity\":2}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("only 1 left"));
    }

    @Test
    void unknownSkuReturns404() throws Exception {
        failWith = () -> Status.NOT_FOUND.withDescription("unknown sku");
        mvc.perform(post("/quotes").contentType(MediaType.APPLICATION_JSON).content("{\"sku\":\"NOPE\",\"quantity\":1}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("unknown sku"));
    }

    @Test
    void grpcUnavailableReturns503() throws Exception {
        failWith = () -> Status.UNAVAILABLE.withDescription("down");
        mvc.perform(post("/quotes").contentType(MediaType.APPLICATION_JSON).content("{\"sku\":\"SKU-1\",\"quantity\":1}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));
    }

    @Test
    void correlationIdIsPropagatedToGrpcAndEchoed() throws Exception {
        mvc.perform(post("/quotes").header("X-Correlation-Id", "abc-123")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"sku\":\"SKU-1\",\"quantity\":1}"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-Id", "abc-123"));
        assertThat(capturedCid.get()).isEqualTo("abc-123");
    }

    @Test
    void missingCorrelationIdIsGenerated() throws Exception {
        String cid = mvc.perform(post("/quotes").contentType(MediaType.APPLICATION_JSON).content("{\"sku\":\"SKU-1\",\"quantity\":1}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("X-Correlation-Id");
        assertThat(UUID.fromString(cid)).isNotNull();
        assertThat(capturedCid.get()).isEqualTo(cid);
    }
}
