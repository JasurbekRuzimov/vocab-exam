package uz.vocabexam.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.*;

@RestControllerAdvice @Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String,Object>> notFound(ResourceNotFoundException ex) {
        return response(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<Map<String,Object>> badRequest(BadRequestException ex) {
        return response(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(AccessForbiddenException.class)
    public ResponseEntity<Map<String,Object>> forbidden(AccessForbiddenException ex) {
        return response(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String,Object>> accessDenied(AccessDeniedException ex) {
        return response(HttpStatus.FORBIDDEN, "Ruxsat yo'q");
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<Map<String,Object>> conflict(org.springframework.dao.DataIntegrityViolationException ex) {
        log.warn("Bog'liq ma'lumot sabab amal bajarilmadi: {}", ex.getMostSpecificCause().getMessage());
        return response(HttpStatus.CONFLICT, "Bu yozuvga boshqa ma'lumotlar bog'langan. Avval ularni o'chiring.");
    }

    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<Map<String,Object>> noResource(org.springframework.web.servlet.resource.NoResourceFoundException ex) {
        return response(HttpStatus.NOT_FOUND, "Sahifa topilmadi");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String,Object>> general(Exception ex) {
        // Ichki xato tafsilotlari foydalanuvchiga ko'rsatilmaydi, faqat logga yoziladi
        log.error("Kutilmagan xato", ex);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "Server xatosi yuz berdi");
    }

    private ResponseEntity<Map<String,Object>> response(HttpStatus status, String message) {
        Map<String,Object> body = new LinkedHashMap<>();
        body.put("status",    status.value());
        body.put("error",     status.getReasonPhrase());
        body.put("message",   message);
        body.put("timestamp", LocalDateTime.now().toString());
        return ResponseEntity.status(status).body(body);
    }
}
