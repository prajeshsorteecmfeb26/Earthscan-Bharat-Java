package com.earthscan.land;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/** Land listings, scoring and saved searches. Owns the MySQL {@code earthscan_land} schema. */
@SpringBootApplication(scanBasePackages = "com.earthscan")
@EnableDiscoveryClient
public class LandServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(LandServiceApplication.class, args);
    }
}
