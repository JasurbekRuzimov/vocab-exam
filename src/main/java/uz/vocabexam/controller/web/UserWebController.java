package uz.vocabexam.controller.web;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import uz.vocabexam.entity.Role;
import uz.vocabexam.service.UserService;
import java.util.UUID;

@Controller
@RequestMapping("/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class UserWebController {

    private final UserService userService;

    @GetMapping
    public String list(Model model,
                       @RequestParam(required = false) Role role,
                       @RequestParam(required = false) String search) {
        // Template list uchun List<User> kerak
        model.addAttribute("users",
            userService.getAll(role, search, PageRequest.of(0, 500)).getContent());
        model.addAttribute("roles", Role.values());
        model.addAttribute("filter_role", role);
        model.addAttribute("filter_search", search);
        return "users/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("roles", Role.values());
        return "users/form";
    }

    @PostMapping
    public String create(@RequestParam String fullName,
                         @RequestParam String username,
                         @RequestParam(required = false) String email,
                         @RequestParam String password,
                         @RequestParam Role role) {
        userService.create(fullName, username, email, password, role);
        return "redirect:/admin/users?success=true";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable UUID id, Model model) {
        model.addAttribute("user", userService.getById(id));
        model.addAttribute("roles", Role.values());
        return "users/form";
    }

    @PostMapping("/{id}/update")
    public String update(@PathVariable UUID id,
                         @RequestParam String fullName,
                         @RequestParam String username,
                         @RequestParam(required = false) String email,
                         @RequestParam Role role) {
        userService.update(id, fullName, username, email, role);
        return "redirect:/admin/users?success=true";
    }
}
