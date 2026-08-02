package com.earthscan.common.event;

/**
 * Published by auth-service when a user account is removed.
 *
 * <p>This is the event that makes the asynchronous design worthwhile: land-service must delete the
 * user's listings and saved searches, and forum-service must anonymise their posts. Doing that
 * synchronously would couple the delete endpoint to the availability of both services; doing it
 * over RabbitMQ means the delete succeeds immediately and the cleanup completes when each service
 * is next healthy.</p>
 */
public class UserDeletedEvent extends IntegrationEvent {

    private Long userId;
    private String name;
    private String email;

    public UserDeletedEvent() {
    }

    public UserDeletedEvent(Long userId, String name, String email) {
        this.userId = userId;
        this.name = name;
        this.email = email;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
