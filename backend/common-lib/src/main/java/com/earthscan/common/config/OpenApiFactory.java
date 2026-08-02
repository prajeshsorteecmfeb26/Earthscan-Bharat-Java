package com.earthscan.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;

/**
 * Builds a consistent OpenAPI document for each service, including the bearer-token scheme so the
 * "Authorize" button in Swagger UI actually works against protected endpoints.
 */
public final class OpenApiFactory {

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    private OpenApiFactory() {
    }

    public static OpenAPI create(String serviceTitle, String description, String version) {
        return new OpenAPI()
                .info(new Info()
                        .title("EarthScan Bharat :: " + serviceTitle)
                        .description(description)
                        .version(version)
                        .contact(new Contact().name("EarthScan Bharat Team"))
                        .license(new License().name("MIT")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("API Gateway"),
                        new Server().url("/").description("This service, called directly")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components().addSecuritySchemes(SECURITY_SCHEME_NAME,
                        new SecurityScheme()
                                .name(SECURITY_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Paste the token returned by POST /api/auth/login")));
    }
}
