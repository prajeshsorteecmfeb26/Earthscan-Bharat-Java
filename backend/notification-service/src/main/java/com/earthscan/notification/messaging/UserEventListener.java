package com.earthscan.notification.messaging;

import com.earthscan.common.event.UserDeletedEvent;
import com.earthscan.common.event.UserRegisteredEvent;
import com.earthscan.common.event.UserRoleChangedEvent;
import com.earthscan.common.messaging.RabbitTopology;
import com.earthscan.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumes identity events from auth-service.
 *
 * <p>Transport only. Message content is composed by the {@code NotificationFactory} beans, so this
 * class has one responsibility — receive, delegate, acknowledge — and adding a new user event type
 * requires no change here.</p>
 *
 * <p>One queue carries all three payload types, dispatched by the {@code __TypeId__} header that
 * {@code Jackson2JsonMessageConverter} writes on publish. That is why the event classes live in
 * common-lib and must keep identical fully-qualified names on both sides of the broker.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RabbitListener(queues = RabbitTopology.QUEUE_NOTIFICATION_USER)
public class UserEventListener {

    private final NotificationService notificationService;

    @RabbitHandler
    public void onUserRegistered(UserRegisteredEvent event) {
        log.debug("Handling user.registered for user id={}", event.getUserId());
        notificationService.createFrom(event);
    }

    @RabbitHandler
    public void onUserRoleChanged(UserRoleChangedEvent event) {
        log.debug("Handling user.role-changed for user id={}", event.getUserId());
        notificationService.createFrom(event);
    }

    /**
     * Purges a deleted user's notifications.
     *
     * <p>Handled here rather than by a factory because it is a deletion, not a creation. Unlike the
     * forum, there is nothing to preserve: a notification is addressed to exactly one person and is
     * meaningless once that account is gone.</p>
     */
    @RabbitHandler
    public void onUserDeleted(UserDeletedEvent event) {
        if (event.getUserId() == null) {
            log.error("Discarding UserDeletedEvent {} with no userId", event.getEventId());
            return;
        }
        long removed = notificationService.deleteAllForUser(event.getUserId());
        log.info("Handled user.deleted for user id={} [event {}]: removed {} notification(s)",
                event.getUserId(), event.getEventId(), removed);
    }

    /**
     * Catches any user-routed payload with no handler.
     *
     * <p>Without a fallback an unrecognised type throws, is retried four times, and lands in the DLQ
     * — noisy, and it delays every message queued behind it. Logging and acknowledging is the right
     * response to an event this service does not care about.</p>
     */
    @RabbitHandler(isDefault = true)
    public void onUnknownUserEvent(Object payload) {
        log.warn("Ignoring unhandled user event of type {}", payload.getClass().getName());
    }
}
