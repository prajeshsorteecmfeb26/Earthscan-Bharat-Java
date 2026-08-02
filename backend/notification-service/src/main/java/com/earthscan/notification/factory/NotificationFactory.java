package com.earthscan.notification.factory;

import com.earthscan.common.event.IntegrationEvent;
import com.earthscan.notification.domain.Notification;
import java.util.Optional;

/**
 * Builds a {@link Notification} from one kind of domain event. <strong>Factory pattern.</strong>
 *
 * <p>Introduced because the message-composition logic was accumulating inside the RabbitMQ listeners,
 * mixed in with acknowledgement and idempotency concerns. Three problems followed from that:</p>
 *
 * <ul>
 *   <li>The listeners had two responsibilities — transport and content — so testing the wording of a
 *       welcome message meant standing up a listener.</li>
 *   <li>Each new event type meant editing an existing listener, which is an Open/Closed violation.
 *       A new factory is now purely additive.</li>
 *   <li>The message text was interleaved with a {@code switch} on role, making it hard to see all
 *       user-facing copy in one place — which matters for a platform that needs Hindi and Marathi
 *       translations of it.</li>
 * </ul>
 *
 * @param <E> the event type this factory understands
 */
public interface NotificationFactory<E extends IntegrationEvent> {

    /** The event type this factory handles. Used by the registry to dispatch. */
    Class<E> eventType();

    /**
     * Builds the notification.
     *
     * @return the notification to persist, or {@link Optional#empty()} when this event should produce
     *     none — for example a reply by the thread author to their own thread. Returning empty is a
     *     legitimate outcome, not a failure, which is why it is modelled rather than signalled with
     *     an exception.
     */
    Optional<Notification> create(E event);
}
