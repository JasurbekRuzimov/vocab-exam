package uz.vocabexam.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uz.vocabexam.entity.*;
import uz.vocabexam.repository.UserRepository;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        initAdmin();
    }

    private void initAdmin() {
        String adminEmail    = "admin@vocabexam.uz";
        String adminPassword = "Admin@1234";

        userRepository.findByEmail(adminEmail).ifPresentOrElse(
            admin -> {
                // Hash noto'g'ri bo'lsa — yangilaymiz
                boolean hashInvalid = !admin.getPasswordHash().startsWith("$2a$")
                    && !admin.getPasswordHash().startsWith("$2b$")
                    && !admin.getPasswordHash().startsWith("$2y$");
                if (hashInvalid) {
                    String newHash = passwordEncoder.encode(adminPassword);
                    admin.setPasswordHash(newHash);
                    admin.setActive(true);
                    admin.setLoginAttempts(0);
                    admin.setLockedUntil(null);
                    userRepository.save(admin);
                    log.info("✅ Admin paroli yangilandi. Email: {} | Parol: {}", adminEmail, adminPassword);
                } else {
                    log.info("✅ Admin topildi: {}", adminEmail);
                }
            },
            () -> {
                // Admin yo'q — yangisini yaratamiz
                User admin = User.builder()
                    .fullName("Super Admin")
                    .email(adminEmail)
                    .passwordHash(passwordEncoder.encode(adminPassword))
                    .role(Role.SUPER_ADMIN)
                    .active(true)
                    .loginAttempts(0)
                    .build();
                userRepository.save(admin);
                log.info("✅ Admin yaratildi. Email: {} | Parol: {}", adminEmail, adminPassword);
            }
        );
    }
}