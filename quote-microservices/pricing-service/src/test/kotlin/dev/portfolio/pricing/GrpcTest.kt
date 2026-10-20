package dev.portfolio.pricing

import dev.portfolio.quote.proto.PriceReply
import dev.portfolio.quote.proto.PriceRequest
import dev.portfolio.quote.proto.PricingGrpc
import io.grpc.BindableService
import io.grpc.Metadata
import io.grpc.ServerInterceptors
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.grpc.stub.MetadataUtils
import io.grpc.stub.StreamObserver
import io.grpc.testing.GrpcCleanupRule
import org.junit.Rule
import org.junit.jupiter.api.Assertions.assertThrows
import org.slf4j.MDC
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class GrpcTest {
    private fun stub(service: BindableService = PricingService(PriceBook())): PricingGrpc.PricingBlockingStub {
        val name = InProcessServerBuilder.generateName()
        val server = InProcessServerBuilder.forName(name).directExecutor()
            .addService(ServerInterceptors.intercept(service, CorrelationInterceptor())).build().start()
        val channel = InProcessChannelBuilder.forName(name).directExecutor().build()
        servers += { channel.shutdownNow(); server.shutdownNow() }
        return PricingGrpc.newBlockingStub(channel)
    }
    private val servers = mutableListOf<() -> Unit>()
    @org.junit.jupiter.api.AfterEach fun cleanup() = servers.forEach { it() }

    private fun req(sku: String, qty: Int) = PriceRequest.newBuilder().setSku(sku).setQuantity(qty).build()

    @Test fun getPriceReturnsUnitTotalAndDiscount() {
        val r = stub().getPrice(req("SKU-3", 100))
        assertEquals("SKU-3", r.sku)
        assertEquals(12000L, r.unitPriceCents)
        assertEquals(1_080_000L, r.totalCents)
        assertEquals("EUR", r.currency)
        assertEquals(10, r.discountPercent)
    }

    @Test fun getPriceWritesOneInfoLogLine() {
        val logger = org.slf4j.LoggerFactory.getLogger(PricingService::class.java) as ch.qos.logback.classic.Logger
        val list = ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>().apply { start() }
        logger.addAppender(list)
        try { stub().getPrice(req("SKU-1", 2)) } finally { logger.detachAppender(list) }
        assertEquals(listOf("GetPrice sku=SKU-1 quantity=2"), list.list.map { it.formattedMessage })
    }

    @Test fun getPriceUnknownSkuReturnsNotFound() {
        val e = assertThrows(StatusRuntimeException::class.java) { stub().getPrice(req("NOPE", 1)) }
        assertEquals(Status.Code.NOT_FOUND, e.status.code)
    }

    @Test fun getPriceZeroQuantityReturnsInvalidArgument() {
        val e = assertThrows(StatusRuntimeException::class.java) { stub().getPrice(req("SKU-1", 0)) }
        assertEquals(Status.Code.INVALID_ARGUMENT, e.status.code)
    }

    /** Service that records the MDC value seen during the call. */
    private class Capturing : PricingGrpc.PricingImplBase() {
        var seen: String? = null
        override fun getPrice(req: PriceRequest, out: StreamObserver<PriceReply>) {
            seen = MDC.get(MDC_KEY)
            out.onNext(PriceReply.getDefaultInstance()); out.onCompleted()
        }
    }

    @Test fun correlationIdFromMetadataIsVisibleInMdcDuringCall() {
        val svc = Capturing()
        val md = Metadata().apply { put(CORRELATION_HEADER, "abc-123") }
        stub(svc).withInterceptors(MetadataUtils.newAttachHeadersInterceptor(md)).getPrice(req("x", 1))
        assertEquals("abc-123", svc.seen)
        assertNull(MDC.get(MDC_KEY))
    }

    @Test fun missingCorrelationIdIsGenerated() {
        val svc = Capturing()
        stub(svc).getPrice(req("x", 1))
        assertNotNull(svc.seen)
        assertEquals(36, svc.seen!!.length)
    }
}
