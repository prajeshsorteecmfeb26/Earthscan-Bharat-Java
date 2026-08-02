package com.earthscan.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Populates the {@link SecurityContextHolder} from a bearer token.
 *
 * <p>Deliberately <em>not</em> a {@code @Component}: any bean of type {@code Filter} is also picked
 * up by Spring Boot's servlet auto-registration, which would run this filter twice — once outside
 * the security chain. Each service instantiates it explicitly inside its own
 * {@code SecurityFilterChain}.</p>
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String MDC_USER_ID = "userId";

    private final JwtTokenProvider tokenProvider;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                JwtTokenProvider.extractBearerToken(request.getHeader(HttpHeaders.AUTHORIZATION))
                        .flatMap(tokenProvider::parse)
                        .ifPresent(this::authenticate);
            }
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_USER_ID);
        }
    }

    private void authenticate(AuthenticatedUser user) {
        List<GrantedAuthority> authorities = user.getRoles().stream()
                .map(RoleName::getAuthority)
                .map(SimpleGrantedAuthority::new)
                .map(GrantedAuthority.class::cast)
                .toList();

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(user, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        MDC.put(MDC_USER_ID, String.valueOf(user.getId()));
    }
}
