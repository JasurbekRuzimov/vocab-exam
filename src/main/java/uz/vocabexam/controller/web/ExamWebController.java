package uz.vocabexam.controller.web;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import uz.vocabexam.entity.ExamType;
import uz.vocabexam.security.SecurityUtils;
import uz.vocabexam.service.*;
import java.util.UUID;

@Controller @RequestMapping("/teacher/exams")
@RequiredArgsConstructor @PreAuthorize("hasAnyRole('TEACHER','SUPER_ADMIN')")
public class ExamWebController {
    private final ExamService examService;
    private final WordSetService wordSetService;
    private final AssignmentService assignmentService;
    private final OnlineExamService onlineExamService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("exams", examService.getByTeacher(SecurityUtils.currentUserId(),
            org.springframework.data.domain.Pageable.unpaged()));
        return "exams/list";
    }

    @GetMapping("/create")
    public String createForm(Model model) {
        model.addAttribute("wordSets", wordSetService.getByTeacher(SecurityUtils.currentUserId()));
        model.addAttribute("examTypes", ExamType.values());
        return "exams/create";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable UUID id, Model model) {
        model.addAttribute("exam",     examService.getOwned(id));
        model.addAttribute("variants", examService.getVariants(id));
        model.addAttribute("assignments", assignmentService.getByTeacher(SecurityUtils.currentUserId()));
        return "exams/detail";
    }

    @GetMapping("/{assignmentId}/submissions")
    public String submissions(@PathVariable UUID assignmentId, Model model) {
        model.addAttribute("assignment", assignmentService.getOwned(assignmentId));
        model.addAttribute("submitted",  onlineExamService.getSubmittedStudents(assignmentId));
        return "online-exam/submissions";
    }

    @GetMapping("/{assignmentId}/submissions/{studentId}/check")
    public String check(@PathVariable UUID assignmentId, @PathVariable UUID studentId, Model model) {
        model.addAttribute("assignment", assignmentService.getOwned(assignmentId));
        var answers = onlineExamService.getStudentAnswersForCheck(assignmentId, studentId);
        model.addAttribute("answers",    answers);
        model.addAttribute("studentId",  studentId);
        // Jami ballni oldindan hisoblab beramiz: avtomatik + o'qituvchi qo'ygan ballar
        model.addAttribute("scoreSum", answers.stream().mapToInt(x -> x.getScore() == null ? 0 : x.getScore()).sum());
        model.addAttribute("scoreMax", answers.size() * AnswerCheckService.MAX_ANSWER_SCORE);
        return "online-exam/check";
    }
}
