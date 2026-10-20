package uz.vocabexam.controller.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.vocabexam.service.UserService;
import java.util.*;

/** O'qituvchi imtihonni alohida o'quvchiga tayinlashi uchun: faqat o'z markazidagi o'quvchilar. */
@RestController @RequestMapping("/api/students") @RequiredArgsConstructor
@PreAuthorize("hasAnyRole('TEACHER','CENTER_ADMIN')")
public class StudentDirectoryApiController {
    private final UserService userService;

    @GetMapping
    public ResponseEntity<?> list() {
        return ResponseEntity.ok(userService.getCenterStudents().stream().map(u -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.getId());
            m.put("fullName", u.getFullName());
            m.put("username", u.getUsername());
            return m;
        }).toList());
    }
}
