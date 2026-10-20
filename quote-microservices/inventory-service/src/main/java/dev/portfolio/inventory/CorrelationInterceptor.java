package dev.portfolio.inventory;

import io.grpc.ForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import org.slf4j.MDC;

import java.util.UUID;

/** Reads x-correlation-id metadata and sets MDC correlationId around every listener callback. */
public final class CorrelationInterceptor implements ServerInterceptor {
    public static final String MDC_KEY = "correlationId";
    public static final Metadata.Key<String> HEADER =
            Metadata.Key.of("x-correlation-id", Metadata.ASCII_STRING_MARSHALLER);

    @Override
    public <Q, R> ServerCall.Listener<Q> interceptCall(ServerCall<Q, R> call, Metadata headers, ServerCallHandler<Q, R> next) {
        String id = headers.get(HEADER);
        String correlationId = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
        MDC.put(MDC_KEY, correlationId);
        try {
            return new ForwardingServerCallListener.SimpleForwardingServerCallListener<>(next.startCall(call, headers)) {
                @Override public void onMessage(Q m) { with(() -> super.onMessage(m)); }
                @Override public void onHalfClose() { with(super::onHalfClose); }
                @Override public void onCancel() { with(super::onCancel); }
                @Override public void onComplete() { with(super::onComplete); }
                @Override public void onReady() { with(super::onReady); }

                private void with(Runnable r) {
                    MDC.put(MDC_KEY, correlationId);
                    try { r.run(); } finally { MDC.remove(MDC_KEY); }
                }
            };
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
