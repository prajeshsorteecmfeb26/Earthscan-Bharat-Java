package com.earthscan.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

/**
 * Aggregate counts for the admin analytics page.
 *
 * <p>Added because {@code AnalyticsReports.jsx} currently pulls the entire user list just to count
 * roles in the browser. That works at demo scale and falls over at real scale; counting in SQL is
 * one query regardless of table size.</p>
 */
@Schema(name = "UserStatsResponse")
public record UserStatsResponse(
        long totalUsers,
        @Schema(description = "User count keyed by role display name") Map<String, Long> usersByRole) {
}
