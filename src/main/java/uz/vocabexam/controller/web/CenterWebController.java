package uz.vocabexam.controller.web;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import uz.vocabexam.exception.BadRequestException;
import uz.vocabexam.service.CenterService;
import java.util.UUID;

/** Platforma admini: o'quv markazlarini yaratish, brendini sozlash, yoqish/o'chirish. */
@Controller
@RequestMapping("/admin/centers")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class CenterWebController {
    private final CenterService centerService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("rows", centerService.listWithCounts());
        return "centers/list";
    }

    @GetMapping("/new")
    public String newForm() {
        return "centers/form";
    }

    @PostMapping
    public String create(@RequestParam String name, @RequestParam String slug,
                         @RequestParam(required = false) String logoUrl,
                         @RequestParam(required = false) String primaryColor,
                         @RequestParam(required = false) String directorName,
                         @RequestParam(required = false) String directorUsername,
                         @RequestParam(required = false) String directorPassword,
                         Model model, RedirectAttributes ra) {
        try {
            centerService.create(name, slug, logoUrl, primaryColor, directorName, directorUsername, directorPassword);
        } catch (BadRequestException e) {
            // Formani to'ldirilgan holida qaytaramiz
            model.addAttribute("error", e.getMessage());
            model.addAttribute("form", new CenterForm(name, slug, logoUrl, primaryColor, directorName, directorUsername));
            return "centers/form";
        }
        ra.addFlashAttribute("success", "Markaz yaratildi: " + name);
        return "redirect:/admin/centers";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable UUID id, Model model) {
        model.addAttribute("center", centerService.getById(id));
        return "centers/form";
    }

    @PostMapping("/{id}/update")
    public String update(@PathVariable UUID id, @RequestParam String name,
                         @RequestParam(required = false) String logoUrl,
                         @RequestParam(required = false) String primaryColor,
                         Model model, RedirectAttributes ra) {
        try {
            centerService.update(id, name, logoUrl, primaryColor);
        } catch (BadRequestException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("center", centerService.getById(id));
            return "centers/form";
        }
        ra.addFlashAttribute("success", "Saqlandi");
        return "redirect:/admin/centers";
    }

    @PostMapping("/{id}/active")
    public String setActive(@PathVariable UUID id, @RequestParam boolean active, RedirectAttributes ra) {
        var c = centerService.setActive(id, active);
        ra.addFlashAttribute("success", c.getName() + (active ? " yoqildi" : " o'chirildi: foydalanuvchilari kira olmaydi"));
        return "redirect:/admin/centers";
    }

    /** Xato bo'lganda formani qayta to'ldirish uchun. */
    public record CenterForm(String name, String slug, String logoUrl, String primaryColor,
                             String directorName, String directorUsername) {}
}
