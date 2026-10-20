package uz.vocabexam.controller.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.vocabexam.exception.BadRequestException;
import uz.vocabexam.service.AnswerCheckService;
import java.util.*;

@RestController @RequestMapping("/api/answers")
@RequiredArgsConstructor @PreAuthorize("hasAnyRole('TEACHER','SUPER_ADMIN')")
public class AnswerApiController {
    private final AnswerCheckService answerCheckService;

    @PostMapping("/{answerId}/errors")
    public ResponseEntity<?> addError(@PathVariable UUID answerId, @RequestBody Map<String,Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
            answerCheckService.addError(answerId,
                requireInt(body, "errorStart"),
                requireInt(body, "errorEnd"),
                (String)  body.get("comment"),
                (String)  body.get("correctHint")));
    }

    @PutMapping("/{answerId}/errors/{errorId}")
    public ResponseEntity<?> updateError(@PathVariable UUID answerId, @PathVariable UUID errorId,
                                          @RequestBody Map<String,String> body) {
        return ResponseEntity.ok(answerCheckService.updateError(answerId, errorId, body.get("comment"), body.get("correctHint")));
    }

    @DeleteMapping("/{answerId}/errors/{errorId}")
    public ResponseEntity<?> deleteError(@PathVariable UUID answerId, @PathVariable UUID errorId) {
        answerCheckService.deleteError(answerId, errorId);
        return ResponseEntity.ok(Map.of("message", "Xato o'chirildi"));
    }

    @PatchMapping("/{answerId}/score")
    public ResponseEntity<?> setScore(@PathVariable UUID answerId, @RequestBody Map<String,Integer> body) {
        Integer score = body.get("score");
        if (score == null) throw new BadRequestException("score majburiy");
        answerCheckService.setScore(answerId, score);
        return ResponseEntity.ok(Map.of("answerId", answerId, "score", body.get("score")));
    }

    static int requireInt(Map<String,Object> body, String key) {
        if (!(body.get(key) instanceof Number n)) throw new BadRequestException(key + " raqam bo'lishi kerak");
        return n.intValue();
    }
}
