package com.earthscan.notification.messaging;

import com.earthscan.common.event.LandListedEvent;
import com.earthscan.common.messaging.RabbitTopology;
import com.earthscan.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/** Consumes land events. Transport only; content comes from the notification factories. */
@Slf4j
@Component
@RequiredArgsConstructor
@RabbitListener(queues = RabbitTopology.QUEUE_NOTIFICATION_LAND)
public class LandEventListener {

    /** Listings at or above this score are noteworthy enough to be worth announcing to buyers. */
    private static final double HIGH_SCORE_THRESHOLD = 75.0;

    private final NotificationService notificationService;

    @RabbitHandler
    public void onLandListed(LandListedEvent event) {
        log.debug("Handling land.listed for land id={}", event.getLandId());
        notificationService.createFrom(event);

        if (event.getLandIntelligenceScore() != null
                && event.getLandIntelligenceScore() >= HIGH_SCORE_THRESHOLD) {
            // Matching against every saved search belongs in a scheduled batch, not here: fanning
            // out synchronously would make one listing creation write thousands of documents and
            // block this queue behind it.
            log.info("High-scoring listing id={} ({}). Saved-search fan-out is a scheduled job, "
                            + "not part of this handler.",
                    event.getLandId(), event.getLandIntelligenceScore());
        }
    }

    @RabbitHandler(isDefault = true)
    public void onUnknownLandEvent(Object payload) {
        log.warn("Ignoring unhandled land event of type {}", payload.getClass().getName());
    }
}
