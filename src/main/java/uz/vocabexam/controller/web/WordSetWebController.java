package uz.vocabexam.controller.web;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import uz.vocabexam.security.SecurityUtils;
import uz.vocabexam.service.WordSetService;
import java.util.UUID;

@Controller @RequestMapping("/teacher/word-sets")
@RequiredArgsConstructor @PreAuthorize("hasAnyRole('TEACHER','SUPER_ADMIN')")
public class WordSetWebController {
    private final WordSetService wordSetService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("wordSets", wordSetService.getByTeacher(SecurityUtils.currentUserId()));
        return "word-sets/list";
    }

    @GetMapping("/upload")
    public String uploadForm() { return "word-sets/upload"; }

    @PostMapping("/upload")
    public String upload(@RequestParam String name, @RequestParam MultipartFile file, Model model) {
        try {
            wordSetService.upload(name, file, SecurityUtils.currentUserId());
            return "redirect:/teacher/word-sets?success=true";
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
            return "word-sets/upload";
        }
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable UUID id, Model model) {
        model.addAttribute("wordSet", wordSetService.getById(id));
        model.addAttribute("words",   wordSetService.getWords(id));
        return "word-sets/detail";
    }
}
