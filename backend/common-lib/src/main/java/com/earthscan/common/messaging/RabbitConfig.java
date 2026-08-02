package com.earthscan.common.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the shared RabbitMQ topology and JSON serialisation.
 *
 * <p>Every service imports this, and AMQP declarations are idempotent, so whichever service starts
 * first creates the exchange and queues and the rest simply attach. That removes the usual startup
 * ordering problem where a consumer boots before its queue exists.</p>
 */
@Configuration
public class RabbitConfig {

    /**
     * JSON over the wire rather than Java serialisation: it keeps messages readable in the RabbitMQ
     * management console and stops the broker contract from being tied to Java class layout.
     */
    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                        MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        // Surfaces "published to an exchange that routed nowhere" instead of silently dropping.
        template.setMandatory(true);
        template.setReturnsCallback(returned ->
                org.slf4j.LoggerFactory.getLogger(RabbitConfig.class).error(
                        "Message returned unrouted: exchange={} routingKey={} reply={}",
                        returned.getExchange(), returned.getRoutingKey(), returned.getReplyText()));
        return template;
    }

    @Bean
    public TopicExchange earthscanEventsExchange() {
        return new TopicExchange(RabbitTopology.EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange earthscanDeadLetterExchange() {
        return new DirectExchange(RabbitTopology.DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    public Queue earthscanDeadLetterQueue() {
        return QueueBuilder.durable(RabbitTopology.DEAD_LETTER_QUEUE).build();
    }

    @Bean
    public Binding earthscanDeadLetterBinding(Queue earthscanDeadLetterQueue,
                                             DirectExchange earthscanDeadLetterExchange) {
        return BindingBuilder.bind(earthscanDeadLetterQueue)
                .to(earthscanDeadLetterExchange)
                .with(RabbitTopology.DEAD_LETTER_ROUTING_KEY);
    }

    /** Helper used by each service's own queue declarations so DLQ wiring is never forgotten. */
    public static Queue durableQueueWithDlq(String name) {
        return QueueBuilder.durable(name)
                .deadLetterExchange(RabbitTopology.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(RabbitTopology.DEAD_LETTER_ROUTING_KEY)
                .build();
    }
}
