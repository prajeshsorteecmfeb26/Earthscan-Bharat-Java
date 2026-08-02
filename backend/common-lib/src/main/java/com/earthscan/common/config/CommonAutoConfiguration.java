package com.earthscan.common.config;

import com.earthscan.common.security.JwtProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Activates the shared {@code earthscan.jwt.*} property binding in every importing service. */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class CommonAutoConfiguration {
}
