package uz.vocabexam.controller.api;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.*;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.vocabexam.entity.*;
import uz.vocabexam.security.SecurityUtils;
import uz.vocabexam.service.ExamService;
import java.io.*;
import java.nio.file.*;
import java.util.*;

@RestController @RequestMapping("/api/exams")
@RequiredArgsConstructor @PreAuthorize("hasAnyRole('TEACHER','SUPER_ADMIN')")
public class ExamApiController {
    private final ExamService examService;

    @GetMapping
    public ResponseEntity<?> getAll(@RequestParam(defaultValue="0") int page,
                                    @RequestParam(defaultValue="20") int size) {
        return ResponseEntity.ok(examService.getByTeacher(SecurityUtils.currentUserId(), PageRequest.of(page,size)));
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String,Object> body) {
        UUID teacherId = SecurityUtils.currentUserId();
        Exam exam = examService.createAndGenerate(
            (String) body.get("title"),
            ExamType.valueOf((String) body.get("type")),
            UUID.fromString((String) body.get("wordSetId")),
            teacherId,
            (Integer) body.get("questionCount"),
            body.containsKey("variantCount") ? (Integer) body.get("variantCount") : 1
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(exam);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getOne(@PathVariable UUID id) {
        return ResponseEntity.ok(examService.getById(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable UUID id) {
        examService.delete(id, SecurityUtils.currentUserId());
        return ResponseEntity.ok(Map.of("message", "Imtihon o'chirildi"));
    }

    @GetMapping("/{id}/variants/{variantId}/pdf")
    public ResponseEntity<Resource> downloadVariant(@PathVariable UUID id, @PathVariable UUID variantId) throws IOException {
        Exam exam = examService.getById(id);
        List<ExamVariant> variants = examService.getVariants(id);
        ExamVariant variant = variants.stream().filter(v -> v.getId().equals(variantId))
            .findFirst().orElseThrow(() -> new uz.vocabexam.exception.ResourceNotFoundException("Variant topilmadi"));
        Path path = Paths.get(variant.getPdfPath());
        Resource resource = new FileSystemResource(path);
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + exam.getTitle() + "_variant_" + variant.getVariantNumber() + ".pdf\"")
            .body(resource);
    }

    @GetMapping("/{id}/download-zip")
    public ResponseEntity<Resource> downloadZip(@PathVariable UUID id) throws IOException {
        Exam exam = examService.getById(id);
        List<ExamVariant> variants = examService.getVariants(id);
        File zipFile = File.createTempFile("variants_", ".zip");
        try (var zos = new java.util.zip.ZipOutputStream(new FileOutputStream(zipFile))) {
            for (ExamVariant v : variants) {
                Path path = Paths.get(v.getPdfPath());
                if (Files.exists(path)) {
                    zos.putNextEntry(new java.util.zip.ZipEntry("variant_" + v.getVariantNumber() + ".pdf"));
                    Files.copy(path, zos);
                    zos.closeEntry();
                }
            }
        }
        Resource resource = new FileSystemResource(zipFile);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("application/zip"))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + exam.getTitle() + "_variants.zip\"")
            .body(resource);
    }
}
