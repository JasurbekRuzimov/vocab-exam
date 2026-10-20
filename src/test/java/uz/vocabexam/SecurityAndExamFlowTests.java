package uz.vocabexam;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import uz.vocabexam.entity.*;
import uz.vocabexam.repository.*;
import uz.vocabexam.security.CustomUserDetails;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Egalik tekshiruvlari, CSRF va onlayn imtihon oqimi uchun integratsion testlar (H2 da).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:flowdb;MODE=PostgreSQL;NON_KEYWORDS=GROUPS,USER",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false"
})
class SecurityAndExamFlowTests {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired WordSetRepository wordSets;
    @Autowired WordRepository words;
    @Autowired ExamRepository exams;
    @Autowired ExamAssignmentRepository assignments;
    @Autowired ExamResultRepository results;

    User teacherA, teacherB, student1, student2;
    Exam exam;
    ExamAssignment assignment;
    List<Word> allWords = new ArrayList<>();

    @BeforeEach
    void setUp() {
        teacherA = saveUser("Teacher A", Role.TEACHER);
        teacherB = saveUser("Teacher B", Role.TEACHER);
        student1 = saveUser("Student 1", Role.STUDENT);
        student2 = saveUser("Student 2", Role.STUDENT);

        WordSet ws = wordSets.save(WordSet.builder().name("Unit 1").teacher(teacherA).build());
        for (String w : List.of("apple", "river", "brave", "window", "garden", "silent", "travel", "honest"))
            allWords.add(words.save(Word.builder().wordSet(ws).text(w).build()));

        exam = exams.save(Exam.builder().title("Quiz").type(ExamType.ONLINE).wordSet(ws)
            .teacher(teacherA).questionCount(5).status(ExamStatus.GENERATED).build());
        assignment = assignments.save(ExamAssignment.builder().exam(exam)
            .targetType(TargetType.STUDENT).student(student1).build());
    }

    // ── Egalik tekshiruvlari ─────────────────────────────────────────────────

    @Test
    void otherTeacherCannotSeeExamOrSubmissions() throws Exception {
        mvc.perform(get("/api/exams/" + exam.getId()).with(as(teacherB))).andExpect(status().isForbidden());
        mvc.perform(get("/api/online-exams/" + assignment.getId() + "/submissions").with(as(teacherB)))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/exams/" + exam.getId()).with(as(teacherA))).andExpect(status().isOk());
    }

    @Test
    void otherTeacherCannotGradeStudent() throws Exception {
        submitAsStudent1();
        String body = "{\"totalScore\":80,\"maxScore\":100}";
        mvc.perform(post(resultUrl(student1)).with(as(teacherB)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isForbidden());
        mvc.perform(post(resultUrl(student1)).with(as(teacherA)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated());
    }

    @Test
    void scoreOutOfRangeIsRejected() throws Exception {
        submitAsStudent1();
        mvc.perform(post(resultUrl(student1)).with(as(teacherA)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"totalScore\":150,\"maxScore\":100}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void cannotGradeStudentWhoDidNotSubmit() throws Exception {
        mvc.perform(post(resultUrl(student1)).with(as(teacherA)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"totalScore\":50,\"maxScore\":100}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void studentCannotOpenAnotherStudentsResult() throws Exception {
        ExamResult r = results.save(ExamResult.builder().assignment(assignment).student(student1)
            .totalScore(70).maxScore(100).build());
        mvc.perform(get("/student/results/" + r.getId()).with(as(student2))).andExpect(status().isForbidden());
        mvc.perform(get("/student/results/" + r.getId()).with(as(student1))).andExpect(status().isOk());
    }

    @Test
    void studentCannotTakeExamAssignedToSomeoneElse() throws Exception {
        mvc.perform(get("/api/online-exams/" + assignment.getId() + "/start").with(as(student2)))
            .andExpect(status().isForbidden());
    }

    @Test
    void passwordHashIsNeverSerialized() throws Exception {
        submitAsStudent1();
        String body = mvc.perform(get("/api/online-exams/" + assignment.getId() + "/submissions").with(as(teacherA)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(body).contains(student1.getUsername()).doesNotContain("passwordHash").doesNotContain("$2a$");
    }

    // ── CSRF ─────────────────────────────────────────────────────────────────

    @Test
    void apiWriteWithoutCsrfTokenIsRejected() throws Exception {
        mvc.perform(post("/api/online-exams/" + assignment.getId() + "/submit").with(as(student1)))
            .andExpect(status().isForbidden());
    }

    // ── Onlayn imtihon oqimi ─────────────────────────────────────────────────

    @Test
    void studentGetsQuestionCountWordsInStableOrder() throws Exception {
        List<String> first = startWordIds(student1);
        assertThat(first).hasSize(5).doesNotHaveDuplicates();
        assertThat(startWordIds(student1)).isEqualTo(first);
    }

    @Test
    void answerForWordOutsideStudentsQuestionsIsRejected() throws Exception {
        Set<String> mine = new HashSet<>(startWordIds(student1));
        Word foreign = allWords.stream().filter(w -> !mine.contains(w.getId().toString())).findFirst().orElseThrow();
        mvc.perform(put("/api/online-exams/" + assignment.getId() + "/answers/" + foreign.getId())
                .with(as(student1)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"answerText\":\"hi\"}"))
            .andExpect(status().isNotFound());
    }

    @Test
    void emptySubmissionIsRecordedAndCannotBeRepeated() throws Exception {
        submitAsStudent1();
        String body = mvc.perform(get("/api/online-exams/" + assignment.getId() + "/submissions/" + student1.getId())
                .with(as(teacherA)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(body)).hasSize(5);

        mvc.perform(post("/api/online-exams/" + assignment.getId() + "/submit").with(as(student1)).with(csrf()))
            .andExpect(status().isBadRequest());
        mvc.perform(put("/api/online-exams/" + assignment.getId() + "/answers/" + allWords.get(0).getId())
                .with(as(student1)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"answerText\":\"late\"}"))
            .andExpect(status().is4xxClientError());
    }

    @Test
    void takePageRendersBeforeAndAfterSubmit() throws Exception {
        String url = "/student/exams/" + assignment.getId() + "/take";
        String before = mvc.perform(get(url).with(as(student1))).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertThat(before).contains("submitBtn");
        submitAsStudent1();
        String after = mvc.perform(get(url).with(as(student1))).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertThat(after).doesNotContain("submitBtn").contains("topshirgansiz");
    }

    @Test
    void submitAfterDeadlineIsRejected() throws Exception {
        assignment.setDeadline(java.time.LocalDateTime.now().minusMinutes(1));
        assignments.save(assignment);
        mvc.perform(post("/api/online-exams/" + assignment.getId() + "/submit").with(as(student1)).with(csrf()))
            .andExpect(status().isBadRequest());
    }

    // ── yordamchilar ─────────────────────────────────────────────────────────

    private User saveUser(String name, Role role) {
        return users.save(User.builder().fullName(name)
            .username(name.toLowerCase().replace(" ", ""))
            .email(name.toLowerCase().replace(' ', '.') + "@test.uz")
            .passwordHash("$2a$12$abcdefghijklmnopqrstuuJ5W8b2rM8Qz0yQfQyQ5o0e1Gq4bZ9m2")
            .role(role).active(true).build());
    }

    private static RequestPostProcessor as(User u) {
        return user(new CustomUserDetails(u));
    }

    private String resultUrl(User student) {
        return "/api/online-exams/" + assignment.getId() + "/submissions/" + student.getId() + "/result";
    }

    private void submitAsStudent1() throws Exception {
        mvc.perform(post("/api/online-exams/" + assignment.getId() + "/submit").with(as(student1)).with(csrf()))
            .andExpect(status().isOk());
    }

    private List<String> startWordIds(User student) throws Exception {
        String body = mvc.perform(get("/api/online-exams/" + assignment.getId() + "/start").with(as(student)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<String> ids = new ArrayList<>();
        for (JsonNode w : json.readTree(body).get("words")) ids.add(w.get("id").asText());
        return ids;
    }
}
