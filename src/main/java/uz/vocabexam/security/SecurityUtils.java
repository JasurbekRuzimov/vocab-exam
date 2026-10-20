package uz.vocabexam.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import uz.vocabexam.entity.Role;
import uz.vocabexam.exception.AccessForbiddenException;
import java.util.UUID;

public class SecurityUtils {
    public static CustomUserDetails currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CustomUserDetails ud) return ud;
        return null;
    }
    public static UUID currentUserId() {
        CustomUserDetails ud = currentUser();
        return ud != null ? ud.getId() : null;
    }
    public static boolean hasRole(Role role) {
        CustomUserDetails ud = currentUser();
        return ud != null && role.name().equals(ud.getRole());
    }
    public static boolean isAdmin()   { return hasRole(Role.SUPER_ADMIN); }
    public static boolean isTeacher() { return hasRole(Role.TEACHER); }
    public static boolean isStudent() { return hasRole(Role.STUDENT); }

    /** Joriy foydalanuvchi resurs egasi yoki SUPER_ADMIN bo'lmasa — 403. */
    public static void assertOwnerOrAdmin(UUID ownerId) {
        if (isAdmin()) return;
        UUID current = currentUserId();
        if (current == null || !current.equals(ownerId)) throw new AccessForbiddenException("Ruxsat yo'q");
    }
}
