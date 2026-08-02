package com.earthscan.notification.factory;

import com.earthscan.common.event.LandListedEvent;
import com.earthscan.notification.domain.Notification;
import com.earthscan.notification.domain.NotificationType;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Confirms to a listing owner that their parcel is live, and reports both derived scores. */
@Component
public class ListingPublishedNotificationFactory implements NotificationFactory<LandListedEvent> {

    @Override
    public Class<LandListedEvent> eventType() {
        return LandListedEvent.class;
    }

    @Override
    public Optional<Notification> create(LandListedEvent event) {
        if (event.getOwnerId() == null) {
            return Optional.empty();
        }

        String scoreText = event.getLandIntelligenceScore() == null
                ? "not available"
                : "%.1f/100".formatted(event.getLandIntelligenceScore());
        double borewell = event.getBorewellSuccessProbability() == null
                ? 0.0 : event.getBorewellSuccessProbability();

        return Optional.of(new Notification(
                event.getOwnerId(),
                NotificationType.LISTING_PUBLISHED,
                "Your listing is live",
                "\"%s\" has been published with a land intelligence score of %s and an estimated "
                        .formatted(event.getTitle(), scoreText)
                        + "borewell success probability of %.0f%%.".formatted(borewell),
                "/lands/" + event.getLandId(),
                event.getEventId()));
    }
}
