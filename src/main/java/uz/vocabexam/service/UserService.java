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
import uz.vocabexam.repository.*;
import uz.vocabexam.security.SecurityUtils;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CenterRepository centerRepository;
    private final GroupRepository groupRepository;
    private final ExamRepository examRepository;
    private final WordSetRepository wordSetRepository;
    private final DataCleanupService cleanupService;

    public Page<User> getAll(Role role, String search, Pageable pageable) {
        return getAll(role, search, null, pageable);
    }

    /**
     * Foydalanuvchilar ro'yxati. Markaz direktori faqat o'z markazini ko'radi;
     * platforma admini hammasini (yoki {@code centerId} berilsa, shu markazni).
     */
    public Page<User> getAll(Role role, String search, UUID centerId, Pageable pageable) {
        UUID scope = SecurityUtils.isAdmin() ? centerId : requireOwnCenter();
        Specification<User> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (scope != null) {
                predicates.add(cb.equal(root.get("center").get("id"), scope));
            }

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

    /** Boshqarish uchun: direktor faqat o'z markazidagi (platforma adminidan boshqa) foydalanuvchini oladi. */
    public User getManaged(UUID id) {
        User u = getById(id);
        if (SecurityUtils.isAdmin()) return u;
        if (u.getRole() == Role.SUPER_ADMIN) throw new AccessForbiddenException("Ruxsat yo'q");
        SecurityUtils.assertSameCenter(u.getCenterId());
        return u;
    }

    /** O'qituvchi imtihon tayinlashi uchun: o'z markazidagi faol o'quvchilar. */
    public List<User> getCenterStudents() {
        UUID center = requireOwnCenter();
        return userRepository.findByCenter_IdAndRoleAndActiveTrueOrderByFullName(center, Role.STUDENT);
    }

    private static UUID requireOwnCenter() {
        UUID mine = SecurityUtils.currentCenterId();
        if (mine == null) throw new AccessForbiddenException("Markaz biriktirilmagan");
        return mine;
    }

    /**
     * Yangi foydalanuvchi qaysi markazga tushishini aniqlaydi. Platforma admini istalgan markazni
     * tanlaydi (markaz bitta bo'lsa, tanlash shart emas); direktor faqat o'z markaziga qo'sha oladi.
     */
    private Center resolveCenter(Role role, UUID centerId) {
        if (role == Role.SUPER_ADMIN) {
            if (!SecurityUtils.isAdmin()) throw new AccessForbiddenException("Platforma adminini faqat platforma admini yaratadi");
            return null;
        }
        if (SecurityUtils.isAdmin()) {
            if (centerId != null)
                return centerRepository.findById(centerId).orElseThrow(() -> new ResourceNotFoundException("Markaz topilmadi"));
            List<Center> all = centerRepository.findAll();
            if (all.size() == 1) return all.get(0);
            throw new BadRequestException("Markazni tanlang");
        }
        UUID mine = requireOwnCenter();
        if (centerId != null && !centerId.equals(mine)) throw new AccessForbiddenException("Bu boshqa markaz");
        return centerRepository.getReferenceById(mine);
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
        return create(fullName, username, email, password, role, null);
    }

    @Transactional
    public User create(String fullName, String username, String email, String password, Role role, UUID centerId) {
        if (fullName == null || fullName.isBlank()) throw new BadRequestException("Ismni yozing");
        if (role == null) throw new BadRequestException("Rolni tanlang");
        Center center = resolveCenter(role, centerId);
        String login = normalizeUsername(username);
        String mail  = normalizeEmail(email);
        if (userRepository.existsByUsername(login))
            throw new BadRequestException("Bu login band: " + login);
        if (mail != null && userRepository.existsByEmailIgnoreCase(mail))
            throw new BadRequestException("Bu email allaqachon ro'yxatdan o'tgan");
        if (password == null || password.length() < 6)
            throw new BadRequestException("Parol kamida 6 belgi bo'lsin");
        return userRepository.save(User.builder()
            .fullName(fullName.trim()).username(login).email(mail)
            .passwordHash(passwordEncoder.encode(password))
            .role(role).center(center).active(true).build());
    }

    @Transactional
    public User update(UUID id, String fullName, String username, String email, Role role) {
        return update(id, fullName, username, email, role, null);
    }

    /** {@code centerId} faqat platforma admini uchun: foydalanuvchini boshqa markazga o'tkazish. */
    @Transactional
    public User update(UUID id, String fullName, String username, String email, Role role, UUID centerId) {
        User user = getManaged(id);
        if (role == null) throw new BadRequestException("Rolni tanlang");
        if (role != user.getRole() || (SecurityUtils.isAdmin() && centerId != null && !centerId.equals(user.getCenterId())))
            user.setCenter(resolveCenter(role, centerId != null ? centerId : user.getCenterId()));
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
    public void delete(UUID id) {
        User u = getManaged(id);
        if (u.getId().equals(SecurityUtils.currentUserId()))
            throw new BadRequestException("O'zingizni o'chira olmaysiz");
        // O'qituvchining guruh va imtihonlari o'z-o'zidan o'chib ketmasin: avval ularni hal qilish kerak
        long groups = groupRepository.countByTeacherId(id), exams = examRepository.countByTeacherId(id);
        if (groups > 0 || exams > 0)
            throw new BadRequestException(u.getFullName() + "ga " + groups + " ta guruh va " + exams
                + " ta imtihon bog'langan. Avval guruhlarni boshqa o'qituvchiga o'tkazing va imtihonlarni o'chiring"
                + " yoki foydalanuvchini bloklang.");
        cleanupService.purgeUserData(id);
        wordSetRepository.deleteAll(wordSetRepository.findByTeacherIdOrderByCreatedAtDesc(id));
        userRepository.delete(getById(id));
    }

    @Transactional
    public void block(UUID id) {
        User u = getManaged(id);
        u.setActive(false);
        userRepository.save(u);
    }

    @Transactional
    public void unblock(UUID id) {
        User u = getManaged(id);
        u.setActive(true);
        u.setLoginAttempts(0);
        u.setLockedUntil(null);
        userRepository.save(u);
    }

    @Transactional
    public void resetPassword(UUID id, String newPassword) {
        User u = getManaged(id);
        if (newPassword == null || newPassword.length() < 6)
            throw new BadRequestException("Parol kamida 6 belgi bo'lsin");
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