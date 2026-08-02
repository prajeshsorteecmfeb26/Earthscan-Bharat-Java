package com.earthscan.auth.security;

import com.earthscan.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads a platform account for Spring Security's {@code DaoAuthenticationProvider}.
 *
 * <p>This is the piece that lets authentication go through {@code AuthenticationManager} rather than
 * a hand-rolled hash comparison, which brings three things the manual path did not have: the
 * enabled/locked checks run as part of the provider contract, {@code AuthenticationProvider} can be
 * swapped or chained (LDAP, an external IdP) without touching the login service, and
 * {@code @WithMockUser} and the rest of {@code spring-security-test} work against real user loading
 * in tests.</p>
 *
 * <p>One behaviour is preserved from the manual implementation and is deliberate: the exception
 * message never says whether the account exists. See {@code DaoAuthenticationProvider}'s
 * {@code hideUserNotFoundExceptions}, which is left at its default of {@code true} so a missing user
 * and a wrong password both surface as {@code BadCredentialsException}.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EarthScanUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * @param email the login identifier
     * @throws UsernameNotFoundException when no account matches. The message is intentionally
     *     generic — anything more specific is a user-enumeration leak.
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmailIgnoreCase(email == null ? "" : email.trim())
                .map(user -> {
                    log.debug("Loaded account id={} for authentication", user.getId());
                    return (UserDetails) new EarthScanUserDetails(user);
                })
                .orElseThrow(() -> {
                    log.debug("Authentication attempted for an unknown account");
                    return new UsernameNotFoundException("Invalid email or password");
                });
    }
}
