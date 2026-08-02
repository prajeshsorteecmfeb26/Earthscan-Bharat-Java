package com.earthscan.gateway.filter;

import com.earthscan.gateway.security.GatewayTokenValidator;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Rejects requests to protected routes that carry no valid bearer token.
 *
 * <p>This is a coarse gate, not the authorisation model. A valid token gets you past the gateway;
 * whether you may actually perform the operation is decided by {@code @PreAuthorize} in the owning
 * service, which re-validates the same token. That redundancy is deliberate — if someone reaches a
 * service directly (a misconfigured security group, a pod inside the cluster), the service is still
 * protected on its own.</p>
 */
@Component
public class AuthenticationGatewayFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationGatewayFilter.class);

    /** Routes reachable without a token. Everything else needs one. */
    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/auth/register",
            "/api/auth/login",
            "/api/auth/reset-password",
            "/api/lands",           // browsing listings is public; creating one is not
            "/api/lands/**",
            "/actuator/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/*/v3/api-docs/**");

    /** Public GET-only paths: any mutating verb on these still needs a token. */
    private static final List<String> READ_ONLY_PUBLIC_PATHS = List.of(
            "/api/lands",
            "/api/lands/**");

    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USER_ROLES = "X-User-Roles";

    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final GatewayTokenValidator tokenValidator;

    public AuthenticationGatewayFilter(GatewayTokenValidator tokenValidator) {
        this.tokenValidator = tokenValidator;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        HttpMethod method = request.getMethod();

        if (HttpMethod.OPTIONS.equals(method)) {
            return chain.filter(exchange);   // CORS preflight never carries the token
        }

        Optional<String> token = extractToken(request);

        if (isPublic(path, method)) {
            // Still forward identity when a token happens to be present, so that, for example,
            // GET /api/lands can highlight the caller's own listings.
            return token.flatMap(tokenValidator::validate)
                    .map(payload -> chain.filter(withIdentityHeaders(exchange, payload)))
                    .orElseGet(() -> chain.filter(stripSpoofedHeaders(exchange)));
        }

        return token.flatMap(tokenValidator::validate)
                .map(payload -> chain.filter(withIdentityHeaders(exchange, payload)))
                .orElseGet(() -> unauthorized(exchange, path));
    }

    private boolean isPublic(String path, HttpMethod method) {
        boolean readOnlyPublic = READ_ONLY_PUBLIC_PATHS.stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, path));
        if (readOnlyPublic) {
            return HttpMethod.GET.equals(method) || HttpMethod.HEAD.equals(method);
        }
        return PUBLIC_PATHS.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private Optional<String> extractToken(ServerHttpRequest request) {
        String header = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return Optional.empty();
        }
        String value = header.substring(7).trim();
        return value.isEmpty() ? Optional.empty() : Optional.of(value);
    }

    private ServerWebExchange withIdentityHeaders(ServerWebExchange exchange,
                                                 GatewayTokenValidator.TokenPayload payload) {
        ServerHttpRequest mutated = exchange.getRequest().mutate()
                .header(HEADER_USER_ID, payload.subject())
                .header(HEADER_USER_ROLES, String.join(",", payload.roles()))
                .build();
        return exchange.mutate().request(mutated).build();
    }

    /**
     * Removes any client-supplied identity headers. Without this a caller could simply send
     * {@code X-User-Id: 1} and impersonate the admin in downstream logs.
     */
    private ServerWebExchange stripSpoofedHeaders(ServerWebExchange exchange) {
        ServerHttpRequest mutated = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove(HEADER_USER_ID);
                    headers.remove(HEADER_USER_ROLES);
                })
                .build();
        return exchange.mutate().request(mutated).build();
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String path) {
        log.warn("Blocked unauthenticated request to {}", path);
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = """
                {"status":401,"error":"Unauthorized",\
                "message":"A valid bearer token is required to access this resource",\
                "path":"%s"}""".formatted(path);
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        // Runs after the correlation-id filter so rejection logs still carry the id.
        return -50;
    }
}
