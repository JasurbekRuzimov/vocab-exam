package uz.vocabexam.controller.web;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import uz.vocabexam.entity.*;
import uz.vocabexam.exception.*;
import uz.vocabexam.repository.ExamResultRepository;
import uz.vocabexam.security.SecurityUtils;
import uz.vocabexam.service.*;
import java.util.*;

@Controller
@RequestMapping("/student")
@RequiredArgsConstructor
@PreAuthorize("hasRole('STUDENT')")
public class StudentExamController {

    private final AssignmentService assignmentService;
    private final OnlineExamService onlineExamService;
    private final ExamResultRepository examResultRepository;
    private final ExamMonitorService monitorService;
    private final AnswerCheckService answerCheckService;

    @GetMapping("/exams")
    public String myExams(Model model) {
        model.addAttribute("assignments",
            assignmentService.getForStudent(SecurityUtils.currentUserId()));
        return "student/exams";
    }

    @GetMapping("/exams/{assignmentId}/take")
    public String takeExam(@PathVariable UUID assignmentId, Model model) {
        UUID studentId = SecurityUtils.currentUserId();
        ExamAssignment a = onlineExamService.getAssignmentForStudent(assignmentId, studentId);

        // Saqlangan javoblarni Map<wordId, answerText> ko'rinishida tayyorlaymiz
        Map<String, String> savedAnswersMap = new HashMap<>();
        onlineExamService.getStudentAnswers(assignmentId, studentId)
            .forEach(ans -> {
                if (ans.getAnswerText() != null) {
                    savedAnswersMap.put(ans.getWord().getId().toString(), ans.getAnswerText());
                }
            });

        boolean submitted = onlineExamService.hasSubmitted(assignmentId, studentId);
        if (!submitted) monitorService.recordOpen(a, studentId); // jonli monitoring: "boshladi"

        model.addAttribute("assignment", a);
        model.addAttribute("questions", onlineExamService.getQuestions(a, studentId));
        model.addAttribute("submitted", submitted);
        model.addAttribute("savedAnswersMap", savedAnswersMap);
        return "online-exam/take";
    }

    @GetMapping("/results")
    public String myResults(Model model) {
        model.addAttribute("results",
            examResultRepository.findByStudentIdOrderByCheckedAtDesc(
                SecurityUtils.currentUserId()));
        return "results/list";
    }

    @GetMapping("/results/{resultId}")
    public String resultDetail(@PathVariable UUID resultId, Model model) {
        ExamResult result = examResultRepository.findById(resultId)
            .orElseThrow(() -> new ResourceNotFoundException("Natija topilmadi"));
        if (!result.getStudent().getId().equals(SecurityUtils.currentUserId()))
            throw new AccessForbiddenException("Ruxsat yo'q");
        model.addAttribute("result", result);
        // Shaffoflik: har savol bo'yicha javob, to'g'ri javob va ball
        model.addAttribute("answers", onlineExamService.getStudentAnswersForCheck(
            result.getAssignment().getId(), result.getStudent().getId()));
        model.addAttribute("changes", answerCheckService.getChanges(result.getId()));
        return "results/detail";
    }
}