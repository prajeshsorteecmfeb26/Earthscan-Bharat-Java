package com.earthscan.auth.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.earthscan.auth.dto.AuthResponse;
import com.earthscan.auth.dto.UserResponse;
import com.earthscan.auth.service.AuthService;
import com.earthscan.common.exception.DuplicateResourceException;
import com.earthscan.common.exception.GlobalExceptionHandler;
import com.earthscan.common.exception.UnauthorizedException;
import com.earthscan.common.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * HTTP-layer tests for {@link AuthController}.
 *
 * <p>These cover the half of the contract that service unit tests structurally cannot: status codes,
 * JSON field names, request deserialisation, and whether Bean Validation annotations actually fire.
 * A service test asserting {@code DuplicateResourceException} proves the exception is thrown; only a
 * MockMvc test proves it becomes a 409 with a {@code message} field the React client can read.</p>
 *
 * <p>Security is deliberately excluded ({@code addFilters = false}) so these tests isolate the
 * controller and the exception handler. Security wiring is covered separately by the integration
 * test, which boots the real filter chain.</p>
 */
@WebMvcTest(controllers = AuthController.class)
// addFilters = false strips the security filter chain so these tests exercise the controller and
// the exception handler in isolation. Security wiring is covered by the integration test, which
// boots the real chain - asserting it here as well would only duplicate that coverage.
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@ActiveProfiles("test")
@DisplayName("AuthController (web slice)")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    /** Required by the security config the slice pulls in transitively. */
    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    @Nested
    @DisplayName("POST /api/auth/register")
    class Register {

        private Map<String, String> validBody() {
            return Map.of(
                    "name", "Anita Deshmukh",
                    "email", "anita@example.com",
                    "password", "harvest2026",
                    "role", "Farmer");
        }

        @Test
        @DisplayName("returns 201 with a message on success")
        void returns201() throws Exception {
            when(authService.register(any())).thenReturn(
                    new UserResponse(7L, "Anita Deshmukh", "anita@example.com", "Farmer", true,
                            Instant.now()));

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(validBody())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.message").value("User registered successfully"));
        }

        @Test
        @DisplayName("returns 409 when the email is already registered")
        void returns409OnDuplicate() throws Exception {
            when(authService.register(any()))
                    .thenThrow(new DuplicateResourceException("Email is already registered"));

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(validBody())))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("Email is already registered"))
                    .andExpect(jsonPath("$.status").value(409));
        }

        @Test
        @DisplayName("returns 400 with per-field errors for a short password")
        void returns400ForShortPassword() throws Exception {
            Map<String, String> body = new java.util.HashMap<>(validBody());
            body.put("password", "short");

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(body)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.password").exists());

            // The request must be rejected before it reaches the service.
            verify(authService, never()).register(any());
        }

        @Test
        @DisplayName("returns 400 for a malformed email")
        void returns400ForBadEmail() throws Exception {
            Map<String, String> body = new java.util.HashMap<>(validBody());
            body.put("email", "not-an-email");

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(body)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.email").exists());
        }

        @Test
        @DisplayName("returns 400 for a role outside the allowed set")
        void returns400ForUnknownRole() throws Exception {
            Map<String, String> body = new java.util.HashMap<>(validBody());
            body.put("role", "Supreme Overlord");

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(body)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.role").exists());

            verify(authService, never()).register(any());
        }

        @Test
        @DisplayName("returns 400 for a blank name")
        void returns400ForBlankName() throws Exception {
            Map<String, String> body = new java.util.HashMap<>(validBody());
            body.put("name", "  ");

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(body)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.name").exists());
        }

        @Test
        @DisplayName("returns 400 for a missing body rather than a 500")
        void returns400ForMissingBody() throws Exception {
            mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("POST /api/auth/login")
    class Login {

        private Map<String, String> validBody() {
            return Map.of("email", "anita@example.com", "password", "harvest2026");
        }

        @Test
        @DisplayName("returns 200 with the {token, user} shape the React client expects")
        void returns200WithTokenAndUser() throws Exception {
            when(authService.login(any())).thenReturn(new AuthResponse(
                    "signed.jwt.token", 604_800L,
                    new UserResponse(7L, "Anita Deshmukh", "anita@example.com", "Land Buyer", true,
                            Instant.now())));

            // These exact field paths are what AuthContext destructures. A rename here silently
            // breaks login in the browser with no backend error, so they are pinned.
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(validBody())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").value("signed.jwt.token"))
                    .andExpect(jsonPath("$.expiresInSeconds").value(604800))
                    .andExpect(jsonPath("$.user.id").value(7))
                    .andExpect(jsonPath("$.user.email").value("anita@example.com"))
                    .andExpect(jsonPath("$.user.role").value("Land Buyer"));
        }

        @Test
        @DisplayName("returns 401 with a message that does not reveal which half was wrong")
        void returns401OnBadCredentials() throws Exception {
            when(authService.login(any()))
                    .thenThrow(new UnauthorizedException("Invalid email or password"));

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(validBody())))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("Invalid email or password"));
        }

        @Test
        @DisplayName("never echoes the submitted password back in an error response")
        void neverEchoesPassword() throws Exception {
            when(authService.login(any()))
                    .thenThrow(new UnauthorizedException("Invalid email or password"));

            String response = mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(validBody())))
                    .andExpect(status().isUnauthorized())
                    .andReturn().getResponse().getContentAsString();

            org.assertj.core.api.Assertions.assertThat(response).doesNotContain("harvest2026");
        }

        @Test
        @DisplayName("returns 400 for a blank password")
        void returns400ForBlankPassword() throws Exception {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("email", "anita@example.com", "password", ""))))
                    .andExpect(status().isBadRequest());

            verify(authService, never()).login(any());
        }
    }

    @Nested
    @DisplayName("GET /api/auth/me")
    class CurrentUser {

        @Test
        @DisplayName("returns 401 when no principal is present in the context")
        void returns401WithoutPrincipal() throws Exception {
            // Filters are disabled in this slice, so nothing populates the SecurityContext.
            // SecurityUtils.requireCurrentUser() must therefore fail closed rather than NPE.
            mockMvc.perform(get("/api/auth/me"))
                    .andExpect(status().isUnauthorized());
        }
    }
}
