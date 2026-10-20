package uz.vocabexam.controller.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.vocabexam.entity.*;
import uz.vocabexam.security.SecurityUtils;
import uz.vocabexam.service.AssignmentService;
import java.time.LocalDateTime;
import java.util.*;

@RestController @RequestMapping("/api/assignments")
@RequiredArgsConstructor @PreAuthorize("hasAnyRole('TEACHER','SUPER_ADMIN')")
public class AssignmentApiController {
    private final AssignmentService assignmentService;

    @GetMapping
    public ResponseEntity<?> getAll() {
        return ResponseEntity.ok(assignmentService.getByTeacher(SecurityUtils.currentUserId()));
    }

    @PostMapping
    public ResponseEntity<?> assign(@RequestBody Map<String,Object> body) {
        LocalDateTime deadline = body.containsKey("deadline") && body.get("deadline") != null
            ? LocalDateTime.parse((String) body.get("deadline")) : null;
        List<ExamAssignment> result = assignmentService.assign(
            UUID.fromString((String) body.get("examId")),
            TargetType.valueOf((String) body.get("targetType")),
            UUID.fromString((String) body.get("targetId")),
            deadline,
            SecurityUtils.currentUserId()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getOne(@PathVariable UUID id) {
        return ResponseEntity.ok(assignmentService.getOwned(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable UUID id) {
        assignmentService.delete(id);
        return ResponseEntity.ok(Map.of("message", "Topshiriq o'chirildi"));
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<?> cancel(@PathVariable UUID id) {
        assignmentService.cancel(id, SecurityUtils.currentUserId());
        return ResponseEntity.ok(Map.of("message", "Topshiriq bekor qilindi"));
    }
}
