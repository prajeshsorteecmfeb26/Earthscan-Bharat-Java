package com.earthscan.notification.factory;

import com.earthscan.common.event.ForumCommentAddedEvent;
import com.earthscan.notification.domain.Notification;
import com.earthscan.notification.domain.NotificationType;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Tells a thread author their question received a reply.
 *
 * <p>The single most useful notification on the platform: a farmer posts a question and has no reason
 * to keep refreshing, so without this an expert's answer can go unread for days.</p>
 */
@Component
public class QuestionAnsweredNotificationFactory
        implements NotificationFactory<ForumCommentAddedEvent> {

    @Override
    public Class<ForumCommentAddedEvent> eventType() {
        return ForumCommentAddedEvent.class;
    }

    @Override
    public Optional<Notification> create(ForumCommentAddedEvent event) {
        if (event.getPostAuthorId() == null) {
            return Optional.empty();
        }
        // Nobody needs telling about their own reply to their own thread. Empty is the correct
        // outcome here, not an error - which is why the factory contract returns Optional.
        if (Objects.equals(event.getPostAuthorId(), event.getCommenterId())) {
            return Optional.empty();
        }

        return Optional.of(new Notification(
                event.getPostAuthorId(),
                NotificationType.QUESTION_ANSWERED,
                "Your question has a new reply",
                "%s (%s) replied to \"%s\".".formatted(
                        event.getCommenterName(), event.getCommenterRole(), event.getPostTitle()),
                "/forum/" + event.getPostId(),
                event.getEventId()));
    }
}
