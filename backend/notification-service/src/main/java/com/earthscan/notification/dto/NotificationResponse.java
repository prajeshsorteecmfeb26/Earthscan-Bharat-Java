package com.earthscan.notification.dto;

import com.earthscan.notification.domain.Notification;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(name = "NotificationResponse")
public record NotificationResponse(
        String id,
        String type,
        String title,
        String message,
        @Schema(description = "Frontend route to open when the notification is clicked",
                example = "/forum/66a3f1c2e8b4a51d3c7f9012")
        String actionPath,
        boolean read,
        Instant createdAt) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType().name(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getActionPath(),
                notification.isRead(),
                notification.getCreatedAt());
    }
}
