package uz.vocabexam.controller.web;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import uz.vocabexam.entity.*;
import uz.vocabexam.service.*;
import java.util.UUID;

@Controller
@RequestMapping("/admin/groups")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','CENTER_ADMIN')")
public class GroupWebController {

    private final GroupService groupService;
    private final UserService userService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("groups", groupService.getManagedList());
        return "groups/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("teachers",
            userService.getAll(Role.TEACHER, null, PageRequest.of(0, 500)).getContent());
        return "groups/form";
    }

    @PostMapping
    public String create(@RequestParam String name, @RequestParam UUID teacherId) {
        groupService.create(name, teacherId);
        return "redirect:/admin/groups?success=true";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable UUID id, Model model) {
        Group group = groupService.getManaged(id);
        model.addAttribute("group", group);
        // Faqat guruh markazidagi o'quvchilarni qo'shish mumkin
        model.addAttribute("students",
            userService.getAll(Role.STUDENT, null, group.getTeacher().getCenterId(), PageRequest.of(0, 500)).getContent());
        return "groups/detail";
    }
}
