package com.eventra.backend.controller;

import com.eventra.backend.entity.Notification;
import com.eventra.backend.service.NotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/notifications")
@CrossOrigin(
    origins = {
        "http://localhost:5173",
        "http://localhost:5180",
        "http://localhost:5181",
        "http://localhost:5182",
        "http://localhost:5183",
        "http://localhost:5184",
        "http://localhost:5185",
        "http://localhost:5186",
        "http://localhost:5187",
        "http://localhost:5188",
        "http://localhost:5189",
        "http://localhost:5190",
        "https://eventra-frontend-theta.vercel.app"
    },
    originPatterns = {"http://localhost:*", "http://127.0.0.1:*"}
)
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    // 1. POST /api/notifications - Create a new notification
    @PostMapping
    public ResponseEntity<?> createNotification(@RequestBody Notification notification) {
        if (notification == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Notification data is required"));
        }
        if (notification.getUserId() == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "User ID is required"));
        }
        if (notification.getTitle() == null || notification.getTitle().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Title is required"));
        }
        if (notification.getMessage() == null || notification.getMessage().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Message is required"));
        }
        if (notification.getType() == null || notification.getType().trim().isEmpty()) {
            notification.setType("INFO");
        }

        Notification created = notificationService.createNotification(notification);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // 2. GET /api/notifications/user/{userId} - Return all notifications for user
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Notification>> getNotificationsByUserId(@PathVariable Long userId) {
        if (userId == null) {
            return ResponseEntity.badRequest().build();
        }
        List<Notification> list = notificationService.getNotificationsForUser(userId);
        return ResponseEntity.ok(list);
    }

    // 3. GET /api/notifications/user/{userId}/unread-count - Return unread count
    @GetMapping("/user/{userId}/unread-count")
    public ResponseEntity<?> getUnreadCount(@PathVariable Long userId) {
        if (userId == null) {
            return ResponseEntity.badRequest().build();
        }
        long count = notificationService.getUnreadCount(userId);
        return ResponseEntity.ok(Map.of("userId", userId, "unreadCount", count));
    }

    // 4. PUT /api/notifications/{id}/read - Mark single notification as read
    @PutMapping("/{id}/read")
    public ResponseEntity<?> markNotificationAsRead(@PathVariable Long id) {
        if (id == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Notification ID is required"));
        }
        Optional<Notification> updated = notificationService.markAsRead(id);
        if (updated.isPresent()) {
            return ResponseEntity.ok(updated.get());
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", "Notification not found with ID: " + id));
    }

    // 5. PUT /api/notifications/user/{userId}/read-all - Mark all user notifications as read
    @PutMapping("/user/{userId}/read-all")
    public ResponseEntity<?> markAllAsRead(@PathVariable Long userId) {
        if (userId == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "User ID is required"));
        }
        int count = notificationService.markAllAsRead(userId);
        return ResponseEntity.ok(Map.of("message", "All notifications marked as read", "updatedCount", count));
    }

    // 6. DELETE /api/notifications/{id} - Delete single notification
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteNotification(@PathVariable Long id) {
        if (id == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Notification ID is required"));
        }
        boolean deleted = notificationService.deleteNotification(id);
        if (deleted) {
            return ResponseEntity.ok(Map.of("message", "Notification deleted successfully", "id", id));
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", "Notification not found with ID: " + id));
    }

    // 7. POST /api/notifications/reminder/{eventId} - Broadcast event reminder to registered participants
    @PostMapping("/reminder/{eventId}")
    public ResponseEntity<?> sendEventReminder(
            @PathVariable Long eventId,
            @RequestBody(required = false) Map<String, String> body) {
        if (eventId == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Event ID is required"));
        }

        String title = body != null && body.containsKey("title") ? body.get("title") : "Event Reminder";
        String message = body != null && body.containsKey("message")
                ? body.get("message")
                : "Your registered event is coming up soon!";

        int sent = notificationService.notifyEventAttendees(eventId, title, message, "REMINDER");
        return ResponseEntity.ok(Map.of("message", "Reminders sent to registered attendees", "sentCount", sent));
    }
}
