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
import uz.vocabexam.exception.BadRequestException;
import uz.vocabexam.repository.*;
import uz.vocabexam.security.CustomUserDetails;
import uz.vocabexam.service.ExamService;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test / tarjima savollari: savol yaratish, avtomatik baholash va darhol natija.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:autodb;MODE=PostgreSQL;NON_KEYWORDS=GROUPS,USER",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false"
})
class AutoGradedExamFlowTests {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired WordSetRepository wordSets;
    @Autowired WordRepository words;
    @Autowired ExamRepository exams;
    @Autowired ExamAssignmentRepository assignments;
    @Autowired ExamResultRepository results;
    @Autowired ExamService examService;

    static final Map<String, String> CARDS = new LinkedHashMap<>();
    static {
        CARDS.put("apple", "olma");
        CARDS.put("river", "daryo");
        CARDS.put("brave", "jasur");
        CARDS.put("window", "deraza");
        CARDS.put("garden", "bog'");
        CARDS.put("beautiful", "chiroyli, go'zal");
        CARDS.put("travel", "sayohat qilmoq");
        CARDS.put("honest", "halol");
    }

    User teacher, student;
    WordSet ws;
    Map<UUID, Word> byId = new HashMap<>();

    @BeforeEach
    void setUp() {
        teacher = users.save(User.builder().fullName("T").username("teacher").passwordHash("x")
            .role(Role.TEACHER).active(true).build());
        student = users.save(User.builder().fullName("S").username("student").passwordHash("x")
            .role(Role.STUDENT).active(true).build());
        ws = wordSets.save(WordSet.builder().name("Unit 2").teacher(teacher).build());
        CARDS.forEach((en, uz) -> {
            Word w = words.save(Word.builder().wordSet(ws).text(en).translation(uz).build());
            byId.put(w.getId(), w);
        });
    }

    @Test
    void questionsMixTypesAndDoNotLeakAnswers() throws Exception {
        ExamAssignment a = assign(List.of(QuestionType.MULTIPLE_CHOICE, QuestionType.TRANSLATE_UZ_EN), 6);
        JsonNode start = start(a);
        Set<String> types = new HashSet<>();
        for (JsonNode q : start.get("words")) {
            Word w = byId.get(UUID.fromString(q.get("id").asText()));
            types.add(q.get("type").asText());
            assertThat(q.has("expected")).isFalse();
            if (q.get("type").asText().equals("MULTIPLE_CHOICE")) {
                List<String> opts = new ArrayList<>();
                q.get("options").forEach(o -> opts.add(o.asText()));
                assertThat(opts).hasSize(4).doesNotHaveDuplicates().contains(w.getTranslation());
                assertThat(q.get("text").asText()).isEqualTo(w.getText());
            } else {
                // UZ → EN: inglizcha so'z ko'rsatilmaydi
                assertThat(q.get("text").asText()).isEqualTo(w.getTranslation());
            }
        }
        assertThat(types).containsExactlyInAnyOrder("MULTIPLE_CHOICE", "TRANSLATE_UZ_EN");
        assertThat(start(a).toString()).isEqualTo(start.toString());   // barqaror
    }

    @Test
    void allAutoGradedExamGetsResultImmediately() throws Exception {
        ExamAssignment a = assign(List.of(QuestionType.MULTIPLE_CHOICE, QuestionType.TRANSLATE_EN_UZ), 6);
        JsonNode qs = start(a).get("words");
        int i = 0;
        for (JsonNode q : qs) {
            Word w = byId.get(UUID.fromString(q.get("id").asText()));
            String answer = switch (q.get("type").asText()) {
                case "MULTIPLE_CHOICE" -> w.getTranslation();
                default -> i == 0 ? "noto'g'ri" : w.getTranslation().split(",")[0].toUpperCase();
            };
            if (!q.get("type").asText().equals("MULTIPLE_CHOICE")) i++;
            save(a, w, answer).andExpect(status().isOk());
        }
        mvc.perform(post("/api/online-exams/" + a.getId() + "/submit").with(as(student)).with(csrf()))
            .andExpect(status().isOk());

        ExamResult r = results.findByAssignmentIdAndStudentId(a.getId(), student.getId()).orElseThrow();
        assertThat(r.getMaxScore()).isEqualTo(60);
        assertThat(r.getTotalScore()).isEqualTo(50);   // bitta tarjima ataylab xato

        String page = mvc.perform(get("/student/results/" + r.getId()).with(as(student)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(page).contains("Savollar bo'yicha").contains("To'g'ri javob");
    }

    @Test
    void examWithSentencesWaitsForTeacher() throws Exception {
        ExamAssignment a = assign(List.of(QuestionType.SENTENCE, QuestionType.TRANSLATE_EN_UZ), 6);
        mvc.perform(post("/api/online-exams/" + a.getId() + "/submit").with(as(student)).with(csrf()))
            .andExpect(status().isOk());
        assertThat(results.findByAssignmentIdAndStudentId(a.getId(), student.getId())).isEmpty();

        String page = mvc.perform(get("/teacher/exams/" + a.getId() + "/submissions/" + student.getId() + "/check")
                .with(as(teacher)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(page).contains("Noto&#39;g&#39;ri").contains("value=\"60\"");
    }

    @Test
    void choiceThatIsNotAnOptionIsRejected() throws Exception {
        ExamAssignment a = assign(List.of(QuestionType.MULTIPLE_CHOICE), 5);
        JsonNode q = start(a).get("words").get(0);
        save(a, byId.get(UUID.fromString(q.get("id").asText())), "hech qaysi").andExpect(status().isBadRequest());
    }

    @Test
    void takePageRendersAllQuestionTypes() throws Exception {
        ExamAssignment a = assign(List.of(QuestionType.MULTIPLE_CHOICE, QuestionType.TRANSLATE_EN_UZ,
            QuestionType.TRANSLATE_UZ_EN, QuestionType.SENTENCE), 8);
        String page = mvc.perform(get("/student/exams/" + a.getId() + "/take").with(as(student)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(page).contains("btn-check").contains("Tarjimasini yozing").contains("gap tuzing");
        // O'quvchi menyusida admin/o'qituvchi bo'limlari ko'rinmaydi
        assertThat(page).doesNotContain("/admin/users").doesNotContain("/teacher/word-sets").contains("/student/results");
    }

    @Test
    void autoGradedTypesRequireTranslations() {
        words.save(Word.builder().wordSet(ws).text("silent").build());
        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
            new CustomUserDetails(teacher), null, new CustomUserDetails(teacher).getAuthorities());
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
        try {
            assertThatThrownBy(() -> examService.createAndGenerate("Q", ExamType.ONLINE, ws.getId(), teacher.getId(),
                    5, 1, List.of(QuestionType.TRANSLATE_EN_UZ)))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("tarjima");
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    void examAndWordSetJsonDoNotRecurse() throws Exception {
        String body = json.writeValueAsString(Map.of("title", "Quiz", "type", "ONLINE", "wordSetId", ws.getId().toString(),
            "questionCount", 5, "questionTypes", List.of("MULTIPLE_CHOICE")));
        mvc.perform(post("/api/exams").with(as(teacher)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated());
        mvc.perform(get("/api/word-sets").with(as(teacher))).andExpect(status().isOk());
        mvc.perform(get("/api/word-sets/" + ws.getId() + "/words").with(as(teacher))).andExpect(status().isOk());
    }

    @Test
    void dashboardRendersForTeacherAndStudent() throws Exception {
        mvc.perform(get("/").with(as(teacher))).andExpect(status().isOk());
        mvc.perform(get("/").with(as(student))).andExpect(status().isOk());
    }

    // ── yordamchilar ─────────────────────────────────────────────────────────

    private ExamAssignment assign(List<QuestionType> types, int count) {
        Exam exam = Exam.builder().title("Quiz").type(ExamType.ONLINE).wordSet(ws).teacher(teacher)
            .questionCount(count).status(ExamStatus.GENERATED).build();
        exam.setQuestionTypeList(types);
        exams.save(exam);
        return assignments.save(ExamAssignment.builder().exam(exam).targetType(TargetType.STUDENT).student(student).build());
    }

    private JsonNode start(ExamAssignment a) throws Exception {
        return json.readTree(mvc.perform(get("/api/online-exams/" + a.getId() + "/start").with(as(student)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private org.springframework.test.web.servlet.ResultActions save(ExamAssignment a, Word w, String text) throws Exception {
        return mvc.perform(put("/api/online-exams/" + a.getId() + "/answers/" + w.getId())
            .with(as(student)).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("answerText", text))));
    }

    private static RequestPostProcessor as(User u) {
        return user(new CustomUserDetails(u));
    }
}
