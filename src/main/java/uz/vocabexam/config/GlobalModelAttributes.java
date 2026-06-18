package uz.vocabexam.config;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import uz.vocabexam.security.CustomUserDetails;
import uz.vocabexam.service.NotificationService;

@ControllerAdvice
@RequiredArgsConstructor
public class GlobalModelAttributes {

    private final NotificationService notificationService;

    @ModelAttribute("unreadCount")
    public long unreadCount(Authentication auth) {
        if (auth != null && auth.isAuthenticated()
                && auth.getPrincipal() instanceof CustomUserDetails ud) {
            return notificationService.unreadCount(ud.getId());
        }
        return 0L;
    }
}
