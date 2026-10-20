package uz.vocabexam.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.vocabexam.entity.*;
import uz.vocabexam.exception.*;
import uz.vocabexam.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public Page<User> getAll(Role role, String search, Pageable pageable) {
        Specification<User> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (role != null) {
                predicates.add(cb.equal(root.get("role"), role));
            }

            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.toLowerCase() + "%";
                predicates.add(cb.or(
                    cb.like(cb.lower(root.get("fullName")), pattern),
                    cb.like(cb.lower(root.get("username")), pattern),
                    cb.like(cb.lower(root.get("email")),    pattern)
                ));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return userRepository.findAll(spec, pageable);
    }

    public User getById(UUID id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Foydalanuvchi topilmadi"));
    }

    private static final java.util.regex.Pattern USERNAME = java.util.regex.Pattern.compile("[a-z0-9._-]{3,50}");

    /** Loginni kichik harfga keltiradi va tekshiradi: masalan "Teacher1" -> "teacher1". */
    static String normalizeUsername(String raw) {
        String u = raw == null ? "" : raw.trim().toLowerCase();
        if (!USERNAME.matcher(u).matches())
            throw new BadRequestException("Login 3-50 belgi bo'lsin: lotin harflari, raqamlar, . _ -");
        return u;
    }

    private static String normalizeEmail(String raw) {
        return raw == null || raw.isBlank() ? null : raw.trim().toLowerCase();
    }

    @Transactional
    public User create(String fullName, String username, String email, String password, Role role) {
        String login = normalizeUsername(username);
        String mail  = normalizeEmail(email);
        if (userRepository.existsByUsername(login))
            throw new BadRequestException("Bu login band: " + login);
        if (mail != null && userRepository.existsByEmailIgnoreCase(mail))
            throw new BadRequestException("Bu email allaqachon ro'yxatdan o'tgan");
        if (password == null || password.length() < 6)
            throw new BadRequestException("Parol kamida 6 belgi bo'lsin");
        return userRepository.save(User.builder()
            .fullName(fullName).username(login).email(mail)
            .passwordHash(passwordEncoder.encode(password))
            .role(role).active(true).build());
    }

    @Transactional
    public User update(UUID id, String fullName, String username, String email, Role role) {
        User user = getById(id);
        String login = normalizeUsername(username);
        String mail  = normalizeEmail(email);
        if (!login.equals(user.getUsername()) && userRepository.existsByUsername(login))
            throw new BadRequestException("Bu login band: " + login);
        if (mail != null && !mail.equalsIgnoreCase(user.getEmail()) && userRepository.existsByEmailIgnoreCase(mail))
            throw new BadRequestException("Bu email allaqachon ishlatilmoqda");
        user.setFullName(fullName);
        user.setUsername(login);
        user.setEmail(mail);
        user.setRole(role);
        return userRepository.save(user);
    }

    @Transactional
    public void delete(UUID id) { userRepository.deleteById(id); }

    @Transactional
    public void block(UUID id) {
        User u = getById(id);
        u.setActive(false);
        userRepository.save(u);
    }

    @Transactional
    public void unblock(UUID id) {
        User u = getById(id);
        u.setActive(true);
        u.setLoginAttempts(0);
        u.setLockedUntil(null);
        userRepository.save(u);
    }

    @Transactional
    public void resetPassword(UUID id, String newPassword) {
        User u = getById(id);
        u.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(u);
    }

    @Transactional
    public void changePassword(UUID id, String currentPassword, String newPassword) {
        User u = getById(id);
        if (!passwordEncoder.matches(currentPassword, u.getPasswordHash()))
            throw new BadRequestException("Joriy parol noto'g'ri");
        u.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(u);
    }

    @Transactional
    public void recordFailedLogin(String login, int maxAttempts, int lockoutMinutes) {
        uz.vocabexam.security.CustomUserDetailsService.findByLogin(userRepository, login).ifPresent(u -> {
            u.setLoginAttempts(u.getLoginAttempts() + 1);
            if (u.getLoginAttempts() >= maxAttempts)
                u.setLockedUntil(LocalDateTime.now().plusMinutes(lockoutMinutes));
            userRepository.save(u);
        });
    }

    @Transactional
    public void recordSuccessLogin(UUID id) {
        userRepository.findById(id).ifPresent(u -> {
            u.setLoginAttempts(0);
            u.setLockedUntil(null);
            u.setLastLogin(LocalDateTime.now());
            userRepository.save(u);
        });
    }
}