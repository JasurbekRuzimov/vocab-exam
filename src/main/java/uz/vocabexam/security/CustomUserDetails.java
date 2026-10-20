package uz.vocabexam.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import uz.vocabexam.entity.User;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Getter
public class CustomUserDetails implements UserDetails {
    private final UUID id;
    private final String fullName;
    private final String username;
    private final String email;
    private final String password;
    private final String role;
    /** Foydalanuvchi markazi (platforma adminida null). */
    private final UUID centerId;
    private final boolean active;
    private final boolean locked;
    private final Collection<? extends GrantedAuthority> authorities;

    public CustomUserDetails(User user) {
        this.id       = user.getId();
        this.fullName = user.getFullName();
        this.username = user.getUsername();
        this.email    = user.getEmail();
        this.password = user.getPasswordHash();
        this.role     = user.getRole().name();
        this.centerId = user.getCenterId();
        // Markaz o'chirilgan bo'lsa (masalan obuna tugagan), uning foydalanuvchilari kira olmaydi
        this.active   = user.isActive() && (user.getCenter() == null || user.getCenter().isActive());
        this.locked   = user.isLocked();
        this.authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
    }

    @Override public String getUsername()               { return username; }
    @Override public String getPassword()               { return password; }
    @Override public boolean isAccountNonExpired()      { return true; }
    @Override public boolean isAccountNonLocked()       { return !locked; }
    @Override public boolean isCredentialsNonExpired()  { return true; }
    @Override public boolean isEnabled()                { return active; }
}
