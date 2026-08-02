package com.earthscan.land.config;

import com.earthscan.common.config.OpenApiFactory;
import io.swagger.v3.oas.models.OpenAPI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI landServiceOpenApi() {
        return OpenApiFactory.create(
                "Land Service",
                """
                Land listings, the land-intelligence scoring engine, saved searches and investment
                analysis. Owns the MySQL `earthscan_land` schema (`lands`, `soil_types`,
                `saved_searches`).

                Publishes `land.listed`; consumes `user.deleted` to purge a removed user's listings
                and shortlist.
                """,
                "1.0.0");
    }
}
