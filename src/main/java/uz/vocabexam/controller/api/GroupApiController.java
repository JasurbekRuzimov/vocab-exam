package uz.vocabexam.controller.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.vocabexam.security.SecurityUtils;
import uz.vocabexam.service.GroupService;
import java.util.*;

@RestController @RequestMapping("/api/groups") @RequiredArgsConstructor
public class GroupApiController {
    private final GroupService groupService;

    @GetMapping @PreAuthorize("hasAnyRole('TEACHER','SUPER_ADMIN','CENTER_ADMIN')")
    public ResponseEntity<?> getAll() {
        // O'qituvchi o'z guruhlarini, direktor va platforma admini boshqaradigan guruhlarni ko'radi
        if (SecurityUtils.isTeacher()) return ResponseEntity.ok(groupService.getAll(SecurityUtils.currentUserId()));
        return ResponseEntity.ok(groupService.getManagedList());
    }

    @PostMapping @PreAuthorize("hasAnyRole('SUPER_ADMIN','CENTER_ADMIN')")
    public ResponseEntity<?> create(@RequestBody Map<String,String> body) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(groupService.create(body.get("name"), UUID.fromString(body.get("teacherId"))));
    }

    @GetMapping("/{id}") @PreAuthorize("hasAnyRole('TEACHER','SUPER_ADMIN','CENTER_ADMIN')")
    public ResponseEntity<?> getOne(@PathVariable UUID id) {
        return ResponseEntity.ok(SecurityUtils.isTeacher() ? groupService.getOwned(id) : groupService.getManaged(id));
    }

    @PutMapping("/{id}") @PreAuthorize("hasAnyRole('SUPER_ADMIN','CENTER_ADMIN')")
    public ResponseEntity<?> update(@PathVariable UUID id, @RequestBody Map<String,String> body) {
        return ResponseEntity.ok(groupService.update(id, body.get("name"), UUID.fromString(body.get("teacherId"))));
    }

    @DeleteMapping("/{id}") @PreAuthorize("hasAnyRole('SUPER_ADMIN','CENTER_ADMIN')")
    public ResponseEntity<?> delete(@PathVariable UUID id) {
        groupService.delete(id);
        return ResponseEntity.ok(Map.of("message", "Guruh o'chirildi"));
    }

    @GetMapping("/{id}/students") @PreAuthorize("hasAnyRole('TEACHER','SUPER_ADMIN','CENTER_ADMIN')")
    public ResponseEntity<?> getStudents(@PathVariable UUID id) {
        return ResponseEntity.ok((SecurityUtils.isTeacher() ? groupService.getOwned(id) : groupService.getManaged(id)).getStudents());
    }

    @PostMapping("/{id}/students") @PreAuthorize("hasAnyRole('SUPER_ADMIN','CENTER_ADMIN')")
    public ResponseEntity<?> addStudents(@PathVariable UUID id, @RequestBody Map<String,List<String>> body) {
        List<UUID> ids = body.get("studentIds").stream().map(UUID::fromString).toList();
        groupService.addStudents(id, ids);
        return ResponseEntity.ok(Map.of("message", ids.size() + " ta student guruhga qo'shildi", "addedCount", ids.size()));
    }

    @DeleteMapping("/{id}/students/{studentId}") @PreAuthorize("hasAnyRole('SUPER_ADMIN','CENTER_ADMIN')")
    public ResponseEntity<?> removeStudent(@PathVariable UUID id, @PathVariable UUID studentId) {
        groupService.removeStudent(id, studentId);
        return ResponseEntity.ok(Map.of("message", "Student guruhdan chiqarildi"));
    }
}
