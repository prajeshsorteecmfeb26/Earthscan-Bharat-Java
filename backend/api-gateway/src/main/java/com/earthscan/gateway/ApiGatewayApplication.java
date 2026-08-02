package com.earthscan.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * The only port the React client ever talks to.
 *
 * <p>Responsibilities are deliberately narrow: routing, CORS, correlation ids, and rejecting
 * requests that carry no valid token at all. Role-level authorisation stays in the owning service,
 * because that is where the resource semantics live — the gateway has no idea whether user 7 owns
 * land 42.</p>
 */
@SpringBootApplication
@EnableDiscoveryClient
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
