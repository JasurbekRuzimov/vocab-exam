package uz.vocabexam.controller.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uz.vocabexam.security.SecurityUtils;
import uz.vocabexam.service.NotificationService;
import java.util.*;

@RestController @RequestMapping("/api/notifications") @RequiredArgsConstructor
public class NotificationApiController {
    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<?> getAll() {
        UUID userId = SecurityUtils.currentUserId();
        return ResponseEntity.ok(Map.of(
            "unreadCount", notificationService.unreadCount(userId),
            "items", notificationService.getForUser(userId)));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<?> markRead(@PathVariable UUID id) {
        notificationService.markRead(id, SecurityUtils.currentUserId());
        return ResponseEntity.ok(Map.of("message", "O'qildi"));
    }

    @PatchMapping("/read-all")
    public ResponseEntity<?> markAllRead() {
        notificationService.markAllRead(SecurityUtils.currentUserId());
        return ResponseEntity.ok(Map.of("message", "Barcha bildirishnomalar o'qildi"));
    }
}
