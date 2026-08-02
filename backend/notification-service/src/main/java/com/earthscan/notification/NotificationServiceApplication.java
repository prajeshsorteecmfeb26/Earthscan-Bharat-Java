package com.earthscan.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Turns domain events into per-user notifications.
 *
 * <p>This is the service that justifies the message bus. It is a pure consumer: no other service
 * calls it, and it calls no other service. Registration, listing creation and forum replies all
 * complete without waiting on it, and if it is down for an hour the events simply queue up and are
 * processed when it returns — the user-facing operations that produced them never noticed.</p>
 *
 * <p>Getting the same behaviour synchronously would mean auth-service, land-service and
 * forum-service each holding an HTTP client for this service, each handling its downtime, and each
 * paying its latency on the critical path of a user request.</p>
 */
@SpringBootApplication(scanBasePackages = "com.earthscan")
@EnableDiscoveryClient
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
