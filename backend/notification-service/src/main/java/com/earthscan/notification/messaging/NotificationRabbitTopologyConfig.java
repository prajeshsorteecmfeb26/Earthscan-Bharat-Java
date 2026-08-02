package com.earthscan.notification.messaging;

import com.earthscan.common.messaging.RabbitConfig;
import com.earthscan.common.messaging.RabbitTopology;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares notification-service's three queues.
 *
 * <p>Three queues rather than one, bound to {@code user.#}, {@code land.#} and {@code forum.#}: a
 * single queue would mean a slow forum handler holding up welcome notifications, and it would make
 * the DLQ a mixed bag that is hard to reason about. Splitting by domain also allows each to be
 * scaled independently later.</p>
 */
@Configuration
public class NotificationRabbitTopologyConfig {

    @Bean
    public Queue notificationUserQueue() {
        return RabbitConfig.durableQueueWithDlq(RabbitTopology.QUEUE_NOTIFICATION_USER);
    }

    @Bean
    public Queue notificationLandQueue() {
        return RabbitConfig.durableQueueWithDlq(RabbitTopology.QUEUE_NOTIFICATION_LAND);
    }

    @Bean
    public Queue notificationForumQueue() {
        return RabbitConfig.durableQueueWithDlq(RabbitTopology.QUEUE_NOTIFICATION_FORUM);
    }

    @Bean
    public Binding notificationUserBinding(Queue notificationUserQueue,
                                           TopicExchange earthscanEventsExchange) {
        return BindingBuilder.bind(notificationUserQueue)
                .to(earthscanEventsExchange)
                .with(RabbitTopology.PATTERN_ALL_USER_EVENTS);
    }

    @Bean
    public Binding notificationLandBinding(Queue notificationLandQueue,
                                           TopicExchange earthscanEventsExchange) {
        return BindingBuilder.bind(notificationLandQueue)
                .to(earthscanEventsExchange)
                .with(RabbitTopology.PATTERN_ALL_LAND_EVENTS);
    }

    @Bean
    public Binding notificationForumBinding(Queue notificationForumQueue,
                                            TopicExchange earthscanEventsExchange) {
        return BindingBuilder.bind(notificationForumQueue)
                .to(earthscanEventsExchange)
                .with(RabbitTopology.PATTERN_ALL_FORUM_EVENTS);
    }
}
