package uz.vocabexam.controller.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
public class AuthWebController {
    @GetMapping("/login")
    public String login(@RequestParam(required=false) String error,
                        @RequestParam(required=false) String logout,
                        @RequestParam(required=false) String expired,
                        org.springframework.ui.Model model) {
        if (error   != null) model.addAttribute("error",   "Login yoki parol noto'g'ri");
        if (logout  != null) model.addAttribute("success", "Tizimdan chiqdingiz");
        if (expired != null) model.addAttribute("error",   "Sessiya muddati tugadi, qayta kiring");
        return "auth/login";
    }

    @GetMapping("/access-denied")
    public String accessDenied() { return "error/403"; }

    @GetMapping("/dashboard")
    public String dashboard() { return "redirect:/"; }
}
