package uz.vocabexam.controller.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
public class AuthWebController {
    @GetMapping("/login")
    public String login(@RequestParam(required=false) String error,
                        @RequestParam(required=false) String logout,
                        @RequestParam(required=false) String expired,
                        @RequestParam(required=false) String disabled,
                        @RequestParam(required=false) String locked,
                        org.springframework.ui.Model model) {
        if (error   != null) model.addAttribute("error",   "Login yoki parol noto'g'ri");
        if (logout  != null) model.addAttribute("success", "Tizimdan chiqdingiz");
        if (disabled != null) model.addAttribute("error",  "Akkaunt yoki o'quv markazi faol emas. Markaz rahbariga murojaat qiling");
        if (locked  != null) model.addAttribute("error",   "Ko'p marta noto'g'ri urinish. Birozdan keyin qayta urinib ko'ring");
        if (expired != null) model.addAttribute("error",   "Sessiya muddati tugadi, qayta kiring");
        return "auth/login";
    }

    @GetMapping("/access-denied")
    public String accessDenied() { return "error/403"; }

    @GetMapping("/dashboard")
    public String dashboard() { return "redirect:/"; }
}
