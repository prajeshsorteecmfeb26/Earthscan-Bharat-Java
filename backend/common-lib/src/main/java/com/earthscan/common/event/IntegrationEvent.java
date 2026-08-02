package com.earthscan.common.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Base class for every message published on the {@code earthscan.events} exchange.
 *
 * <p>{@code eventId} exists so consumers can be made idempotent: RabbitMQ guarantees at-least-once
 * delivery, so a redelivery after a consumer crash is normal, not exceptional.</p>
 */
public abstract class IntegrationEvent {

    private String eventId = UUID.randomUUID().toString();
    private Instant occurredAt = Instant.now();

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }
}
