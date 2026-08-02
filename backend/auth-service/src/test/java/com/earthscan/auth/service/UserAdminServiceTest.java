package com.earthscan.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.earthscan.auth.domain.Role;
import com.earthscan.auth.domain.User;
import com.earthscan.auth.dto.UpdateRoleRequest;
import com.earthscan.auth.dto.UserResponse;
import com.earthscan.auth.dto.UserStatsResponse;
import com.earthscan.auth.repository.RoleRepository;
import com.earthscan.auth.repository.UserRepository;
import com.earthscan.common.event.IntegrationEvent;
import com.earthscan.common.event.UserDeletedEvent;
import com.earthscan.common.event.UserRoleChangedEvent;
import com.earthscan.common.exception.BadRequestException;
import com.earthscan.common.exception.ResourceNotFoundException;
import com.earthscan.common.messaging.EventPublisher;
import com.earthscan.common.messaging.RabbitTopology;
import com.earthscan.common.security.RoleName;
import java.lang.reflect.Field;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserAdminService")
class UserAdminServiceTest {

    private static final Long ACTING_ADMIN_ID = 1L;

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private EventPublisher eventPublisher;

    private UserAdminService service;

    @BeforeEach
    void setUp() {
        service = new UserAdminService(userRepository, roleRepository, eventPublisher);
    }

    private static User user(long id, String name, RoleName roleName) {
        User user = new User(name, name.toLowerCase().replace(' ', '.') + "@example.com", "hash");
        user.assignRole(new Role(roleName, "test"));
        try {
            Field idField = User.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(user, id);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
        return user;
    }

    @Nested
    @DisplayName("updateRole")
    class UpdateRole {

        @Test
        @DisplayName("changes the role and publishes UserRoleChangedEvent")
        void updatesRole() {
            User target = user(5L, "Ravi Patil", RoleName.FARMER);
            when(userRepository.findById(5L)).thenReturn(Optional.of(target));
            when(roleRepository.findByName(RoleName.AGRICULTURE_EXPERT))
                    .thenReturn(Optional.of(new Role(RoleName.AGRICULTURE_EXPERT, "test")));
            when(userRepository.save(target)).thenReturn(target);

            UserResponse response = service.updateRole(
                    5L, new UpdateRoleRequest("Agriculture Expert"), ACTING_ADMIN_ID);

            assertThat(response.role()).isEqualTo("Agriculture Expert");

            ArgumentCaptor<IntegrationEvent> captor = ArgumentCaptor.forClass(IntegrationEvent.class);
            verify(eventPublisher).publish(
                    org.mockito.ArgumentMatchers.eq(RabbitTopology.ROUTING_USER_ROLE_CHANGED),
                    captor.capture());
            UserRoleChangedEvent event = (UserRoleChangedEvent) captor.getValue();
            assertThat(event.getPreviousRole()).isEqualTo("Farmer");
            assertThat(event.getNewRole()).isEqualTo("Agriculture Expert");
        }

        @Test
        @DisplayName("is a no-op when the role is unchanged, and publishes no event")
        void skipsWhenRoleUnchanged() {
            User target = user(5L, "Ravi Patil", RoleName.FARMER);
            when(userRepository.findById(5L)).thenReturn(Optional.of(target));

            UserResponse response = service.updateRole(
                    5L, new UpdateRoleRequest("Farmer"), ACTING_ADMIN_ID);

            assertThat(response.role()).isEqualTo("Farmer");
            verify(userRepository, never()).save(any());
            verify(eventPublisher, never()).publish(anyString(), any());
        }

        @Test
        @DisplayName("refuses to demote the last remaining administrator")
        void refusesToDemoteLastAdmin() {
            User lastAdmin = user(2L, "Only Admin", RoleName.ADMIN);
            when(userRepository.findById(2L)).thenReturn(Optional.of(lastAdmin));
            when(userRepository.countByRoles_Name(RoleName.ADMIN)).thenReturn(1L);

            assertThatThrownBy(() -> service.updateRole(
                    2L, new UpdateRoleRequest("Farmer"), ACTING_ADMIN_ID))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("last administrator");

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("allows demoting an administrator while others remain")
        void allowsDemotingWhenOtherAdminsExist() {
            User admin = user(2L, "Second Admin", RoleName.ADMIN);
            when(userRepository.findById(2L)).thenReturn(Optional.of(admin));
            when(userRepository.countByRoles_Name(RoleName.ADMIN)).thenReturn(3L);
            when(roleRepository.findByName(RoleName.FARMER))
                    .thenReturn(Optional.of(new Role(RoleName.FARMER, "test")));
            when(userRepository.save(admin)).thenReturn(admin);

            UserResponse response = service.updateRole(
                    2L, new UpdateRoleRequest("Farmer"), ACTING_ADMIN_ID);

            assertThat(response.role()).isEqualTo("Farmer");
        }

        @Test
        @DisplayName("returns 404 for an unknown user")
        void failsForUnknownUser() {
            when(userRepository.findById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.updateRole(
                    404L, new UpdateRoleRequest("Farmer"), ACTING_ADMIN_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("deleteUser")
    class DeleteUser {

        @Test
        @DisplayName("deletes and publishes UserDeletedEvent so other services can clean up")
        void deletesUser() {
            User target = user(5L, "Ravi Patil", RoleName.FARMER);
            when(userRepository.findById(5L)).thenReturn(Optional.of(target));

            service.deleteUser(5L, ACTING_ADMIN_ID);

            verify(userRepository).delete(target);

            ArgumentCaptor<IntegrationEvent> captor = ArgumentCaptor.forClass(IntegrationEvent.class);
            verify(eventPublisher).publish(
                    org.mockito.ArgumentMatchers.eq(RabbitTopology.ROUTING_USER_DELETED),
                    captor.capture());
            assertThat(((UserDeletedEvent) captor.getValue()).getUserId()).isEqualTo(5L);
        }

        @Test
        @DisplayName("refuses self-deletion, which would strand the caller mid-session")
        void refusesSelfDeletion() {
            User self = user(ACTING_ADMIN_ID, "Acting Admin", RoleName.ADMIN);
            when(userRepository.findById(ACTING_ADMIN_ID)).thenReturn(Optional.of(self));

            assertThatThrownBy(() -> service.deleteUser(ACTING_ADMIN_ID, ACTING_ADMIN_ID))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("your own administrator account");

            verify(userRepository, never()).delete(any());
            verify(eventPublisher, never()).publish(anyString(), any());
        }

        @Test
        @DisplayName("refuses to delete the last administrator, which would lock everyone out")
        void refusesToDeleteLastAdmin() {
            User lastAdmin = user(2L, "Only Admin", RoleName.ADMIN);
            when(userRepository.findById(2L)).thenReturn(Optional.of(lastAdmin));
            when(userRepository.countByRoles_Name(RoleName.ADMIN)).thenReturn(1L);

            assertThatThrownBy(() -> service.deleteUser(2L, ACTING_ADMIN_ID))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("last administrator");

            verify(userRepository, never()).delete(any());
        }

        @Test
        @DisplayName("returns 404 for an unknown user")
        void failsForUnknownUser() {
            when(userRepository.findById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.deleteUser(404L, ACTING_ADMIN_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("setEnabled")
    class SetEnabled {

        @Test
        @DisplayName("disables an ordinary account")
        void disablesAccount() {
            User target = user(5L, "Ravi Patil", RoleName.FARMER);
            when(userRepository.findById(5L)).thenReturn(Optional.of(target));
            when(userRepository.save(target)).thenReturn(target);

            UserResponse response = service.setEnabled(5L, false, ACTING_ADMIN_ID);

            assertThat(response.enabled()).isFalse();
        }

        @Test
        @DisplayName("refuses to disable the caller's own account")
        void refusesSelfDisable() {
            User self = user(ACTING_ADMIN_ID, "Acting Admin", RoleName.ADMIN);
            when(userRepository.findById(ACTING_ADMIN_ID)).thenReturn(Optional.of(self));

            assertThatThrownBy(() -> service.setEnabled(ACTING_ADMIN_ID, false, ACTING_ADMIN_ID))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        @DisplayName("re-enabling is always allowed, including for the last admin")
        void allowsReEnabling() {
            User target = user(2L, "Only Admin", RoleName.ADMIN);
            target.setEnabled(false);
            when(userRepository.findById(2L)).thenReturn(Optional.of(target));
            when(userRepository.save(target)).thenReturn(target);

            UserResponse response = service.setEnabled(2L, true, ACTING_ADMIN_ID);

            assertThat(response.enabled()).isTrue();
        }
    }

    @Nested
    @DisplayName("stats")
    class Stats {

        @Test
        @DisplayName("counts users per role in SQL rather than in memory")
        void countsByRole() {
            when(userRepository.count()).thenReturn(42L);
            when(userRepository.countByRoles_Name(RoleName.FARMER)).thenReturn(30L);
            when(userRepository.countByRoles_Name(RoleName.LAND_BUYER)).thenReturn(8L);
            when(userRepository.countByRoles_Name(RoleName.AGRICULTURE_EXPERT)).thenReturn(3L);
            when(userRepository.countByRoles_Name(RoleName.ADMIN)).thenReturn(1L);

            UserStatsResponse stats = service.stats();

            assertThat(stats.totalUsers()).isEqualTo(42L);
            assertThat(stats.usersByRole())
                    .containsEntry("Farmer", 30L)
                    .containsEntry("Land Buyer", 8L)
                    .containsEntry("Agriculture Expert", 3L)
                    .containsEntry("Admin", 1L);
        }
    }
}
