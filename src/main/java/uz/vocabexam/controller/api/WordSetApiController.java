package uz.vocabexam.controller.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import uz.vocabexam.security.SecurityUtils;
import uz.vocabexam.service.WordSetService;
import java.util.*;

@RestController @RequestMapping("/api/word-sets")
@RequiredArgsConstructor @PreAuthorize("hasAnyRole('TEACHER','SUPER_ADMIN')")
public class WordSetApiController {
    private final WordSetService wordSetService;

    @GetMapping
    public ResponseEntity<?> getAll() {
        return ResponseEntity.ok(wordSetService.getByTeacher(SecurityUtils.currentUserId()));
    }

    @PostMapping("/upload")
    public ResponseEntity<?> upload(@RequestParam String name, @RequestParam MultipartFile file) {
        Map<String,Object> result = wordSetService.upload(name, file, SecurityUtils.currentUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getOne(@PathVariable UUID id) {
        return ResponseEntity.ok(wordSetService.getOwned(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> rename(@PathVariable UUID id, @RequestBody Map<String,String> body) {
        wordSetService.rename(id, body.get("name"), SecurityUtils.currentUserId());
        return ResponseEntity.ok(Map.of("message", "Nom yangilandi"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable UUID id) {
        wordSetService.delete(id, SecurityUtils.currentUserId());
        return ResponseEntity.ok(Map.of("message", "So'zlar to'plami o'chirildi"));
    }

    @GetMapping("/{id}/words")
    public ResponseEntity<?> getWords(@PathVariable UUID id) {
        wordSetService.getOwned(id);
        return ResponseEntity.ok(wordSetService.getWords(id));
    }

    @PostMapping("/{id}/words")
    public ResponseEntity<?> addWord(@PathVariable UUID id, @RequestBody Map<String,String> body) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(wordSetService.addWord(id, body.get("text"), body.get("translation"), body.get("example"),
                SecurityUtils.currentUserId()));
    }

    @PutMapping("/{id}/words/{wordId}")
    public ResponseEntity<?> updateWord(@PathVariable UUID id, @PathVariable UUID wordId,
                                        @RequestBody Map<String,String> body) {
        return ResponseEntity.ok(wordSetService.updateWord(id, wordId, body.get("translation"), body.get("example"),
            SecurityUtils.currentUserId()));
    }

    @DeleteMapping("/{id}/words/{wordId}")
    public ResponseEntity<?> deleteWord(@PathVariable UUID id, @PathVariable UUID wordId) {
        wordSetService.deleteWord(wordId, SecurityUtils.currentUserId());
        return ResponseEntity.ok(Map.of("message", "So'z o'chirildi"));
    }
}
