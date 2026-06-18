package uz.vocabexam.controller.api;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.vocabexam.entity.*;
import uz.vocabexam.service.UserService;
import java.util.*;

@RestController @RequestMapping("/api/users")
@RequiredArgsConstructor @PreAuthorize("hasRole('SUPER_ADMIN')")
public class UserApiController {
    private final UserService userService;

    @GetMapping
    public ResponseEntity<?> getAll(
            @RequestParam(required=false) Role role,
            @RequestParam(required=false) String search,
            @RequestParam(defaultValue="0")  int page,
            @RequestParam(defaultValue="20") int size) {
        return ResponseEntity.ok(userService.getAll(role, search, PageRequest.of(page, size)));
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String,String> body) {
        User u = userService.create(body.get("fullName"), body.get("email"),
            body.get("password"), Role.valueOf(body.get("role")));
        return ResponseEntity.status(HttpStatus.CREATED).body(u);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getOne(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.getById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable UUID id, @RequestBody Map<String,String> body) {
        return ResponseEntity.ok(userService.update(id, body.get("fullName"),
            body.get("email"), Role.valueOf(body.get("role"))));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable UUID id) {
        userService.delete(id);
        return ResponseEntity.ok(Map.of("message", "Foydalanuvchi o'chirildi"));
    }

    @PatchMapping("/{id}/block")
    public ResponseEntity<?> block(@PathVariable UUID id) {
        userService.block(id);
        return ResponseEntity.ok(Map.of("message", "Akkaunt bloklandi"));
    }

    @PatchMapping("/{id}/unblock")
    public ResponseEntity<?> unblock(@PathVariable UUID id) {
        userService.unblock(id);
        return ResponseEntity.ok(Map.of("message", "Akkaunt blokdan chiqarildi"));
    }

    @PatchMapping("/{id}/reset-password")
    public ResponseEntity<?> resetPassword(@PathVariable UUID id, @RequestBody Map<String,String> body) {
        userService.resetPassword(id, body.get("newPassword"));
        return ResponseEntity.ok(Map.of("message", "Parol tiklandi"));
    }
}
