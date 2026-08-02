package com.earthscan.auth.config;

import com.earthscan.auth.security.EarthScanUserDetailsService;
import com.earthscan.common.security.JwtAuthenticationFilter;
import com.earthscan.common.security.JwtTokenProvider;
import com.earthscan.common.security.RestAccessDeniedHandler;
import com.earthscan.common.security.RestAuthenticationEntryPoint;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Security configuration for auth-service.
 *
 * <p>{@code @EnableMethodSecurity} is what makes the {@code @PreAuthorize("hasRole('ADMIN')")} on
 * {@code AdminController} take effect; without it the annotation is silently ignored, which is one of
 * the easier ways to ship an unprotected admin API.</p>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * BCrypt at strength 10 — the same algorithm the ASP.NET version used via BCrypt.Net, so every
     * existing password hash in the database keeps verifying after the migration. That compatibility
     * is the whole reason for choosing BCrypt over Argon2id here.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    /**
     * Wires the custom {@link EarthScanUserDetailsService} into a {@link DaoAuthenticationProvider}.
     *
     * <p>{@code hideUserNotFoundExceptions} is left at its default of {@code true} on purpose: it
     * converts {@code UsernameNotFoundException} into {@code BadCredentialsException}, so an unknown
     * email and a wrong password are indistinguishable to the caller. Setting it to {@code false} —
     * which is a common "improvement" for nicer error messages — turns the login endpoint into a
     * user-enumeration oracle.</p>
     */
    @Bean
    public DaoAuthenticationProvider daoAuthenticationProvider(
            EarthScanUserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(DaoAuthenticationProvider provider) {
        return new ProviderManager(provider);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           JwtTokenProvider tokenProvider,
                                           ObjectMapper objectMapper) throws Exception {
        http
                // No browser session and no server-side state: the JWT is the whole session, so the
                // CSRF token pattern has nothing to protect and would only break the API clients.
                .csrf(csrf -> csrf.disable())
                // CORS is handled once, at the gateway. Enabling it here too would emit duplicate
                // Access-Control-Allow-Origin headers, which browsers reject outright.
                .cors(cors -> cors.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/auth/register",
                                "/api/auth/login",
                                "/api/auth/reset-password").permitAll()
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(new RestAuthenticationEntryPoint(objectMapper))
                        .accessDeniedHandler(new RestAccessDeniedHandler(objectMapper)))
                .addFilterBefore(new JwtAuthenticationFilter(tokenProvider),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
