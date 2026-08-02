package com.earthscan.notification.messaging;

import com.earthscan.common.event.ForumCommentAddedEvent;
import com.earthscan.common.event.ForumPostCreatedEvent;
import com.earthscan.common.messaging.RabbitTopology;
import com.earthscan.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumes forum events. Transport only.
 *
 * <p>Note what is absent: the self-reply check. "Do not notify someone about their own reply" is a
 * content decision, so it lives in {@code QuestionAnsweredNotificationFactory} where it can be tested
 * without a broker.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RabbitListener(queues = RabbitTopology.QUEUE_NOTIFICATION_FORUM)
public class ForumEventListener {

    private final NotificationService notificationService;

    @RabbitHandler
    public void onCommentAdded(ForumCommentAddedEvent event) {
        log.debug("Handling forum.comment.added for post id={}", event.getPostId());
        notificationService.createFrom(event);
    }

    @RabbitHandler
    public void onPostCreated(ForumPostCreatedEvent event) {
        log.debug("Handling forum.post.created for post id={}", event.getPostId());
        notificationService.createFrom(event);
    }

    @RabbitHandler(isDefault = true)
    public void onUnknownForumEvent(Object payload) {
        log.warn("Ignoring unhandled forum event of type {}", payload.getClass().getName());
    }
}
