package com.earthscan.gateway.config;

import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Terminates circuit-breaker fallbacks with a 503 the frontend can display sensibly, instead of the
 * connection simply hanging when a downstream service is unavailable.
 */
@RestController
public class FallbackController {

    @RequestMapping("/fallback/{service}")
    public ResponseEntity<Map<String, Object>> fallback(
            @org.springframework.web.bind.annotation.PathVariable String service) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "timestamp", Instant.now().toString(),
                "status", 503,
                "error", "Service Unavailable",
                "message", "The " + service + " service is temporarily unreachable. Please retry shortly."));
    }
}
