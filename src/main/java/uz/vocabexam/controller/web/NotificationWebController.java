package uz.vocabexam.controller.web;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import uz.vocabexam.security.SecurityUtils;
import uz.vocabexam.service.NotificationService;
import java.util.UUID;

/** Har qanday rol uchun bildirishnomalar sahifasi (yuqoridagi qo'ng'iroqcha). */
@Controller @RequestMapping("/notifications")
@RequiredArgsConstructor @PreAuthorize("isAuthenticated()")
public class NotificationWebController {
    private final NotificationService notificationService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("notifications", notificationService.getForUser(SecurityUtils.currentUserId()));
        return "notifications/list";
    }

    /** Bildirishnoma bosilganda: o'qilgan deb belgilab, tegishli sahifaga o'tkazadi. */
    @GetMapping("/{id}/open")
    public String open(@PathVariable UUID id) {
        return "redirect:" + notificationService.open(id, SecurityUtils.currentUserId());
    }

    @PostMapping("/read-all")
    public String readAll() {
        notificationService.markAllRead(SecurityUtils.currentUserId());
        return "redirect:/notifications";
    }
}
