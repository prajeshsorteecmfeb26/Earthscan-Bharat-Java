package com.earthscan.notification.factory;

import com.earthscan.common.event.ForumPostCreatedEvent;
import com.earthscan.notification.domain.Notification;
import com.earthscan.notification.domain.NotificationType;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Acknowledges a newly opened thread back to its author.
 *
 * <p>Alerting every Agriculture Expert about every new question would be the more obvious behaviour,
 * but it needs a subscription model — experts choosing the categories they cover. Without one it
 * degenerates into notification spam that gets muted, taking the useful notifications with it.</p>
 */
@Component
public class PostCreatedNotificationFactory implements NotificationFactory<ForumPostCreatedEvent> {

    @Override
    public Class<ForumPostCreatedEvent> eventType() {
        return ForumPostCreatedEvent.class;
    }

    @Override
    public Optional<Notification> create(ForumPostCreatedEvent event) {
        if (event.getAuthorId() == null) {
            return Optional.empty();
        }

        return Optional.of(new Notification(
                event.getAuthorId(),
                NotificationType.NEW_QUESTION_IN_CATEGORY,
                "Your question has been posted",
                "\"%s\" is now visible in %s. You will be notified when someone replies."
                        .formatted(event.getTitle(), event.getCategory()),
                "/forum/" + event.getPostId(),
                event.getEventId()));
    }
}
