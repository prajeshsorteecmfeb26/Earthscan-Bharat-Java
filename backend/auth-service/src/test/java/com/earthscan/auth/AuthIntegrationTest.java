package com.earthscan.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.earthscan.auth.repository.RoleRepository;
import com.earthscan.auth.repository.UserRepository;
import com.earthscan.common.messaging.EventPublisher;
import com.earthscan.common.security.RoleName;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/**
 * End-to-end test through the real application context.
 *
 * <p>This is the layer the unit and slice tests cannot reach. It boots the whole thing — the real
 * {@code SecurityFilterChain}, the real {@code DaoAuthenticationProvider} and
 * {@code UserDetailsService}, the real BCrypt encoder, real Hibernate against H2, and the real JWT
 * filter — and then drives it over HTTP. Everything it asserts is a wiring property, and wiring is
 * exactly what mocks hide: every collaborator in the unit tests could be correct while the
 * application still refuses every request because a filter is registered in the wrong order.</p>
 *
 * <p>RabbitMQ is the one mocked collaborator. A test must not require a broker to be running, and
 * publication is already asserted at the unit level.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Auth service integration")
class AuthIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    /** Mocked so the suite needs no broker. Publication itself is unit-tested. */
    @MockBean
    private EventPublisher eventPublisher;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // springSecurity() installs the real filter chain, which is the entire point of this class.
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
                        .springSecurity())
                .build();
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    private String registerAndLogin(String email, String role) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "name", "Test " + role,
                                "email", email,
                                "password", "harvest2026",
                                "role", role))))
                .andExpect(status().isCreated());

        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", "harvest2026"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(response).get("token").asText();
    }

    @Test
    @DisplayName("the roles lookup table is seeded on startup")
    void rolesAreSeeded() {
        // RoleSeeder is an ApplicationRunner, so this also proves it ran in the test context.
        for (RoleName role : RoleName.values()) {
            assertThat(roleRepository.findByName(role))
                    .as("role %s should be seeded", role)
                    .isPresent();
        }
    }

    @Nested
    @DisplayName("registration and login round trip")
    class RoundTrip {

        @Test
        @DisplayName("a registered user can log in and use the token on a protected endpoint")
        void fullRoundTrip() throws Exception {
            String token = registerAndLogin("roundtrip@example.com", "Farmer");

            assertThat(token).isNotBlank();

            mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value("roundtrip@example.com"))
                    .andExpect(jsonPath("$.role").value("Farmer"));
        }

        @Test
        @DisplayName("the stored password is a BCrypt hash, never the plaintext")
        void passwordIsHashed() throws Exception {
            registerAndLogin("hashcheck@example.com", "Farmer");

            String stored = userRepository.findByEmailIgnoreCase("hashcheck@example.com")
                    .orElseThrow().getPasswordHash();

            assertThat(stored).isNotEqualTo("harvest2026");
            assertThat(stored).startsWith("$2a$10$");
        }

        @Test
        @DisplayName("email is normalised to lower case on registration")
        void emailIsNormalised() throws Exception {
            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("name", "Mixed Case", "email", "MiXeD@Example.COM",
                                    "password", "harvest2026", "role", "Farmer"))))
                    .andExpect(status().isCreated());

            assertThat(userRepository.findByEmailIgnoreCase("mixed@example.com")).isPresent();

            // And login works with any casing, because DaoAuthenticationProvider goes through the
            // case-insensitive UserDetailsService lookup.
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("email", "MIXED@EXAMPLE.COM",
                                    "password", "harvest2026"))))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("registering the same email twice returns 409")
        void duplicateRegistrationConflicts() throws Exception {
            Map<String, String> body = Map.of("name", "First", "email", "dupe@example.com",
                    "password", "harvest2026", "role", "Farmer");

            mockMvc.perform(post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                    .andExpect(status().isCreated());

            mockMvc.perform(post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("a wrong password is rejected with 401")
        void wrongPasswordRejected() throws Exception {
            registerAndLogin("wrongpass@example.com", "Farmer");

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("email", "wrongpass@example.com",
                                    "password", "notthepassword"))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("Invalid email or password"));
        }

        @Test
        @DisplayName("an unknown email is rejected with the identical message")
        void unknownEmailIndistinguishable() throws Exception {
            // Proves the DaoAuthenticationProvider collapse survives all the way to the HTTP body -
            // this is the user-enumeration defence, tested where it actually matters.
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("email", "nosuchuser@example.com",
                                    "password", "harvest2026"))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("Invalid email or password"));
        }
    }

    @Nested
    @DisplayName("security filter chain")
    class SecurityChain {

        @Test
        @DisplayName("a protected endpoint returns JSON 401 without a token, not HTML")
        void protectedEndpointRequiresToken() throws Exception {
            mockMvc.perform(get("/api/auth/me"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.message").exists());
        }

        @Test
        @DisplayName("a garbage token is rejected without a 500")
        void garbageTokenRejected() throws Exception {
            mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer not.a.token"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("a token signed with a foreign secret is rejected")
        void forgedTokenRejected() throws Exception {
            com.earthscan.common.security.JwtProperties attacker =
                    new com.earthscan.common.security.JwtProperties();
            attacker.setSecret("AttackerControlledKeyThatIsAlsoLongEnough123456");
            attacker.setIssuer("EarthScanBackend");
            attacker.setAudience("EarthScanUsers");
            String forged = new com.earthscan.common.security.JwtTokenProvider(attacker)
                    .generateToken(1L, "Attacker", "evil@example.com",
                            java.util.List.of(RoleName.ADMIN));

            // The whole authorization model rests on this being impossible.
            mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + forged))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("public endpoints are reachable without a token")
        void publicEndpointsOpen() throws Exception {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("email", "x@example.com", "password", "wrongpass1"))))
                    // 401 rather than 403: the route was reachable, the credentials were not valid.
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("role-based authorization")
    class RoleBasedAccess {

        @Test
        @DisplayName("a Farmer token is refused with 403 on an admin endpoint")
        void farmerCannotReachAdminEndpoints() throws Exception {
            String farmerToken = registerAndLogin("farmer-rbac@example.com", "Farmer");

            mockMvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + farmerToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403));
        }

        @Test
        @DisplayName("an Admin token can list users")
        void adminCanListUsers() throws Exception {
            String adminToken = registerAndLogin("admin-rbac@example.com", "Admin");

            mockMvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].email").exists());
        }

        @Test
        @DisplayName("an admin cannot delete their own account")
        void adminCannotSelfDelete() throws Exception {
            String adminToken = registerAndLogin("selfdelete@example.com", "Admin");
            Long ownId = userRepository.findByEmailIgnoreCase("selfdelete@example.com")
                    .orElseThrow().getId();

            mockMvc.perform(delete("/api/admin/users/" + ownId)
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(
                            org.hamcrest.Matchers.containsString("your own administrator account")));
        }

        @Test
        @DisplayName("the last remaining admin cannot be deleted")
        void lastAdminProtected() throws Exception {
            String adminToken = registerAndLogin("onlyadmin@example.com", "Admin");
            String farmerToken = registerAndLogin("victim@example.com", "Farmer");
            assertThat(farmerToken).isNotBlank();

            Long adminId = userRepository.findByEmailIgnoreCase("onlyadmin@example.com")
                    .orElseThrow().getId();
            Long farmerId = userRepository.findByEmailIgnoreCase("victim@example.com")
                    .orElseThrow().getId();

            // Deleting a non-admin is fine.
            mockMvc.perform(delete("/api/admin/users/" + farmerId)
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk());

            // Self-deletion is caught first, so demote-then-delete is not reachable here; assert
            // the guard via the role-change path instead, which is the other way to lose all admins.
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .put("/api/admin/users/" + adminId)
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("role", "Farmer"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(
                            org.hamcrest.Matchers.containsString("last administrator")));
        }

        @Test
        @DisplayName("admin stats aggregate role counts")
        void adminStatsWork() throws Exception {
            String adminToken = registerAndLogin("stats-admin@example.com", "Admin");
            registerAndLogin("stats-farmer@example.com", "Farmer");

            String body = mockMvc.perform(get("/api/admin/stats")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            JsonNode stats = objectMapper.readTree(body);
            assertThat(stats.get("totalUsers").asLong()).isGreaterThanOrEqualTo(2);
            assertThat(stats.get("usersByRole").get("Farmer").asLong()).isGreaterThanOrEqualTo(1);
            assertThat(stats.get("usersByRole").get("Admin").asLong()).isGreaterThanOrEqualTo(1);
        }
    }

    @Nested
    @DisplayName("correlation id propagation")
    class CorrelationId {

        @Test
        @DisplayName("a response carries a correlation id even when none was sent")
        void generatesCorrelationId() throws Exception {
            String header = mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("email", "x@example.com", "password", "wrongpass1"))))
                    .andReturn().getResponse().getHeader("X-Correlation-Id");

            assertThat(header).isNotBlank();
        }

        @Test
        @DisplayName("a supplied correlation id is echoed rather than replaced")
        void echoesSuppliedCorrelationId() throws Exception {
            String supplied = "test-correlation-1234";

            String header = mockMvc.perform(post("/api/auth/login")
                            .header("X-Correlation-Id", supplied)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("email", "x@example.com", "password", "wrongpass1"))))
                    .andReturn().getResponse().getHeader("X-Correlation-Id");

            // Echoing is what lets one id span the gateway and every downstream service.
            assertThat(header).isEqualTo(supplied);
        }
    }
}
