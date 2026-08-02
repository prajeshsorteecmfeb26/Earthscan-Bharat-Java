package com.earthscan.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * One access-log line per request with method, path, status and duration.
 *
 * <p>Query strings and request bodies are intentionally omitted: login and reset-password bodies
 * contain plaintext passwords, and logging those would be a far worse problem than the missing
 * debugging detail.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
    private static final long SLOW_REQUEST_THRESHOLD_MS = 1_000L;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long startedAt = System.currentTimeMillis();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long elapsed = System.currentTimeMillis() - startedAt;
            int status = response.getStatus();
            if (status >= 500 || elapsed > SLOW_REQUEST_THRESHOLD_MS) {
                log.warn("{} {} -> {} in {}ms", request.getMethod(), request.getRequestURI(), status, elapsed);
            } else {
                log.info("{} {} -> {} in {}ms", request.getMethod(), request.getRequestURI(), status, elapsed);
            }
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Health probes and docs would otherwise dominate the log volume.
        return path.startsWith("/actuator")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-ui");
    }
}
