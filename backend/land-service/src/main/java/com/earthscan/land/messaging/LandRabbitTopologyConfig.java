package com.earthscan.land.messaging;

import com.earthscan.common.messaging.RabbitConfig;
import com.earthscan.common.messaging.RabbitTopology;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Declares the queue land-service consumes and binds it to {@code user.deleted}. */
@Configuration
public class LandRabbitTopologyConfig {

    @Bean
    public Queue landUserEventsQueue() {
        return RabbitConfig.durableQueueWithDlq(RabbitTopology.QUEUE_LAND_USER_EVENTS);
    }

    @Bean
    public Binding landUserDeletedBinding(Queue landUserEventsQueue,
                                          TopicExchange earthscanEventsExchange) {
        // Binds the specific key, not user.#: land-service has no interest in registrations or
        // role changes, and a broad binding would deliver messages this consumer cannot handle.
        return BindingBuilder.bind(landUserEventsQueue)
                .to(earthscanEventsExchange)
                .with(RabbitTopology.ROUTING_USER_DELETED);
    }
}
