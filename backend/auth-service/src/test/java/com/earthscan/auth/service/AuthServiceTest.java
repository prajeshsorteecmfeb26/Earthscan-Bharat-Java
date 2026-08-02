package com.earthscan.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.earthscan.auth.domain.Role;
import com.earthscan.auth.domain.User;
import com.earthscan.auth.dto.AuthResponse;
import com.earthscan.auth.dto.LoginRequest;
import com.earthscan.auth.dto.RegisterRequest;
import com.earthscan.auth.dto.ResetPasswordRequest;
import com.earthscan.auth.dto.UserResponse;
import com.earthscan.auth.repository.RoleRepository;
import com.earthscan.auth.repository.UserRepository;
import com.earthscan.common.event.IntegrationEvent;
import com.earthscan.common.event.UserRegisteredEvent;
import com.earthscan.common.exception.DuplicateResourceException;
import com.earthscan.common.exception.ResourceNotFoundException;
import com.earthscan.common.exception.UnauthorizedException;
import com.earthscan.common.messaging.EventPublisher;
import com.earthscan.common.messaging.RabbitTopology;
import com.earthscan.common.security.JwtProperties;
import com.earthscan.common.security.JwtTokenProvider;
import com.earthscan.auth.security.EarthScanUserDetails;
import com.earthscan.common.security.RoleName;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService")
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtTokenProvider tokenProvider;
    @Mock
    private EventPublisher eventPublisher;
    @Mock
    private AuthenticationManager authenticationManager;

    @Captor
    private ArgumentCaptor<IntegrationEvent> eventCaptor;

    private JwtProperties jwtProperties;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        jwtProperties = new JwtProperties();
        jwtProperties.setExpirationMs(604_800_000L);
        authService = new AuthService(userRepository, roleRepository, passwordEncoder,
                tokenProvider, jwtProperties, eventPublisher, authenticationManager);
    }

    // ------------------------------------------------------------------ helpers

    private static Role role(RoleName name) {
        return new Role(name, "test role");
    }

    /**
     * Assigns the JPA-generated id, which has no setter by design. Reflection is used here rather
     * than adding a production setter purely to satisfy tests — the id must stay immutable to
     * application code.
     */
    private static User userWithId(long id, String email, String hash, RoleName roleName) {
        User user = new User("Anita Deshmukh", email, hash);
        user.assignRole(role(roleName));
        try {
            Field idField = User.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(user, id);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Test fixture could not set User.id", ex);
        }
        return user;
    }

    @Nested
    @DisplayName("register")
    class Register {

        private final RegisterRequest request = new RegisterRequest(
                "Anita Deshmukh", "Anita@Example.com", "harvest2026", "Land Buyer");

        @Test
        @DisplayName("persists the user, hashes the password and publishes UserRegisteredEvent")
        void registersSuccessfully() {
            when(userRepository.existsByEmailIgnoreCase("anita@example.com")).thenReturn(false);
            when(roleRepository.findByName(RoleName.LAND_BUYER))
                    .thenReturn(Optional.of(role(RoleName.LAND_BUYER)));
            when(passwordEncoder.encode("harvest2026")).thenReturn("$2a$10$hashed");
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
                User toSave = invocation.getArgument(0);
                return userWithId(7L, toSave.getEmail(), toSave.getPasswordHash(),
                        RoleName.LAND_BUYER);
            });

            UserResponse response = authService.register(request);

            assertThat(response.id()).isEqualTo(7L);
            assertThat(response.role()).isEqualTo("Land Buyer");

            ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(savedUser.capture());
            assertThat(savedUser.getValue().getPasswordHash()).isEqualTo("$2a$10$hashed");
            // The plaintext must never reach the entity.
            assertThat(savedUser.getValue().getPasswordHash()).isNotEqualTo("harvest2026");

            verify(eventPublisher).publish(eq(RabbitTopology.ROUTING_USER_REGISTERED),
                    eventCaptor.capture());
            assertThat(eventCaptor.getValue()).isInstanceOf(UserRegisteredEvent.class);
            UserRegisteredEvent event = (UserRegisteredEvent) eventCaptor.getValue();
            assertThat(event.getUserId()).isEqualTo(7L);
            assertThat(event.getRole()).isEqualTo("Land Buyer");
        }

        @Test
        @DisplayName("normalises the email to lower case before storing it")
        void lowercasesEmail() {
            when(userRepository.existsByEmailIgnoreCase("anita@example.com")).thenReturn(false);
            when(roleRepository.findByName(RoleName.LAND_BUYER))
                    .thenReturn(Optional.of(role(RoleName.LAND_BUYER)));
            when(passwordEncoder.encode(anyString())).thenReturn("hash");
            when(userRepository.save(any(User.class)))
                    .thenAnswer(invocation -> userWithId(1L,
                            ((User) invocation.getArgument(0)).getEmail(), "hash",
                            RoleName.LAND_BUYER));

            authService.register(request);

            ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(saved.capture());
            assertThat(saved.getValue().getEmail()).isEqualTo("anita@example.com");
        }

        @Test
        @DisplayName("rejects a duplicate email with 409 and publishes nothing")
        void rejectsDuplicateEmail() {
            when(userRepository.existsByEmailIgnoreCase("anita@example.com")).thenReturn(true);

            assertThatThrownBy(() -> authService.register(request))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessage("Email is already registered");

            verify(userRepository, never()).save(any());
            verify(eventPublisher, never()).publish(anyString(), any());
        }

        @Test
        @DisplayName("rejects an unknown role name")
        void rejectsUnknownRole() {
            RegisterRequest bad = new RegisterRequest(
                    "Anita", "anita@example.com", "harvest2026", "Supreme Overlord");
            when(userRepository.existsByEmailIgnoreCase("anita@example.com")).thenReturn(false);

            assertThatThrownBy(() -> authService.register(bad))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unknown role");
        }
    }

    @Nested
    @DisplayName("login")
    class Login {

        private final LoginRequest request = new LoginRequest("anita@example.com", "harvest2026");

        private Authentication authenticatedAs(User user) {
            EarthScanUserDetails principal = new EarthScanUserDetails(user);
            return new UsernamePasswordAuthenticationToken(
                    principal, null, principal.getAuthorities());
        }

        @Test
        @DisplayName("returns a token and the user profile on valid credentials")
        void returnsToken() {
            User user = userWithId(7L, "anita@example.com", "$2a$10$stored", RoleName.FARMER);
            when(authenticationManager.authenticate(any())).thenReturn(authenticatedAs(user));
            when(userRepository.findById(7L)).thenReturn(Optional.of(user));
            when(tokenProvider.generateToken(7L, "Anita Deshmukh", "anita@example.com",
                    List.of(RoleName.FARMER))).thenReturn("signed.jwt.token");

            AuthResponse response = authService.login(request);

            assertThat(response.token()).isEqualTo("signed.jwt.token");
            assertThat(response.expiresInSeconds()).isEqualTo(604_800L);
            assertThat(response.user().email()).isEqualTo("anita@example.com");
            assertThat(response.user().role()).isEqualTo("Farmer");
        }

        @Test
        @DisplayName("delegates credential checking to the AuthenticationManager")
        void delegatesToAuthenticationManager() {
            User user = userWithId(7L, "anita@example.com", "$2a$10$stored", RoleName.FARMER);
            when(authenticationManager.authenticate(any())).thenReturn(authenticatedAs(user));
            when(userRepository.findById(7L)).thenReturn(Optional.of(user));
            when(tokenProvider.generateToken(any(), any(), any(), any())).thenReturn("t");

            authService.login(request);

            ArgumentCaptor<org.springframework.security.core.Authentication> captor =
                    ArgumentCaptor.forClass(org.springframework.security.core.Authentication.class);
            verify(authenticationManager).authenticate(captor.capture());
            assertThat(captor.getValue().getName()).isEqualTo("anita@example.com");
            // The service must not compare hashes itself - that is the provider's job now, and a
            // second copy of that logic is exactly what this refactor removed.
            verify(passwordEncoder, never()).matches(anyString(), anyString());
        }

        @Test
        @DisplayName("gives the same error for an unknown email as for a wrong password")
        void doesNotRevealWhetherAccountExists() {
            // DaoAuthenticationProvider collapses both cases into BadCredentialsException, and this
            // assertion pins that they stay collapsed at the service boundary.
            when(authenticationManager.authenticate(any()))
                    .thenThrow(new BadCredentialsException("Bad credentials"));

            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessage("Invalid email or password");

            verify(tokenProvider, never()).generateToken(any(), any(), any(), any());
        }

        @Test
        @DisplayName("refuses a disabled account with a distinct, non-enumerating message")
        void rejectsDisabledAccount() {
            when(authenticationManager.authenticate(any()))
                    .thenThrow(new DisabledException("Account disabled"));

            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessage("This account has been disabled");

            verify(tokenProvider, never()).generateToken(any(), any(), any(), any());
        }

        @Test
        @DisplayName("mints no token when authentication fails")
        void mintsNoTokenOnFailure() {
            when(authenticationManager.authenticate(any()))
                    .thenThrow(new BadCredentialsException("Bad credentials"));

            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(UnauthorizedException.class);

            verify(tokenProvider, never()).generateToken(any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("resetPassword")
    class ResetPassword {

        @Test
        @DisplayName("re-hashes and stores the new password")
        void resetsPassword() {
            User user = userWithId(7L, "anita@example.com", "$2a$10$old", RoleName.FARMER);
            when(userRepository.findByEmailIgnoreCase("anita@example.com"))
                    .thenReturn(Optional.of(user));
            when(passwordEncoder.encode("newharvest2026")).thenReturn("$2a$10$new");

            authService.resetPassword(
                    new ResetPasswordRequest("anita@example.com", "newharvest2026"));

            assertThat(user.getPasswordHash()).isEqualTo("$2a$10$new");
            verify(userRepository).save(user);
        }

        @Test
        @DisplayName("returns 404 for an unknown email")
        void failsForUnknownEmail() {
            when(userRepository.findByEmailIgnoreCase("nobody@example.com"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.resetPassword(
                    new ResetPasswordRequest("nobody@example.com", "newharvest2026")))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("currentUser")
    class CurrentUser {

        @Test
        @DisplayName("maps the entity to a response for a known id")
        void returnsProfile() {
            when(userRepository.findById(7L)).thenReturn(
                    Optional.of(userWithId(7L, "anita@example.com", "h", RoleName.ADMIN)));

            UserResponse response = authService.currentUser(7L);

            assertThat(response.role()).isEqualTo("Admin");
        }

        @Test
        @DisplayName("returns 404 when the token references a deleted account")
        void failsForDeletedAccount() {
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.currentUser(99L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
