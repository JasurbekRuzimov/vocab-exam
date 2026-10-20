package uz.vocabexam;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import uz.vocabexam.entity.*;
import uz.vocabexam.repository.*;
import uz.vocabexam.security.CustomUserDetails;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Yuqoridagi qo'ng'iroqcha ochadigan bildirishnomalar sahifasi. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:notifdb;MODE=PostgreSQL;NON_KEYWORDS=GROUPS,USER",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false"
})
class NotificationPageTests {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired NotificationRepository notifications;

    User student, other;
    Notification result, external;

    @BeforeEach
    void setUp() {
        student = save("Ali", "ali", Role.STUDENT);
        other   = save("Vali", "vali", Role.STUDENT);
        result = notifications.save(Notification.builder().user(student).type(NotificationType.RESULT_READY)
            .message("Natijangiz tayyor: <b>8/10</b>").link("/student/results").build());
        external = notifications.save(Notification.builder().user(student).type(NotificationType.EXAM_ASSIGNED)
            .message("Yangi imtihon").link("//evil.example/phish").build());
    }

    @Test
    void pageListsOwnNotificationsEscaped() throws Exception {
        String page = mvc.perform(get("/notifications").with(as(student))).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertThat(page).contains("Natijangiz tayyor: &lt;b&gt;8/10&lt;/b&gt;", "Yangi imtihon", "Hammasini o'qildi");
        assertThat(mvc.perform(get("/notifications").with(as(other))).andReturn().getResponse().getContentAsString())
            .doesNotContain("Natijangiz tayyor").contains("Hozircha bildirishnoma yo");
    }

    @Test
    void openingMarksReadAndFollowsInternalLinkOnly() throws Exception {
        mvc.perform(get("/notifications/" + result.getId() + "/open").with(as(student)))
            .andExpect(redirectedUrl("/student/results"));
        assertThat(notifications.findById(result.getId()).orElseThrow().isRead()).isTrue();

        mvc.perform(get("/notifications/" + external.getId() + "/open").with(as(student)))
            .andExpect(redirectedUrl("/notifications"));
    }

    @Test
    void cannotOpenSomeoneElsesNotification() throws Exception {
        mvc.perform(get("/notifications/" + result.getId() + "/open").with(as(other)))
            .andExpect(redirectedUrl("/notifications"));
        assertThat(notifications.findById(result.getId()).orElseThrow().isRead()).isFalse();
    }

    @Test
    void markAllRead() throws Exception {
        mvc.perform(post("/notifications/read-all").with(as(student)).with(csrf()))
            .andExpect(redirectedUrl("/notifications"));
        assertThat(notifications.countByUserIdAndReadFalse(student.getId())).isZero();
    }

    @Test
    void oldExamLinkRedirectsToExamPage() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(get("/student/exams/" + id).with(as(student)))
            .andExpect(redirectedUrl("/student/exams/" + id + "/take"));
    }

    @Test
    void unknownPathIsNotFound() throws Exception {
        mvc.perform(get("/no-such-page").with(as(student))).andExpect(status().isNotFound());
    }

    private User save(String name, String username, Role role) {
        return users.save(User.builder().fullName(name).username(username).passwordHash("x").role(role).active(true).build());
    }

    private static RequestPostProcessor as(User u) {
        return user(new CustomUserDetails(u));
    }
}
