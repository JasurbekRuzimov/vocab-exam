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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import uz.vocabexam.entity.*;
import uz.vocabexam.repository.*;
import uz.vocabexam.security.CustomUserDetails;
import uz.vocabexam.service.ExamMonitorService;
import uz.vocabexam.service.ExamMonitorService.Status;

import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Jonli monitoring, xato turlari, ball o'zgarishlari tarixi va o'qituvchi bosh sahifasi.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:monitordb;MODE=PostgreSQL;NON_KEYWORDS=GROUPS,USER",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false"
})
class MonitoringAndTransparencyTests {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired GroupRepository groups;
    @Autowired WordSetRepository wordSets;
    @Autowired WordRepository words;
    @Autowired ExamRepository exams;
    @Autowired ExamAssignmentRepository assignments;
    @Autowired ExamAttemptRepository attempts;
    @Autowired ExamMonitorService monitorService;

    User teacher, otherTeacher, ali, vali, gani, outsider;
    ExamAssignment assignment;

    @BeforeEach
    void setUp() {
        teacher      = saveUser("Teacher", "teacher", Role.TEACHER);
        otherTeacher = saveUser("Other", "other", Role.TEACHER);
        ali  = saveUser("Ali Valiyev", "ali", Role.STUDENT);
        vali = saveUser("Vali Aliyev", "vali", Role.STUDENT);
        gani = saveUser("G'ani Karimov", "gani", Role.STUDENT);
        outsider = saveUser("Begona", "begona", Role.STUDENT);

        Group g = groups.save(Group.builder().name("IELTS-1").teacher(teacher)
            .students(new HashSet<>(List.of(ali, vali, gani))).build());
        WordSet ws = wordSets.save(WordSet.builder().name("Unit 3").teacher(teacher).build());
        Map<String, String> cards = Map.of("apple", "olma", "river", "daryo", "brave", "jasur",
            "window", "deraza", "garden", "bog'", "honest", "halol");
        cards.forEach((en, uz) -> words.save(Word.builder().wordSet(ws).text(en).translation(uz).build()));

        Exam exam = Exam.builder().title("Unit 3 quiz").type(ExamType.ONLINE).wordSet(ws)
            .teacher(teacher).questionCount(4).status(ExamStatus.GENERATED).build();
        exam.setQuestionTypeList(List.of(QuestionType.SENTENCE, QuestionType.TRANSLATE_EN_UZ));
        exams.save(exam);
        assignment = assignments.save(ExamAssignment.builder().exam(exam)
            .targetType(TargetType.GROUP).group(g).build());
    }

    // ── Jonli monitoring ─────────────────────────────────────────────────────

    @Test
    void monitorShowsEachStudentsStatusProgressAndFocusLoss() throws Exception {
        // Ali sahifani ochdi, bitta javob yozdi va 2 marta boshqa oynaga o'tdi
        openExam(ali);
        JsonNode aliQ = firstOfType(ali, "TRANSLATE_EN_UZ");
        saveAnswer(ali, aliQ.get("id").asText(), "olma");
        activity(ali, "focus-lost");
        activity(ali, "focus-lost");
        activity(ali, "ping");
        // Vali topshirdi (gap savollari bor, shuning uchun tekshirish kutilmoqda)
        submit(vali);

        Map<String, ExamMonitorService.StudentRow> rows = monitorService.getMonitor(assignments.findById(assignment.getId()).orElseThrow())
            .rows().stream().collect(Collectors.toMap(r -> r.student().getUsername(), r -> r));
        assertThat(rows.get("ali").status()).isEqualTo(Status.IN_PROGRESS);
        assertThat(rows.get("ali").answered()).isEqualTo(1);
        assertThat(rows.get("ali").total()).isEqualTo(4);
        assertThat(rows.get("ali").focusLost()).isEqualTo(2);
        assertThat(rows.get("ali").online()).isTrue();
        assertThat(rows.get("vali").status()).isEqualTo(Status.SUBMITTED);
        assertThat(rows.get("gani").status()).isEqualTo(Status.NOT_STARTED);

        String page = mvc.perform(get(monitorUrl()).with(as(teacher))).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertThat(page).contains("Ali Valiyev", "Jarayonda", "2 marta", "Tekshirish kutilmoqda", "Boshlamagan", "1/4");
    }

    @Test
    void liveFragmentReturnsOnlyRosterAndOnlyForOwner() throws Exception {
        String fragment = mvc.perform(get(monitorUrl() + "/live").with(as(teacher))).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertThat(fragment).contains("id=\"roster\"", "Vali Aliyev").doesNotContain("<html", "Natijalar taqsimoti");

        mvc.perform(get(monitorUrl() + "/live").with(as(otherTeacher))).andExpect(status().isForbidden());
        mvc.perform(get(monitorUrl()).with(as(otherTeacher))).andExpect(status().isForbidden());
    }

    @Test
    void activityIsIgnoredAfterSubmitAndRejectedForOutsiders() throws Exception {
        openExam(ali);
        activity(ali, "focus-lost");
        submit(ali);
        activity(ali, "focus-lost");
        assertThat(attempts.findByAssignmentIdAndStudentId(assignment.getId(), ali.getId()).orElseThrow()
            .getFocusLostCount()).isEqualTo(1);

        mvc.perform(post(activityUrl()).with(as(outsider)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"event\":\"ping\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void hardestWordsAndAverageAppearAfterGrading() throws Exception {
        JsonNode q = firstOfType(ali, "TRANSLATE_EN_UZ");
        saveAnswer(ali, q.get("id").asText(), "noto'g'ri");
        submit(ali);
        saveResult(ali, 10, 40, null).andExpect(status().isCreated());

        ExamMonitorService.Monitor m = monitorService.getMonitor(assignments.findById(assignment.getId()).orElseThrow());
        assertThat(m.stats().graded()).isEqualTo(1);
        assertThat(m.stats().avgPercent()).isEqualTo(25);
        assertThat(m.stats().buckets()[0]).isEqualTo(1);
        assertThat(m.hardestWords()).extracting(ExamMonitorService.WordStat::percent).contains(0);
    }

    // ── Xato turlari va o'quvchiga ko'rinishi ────────────────────────────────

    @Test
    void errorCanBeMarkedByCategoryAndStudentSeesItHighlighted() throws Exception {
        JsonNode q = firstOfType(ali, "SENTENCE");
        saveAnswer(ali, q.get("id").asText(), "He go to school yesterday");
        submit(ali);
        UUID answerId = answerIdFor(ali, q.get("id").asText());

        // Faqat tur tanlangan, izoh bo'sh: izoh o'rniga tur nomi yoziladi
        String err = mvc.perform(post("/api/answers/" + answerId + "/errors").with(as(teacher)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"errorStart\":3,\"errorEnd\":5,\"category\":\"TENSE\",\"comment\":\"\",\"correctHint\":\"went\"}"))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(err).get("comment").asText()).isEqualTo("Zamon");

        mvc.perform(post("/api/answers/" + answerId + "/errors").with(as(teacher)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"errorStart\":0,\"errorEnd\":2,\"category\":\"NOPE\"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/answers/" + answerId + "/errors").with(as(teacher)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"errorStart\":0,\"errorEnd\":2}"))
            .andExpect(status().isBadRequest());

        String check = mvc.perform(get(monitorUrl() + "/" + ali.getId() + "/check").with(as(teacher)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(check).contains("Zamon", "«go»", "Imlo");

        String resultId = json.readTree(saveResult(ali, 25, 40, null).andReturn().getResponse().getContentAsString())
            .get("id").asText();
        String page = mvc.perform(get("/student/results/" + resultId).with(as(ali))).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertThat(page).contains("error-highlight", "Zamon", "went", ">go</span>");
    }

    @Test
    void segmentsSplitTextAroundErrorsAndSkipOverlaps() {
        OnlineAnswer a = OnlineAnswer.builder().answerText("He go to school").build();
        a.getErrors().add(AnswerError.builder().errorStart(3).errorEnd(5).comment("x").build());
        a.getErrors().add(AnswerError.builder().errorStart(4).errorEnd(8).comment("overlap").build());
        List<OnlineAnswer.Segment> segs = a.getSegments();
        assertThat(segs).extracting(OnlineAnswer.Segment::text).containsExactly("He ", "go", " to", " school");
        assertThat(segs.get(1).error().getComment()).isEqualTo("x");
        assertThat(segs.get(2).error().getComment()).isEqualTo("overlap");
        assertThat(segs.get(3).error()).isNull();
    }

    // ── Ball o'zgarishlari tarixi ────────────────────────────────────────────

    @Test
    void changingPublishedScoreNeedsReasonAndStudentSeesHistory() throws Exception {
        submit(ali);
        String resultId = json.readTree(saveResult(ali, 20, 40, null, "Yaxshi").andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString()).get("id").asText();

        saveResult(ali, 30, 40, null).andExpect(status().isBadRequest());
        saveResult(ali, 20, 40, null, "Izoh o'zgardi").andExpect(status().isCreated()); // ball o'zgarmadi: sabab shart emas
        saveResult(ali, 30, 40, "Sinonim qabul qilindi").andExpect(status().isCreated());

        String page = mvc.perform(get("/student/results/" + resultId).with(as(ali))).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertThat(page).contains("Ball o'zgarishlari", "20/40", "30/40", "Sinonim qabul qilindi", "Teacher");
    }

    // ── O'qituvchi bosh sahifasi ─────────────────────────────────────────────

    @Test
    void dashboardListsWorkWaitingForCheck() throws Exception {
        submit(vali);
        String before = mvc.perform(get("/").with(as(teacher))).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertThat(before).contains("Tekshirish kutilmoqda", "Vali Aliyev", "Unit 3 quiz");

        saveResult(vali, 30, 40, null).andExpect(status().isCreated());
        String after = mvc.perform(get("/").with(as(teacher))).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertThat(after).contains("Hamma ish tekshirilgan").doesNotContain("Vali Aliyev");

        // Boshqa o'qituvchiga ko'rinmaydi
        submit(ali);
        assertThat(mvc.perform(get("/").with(as(otherTeacher))).andReturn().getResponse().getContentAsString())
            .doesNotContain("Ali Valiyev");
    }

    // ── yordamchilar ─────────────────────────────────────────────────────────

    private User saveUser(String name, String username, Role role) {
        return users.save(User.builder().fullName(name).username(username).passwordHash("x")
            .role(role).active(true).build());
    }

    private static RequestPostProcessor as(User u) {
        return user(new CustomUserDetails(u));
    }

    private String monitorUrl() { return "/teacher/exams/" + assignment.getId() + "/submissions"; }
    private String activityUrl() { return "/api/online-exams/" + assignment.getId() + "/activity"; }

    private void openExam(User s) throws Exception {
        mvc.perform(get("/student/exams/" + assignment.getId() + "/take").with(as(s))).andExpect(status().isOk());
    }

    private void activity(User s, String event) throws Exception {
        mvc.perform(post(activityUrl()).with(as(s)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"event\":\"" + event + "\"}"))
            .andExpect(status().isNoContent());
    }

    private JsonNode firstOfType(User s, String type) throws Exception {
        String body = mvc.perform(get("/api/online-exams/" + assignment.getId() + "/start").with(as(s)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        for (JsonNode q : json.readTree(body).get("words"))
            if (q.get("type").asText().equals(type)) return q;
        throw new AssertionError("Savol turi topilmadi: " + type);
    }

    private void saveAnswer(User s, String wordId, String text) throws Exception {
        mvc.perform(put("/api/online-exams/" + assignment.getId() + "/answers/" + wordId).with(as(s)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("answerText", text))))
            .andExpect(status().isOk());
    }

    private void submit(User s) throws Exception {
        mvc.perform(post("/api/online-exams/" + assignment.getId() + "/submit").with(as(s)).with(csrf()))
            .andExpect(status().isOk());
    }

    private UUID answerIdFor(User s, String wordId) throws Exception {
        String body = mvc.perform(get("/api/online-exams/" + assignment.getId() + "/submissions/" + s.getId())
                .with(as(teacher))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        for (JsonNode a : json.readTree(body))
            if (a.get("word").get("id").asText().equals(wordId)) return UUID.fromString(a.get("id").asText());
        throw new AssertionError("Javob topilmadi");
    }

    private ResultActions saveResult(User s, int total, int max, String reason) throws Exception {
        return saveResult(s, total, max, reason, null);
    }

    private ResultActions saveResult(User s, int total, int max, String reason,
                                                                          String comment) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("totalScore", total);
        body.put("maxScore", max);
        body.put("comment", comment);
        body.put("changeReason", reason);
        return mvc.perform(post("/api/online-exams/" + assignment.getId() + "/submissions/" + s.getId() + "/result")
            .with(as(teacher)).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }
}
