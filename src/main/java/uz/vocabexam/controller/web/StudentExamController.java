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

        model.addAttribute("assignment", a);
        model.addAttribute("words", onlineExamService.getExamWords(a, studentId));
        model.addAttribute("submitted", onlineExamService.hasSubmitted(assignmentId, studentId));
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
        return "results/detail";
    }
}