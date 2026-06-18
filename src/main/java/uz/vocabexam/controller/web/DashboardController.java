package uz.vocabexam.controller.web;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import uz.vocabexam.security.SecurityUtils;
import uz.vocabexam.service.*;
import java.util.UUID;

@Controller @RequiredArgsConstructor
public class DashboardController {
    private final AnalyticsService analyticsService;
    private final NotificationService notificationService;

    @GetMapping("/")
    public String index(Model model) {
        UUID userId = SecurityUtils.currentUserId();
        model.addAttribute("unreadCount", notificationService.unreadCount(userId));
        if (SecurityUtils.isAdmin()) {
            model.addAttribute("stats", analyticsService.getSystemSummary());
        } else if (SecurityUtils.isStudent()) {
            model.addAttribute("stats", analyticsService.getStudentStats(userId));
        }
        return "dashboard/index";
    }
}
