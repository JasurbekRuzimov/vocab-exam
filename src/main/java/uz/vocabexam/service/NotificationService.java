package uz.vocabexam.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uz.vocabexam.entity.*;
import uz.vocabexam.repository.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class NotificationService {
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public void send(UUID userId, NotificationType type, String message, String link) {
        userRepository.findById(userId).ifPresent(user -> {
            Notification n = Notification.builder()
                .user(user).type(type).message(message).link(link).build();
            notificationRepository.save(n);
        });
    }

    public List<Notification> getForUser(UUID userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }
    public long unreadCount(UUID userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }
    public void markRead(UUID notifId, UUID userId) {
        notificationRepository.findById(notifId).ifPresent(n -> {
            if (n.getUser().getId().equals(userId)) { n.setRead(true); notificationRepository.save(n); }
        });
    }
    public void markAllRead(UUID userId) {
        notificationRepository.markAllReadByUserId(userId);
    }
}
