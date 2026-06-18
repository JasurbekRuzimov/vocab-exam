package uz.vocabexam.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uz.vocabexam.entity.*;
import uz.vocabexam.repository.*;
import uz.vocabexam.security.SecurityUtils;
import java.util.UUID;

@Service @RequiredArgsConstructor
public class AuditService {
    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    public void log(String action, String target, String details, HttpServletRequest request) {
        UUID userId = SecurityUtils.currentUserId();
        AuditLog log = AuditLog.builder()
            .user(userId != null ? userRepository.findById(userId).orElse(null) : null)
            .action(action).target(target).details(details)
            .ip(request != null ? request.getRemoteAddr() : null)
            .build();
        auditLogRepository.save(log);
    }
    public void log(String action, String target, String details) {
        log(action, target, details, null);
    }
}
