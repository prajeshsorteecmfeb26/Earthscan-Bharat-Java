package com.earthscan.forum.messaging;

import com.earthscan.common.messaging.RabbitConfig;
import com.earthscan.common.messaging.RabbitTopology;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Declares the queue forum-service consumes and binds it to {@code user.deleted}. */
@Configuration
public class ForumRabbitTopologyConfig {

    @Bean
    public Queue forumUserEventsQueue() {
        return RabbitConfig.durableQueueWithDlq(RabbitTopology.QUEUE_FORUM_USER_EVENTS);
    }

    @Bean
    public Binding forumUserDeletedBinding(Queue forumUserEventsQueue,
                                           TopicExchange earthscanEventsExchange) {
        return BindingBuilder.bind(forumUserEventsQueue)
                .to(earthscanEventsExchange)
                .with(RabbitTopology.ROUTING_USER_DELETED);
    }
}
