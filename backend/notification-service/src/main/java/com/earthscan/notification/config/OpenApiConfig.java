package com.earthscan.notification.config;

import com.earthscan.common.config.OpenApiFactory;
import io.swagger.v3.oas.models.OpenAPI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI notificationServiceOpenApi() {
        return OpenApiFactory.create(
                "Notification Service",
                """
                Consumes every domain event on the `earthscan.events` exchange and turns it into
                per-user notifications. Owns the MongoDB `earthscan_notifications` database.

                Pure consumer: no other service calls it, and it calls no other service. There is
                deliberately no endpoint for creating a notification — they originate only from
                events.
                """,
                "1.0.0");
    }
}
