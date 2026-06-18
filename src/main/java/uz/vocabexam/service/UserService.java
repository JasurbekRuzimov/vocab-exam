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

    @Transactional
    public User create(String fullName, String email, String password, Role role) {
        if (userRepository.existsByEmail(email))
            throw new BadRequestException("Bu email allaqachon ro'yxatdan o'tgan");
        return userRepository.save(User.builder()
            .fullName(fullName).email(email)
            .passwordHash(passwordEncoder.encode(password))
            .role(role).active(true).build());
    }

    @Transactional
    public User update(UUID id, String fullName, String email, Role role) {
        User user = getById(id);
        if (!user.getEmail().equals(email) && userRepository.existsByEmail(email))
            throw new BadRequestException("Bu email allaqachon ishlatilmoqda");
        user.setFullName(fullName);
        user.setEmail(email);
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
    public void recordFailedLogin(String email, int maxAttempts, int lockoutMinutes) {
        userRepository.findByEmail(email).ifPresent(u -> {
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