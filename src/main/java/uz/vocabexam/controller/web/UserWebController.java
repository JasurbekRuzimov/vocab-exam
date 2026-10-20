package uz.vocabexam.controller.web;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import uz.vocabexam.entity.Role;
import uz.vocabexam.security.SecurityUtils;
import uz.vocabexam.service.*;
import java.util.*;

@Controller
@RequestMapping("/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','CENTER_ADMIN')")
public class UserWebController {

    private final UserService userService;
    private final CenterService centerService;

    /** Direktor platforma adminini yarata olmaydi; platforma admini markazni tanlaydi. */
    private void formOptions(Model model) {
        boolean admin = SecurityUtils.isAdmin();
        model.addAttribute("roles", admin ? List.of(Role.values())
            : List.of(Role.CENTER_ADMIN, Role.TEACHER, Role.STUDENT));
        if (admin) model.addAttribute("centers", centerService.getAll());
    }

    @GetMapping
    public String list(Model model,
                       @RequestParam(required = false) Role role,
                       @RequestParam(required = false) String search,
                       @RequestParam(required = false) UUID centerId) {
        // Template list uchun List<User> kerak
        model.addAttribute("users",
            userService.getAll(role, search, centerId, PageRequest.of(0, 500)).getContent());
        formOptions(model);
        model.addAttribute("filter_center", centerId);
        model.addAttribute("filter_role", role);
        model.addAttribute("filter_search", search);
        return "users/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        formOptions(model);
        return "users/form";
    }

    @PostMapping
    public String create(@RequestParam String fullName,
                         @RequestParam String username,
                         @RequestParam(required = false) String email,
                         @RequestParam String password,
                         @RequestParam Role role,
                         @RequestParam(required = false) UUID centerId) {
        userService.create(fullName, username, email, password, role, centerId);
        return "redirect:/admin/users?success=true";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable UUID id, Model model) {
        model.addAttribute("user", userService.getManaged(id));
        formOptions(model);
        return "users/form";
    }

    @PostMapping("/{id}/update")
    public String update(@PathVariable UUID id,
                         @RequestParam String fullName,
                         @RequestParam String username,
                         @RequestParam(required = false) String email,
                         @RequestParam Role role,
                         @RequestParam(required = false) UUID centerId) {
        userService.update(id, fullName, username, email, role, centerId);
        return "redirect:/admin/users?success=true";
    }
}
