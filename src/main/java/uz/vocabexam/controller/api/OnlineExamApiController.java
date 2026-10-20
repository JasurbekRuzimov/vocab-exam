package uz.vocabexam.controller.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.vocabexam.entity.*;
import uz.vocabexam.security.SecurityUtils;
import uz.vocabexam.service.*;
import java.util.*;

@RestController @RequestMapping("/api/online-exams") @RequiredArgsConstructor
public class OnlineExamApiController {
    private final OnlineExamService onlineExamService;
    private final AssignmentService assignmentService;

    @GetMapping("/my") @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<?> myExams() {
        return ResponseEntity.ok(assignmentService.getForStudent(SecurityUtils.currentUserId()));
    }

    @GetMapping("/{assignmentId}/start") @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<?> start(@PathVariable UUID assignmentId) {
        UUID studentId = SecurityUtils.currentUserId();
        ExamAssignment a = onlineExamService.getAssignmentForStudent(assignmentId, studentId);
        List<uz.vocabexam.service.ExamQuestion> questions = onlineExamService.getQuestions(a, studentId);
        List<OnlineAnswer> savedAnswers = onlineExamService.getStudentAnswers(assignmentId, studentId);
        Map<UUID, String> savedMap = new HashMap<>();
        savedAnswers.forEach(ans -> savedMap.put(ans.getWord().getId(), ans.getAnswerText()));
        // To'g'ri javob (expected) bu yerda yuborilmaydi; UZ → EN savolida inglizcha so'z ham ko'rsatilmaydi
        List<Map<String,Object>> wordList = questions.stream().map(q -> {
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("id", q.word().getId());
            m.put("text", q.prompt());
            m.put("type", q.type());
            m.put("instruction", q.instruction());
            m.put("options", q.options());
            m.put("savedAnswer", savedMap.get(q.word().getId()));
            return m;
        }).toList();
        // LinkedHashMap: deadline null bo'lishi mumkin (Map.of null qabul qilmaydi)
        Map<String,Object> body = new LinkedHashMap<>();
        body.put("assignmentId", assignmentId);
        body.put("examTitle", a.getExam().getTitle());
        body.put("deadline", a.getDeadline());
        body.put("words", wordList);
        body.put("totalWords", questions.size());
        body.put("submitted", onlineExamService.hasSubmitted(assignmentId, studentId));
        return ResponseEntity.ok(body);
    }

    @PutMapping("/{assignmentId}/answers/{wordId}") @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<?> saveAnswer(@PathVariable UUID assignmentId, @PathVariable UUID wordId,
                                        @RequestBody Map<String,String> body) {
        return ResponseEntity.ok(onlineExamService.saveAnswer(assignmentId, wordId,
            body.get("answerText"), SecurityUtils.currentUserId()));
    }

    @PostMapping("/{assignmentId}/submit") @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<?> submit(@PathVariable UUID assignmentId) {
        onlineExamService.submit(assignmentId, SecurityUtils.currentUserId());
        return ResponseEntity.ok(Map.of("message", "Imtihon muvaffaqiyatli topshirildi"));
    }

    @GetMapping("/{assignmentId}/submissions") @PreAuthorize("hasAnyRole('TEACHER','SUPER_ADMIN')")
    public ResponseEntity<?> getSubmissions(@PathVariable UUID assignmentId) {
        assignmentService.getOwned(assignmentId);
        return ResponseEntity.ok(onlineExamService.getSubmittedStudents(assignmentId));
    }

    @GetMapping("/{assignmentId}/submissions/{studentId}") @PreAuthorize("hasAnyRole('TEACHER','SUPER_ADMIN')")
    public ResponseEntity<?> getStudentAnswers(@PathVariable UUID assignmentId, @PathVariable UUID studentId) {
        assignmentService.getOwned(assignmentId);
        return ResponseEntity.ok(onlineExamService.getStudentAnswersForCheck(assignmentId, studentId));
    }
}
