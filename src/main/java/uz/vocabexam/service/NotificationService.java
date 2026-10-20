package uz.vocabexam.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
    /**
     * Bildirishnomani o'qilgan deb belgilaydi va uning havolasini qaytaradi.
     * Faqat sayt ichidagi havolaga o'tkazamiz; boshqasi bo'lsa ro'yxatda qolamiz.
     */
    @Transactional
    public String open(UUID notifId, UUID userId) {
        markRead(notifId, userId);
        return notificationRepository.findById(notifId)
            .filter(n -> n.getUser().getId().equals(userId))
            .map(Notification::getLink)
            .filter(l -> l != null && l.startsWith("/") && !l.startsWith("//"))
            .orElse("/notifications");
    }
    @Transactional
    public void markAllRead(UUID userId) {
        notificationRepository.markAllReadByUserId(userId);
    }
}
