package uz.vocabexam.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import uz.vocabexam.entity.User;
import uz.vocabexam.repository.UserRepository;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {
    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String login) throws UsernameNotFoundException {
        return findByLogin(userRepository, login)
            .map(CustomUserDetails::new)
            .orElseThrow(() -> new UsernameNotFoundException("Foydalanuvchi topilmadi: " + login));
    }

    /** Login bo'yicha qidiradi (katta-kichik harf farqsiz); eski foydalanuvchilar uchun email ham ishlaydi. */
    public static Optional<User> findByLogin(UserRepository repo, String login) {
        if (login == null || login.isBlank()) return Optional.empty();
        String value = login.trim().toLowerCase();
        Optional<User> byUsername = repo.findByUsername(value);
        return byUsername.isPresent() || !value.contains("@") ? byUsername : repo.findByEmailIgnoreCase(value);
    }
}
