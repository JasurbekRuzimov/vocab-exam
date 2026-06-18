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
        List<Word> words = onlineExamService.getExamWords(a);
        List<OnlineAnswer> savedAnswers = onlineExamService.getStudentAnswers(assignmentId, studentId);
        Map<UUID, String> savedMap = new HashMap<>();
        savedAnswers.forEach(ans -> savedMap.put(ans.getWord().getId(), ans.getAnswerText()));
        List<Map<String,Object>> wordList = words.stream().map(w -> {
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("id", w.getId()); m.put("text", w.getText());
            m.put("savedAnswer", savedMap.get(w.getId()));
            return m;
        }).toList();
        return ResponseEntity.ok(Map.of("assignmentId", assignmentId,
            "examTitle", a.getExam().getTitle(),
            "deadline", a.getDeadline(),
            "words", wordList,
            "totalWords", words.size()));
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
        return ResponseEntity.ok(onlineExamService.getSubmittedStudents(assignmentId));
    }

    @GetMapping("/{assignmentId}/submissions/{studentId}") @PreAuthorize("hasAnyRole('TEACHER','SUPER_ADMIN')")
    public ResponseEntity<?> getStudentAnswers(@PathVariable UUID assignmentId, @PathVariable UUID studentId) {
        return ResponseEntity.ok(onlineExamService.getStudentAnswersForCheck(assignmentId, studentId));
    }
}
