package uz.vocabexam;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import uz.vocabexam.entity.*;
import uz.vocabexam.repository.*;
import uz.vocabexam.security.CustomUserDetails;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Ko'p markazli tizim: har markaz faqat o'z ma'lumotini ko'radi va boshqaradi,
 * platforma admini markazlarni yaratadi, o'chirilgan markaz foydalanuvchilari kira olmaydi.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:centerdb;MODE=PostgreSQL;NON_KEYWORDS=GROUPS,USER",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false"
})
class MultiCenterTests {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder encoder;
    @Autowired CenterRepository centers;
    @Autowired UserRepository users;
    @Autowired GroupRepository groups;
    @Autowired WordSetRepository wordSets;
    @Autowired WordRepository words;
    @Autowired ExamRepository exams;

    Center a, b;
    User admin, directorA, teacherA, studentA, teacherB, studentB;
    Group groupA, groupB;

    @BeforeEach
    void setUp() {
        a = centers.save(Center.builder().name("Ilm Ziyo").slug("ilmziyo").primaryColor("#AA3300").build());
        b = centers.save(Center.builder().name("Bilim Plus").slug("bilim").build());
        admin     = save("Platforma", "root", Role.SUPER_ADMIN, null);
        directorA = save("Direktor A", "dir.a", Role.CENTER_ADMIN, a);
        teacherA  = save("Teacher A", "teacher.a", Role.TEACHER, a);
        studentA  = save("Student A", "student.a", Role.STUDENT, a);
        teacherB  = save("Teacher B", "teacher.b", Role.TEACHER, b);
        studentB  = save("Student B", "student.b", Role.STUDENT, b);
        groupA = groups.save(Group.builder().name("A-1").teacher(teacherA).build());
        groupB = groups.save(Group.builder().name("B-1").teacher(teacherB).build());
    }

    // ── Foydalanuvchilarni boshqarish ────────────────────────────────────────

    @Test
    void directorSeesOnlyOwnCenterUsers() throws Exception {
        String page = mvc.perform(get("/admin/users").with(as(directorA))).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertThat(page).contains("Student A", "Teacher A").doesNotContain("Student B", "Platforma");

        String api = mvc.perform(get("/api/users?size=100").with(as(directorA))).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertThat(api).contains("student.a").doesNotContain("student.b", "root");
    }

    @Test
    void directorCannotTouchOtherCenterUsers() throws Exception {
        mvc.perform(get("/admin/users/" + studentB.getId() + "/edit").with(as(directorA))).andExpect(status().isForbidden());
        mvc.perform(patch("/api/users/" + studentB.getId() + "/block").with(as(directorA)).with(csrf()))
            .andExpect(status().isForbidden());
        mvc.perform(patch("/api/users/" + admin.getId() + "/block").with(as(directorA)).with(csrf()))
            .andExpect(status().isForbidden());
        mvc.perform(patch("/api/users/" + studentA.getId() + "/block").with(as(directorA)).with(csrf()))
            .andExpect(status().isOk());
    }

    @Test
    void directorCreatesUsersOnlyInOwnCenter() throws Exception {
        createUser(directorA, "new.t1", "TEACHER", b.getId()).andExpect(status().isForbidden());
        createUser(directorA, "new.root", "SUPER_ADMIN", null).andExpect(status().isForbidden());

        String body = createUser(directorA, "new.t2", "TEACHER", null).andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(body).get("centerName").asText()).isEqualTo("Ilm Ziyo");
    }

    @Test
    void platformAdminPicksCenterWhenThereAreSeveral() throws Exception {
        createUser(admin, "new.s1", "STUDENT", null).andExpect(status().isBadRequest());
        String body = createUser(admin, "new.s2", "STUDENT", b.getId()).andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(body).get("centerName").asText()).isEqualTo("Bilim Plus");
    }

    // ── Guruhlar va imtihon tayinlash ────────────────────────────────────────

    @Test
    void groupsDoNotMixCenters() throws Exception {
        addStudent(directorA, groupA, studentB).andExpect(status().isBadRequest());
        addStudent(directorA, groupA, studentA).andExpect(status().isOk());
        addStudent(directorA, groupB, studentB).andExpect(status().isForbidden());

        String list = mvc.perform(get("/admin/groups").with(as(directorA))).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertThat(list).contains("A-1").doesNotContain("B-1");
        mvc.perform(get("/admin/groups/" + groupB.getId()).with(as(directorA))).andExpect(status().isForbidden());

        // Boshqa markaz o'qituvchisiga guruh ochib bo'lmaydi
        mvc.perform(post("/api/groups").with(as(directorA)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"X\",\"teacherId\":\"" + teacherB.getId() + "\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void teacherSeesAndAssignsOnlyOwnCenterStudents() throws Exception {
        String list = mvc.perform(get("/api/students").with(as(teacherA))).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertThat(list).contains("Student A").doesNotContain("Student B");

        WordSet ws = wordSets.save(WordSet.builder().name("U1").teacher(teacherA).build());
        for (String w : List.of("apple", "river", "brave", "window", "garden"))
            words.save(Word.builder().wordSet(ws).text(w).build());
        Exam exam = exams.save(Exam.builder().title("Quiz").type(ExamType.ONLINE).wordSet(ws)
            .teacher(teacherA).questionCount(5).status(ExamStatus.GENERATED).build());

        assign(exam, studentB).andExpect(status().isForbidden());
        assign(exam, studentA).andExpect(status().isCreated());
    }

    // ── Markazlar (platforma admini) ─────────────────────────────────────────

    @Test
    void deactivatedCenterCannotLogIn() throws Exception {
        mvc.perform(post("/admin/centers/" + a.getId() + "/active").param("active", "false")
                .with(as(admin)).with(csrf()))
            .andExpect(status().is3xxRedirection());
        mvc.perform(formLogin("/login").user("username", "student.a").password("parol123"))
            .andExpect(unauthenticated()).andExpect(redirectedUrl("/login?disabled=true"));
        mvc.perform(get("/login?disabled=true")).andExpect(content().string(org.hamcrest.Matchers.containsString("markazi faol emas")));
        mvc.perform(formLogin("/login").user("username", "student.b").password("parol123")).andExpect(authenticated());
    }

    @Test
    void platformAdminCreatesCenterWithDirector() throws Exception {
        mvc.perform(post("/admin/centers").with(as(admin)).with(csrf())
                .param("name", "Yangi Markaz").param("slug", "yangi").param("primaryColor", "#123456")
                .param("directorName", "Direktor Y").param("directorUsername", "yangi.admin").param("directorPassword", "parol123"))
            .andExpect(status().is3xxRedirection());
        User director = users.findByUsername("yangi.admin").orElseThrow();
        assertThat(director.getRole()).isEqualTo(Role.CENTER_ADMIN);
        assertThat(director.getCenterName()).isEqualTo("Yangi Markaz");

        // Noto'g'ri rang: forma xato bilan qaytadi, markaz yaratilmaydi
        String page = mvc.perform(post("/admin/centers").with(as(admin)).with(csrf())
                .param("name", "Yomon").param("slug", "yomon").param("primaryColor", "red;}")
                .param("directorUsername", "yomon.admin").param("directorPassword", "parol123"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(page).contains("#RRGGBB");
        assertThat(centers.existsBySlug("yomon")).isFalse();

        // Band qisqa nom
        mvc.perform(post("/admin/centers").with(as(admin)).with(csrf())
                .param("name", "Takror").param("slug", "ilmziyo").param("directorUsername", "t.admin").param("directorPassword", "parol123"))
            .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("band")));

        mvc.perform(get("/admin/centers").with(as(directorA))).andExpect(status().isForbidden());
        String list = mvc.perform(get("/admin/centers").with(as(admin))).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertThat(list).contains("Ilm Ziyo", "Bilim Plus", "Yangi Markaz");
    }

    @Test
    void centerBrandingIsShownToItsUsers() throws Exception {
        String page = mvc.perform(get("/").with(as(teacherA))).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertThat(page).contains("Ilm Ziyo", "--brand:#AA3300");
        assertThat(mvc.perform(get("/").with(as(studentB))).andReturn().getResponse().getContentAsString())
            .contains("Bilim Plus").doesNotContain("Ilm Ziyo");
    }

    @Test
    void directorDashboardShowsTeacherStats() throws Exception {
        String page = mvc.perform(get("/").with(as(directorA))).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertThat(page).contains("O'qituvchilar", "Teacher A").doesNotContain("Teacher B");
    }

    // ── yordamchilar ─────────────────────────────────────────────────────────

    private User save(String name, String username, Role role, Center center) {
        return users.save(User.builder().fullName(name).username(username)
            .passwordHash(encoder.encode("parol123")).role(role).center(center).active(true).build());
    }

    private static RequestPostProcessor as(User u) {
        return user(new CustomUserDetails(u));
    }

    private org.springframework.test.web.servlet.ResultActions createUser(User by, String username, String role, UUID centerId) throws Exception {
        Map<String, String> body = new HashMap<>();
        body.put("fullName", "New " + username);
        body.put("username", username);
        body.put("password", "parol123");
        body.put("role", role);
        if (centerId != null) body.put("centerId", centerId.toString());
        return mvc.perform(post("/api/users").with(as(by)).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    private org.springframework.test.web.servlet.ResultActions addStudent(User by, Group g, User s) throws Exception {
        return mvc.perform(post("/api/groups/" + g.getId() + "/students").with(as(by)).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"studentIds\":[\"" + s.getId() + "\"]}"));
    }

    private org.springframework.test.web.servlet.ResultActions assign(Exam exam, User s) throws Exception {
        return mvc.perform(post("/api/assignments").with(as(teacherA)).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"examId\":\"" + exam.getId() + "\",\"targetType\":\"STUDENT\",\"targetId\":\"" + s.getId() + "\"}"));
    }
}
