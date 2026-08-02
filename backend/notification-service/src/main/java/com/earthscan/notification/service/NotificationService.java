package com.earthscan.notification.service;

import com.earthscan.common.event.IntegrationEvent;
import com.earthscan.common.exception.ResourceNotFoundException;
import com.earthscan.notification.domain.Notification;
import com.earthscan.notification.factory.NotificationFactoryRegistry;
import com.earthscan.notification.domain.NotificationType;
import com.earthscan.notification.dto.NotificationResponse;
import com.earthscan.notification.repository.NotificationRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/** Creation and retrieval of notifications. */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository repository;
    private final NotificationFactoryRegistry factoryRegistry;

    public NotificationService(NotificationRepository repository,
                               NotificationFactoryRegistry factoryRegistry) {
        this.repository = repository;
        this.factoryRegistry = factoryRegistry;
    }

    /**
     * Turns any domain event into a notification, if one is warranted.
     *
     * <p>The single entry point used by all three RabbitMQ listeners. They no longer compose message
     * text themselves — the {@link NotificationFactoryRegistry} resolves the right factory and this
     * method handles persistence and idempotency. Listeners are left with transport concerns only.</p>
     *
     * @return the persisted notification, or {@code null} when no factory handled the event, the
     *     factory decided none was warranted, or it was a duplicate delivery
     */
    public Notification createFrom(IntegrationEvent event) {
        return factoryRegistry.create(event)
                .map(this::persistIfNew)
                .orElse(null);
    }

    /** Shared persistence path with the idempotency guard applied. */
    private Notification persistIfNew(Notification notification) {
        return create(notification.getUserId(),
                notification.getType(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getActionPath(),
                notification.getSourceEventId());
    }

    /**
     * Creates a notification, silently ignoring a duplicate delivery of the same source event.
     *
     * <p>The idempotency check is deliberately belt-and-braces: an {@code exists} probe first, then a
     * caught {@link DuplicateKeyException} from the unique index. The probe alone is not sufficient —
     * two consumer threads handling the same redelivered message can both pass it before either
     * writes. The index is what actually guarantees uniqueness; the probe just avoids the common case
     * reaching the database as an error.</p>
     *
     * @return the persisted notification, or {@code null} when it was a duplicate
     */
    public Notification create(Long userId,
                               NotificationType type,
                               String title,
                               String message,
                               String actionPath,
                               String sourceEventId) {
        if (userId == null) {
            log.debug("Skipping {} notification with no target user (event {})", type, sourceEventId);
            return null;
        }
        if (repository.existsBySourceEventIdAndUserId(sourceEventId, userId)) {
            log.debug("Skipping duplicate {} notification for user {} from event {}",
                    type, userId, sourceEventId);
            return null;
        }

        try {
            Notification saved = repository.save(new Notification(
                    userId, type, title, message, actionPath, sourceEventId));
            log.info("Created {} notification id={} for user id={}", type, saved.getId(), userId);
            return saved;
        } catch (DuplicateKeyException ex) {
            // Lost a race with a concurrent delivery of the same event. The other writer succeeded,
            // so the desired end state already holds and there is nothing to do.
            log.debug("Concurrent duplicate {} notification for user {} from event {}",
                    type, userId, sourceEventId);
            return null;
        }
    }

    public Page<NotificationResponse> findForUser(Long userId, boolean unreadOnly, Pageable pageable) {
        Page<Notification> page = unreadOnly
                ? repository.findByUserIdAndReadFalseOrderByCreatedAtDesc(userId, pageable)
                : repository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        return page.map(NotificationResponse::from);
    }

    public long unreadCount(Long userId) {
        return repository.countByUserIdAndReadFalse(userId);
    }

    /**
     * Marks one notification read.
     *
     * <p>Scoped by user id in the query itself, so another user's notification is a 404 rather than a
     * 403 — a 403 would confirm the id exists.</p>
     */
    public NotificationResponse markRead(String id, Long userId) {
        Notification notification = repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ResourceNotFoundException.of("Notification", id));
        notification.markRead();
        return NotificationResponse.from(repository.save(notification));
    }

    public int markAllRead(Long userId) {
        List<Notification> unread = repository.findByUserIdAndReadFalse(userId);
        unread.forEach(Notification::markRead);
        repository.saveAll(unread);
        log.info("Marked {} notification(s) read for user id={}", unread.size(), userId);
        return unread.size();
    }

    public void delete(String id, Long userId) {
        Notification notification = repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ResourceNotFoundException.of("Notification", id));
        repository.delete(notification);
    }

    /** Called by the {@code user.deleted} consumer. */
    public long deleteAllForUser(Long userId) {
        return repository.deleteByUserId(userId);
    }
}
