package uz.vocabexam.controller.web;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import uz.vocabexam.entity.*;
import uz.vocabexam.repository.ExamResultRepository;
import uz.vocabexam.security.SecurityUtils;
import uz.vocabexam.service.*;
import java.util.*;

@Controller @RequestMapping("/teacher/exams")
@RequiredArgsConstructor @PreAuthorize("hasAnyRole('TEACHER','SUPER_ADMIN')")
public class ExamWebController {
    private final ExamService examService;
    private final WordSetService wordSetService;
    private final AssignmentService assignmentService;
    private final OnlineExamService onlineExamService;
    private final ExamMonitorService monitorService;
    private final AnswerCheckService answerCheckService;
    private final ExamResultRepository resultRepository;

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
        ExamAssignment a = assignmentService.getOwned(assignmentId);
        model.addAttribute("assignment", a);
        model.addAttribute("monitor", monitorService.getMonitor(a));
        model.addAttribute("bucketLabels", ExamMonitorService.BUCKET_LABELS);
        return "online-exam/submissions";
    }

    /** Jonli monitoring jadvali: sahifa uni har 10 soniyada qayta yuklaydi. */
    @GetMapping("/{assignmentId}/submissions/live")
    public String submissionsLive(@PathVariable UUID assignmentId, Model model) {
        ExamAssignment a = assignmentService.getOwned(assignmentId);
        model.addAttribute("assignment", a);
        model.addAttribute("monitor", monitorService.getMonitor(a));
        return "online-exam/submissions :: roster";
    }

    @GetMapping("/{assignmentId}/submissions/{studentId}/check")
    public String check(@PathVariable UUID assignmentId, @PathVariable UUID studentId, Model model) {
        model.addAttribute("assignment", assignmentService.getOwned(assignmentId));
        var answers = onlineExamService.getStudentAnswersForCheck(assignmentId, studentId);
        answers.stream().filter(x -> x.getStudent() != null).findFirst()
            .ifPresent(x -> model.addAttribute("student", x.getStudent()));
        model.addAttribute("attempt", monitorService.getAttempt(assignmentId, studentId).orElse(null));
        var result = resultRepository.findByAssignmentIdAndStudentId(assignmentId, studentId).orElse(null);
        model.addAttribute("result", result);
        model.addAttribute("changes", result == null ? List.of() : answerCheckService.getChanges(result.getId()));
        model.addAttribute("categories", ErrorCategory.values());
        model.addAttribute("answers",    answers);
        model.addAttribute("studentId",  studentId);
        // Jami ballni oldindan hisoblab beramiz: avtomatik + o'qituvchi qo'ygan ballar
        model.addAttribute("scoreSum", answers.stream().mapToInt(x -> x.getScore() == null ? 0 : x.getScore()).sum());
        model.addAttribute("scoreMax", answers.size() * AnswerCheckService.MAX_ANSWER_SCORE);
        return "online-exam/check";
    }
}
