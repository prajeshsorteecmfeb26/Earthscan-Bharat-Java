package com.earthscan.notification.domain;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A single notification for a single user.
 *
 * <p>MongoDB suits this collection for a different reason than the forum: notifications are
 * append-heavy, read in one narrow pattern (newest first for one user), never joined, and disposable
 * after a while. There is no relational structure to preserve, and the {@code sourceEventId} field
 * gives idempotency without needing a schema migration each time a new event type is added.</p>
 */
@Document(collection = "notifications")
@CompoundIndex(name = "idx_user_created", def = "{'userId': 1, 'createdAt': -1}")
@CompoundIndex(name = "idx_user_read", def = "{'userId': 1, 'read': 1}")
// Unique on the event id + user pair: this is the constraint that makes redelivery harmless.
@CompoundIndex(name = "idx_event_user_unique", def = "{'sourceEventId': 1, 'userId': 1}", unique = true)
public class Notification {

    @Id
    private String id;

    @Indexed
    private Long userId;

    private NotificationType type;

    private String title;

    private String message;

    /** Frontend route this notification should navigate to, e.g. {@code /forum/66a3f1c2...}. */
    private String actionPath;

    private boolean read;

    private Instant createdAt = Instant.now();

    private Instant readAt;

    /**
     * The {@code eventId} of the message that produced this notification.
     *
     * <p>RabbitMQ delivers at least once, so this handler will see duplicates. Combined with the
     * unique index above, this field turns a redelivery into a caught duplicate-key error instead of
     * a second copy in the user's inbox.</p>
     */
    private String sourceEventId;

    public Notification() {
    }

    public Notification(Long userId, NotificationType type, String title, String message,
                        String actionPath, String sourceEventId) {
        this.userId = userId;
        this.type = type;
        this.title = title;
        this.message = message;
        this.actionPath = actionPath;
        this.sourceEventId = sourceEventId;
    }

    public void markRead() {
        if (!this.read) {
            this.read = true;
            this.readAt = Instant.now();
        }
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public NotificationType getType() {
        return type;
    }

    public void setType(NotificationType type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getActionPath() {
        return actionPath;
    }

    public void setActionPath(String actionPath) {
        this.actionPath = actionPath;
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public void setReadAt(Instant readAt) {
        this.readAt = readAt;
    }

    public String getSourceEventId() {
        return sourceEventId;
    }

    public void setSourceEventId(String sourceEventId) {
        this.sourceEventId = sourceEventId;
    }
}
