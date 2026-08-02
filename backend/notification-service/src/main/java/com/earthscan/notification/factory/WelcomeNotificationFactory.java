package com.earthscan.notification.factory;

import com.earthscan.common.event.UserRegisteredEvent;
import com.earthscan.notification.domain.Notification;
import com.earthscan.notification.domain.NotificationType;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Role-specific welcome message on registration. */
@Component
public class WelcomeNotificationFactory implements NotificationFactory<UserRegisteredEvent> {

    @Override
    public Class<UserRegisteredEvent> eventType() {
        return UserRegisteredEvent.class;
    }

    @Override
    public Optional<Notification> create(UserRegisteredEvent event) {
        if (event.getUserId() == null) {
            return Optional.empty();
        }

        // All user-facing copy for this notification lives here, in one block, so it can be handed
        // to a translator without them reading the messaging layer.
        String body = switch (event.getRole() == null ? "" : event.getRole()) {
            case "Farmer" -> "Start by checking the water and crop advisory tools for your land.";
            case "Land Buyer" -> "Search verified listings and compare land intelligence scores.";
            case "Agriculture Expert" -> "Farmers are waiting for answers - see the unanswered queue.";
            case "Admin" -> "You have full administrative access to the platform.";
            default -> "Welcome to the EarthScan Bharat platform.";
        };

        return Optional.of(new Notification(
                event.getUserId(),
                NotificationType.WELCOME,
                "Welcome to EarthScan Bharat, " + event.getName(),
                body,
                "/",
                event.getEventId()));
    }
}
