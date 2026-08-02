package com.earthscan.auth.service;

import com.earthscan.auth.domain.Role;
import com.earthscan.auth.domain.User;
import com.earthscan.auth.dto.UpdateRoleRequest;
import com.earthscan.auth.dto.UserResponse;
import com.earthscan.auth.dto.UserStatsResponse;
import com.earthscan.auth.repository.RoleRepository;
import com.earthscan.auth.repository.UserRepository;
import com.earthscan.common.event.UserDeletedEvent;
import com.earthscan.common.event.UserRoleChangedEvent;
import com.earthscan.common.exception.BadRequestException;
import com.earthscan.common.exception.ResourceNotFoundException;
import com.earthscan.common.messaging.EventPublisher;
import com.earthscan.common.messaging.RabbitTopology;
import com.earthscan.common.security.RoleName;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Administrative operations over user accounts.
 *
 * <p>Two safety rules are enforced here rather than in the controller, because they are domain
 * invariants and must hold no matter which entry point calls them: an administrator may not delete
 * their own account, and the last remaining administrator may not be removed or demoted. The
 * original {@code AdminController} had a {@code // Optional: prevent deleting the last admin} comment
 * and no implementation, which means one wrong click could lock every administrator out of the
 * platform with no recovery path through the UI.</p>
 */
@Service
public class UserAdminService {

    private static final Logger log = LoggerFactory.getLogger(UserAdminService.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final EventPublisher eventPublisher;

    public UserAdminService(UserRepository userRepository,
                            RoleRepository roleRepository,
                            EventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.eventPublisher = eventPublisher;
    }

    /** Full user list, unpaged. Kept for backwards compatibility with the current admin table. */
    @Transactional(readOnly = true)
    public List<UserResponse> findAll() {
        return userRepository.findAll().stream().map(UserResponse::from).toList();
    }

    /** Paged and searchable variant for the admin console as the table grows. */
    @Transactional(readOnly = true)
    public Page<UserResponse> search(String term, Pageable pageable) {
        Page<User> page = (term == null || term.isBlank())
                ? userRepository.findAll(pageable)
                : userRepository.search(term.trim(), pageable);
        return page.map(UserResponse::from);
    }

    @Transactional(readOnly = true)
    public UserResponse findById(Long id) {
        return userRepository.findById(id)
                .map(UserResponse::from)
                .orElseThrow(() -> ResourceNotFoundException.of("User", id));
    }

    @Transactional(readOnly = true)
    public UserStatsResponse stats() {
        Map<String, Long> byRole = new LinkedHashMap<>();
        for (RoleName roleName : RoleName.values()) {
            byRole.put(roleName.getDisplayName(), userRepository.countByRoles_Name(roleName));
        }
        return new UserStatsResponse(userRepository.count(), byRole);
    }

    /**
     * Changes a user's role and announces the change.
     *
     * @param actingAdminId the administrator performing the change, from the JWT subject
     * @throws BadRequestException if this would demote the last administrator
     */
    @Transactional
    public UserResponse updateRole(Long userId, UpdateRoleRequest request, Long actingAdminId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ResourceNotFoundException.of("User", userId));

        RoleName targetRole = RoleName.from(request.role());
        RoleName previousRole = user.getPrimaryRoleName();

        if (previousRole == targetRole) {
            return UserResponse.from(user);   // No-op, no event, no log noise.
        }

        if (previousRole == RoleName.ADMIN && isLastAdmin()) {
            throw new BadRequestException(
                    "This is the last administrator account and its role cannot be changed");
        }

        Role role = roleRepository.findByName(targetRole).orElseThrow(() ->
                new IllegalStateException("Role " + targetRole + " is missing from the roles table"));
        user.assignRole(role);
        User saved = userRepository.save(user);

        log.info("Admin id={} changed role of user id={} from {} to {}",
                actingAdminId, userId, previousRole, targetRole);

        eventPublisher.publish(RabbitTopology.ROUTING_USER_ROLE_CHANGED,
                new UserRoleChangedEvent(saved.getId(), saved.getName(), saved.getEmail(),
                        previousRole.getDisplayName(), targetRole.getDisplayName()));

        return UserResponse.from(saved);
    }

    /**
     * Deletes a user and publishes {@link UserDeletedEvent} so land-service and forum-service can
     * clean up the records they own.
     *
     * <p>The event is published after the delete has been staged in the transaction. If the
     * transaction later rolls back, a spurious event has gone out — an accepted trade-off here,
     * because the alternative (a transactional outbox table plus a relay) is a large amount of
     * machinery for an operation an administrator performs a handful of times a month. The consumers
     * are written to tolerate an unknown user id.</p>
     *
     * @throws BadRequestException if an admin targets their own account, or the last admin
     */
    @Transactional
    public void deleteUser(Long userId, Long actingAdminId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ResourceNotFoundException.of("User", userId));

        if (userId.equals(actingAdminId)) {
            throw new BadRequestException("You cannot delete your own administrator account");
        }
        if (user.hasRole(RoleName.ADMIN) && isLastAdmin()) {
            throw new BadRequestException(
                    "This is the last administrator account and it cannot be deleted");
        }

        userRepository.delete(user);
        log.info("Admin id={} deleted user id={}", actingAdminId, userId);

        eventPublisher.publish(RabbitTopology.ROUTING_USER_DELETED,
                new UserDeletedEvent(user.getId(), user.getName(), user.getEmail()));
    }

    /** Enables or disables an account without destroying its data. */
    @Transactional
    public UserResponse setEnabled(Long userId, boolean enabled, Long actingAdminId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ResourceNotFoundException.of("User", userId));

        if (!enabled && userId.equals(actingAdminId)) {
            throw new BadRequestException("You cannot disable your own administrator account");
        }
        if (!enabled && user.hasRole(RoleName.ADMIN) && isLastAdmin()) {
            throw new BadRequestException(
                    "This is the last administrator account and it cannot be disabled");
        }

        user.setEnabled(enabled);
        User saved = userRepository.save(user);
        log.info("Admin id={} set enabled={} on user id={}", actingAdminId, enabled, userId);
        return UserResponse.from(saved);
    }

    private boolean isLastAdmin() {
        return userRepository.countByRoles_Name(RoleName.ADMIN) <= 1;
    }
}
