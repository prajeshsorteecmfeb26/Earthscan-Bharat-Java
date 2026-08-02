package com.earthscan.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Identity provider for the platform. The only service that can mint a JWT.
 *
 * <p>{@code scanBasePackages} reaches up to {@code com.earthscan} so the cross-cutting beans in
 * common-lib — global exception handler, correlation-id filter, RabbitMQ topology — are picked up
 * without each service redeclaring them.</p>
 */
@SpringBootApplication(scanBasePackages = "com.earthscan")
@EnableDiscoveryClient
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
