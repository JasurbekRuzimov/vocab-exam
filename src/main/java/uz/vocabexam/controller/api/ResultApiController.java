package uz.vocabexam.controller.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.vocabexam.security.SecurityUtils;
import uz.vocabexam.service.*;
import java.util.*;

@RestController @RequestMapping("/api") @RequiredArgsConstructor
public class ResultApiController {
    private final AnswerCheckService answerCheckService;

    @PostMapping("/online-exams/{assignmentId}/submissions/{studentId}/result")
    @PreAuthorize("hasAnyRole('TEACHER','SUPER_ADMIN')")
    public ResponseEntity<?> saveResult(@PathVariable UUID assignmentId, @PathVariable UUID studentId,
                                         @RequestBody Map<String,Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
            answerCheckService.saveResult(assignmentId, studentId,
                (Integer) body.get("totalScore"),
                body.containsKey("maxScore") ? (Integer) body.get("maxScore") : 100,
                (String)  body.get("comment")));
    }
}
