package com.earthscan.gateway.config;

import com.earthscan.gateway.security.GatewayJwtProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Binds {@code earthscan.jwt.*} for the gateway. */
@Configuration
@EnableConfigurationProperties(GatewayJwtProperties.class)
public class GatewayPropertiesConfig {
}
