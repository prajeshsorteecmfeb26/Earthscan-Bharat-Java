package com.earthscan.auth.service;

import com.earthscan.auth.domain.Role;
import com.earthscan.auth.domain.User;
import com.earthscan.auth.dto.AuthResponse;
import com.earthscan.auth.dto.LoginRequest;
import com.earthscan.auth.dto.RegisterRequest;
import com.earthscan.auth.dto.ResetPasswordRequest;
import com.earthscan.auth.dto.UserResponse;
import com.earthscan.auth.repository.RoleRepository;
import com.earthscan.auth.repository.UserRepository;
import com.earthscan.common.event.UserRegisteredEvent;
import com.earthscan.common.exception.DuplicateResourceException;
import com.earthscan.common.exception.ResourceNotFoundException;
import com.earthscan.common.exception.UnauthorizedException;
import com.earthscan.common.messaging.EventPublisher;
import com.earthscan.common.messaging.RabbitTopology;
import com.earthscan.auth.security.EarthScanUserDetails;
import com.earthscan.common.security.JwtProperties;
import com.earthscan.common.security.JwtTokenProvider;
import com.earthscan.common.security.RoleName;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registration, authentication and password reset.
 *
 * <p>Authentication runs through Spring Security's {@link AuthenticationManager} rather than a
 * hand-rolled hash comparison. That was the earlier implementation, and replacing it buys three
 * things: the enabled and locked checks execute as part of the provider contract instead of as
 * ad-hoc {@code if} statements, the provider can be swapped or chained (LDAP, an external identity
 * provider) without touching this class, and {@code spring-security-test} can exercise real user
 * loading.</p>
 *
 * <p>It also removes a piece of hand-written security code. {@code DaoAuthenticationProvider}
 * already performs the timing-attack mitigation this class used to implement itself — see its
 * {@code mitigateAgainstTimingAttack}, which runs the password encoder against a dummy hash when no
 * user is found, so a missing account takes the same time to reject as a wrong password. Keeping a
 * second copy of that logic here would be duplication of a subtle security control, which is the
 * worst kind to duplicate.</p>
 */
@Slf4j
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final JwtProperties jwtProperties;
    private final EventPublisher eventPublisher;
    private final AuthenticationManager authenticationManager;

    public AuthService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider tokenProvider,
                       JwtProperties jwtProperties,
                       EventPublisher eventPublisher,
                       AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.jwtProperties = jwtProperties;
        this.eventPublisher = eventPublisher;
        this.authenticationManager = authenticationManager;
    }

    /**
     * Creates an account and announces it on the event bus.
     *
     * @throws DuplicateResourceException if the email is already taken
     */
    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();

        if (userRepository.existsByEmailIgnoreCase(email)) {
            // 409, not 400: the request was well formed, it just conflicts with existing state.
            throw new DuplicateResourceException("Email is already registered");
        }

        RoleName roleName = RoleName.from(request.role());
        Role role = roleRepository.findByName(roleName).orElseThrow(() ->
                new IllegalStateException("Role " + roleName + " is missing from the roles table. "
                        + "RoleSeeder should have created it on startup."));

        User user = new User(request.name().trim(), email, passwordEncoder.encode(request.password()));
        user.assignRole(role);
        User saved = userRepository.save(user);

        log.info("Registered user id={} with role {}", saved.getId(), roleName);

        try {
            eventPublisher.publish(RabbitTopology.ROUTING_USER_REGISTERED,
                    new UserRegisteredEvent(saved.getId(), saved.getName(), saved.getEmail(),
                            roleName.getDisplayName()));
        } catch (Exception ex) {
            log.warn("Non-critical event publishing warning for user id={}: {}", saved.getId(), ex.getMessage());
        }

        return UserResponse.from(saved);
    }

    /**
     * Verifies credentials through the {@link AuthenticationManager} and mints a token.
     *
     * @throws UnauthorizedException with a message that never distinguishes an unknown email from a
     *     wrong password
     */
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = request.email().trim();
        EarthScanUserDetails principal;

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.password()));
            principal = (EarthScanUserDetails) authentication.getPrincipal();
        } catch (DisabledException ex) {
            log.warn("Login blocked for disabled account, email ending '{}'", maskEmail(email));
            throw new UnauthorizedException("This account has been disabled");
        } catch (BadCredentialsException ex) {
            // Covers both "no such account" and "wrong password" - DaoAuthenticationProvider
            // collapses them by design, and this handler must not pull them back apart.
            log.warn("Failed login attempt for email ending '{}'", maskEmail(email));
            throw new UnauthorizedException("Invalid email or password");
        }

        String token = tokenProvider.generateToken(
                principal.getId(), principal.getName(), principal.getEmail(), principal.getRoles());

        log.info("Issued token for user id={} roles={}", principal.getId(), principal.getRoles());

        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> ResourceNotFoundException.of("User", principal.getId()));

        return new AuthResponse(token, jwtProperties.getExpirationMs() / 1000,
                UserResponse.from(user));
    }

    /**
     * Sets a new password for the given email.
     *
     * @throws ResourceNotFoundException if no such account exists. This does leak account existence;
     *     it matches the original behaviour and the endpoint is rate-limited to blunt enumeration.
     */
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        User user = userRepository.findByEmailIgnoreCase(request.email().trim())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User with this email does not exist"));

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        log.info("Password reset completed for user id={}", user.getId());
    }

    /** Returns the caller's own profile, resolved from the token's subject. */
    @Transactional(readOnly = true)
    public UserResponse currentUser(Long userId) {
        return userRepository.findById(userId)
                .map(UserResponse::from)
                .orElseThrow(() -> ResourceNotFoundException.of("User", userId));
    }

    /** Keeps failed-login logs useful without writing full addresses into log files. */
    private String maskEmail(String email) {
        if (email == null) {
            return "unknown";
        }
        int at = email.indexOf('@');
        return at < 0 ? "***" : "***" + email.substring(at);
    }
}
