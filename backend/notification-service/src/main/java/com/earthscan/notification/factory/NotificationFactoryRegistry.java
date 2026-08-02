package com.earthscan.notification.factory;

import com.earthscan.common.event.IntegrationEvent;
import com.earthscan.notification.domain.Notification;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Dispatches an event to the factory that understands it.
 *
 * <p>Spring injects every {@link NotificationFactory} bean and this class indexes them by event type
 * at construction. The listeners then hold no knowledge of which events map to which content — they
 * hand the event over and persist whatever comes back. Adding an event type requires no change here
 * and no change to any listener.</p>
 */
@Slf4j
@Component
public class NotificationFactoryRegistry {

    private final Map<Class<? extends IntegrationEvent>, NotificationFactory<?>> factories;

    public NotificationFactoryRegistry(List<NotificationFactory<?>> discovered) {
        Map<Class<? extends IntegrationEvent>, NotificationFactory<?>> index = new HashMap<>();
        for (NotificationFactory<?> factory : discovered) {
            NotificationFactory<?> existing = index.put(factory.eventType(), factory);
            if (existing != null) {
                // Two factories for one event type means one of them silently never runs. Failing
                // at startup is far better than discovering months later that a notification type
                // stopped being produced.
                throw new IllegalStateException(
                        "Two NotificationFactory beans claim %s: %s and %s".formatted(
                                factory.eventType().getSimpleName(),
                                existing.getClass().getSimpleName(),
                                factory.getClass().getSimpleName()));
            }
        }
        this.factories = Map.copyOf(index);
        log.info("Registered {} notification factory/factories: {}",
                factories.size(),
                factories.keySet().stream().map(Class::getSimpleName).sorted().toList());
    }

    /**
     * @return the notification for this event, or empty when no factory handles the type or the
     *     factory decided none was warranted
     */
    @SuppressWarnings("unchecked")
    public <E extends IntegrationEvent> Optional<Notification> create(E event) {
        NotificationFactory<E> factory = (NotificationFactory<E>) factories.get(event.getClass());
        if (factory == null) {
            log.debug("No notification factory for {}; ignoring", event.getClass().getSimpleName());
            return Optional.empty();
        }
        return factory.create(event);
    }
}
