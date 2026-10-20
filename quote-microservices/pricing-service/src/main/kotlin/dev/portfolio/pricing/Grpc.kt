package dev.portfolio.pricing

import dev.portfolio.quote.proto.PriceReply
import dev.portfolio.quote.proto.PriceRequest
import dev.portfolio.quote.proto.PricingGrpc
import io.grpc.ForwardingServerCallListener.SimpleForwardingServerCallListener
import io.grpc.Metadata
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor
import io.grpc.Status
import io.grpc.stub.StreamObserver
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.util.UUID

const val MDC_KEY = "correlationId"
val CORRELATION_HEADER: Metadata.Key<String> =
    Metadata.Key.of("x-correlation-id", Metadata.ASCII_STRING_MARSHALLER)

private val log = LoggerFactory.getLogger(PricingService::class.java)

class PricingService(private val book: PriceBook) : PricingGrpc.PricingImplBase() {
    override fun getPrice(req: PriceRequest, out: StreamObserver<PriceReply>) {
        log.info("GetPrice sku={} quantity={}", req.sku, req.quantity)
        if (req.quantity < 1) {
            out.onError(Status.INVALID_ARGUMENT.withDescription("quantity must be >= 1").asRuntimeException())
            return
        }
        val unit = book.prices[req.sku]
        if (unit == null) {
            out.onError(Status.NOT_FOUND.withDescription("unknown sku ${req.sku}").asRuntimeException())
            return
        }
        out.onNext(
            PriceReply.newBuilder()
                .setSku(req.sku)
                .setUnitPriceCents(unit)
                .setTotalCents(total(unit, req.quantity))
                .setCurrency(CURRENCY)
                .setDiscountPercent(discountPercent(req.quantity))
                .build()
        )
        out.onCompleted()
    }
}

/** Puts the correlation id in MDC for every listener callback of the call. */
class CorrelationInterceptor : ServerInterceptor {
    override fun <Q, R> interceptCall(
        call: ServerCall<Q, R>, headers: Metadata, next: ServerCallHandler<Q, R>
    ): ServerCall.Listener<Q> {
        val id = headers.get(CORRELATION_HEADER) ?: UUID.randomUUID().toString()
        MDC.put(MDC_KEY, id)
        val listener = try { next.startCall(call, headers) } finally { MDC.remove(MDC_KEY) }
        return object : SimpleForwardingServerCallListener<Q>(listener) {
            private inline fun scoped(block: () -> Unit) {
                MDC.put(MDC_KEY, id)
                try { block() } finally { MDC.remove(MDC_KEY) }
            }
            override fun onMessage(message: Q) = scoped { super.onMessage(message) }
            override fun onHalfClose() = scoped { super.onHalfClose() }
            override fun onCancel() = scoped { super.onCancel() }
            override fun onComplete() = scoped { super.onComplete() }
            override fun onReady() = scoped { super.onReady() }
        }
    }
}
