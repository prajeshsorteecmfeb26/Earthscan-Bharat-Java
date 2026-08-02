package com.earthscan.gateway.filter;

import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Mints the correlation id for the whole request fan-out and forwards it downstream.
 *
 * <p>Because this is the first hop, an id generated here appears in the logs of every service that
 * participates in the request. Downstream services echo the incoming header rather than generating
 * their own (see {@code CorrelationIdFilter} in common-lib).</p>
 */
@Component
public class CorrelationIdGatewayFilter implements GlobalFilter, Ordered {

    public static final String HEADER = "X-Correlation-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String correlationId = exchange.getRequest().getHeaders().getFirst(HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        final String resolved = correlationId;

        ServerHttpRequest mutated = exchange.getRequest().mutate()
                .header(HEADER, resolved)
                .build();
        exchange.getResponse().getHeaders().set(HEADER, resolved);

        // MDC on a reactive stack only covers the synchronous portion of this filter; it is set so
        // that this filter's own logging is labelled. Per-service logs get the id from the header.
        MDC.put("correlationId", resolved);
        try {
            return chain.filter(exchange.mutate().request(mutated).build());
        } finally {
            MDC.remove("correlationId");
        }
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
