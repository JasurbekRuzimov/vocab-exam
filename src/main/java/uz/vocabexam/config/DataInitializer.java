package uz.vocabexam.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uz.vocabexam.entity.*;
import uz.vocabexam.repository.UserRepository;

import java.security.SecureRandom;
import java.util.Base64;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.email:admin@vocabexam.uz}")
    private String adminEmail;

    @Value("${app.admin.password:}")
    private String configuredPassword;

    @Override
    @Transactional
    public void run(String... args) {
        initAdmin();
    }

    private void initAdmin() {
        userRepository.findByEmail(adminEmail).ifPresentOrElse(
            admin -> {
                // Migratsiyadagi PLACEHOLDER hash bo'lsa — haqiqiy parol o'rnatamiz
                String hash = admin.getPasswordHash();
                boolean hashInvalid = !hash.startsWith("$2a$") && !hash.startsWith("$2b$") && !hash.startsWith("$2y$");
                if (hashInvalid) {
                    admin.setPasswordHash(passwordEncoder.encode(resolvePassword()));
                    admin.setActive(true);
                    admin.setLoginAttempts(0);
                    admin.setLockedUntil(null);
                    userRepository.save(admin);
                } else {
                    log.info("Admin topildi: {}", adminEmail);
                }
            },
            () -> {
                User admin = User.builder()
                    .fullName("Super Admin")
                    .email(adminEmail)
                    .passwordHash(passwordEncoder.encode(resolvePassword()))
                    .role(Role.SUPER_ADMIN)
                    .active(true)
                    .loginAttempts(0)
                    .build();
                userRepository.save(admin);
            }
        );
    }

    /** ADMIN_PASSWORD berilgan bo'lsa o'shani, aks holda tasodifiy parol (logga bir marta chiqadi). */
    private String resolvePassword() {
        if (configuredPassword != null && !configuredPassword.isBlank()) {
            log.info("Admin parol o'rnatildi (ADMIN_PASSWORD). Email: {}", adminEmail);
            return configuredPassword;
        }
        byte[] bytes = new byte[12];
        new SecureRandom().nextBytes(bytes);
        String generated = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        log.warn("Admin uchun vaqtinchalik parol yaratildi. Email: {} | Parol: {} — kirgandan keyin almashtiring!",
            adminEmail, generated);
        return generated;
    }
}
