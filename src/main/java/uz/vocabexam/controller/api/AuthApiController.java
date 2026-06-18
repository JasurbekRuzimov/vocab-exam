package uz.vocabexam.controller.api;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;
import uz.vocabexam.security.*;
import uz.vocabexam.service.UserService;
import java.util.*;

@RestController @RequestMapping("/api/auth") @RequiredArgsConstructor
public class AuthApiController {
    private final AuthenticationManager authenticationManager;
    private final UserService userService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String,String> body, HttpSession session) {
        try {
            Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(body.get("email"), body.get("password")));
            SecurityContextHolder.getContext().setAuthentication(auth);
            session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                SecurityContextHolder.getContext());
            CustomUserDetails ud = (CustomUserDetails) auth.getPrincipal();
            userService.recordSuccessLogin(ud.getId());
            return ResponseEntity.ok(Map.of("id", ud.getId(), "fullName", ud.getFullName(),
                "email", ud.getEmail(), "role", ud.getRole()));
        } catch (BadCredentialsException e) {
            userService.recordFailedLogin(body.get("email"), 5, 15);
            return ResponseEntity.status(401).body(Map.of("message", "Email yoki parol noto'g'ri"));
        } catch (LockedException e) {
            return ResponseEntity.status(423).body(Map.of("message", "Akkaunt vaqtincha bloklangan"));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> me() {
        CustomUserDetails ud = SecurityUtils.currentUser();
        if (ud == null) return ResponseEntity.status(401).body(Map.of("message", "Kiring"));
        return ResponseEntity.ok(Map.of("id", ud.getId(), "fullName", ud.getFullName(),
            "email", ud.getEmail(), "role", ud.getRole()));
    }

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@RequestBody Map<String,String> body) {
        UUID id = SecurityUtils.currentUserId();
        userService.changePassword(id, body.get("currentPassword"), body.get("newPassword"));
        return ResponseEntity.ok(Map.of("message", "Parol muvaffaqiyatli o'zgartirildi"));
    }
}
