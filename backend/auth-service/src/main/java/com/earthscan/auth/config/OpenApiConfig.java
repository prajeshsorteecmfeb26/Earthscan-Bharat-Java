package com.earthscan.auth.config;

import com.earthscan.common.config.OpenApiFactory;
import io.swagger.v3.oas.models.OpenAPI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI authServiceOpenApi() {
        return OpenApiFactory.create(
                "Auth Service",
                """
                Identity provider for the EarthScan Bharat platform. Owns the MySQL `earthscan_auth`
                schema (`users`, `roles`, `user_roles`) and is the only service permitted to mint a JWT.

                Publishes `user.registered`, `user.role-changed` and `user.deleted` to the
                `earthscan.events` topic exchange.
                """,
                "1.0.0");
    }
}
