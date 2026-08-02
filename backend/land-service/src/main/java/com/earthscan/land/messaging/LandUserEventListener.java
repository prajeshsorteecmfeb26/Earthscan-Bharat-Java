package com.earthscan.land.messaging;

import com.earthscan.common.event.UserDeletedEvent;
import com.earthscan.common.messaging.RabbitTopology;
import com.earthscan.land.repository.LandRepository;
import com.earthscan.land.repository.SavedSearchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps land data consistent with account deletions in auth-service.
 *
 * <p>Idempotent by construction: deleting rows for a user id that has already been purged simply
 * affects zero rows. That matters because RabbitMQ delivers at least once, so this handler will
 * occasionally see the same event twice — after a consumer restart, for instance.</p>
 */
@Component
public class LandUserEventListener {

    private static final Logger log = LoggerFactory.getLogger(LandUserEventListener.class);

    private final LandRepository landRepository;
    private final SavedSearchRepository savedSearchRepository;

    public LandUserEventListener(LandRepository landRepository,
                                 SavedSearchRepository savedSearchRepository) {
        this.landRepository = landRepository;
        this.savedSearchRepository = savedSearchRepository;
    }

    @RabbitListener(queues = RabbitTopology.QUEUE_LAND_USER_EVENTS)
    @Transactional
    public void onUserDeleted(UserDeletedEvent event) {
        if (event.getUserId() == null) {
            // Malformed message: log and return so it is acknowledged rather than looping forever.
            log.error("Discarding UserDeletedEvent {} with no userId", event.getEventId());
            return;
        }

        long listingsRemoved = landRepository.deleteByOwnerId(event.getUserId());
        long searchesRemoved = savedSearchRepository.deleteByUserId(event.getUserId());

        log.info("Handled user.deleted for user id={} [event {}]: removed {} listing(s), {} saved search(es)",
                event.getUserId(), event.getEventId(), listingsRemoved, searchesRemoved);
    }
}
