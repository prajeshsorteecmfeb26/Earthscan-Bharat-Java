package com.earthscan.common.messaging;

import com.earthscan.common.event.IntegrationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Thin wrapper over {@link RabbitTemplate} for publishing domain events.
 *
 * <p>Publishing never propagates an exception to the caller. The business transaction has already
 * committed by the time we publish, so failing the HTTP request because the broker hiccuped would
 * be strictly worse than logging it: the user would see an error for an operation that actually
 * succeeded, and would probably retry it. The trade-off is that a broker outage can lose a
 * notification, which is acceptable for these event types — none of them carry money or state the
 * publisher is responsible for.</p>
 */
@Component
public class EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(EventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public EventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(String routingKey, IntegrationEvent event) {
        try {
            rabbitTemplate.convertAndSend(RabbitTopology.EVENTS_EXCHANGE, routingKey, event);
            log.info("Published event {} [{}] with routing key '{}'",
                    event.getClass().getSimpleName(), event.getEventId(), routingKey);
        } catch (AmqpException ex) {
            log.error("Failed to publish event {} [{}] with routing key '{}'. "
                            + "The originating transaction has already been committed.",
                    event.getClass().getSimpleName(), event.getEventId(), routingKey, ex);
        }
    }
}
