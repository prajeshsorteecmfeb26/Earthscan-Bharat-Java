package com.earthscan.notification.factory;

import com.earthscan.common.event.UserRoleChangedEvent;
import com.earthscan.notification.domain.Notification;
import com.earthscan.notification.domain.NotificationType;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Tells a user their role changed, and that they must re-authenticate for it to take effect. */
@Component
public class RoleChangedNotificationFactory implements NotificationFactory<UserRoleChangedEvent> {

    @Override
    public Class<UserRoleChangedEvent> eventType() {
        return UserRoleChangedEvent.class;
    }

    @Override
    public Optional<Notification> create(UserRoleChangedEvent event) {
        if (event.getUserId() == null) {
            return Optional.empty();
        }

        // The "sign out and back in" instruction is functionally necessary, not politeness: a JWT
        // carries the role it was minted with, so the existing token keeps the old role until it
        // expires or is reissued.
        return Optional.of(new Notification(
                event.getUserId(),
                NotificationType.ROLE_CHANGED,
                "Your account role has changed",
                "An administrator changed your role from %s to %s. Sign out and back in for the "
                        .formatted(event.getPreviousRole(), event.getNewRole())
                        + "change to take effect.",
                "/",
                event.getEventId()));
    }
}
