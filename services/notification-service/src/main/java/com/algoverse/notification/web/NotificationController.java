package com.algoverse.notification.web;

import com.algoverse.notification.domain.InAppNotification;
import com.algoverse.notification.repository.InAppNotificationRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "In-app notification bell endpoints")
public class NotificationController {

    private final InAppNotificationRepository repository;

    @GetMapping("/{userId}")
    @Operation(summary = "Get paginated in-app notifications for user")
    public Page<InAppNotification> getNotifications(
            @PathVariable String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size));
    }

    @GetMapping("/{userId}/unread-count")
    @Operation(summary = "Get unread notification count")
    public ResponseEntity<Map<String, Long>> unreadCount(@PathVariable String userId) {
        return ResponseEntity.ok(Map.of("count", repository.countByUserIdAndIsReadFalse(userId)));
    }

    @PostMapping("/{userId}/mark-all-read")
    @Operation(summary = "Mark all notifications as read")
    public ResponseEntity<Map<String, Integer>> markAllRead(@PathVariable String userId) {
        int updated = repository.markAllReadForUser(userId);
        return ResponseEntity.ok(Map.of("updated", updated));
    }

    @DeleteMapping("/{notificationId}")
    @Operation(summary = "Delete a notification")
    public ResponseEntity<Void> delete(@PathVariable UUID notificationId) {
        repository.deleteById(notificationId);
        return ResponseEntity.noContent().build();
    }
}
