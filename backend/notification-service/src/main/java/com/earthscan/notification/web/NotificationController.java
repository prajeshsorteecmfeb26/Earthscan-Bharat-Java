package com.earthscan.notification.web;

import com.earthscan.common.security.SecurityUtils;
import com.earthscan.notification.dto.NotificationResponse;
import com.earthscan.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The caller's own notifications.
 *
 * <p>There is no endpoint to create a notification. They arrive only from the event bus, which means
 * no client can inject a notification into another user's inbox.</p>
 */
@RestController
@RequestMapping("/api/notifications")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Notifications", description = "Per-user notifications generated from domain events")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    @Operation(summary = "List the caller's notifications, newest first")
    public ResponseEntity<Page<NotificationResponse>> list(
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(notificationService.findForUser(
                SecurityUtils.currentUserId(), unreadOnly, pageable));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Count unread notifications",
            description = "Cheap enough for the navbar badge to poll.")
    public ResponseEntity<Map<String, Long>> unreadCount() {
        return ResponseEntity.ok(Map.of(
                "unreadCount", notificationService.unreadCount(SecurityUtils.currentUserId())));
    }

    @PatchMapping("/{id}/read")
    @Operation(summary = "Mark one notification read")
    public ResponseEntity<NotificationResponse> markRead(@PathVariable String id) {
        return ResponseEntity.ok(
                notificationService.markRead(id, SecurityUtils.currentUserId()));
    }

    @PatchMapping("/read-all")
    @Operation(summary = "Mark every notification read")
    public ResponseEntity<Map<String, Integer>> markAllRead() {
        int updated = notificationService.markAllRead(SecurityUtils.currentUserId());
        return ResponseEntity.ok(Map.of("markedRead", updated));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete one notification")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        notificationService.delete(id, SecurityUtils.currentUserId());
        return ResponseEntity.noContent().build();
    }
}
