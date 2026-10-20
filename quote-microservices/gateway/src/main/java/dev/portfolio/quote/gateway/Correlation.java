package dev.portfolio.quote.gateway;

import io.grpc.*;
import io.grpc.ForwardingClientCall.SimpleForwardingClientCall;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.GlobalClientInterceptor;
import org.springframework.web.filter.OncePerRequestFilter;

/** Correlation id: HTTP header -> MDC -> outgoing gRPC metadata. */
@Configuration(proxyBeanMethods = false)
class Correlation {

    static final String HEADER = "X-Correlation-Id";
    static final String MDC_KEY = "correlationId";
    static final Metadata.Key<String> GRPC_KEY = Metadata.Key.of("x-correlation-id", Metadata.ASCII_STRING_MARSHALLER);

    @Bean
    OncePerRequestFilter correlationFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                    throws ServletException, IOException {
                String id = req.getHeader(HEADER);
                if (id == null || id.isBlank()) id = UUID.randomUUID().toString();
                MDC.put(MDC_KEY, id);
                res.setHeader(HEADER, id);
                try {
                    chain.doFilter(req, res);
                } finally {
                    MDC.remove(MDC_KEY);
                }
            }
        };
    }

    @Bean
    @GlobalClientInterceptor
    ClientInterceptor correlationClientInterceptor() {
        return new ClientInterceptor() {
            @Override
            public <Q, R> ClientCall<Q, R> interceptCall(MethodDescriptor<Q, R> m, CallOptions o, Channel next) {
                return new SimpleForwardingClientCall<>(next.newCall(m, o)) {
                    @Override
                    public void start(Listener<R> l, Metadata headers) {
                        String id = MDC.get(MDC_KEY);
                        if (id != null) headers.put(GRPC_KEY, id);
                        super.start(l, headers);
                    }
                };
            }
        };
    }
}
