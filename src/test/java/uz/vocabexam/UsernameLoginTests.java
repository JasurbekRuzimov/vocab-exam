package uz.vocabexam;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import uz.vocabexam.entity.*;
import uz.vocabexam.exception.BadRequestException;
import uz.vocabexam.repository.CenterRepository;
import uz.vocabexam.repository.UserRepository;
import uz.vocabexam.security.CustomUserDetails;
import uz.vocabexam.service.UserService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;

/**
 * Oddiy login (username) bilan kirish va foydalanuvchi yaratish qoidalari.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:logindb;MODE=PostgreSQL;NON_KEYWORDS=GROUPS,USER",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false"
})
class UsernameLoginTests {

    @Autowired MockMvc mvc;
    @Autowired UserService userService;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired CenterRepository centers;

    /** Foydalanuvchilarni platforma admini nomidan, yagona markazda yaratamiz. */
    @BeforeEach
    void actAsPlatformAdmin() {
        centers.save(Center.builder().name("Markaz").slug("markaz").build());
        User admin = users.save(User.builder().fullName("Admin").username("root").passwordHash("x")
            .role(Role.SUPER_ADMIN).active(true).build());
        CustomUserDetails ud = new CustomUserDetails(admin);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(ud, null, ud.getAuthorities()));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void loginWithUsernameAndPassword() throws Exception {
        userService.create("Ali Valiyev", "Teacher1", null, "parol123", Role.TEACHER);
        SecurityContextHolder.clearContext(); // endi oddiy mehmon sifatida kiramiz
        mvc.perform(formLogin("/login").user("username", "teacher1").password("parol123"))
            .andExpect(authenticated().withUsername("teacher1"));
        // katta-kichik harf farqi yo'q
        mvc.perform(formLogin("/login").user("username", "TEACHER1").password("parol123"))
            .andExpect(authenticated());
        mvc.perform(formLogin("/login").user("username", "teacher1").password("noto'g'ri"))
            .andExpect(unauthenticated());
    }

    @Test
    void legacyEmailStillWorksForExistingUsers() throws Exception {
        users.save(User.builder().fullName("Old").username("old").email("old@gmail.com")
            .passwordHash(encoder.encode("parol123")).role(Role.STUDENT).active(true).build());
        SecurityContextHolder.clearContext();
        mvc.perform(formLogin("/login").user("username", "old@gmail.com").password("parol123"))
            .andExpect(authenticated().withUsername("old"));
    }

    @Test
    void usernameIsNormalizedAndValidated() {
        User u = userService.create("Student", "  Student.01 ", "", "parol123", Role.STUDENT);
        assertThat(u.getUsername()).isEqualTo("student.01");
        assertThat(u.getEmail()).isNull();

        assertThatThrownBy(() -> userService.create("X", "student.01", null, "parol123", Role.STUDENT))
            .isInstanceOf(BadRequestException.class).hasMessageContaining("band");
        assertThatThrownBy(() -> userService.create("X", "ab", null, "parol123", Role.STUDENT))
            .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> userService.create("X", "ali valiyev", null, "parol123", Role.STUDENT))
            .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> userService.create("X", "short", null, "123", Role.STUDENT))
            .isInstanceOf(BadRequestException.class).hasMessageContaining("Parol");
    }
}
