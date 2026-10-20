package uz.vocabexam;

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
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tahrirlash va o'chirish: bog'liq ma'lumotlar tartib bilan tozalanadi,
 * tarix yo'qolishi mumkin bo'lgan joyda esa tushunarli xabar bilan to'xtatiladi.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:cruddb;MODE=PostgreSQL;NON_KEYWORDS=GROUPS,USER",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false"
})
class CrudTests {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired CenterRepository centers;
    @Autowired UserRepository users;
    @Autowired GroupRepository groups;
    @Autowired WordSetRepository wordSets;
    @Autowired WordRepository words;
    @Autowired ExamRepository exams;
    @Autowired ExamAssignmentRepository assignments;
    @Autowired OnlineAnswerRepository answers;
    @Autowired AnswerErrorRepository answerErrors;
    @Autowired ExamAttemptRepository attempts;
    @Autowired ExamResultRepository results;
    @Autowired ResultChangeRepository changes;
    @Autowired NotificationRepository notifications;

    Center center;
    User admin, director, teacher, student;
    Group group;
    WordSet wordSet;
    Word apple, river;
    Exam exam;
    ExamAssignment groupAssignment, studentAssignment;

    @BeforeEach
    void setUp() {
        center   = centers.save(Center.builder().name("Ilm Ziyo").slug("ilmziyo").build());
        admin    = save("Platforma", "root", Role.SUPER_ADMIN, null);
        director = save("Direktor", "direktor", Role.CENTER_ADMIN, center);
        teacher  = save("Teacher One", "teacher1", Role.TEACHER, center);
        student  = save("Ali Valiyev", "ali", Role.STUDENT, center);
        group = groups.save(Group.builder().name("IELTS-1").teacher(teacher)
            .students(new HashSet<>(List.of(student))).build());

        wordSet = wordSets.save(WordSet.builder().name("Unit 1").teacher(teacher).build());
        apple = words.save(Word.builder().wordSet(wordSet).text("apple").translation("olma").build());
        river = words.save(Word.builder().wordSet(wordSet).text("river").translation("daryo").build());

        exam = Exam.builder().title("Unit 1 quiz").type(ExamType.ONLINE).wordSet(wordSet)
            .teacher(teacher).questionCount(2).status(ExamStatus.GENERATED).build();
        exam.setQuestionTypeList(List.of(QuestionType.SENTENCE));
        exams.save(exam);
        groupAssignment = assignments.save(ExamAssignment.builder().exam(exam)
            .targetType(TargetType.GROUP).group(group).build());
        studentAssignment = assignments.save(ExamAssignment.builder().exam(exam)
            .targetType(TargetType.STUDENT).student(student).build());

        // O'quvchi ishlagan, o'qituvchi tekshirgan: javob, xato belgisi, urinish, natija va uning tarixi
        OnlineAnswer answer = answers.save(OnlineAnswer.builder().assignment(groupAssignment).student(student)
            .word(apple).answerText("I eat apple").submitted(true).score(5).build());
        answerErrors.save(AnswerError.builder().answer(answer).errorStart(6).errorEnd(11).comment("an apple").build());
        attempts.save(ExamAttempt.builder().assignment(groupAssignment).student(student).build());
        ExamResult result = results.save(ExamResult.builder().assignment(groupAssignment).student(student)
            .totalScore(5).maxScore(10).build());
        changes.save(ResultChange.builder().result(result).changedBy(teacher)
            .oldScore(4).oldMax(10).newScore(5).newMax(10).reason("qayta ko'rildi").build());
        notifications.save(Notification.builder().user(student).type(NotificationType.EXAM_ASSIGNED).message("yangi imtihon").build());
    }

    // ── Imtihon sahifasi ─────────────────────────────────────────────────────

    @Test
    void examPageListsGroupAndStudentAssignments() throws Exception {
        for (User viewer : List.of(teacher, admin)) {
            String page = mvc.perform(get("/teacher/exams/" + exam.getId()).with(as(viewer)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(page).contains("IELTS-1", "Ali Valiyev", "Faol");
        }
    }

    // ── Imtihon va topshiriq ─────────────────────────────────────────────────

    @Test
    void deletingExamRemovesItsAssignmentsAnswersAndResults() throws Exception {
        mvc.perform(delete("/api/exams/" + exam.getId()).with(as(teacher)).with(csrf()))
            .andExpect(status().isOk());
        assertThat(exams.findById(exam.getId())).isEmpty();
        assertThat(assignments.count()).isZero();
        assertThat(answers.count()).isZero();
        assertThat(answerErrors.count()).isZero();
        assertThat(attempts.count()).isZero();
        assertThat(results.count()).isZero();
        assertThat(changes.count()).isZero();
        // So'zlar to'plami joyida qoladi
        assertThat(wordSets.findById(wordSet.getId())).isPresent();
    }

    @Test
    void otherTeacherCannotDeleteExam() throws Exception {
        User other = save("Other", "other", Role.TEACHER, center);
        mvc.perform(delete("/api/exams/" + exam.getId()).with(as(other)).with(csrf()))
            .andExpect(status().isForbidden());
        assertThat(exams.findById(exam.getId())).isPresent();
    }

    @Test
    void examCanBeRenamed() throws Exception {
        mvc.perform(put("/api/exams/" + exam.getId()).with(as(teacher)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Unit 1 yakuniy\"}"))
            .andExpect(status().isOk());
        assertThat(exams.findById(exam.getId()).orElseThrow().getTitle()).isEqualTo("Unit 1 yakuniy");
    }

    @Test
    void assignmentCanBeCancelledAndDeleted() throws Exception {
        mvc.perform(patch("/api/assignments/" + studentAssignment.getId() + "/cancel").with(as(teacher)).with(csrf()))
            .andExpect(status().isOk());
        assertThat(assignments.findById(studentAssignment.getId()).orElseThrow().getStatus())
            .isEqualTo(AssignmentStatus.CANCELLED);

        mvc.perform(delete("/api/assignments/" + groupAssignment.getId()).with(as(teacher)).with(csrf()))
            .andExpect(status().isOk());
        assertThat(assignments.findById(groupAssignment.getId())).isEmpty();
        assertThat(answers.count()).isZero();
        assertThat(results.count()).isZero();
    }

    // ── So'zlar to'plami ─────────────────────────────────────────────────────

    @Test
    void wordSetUsedByExamIsProtected() throws Exception {
        mvc.perform(delete("/api/word-sets/" + wordSet.getId()).with(as(teacher)).with(csrf()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message", containsString("1 ta imtihon")));
        // O'quvchi javob bergan so'z o'chirilmaydi, ishlatilmagani o'chiriladi
        mvc.perform(delete("/api/word-sets/" + wordSet.getId() + "/words/" + apple.getId()).with(as(teacher)).with(csrf()))
            .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/word-sets/" + wordSet.getId() + "/words/" + river.getId()).with(as(teacher)).with(csrf()))
            .andExpect(status().isOk());
        assertThat(words.findById(river.getId())).isEmpty();
    }

    @Test
    void wordSetCanBeRenamedAndDeletedOnceFree() throws Exception {
        mvc.perform(put("/api/word-sets/" + wordSet.getId()).with(as(teacher)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Unit 1 (yangi)\"}"))
            .andExpect(status().isOk());
        assertThat(wordSets.findById(wordSet.getId()).orElseThrow().getName()).isEqualTo("Unit 1 (yangi)");

        mvc.perform(delete("/api/exams/" + exam.getId()).with(as(teacher)).with(csrf())).andExpect(status().isOk());
        mvc.perform(delete("/api/word-sets/" + wordSet.getId()).with(as(teacher)).with(csrf())).andExpect(status().isOk());
        assertThat(wordSets.findById(wordSet.getId())).isEmpty();
        assertThat(words.count()).isZero();
    }

    // ── Foydalanuvchilar ─────────────────────────────────────────────────────

    @Test
    void teacherWithGroupsAndExamsIsNotDeleted() throws Exception {
        mvc.perform(delete("/api/users/" + teacher.getId()).with(as(director)).with(csrf()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message", containsString("1 ta guruh va 1 ta imtihon")));
        assertThat(users.findById(teacher.getId())).isPresent();
    }

    @Test
    void deletingStudentRemovesTheirWorkAndMembership() throws Exception {
        mvc.perform(delete("/api/users/" + student.getId()).with(as(director)).with(csrf()))
            .andExpect(status().isOk());
        assertThat(users.findById(student.getId())).isEmpty();
        assertThat(answers.count()).isZero();
        assertThat(results.count()).isZero();
        assertThat(notifications.count()).isZero();
        // Shaxsiy topshiriq o'chadi, guruh topshirig'i va guruh qoladi
        assertThat(assignments.findById(studentAssignment.getId())).isEmpty();
        assertThat(assignments.findById(groupAssignment.getId())).isPresent();
        assertThat(groups.findById(group.getId()).orElseThrow().getStudents()).isEmpty();
    }

    @Test
    void freeTeacherIsDeletedWithWordSets() throws Exception {
        User fresh = save("Yangi", "yangi", Role.TEACHER, center);
        wordSets.save(WordSet.builder().name("Draft").teacher(fresh).build());
        mvc.perform(delete("/api/users/" + fresh.getId()).with(as(director)).with(csrf()))
            .andExpect(status().isOk());
        assertThat(users.findById(fresh.getId())).isEmpty();
        assertThat(wordSets.findByTeacherIdOrderByCreatedAtDesc(fresh.getId())).isEmpty();
    }

    @Test
    void userCannotDeleteThemselves() throws Exception {
        mvc.perform(delete("/api/users/" + director.getId()).with(as(director)).with(csrf()))
            .andExpect(status().isBadRequest());
        assertThat(users.findById(director.getId())).isPresent();
    }

    @Test
    void usersPageShowsRowActionsButNotForSelf() throws Exception {
        String page = mvc.perform(get("/admin/users").with(as(director))).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertThat(page).contains("/api/users/" + student.getId() + "/block", "/api/users/" + student.getId() + "/reset-password")
            .doesNotContain("/api/users/" + director.getId() + "/block");
    }

    // ── Guruh va markaz ──────────────────────────────────────────────────────

    @Test
    void deletingGroupKeepsAssignmentHistory() throws Exception {
        mvc.perform(delete("/api/groups/" + group.getId()).with(as(director)).with(csrf()))
            .andExpect(status().isOk());
        assertThat(groups.findById(group.getId())).isEmpty();
        ExamAssignment kept = assignments.findById(groupAssignment.getId()).orElseThrow();
        assertThat(kept.getGroup()).isNull();
        assertThat(results.count()).isEqualTo(1);
        assertThat(users.findById(student.getId())).isPresent();

        String page = mvc.perform(get("/teacher/exams/" + exam.getId()).with(as(teacher)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(page).contains("o'chirilgan");
    }

    @Test
    void groupPageOffersEditAndDelete() throws Exception {
        String page = mvc.perform(get("/admin/groups/" + group.getId()).with(as(director))).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertThat(page).contains("editGroupModal", "Teacher One");
    }

    @Test
    void onlyEmptyCenterCanBeDeleted() throws Exception {
        mvc.perform(post("/admin/centers/" + center.getId() + "/delete").with(as(admin)).with(csrf()))
            .andExpect(redirectedUrl("/admin/centers"))
            .andExpect(flash().attribute("error", containsString("foydalanuvchi bor")));
        assertThat(centers.findById(center.getId())).isPresent();

        Center empty = centers.save(Center.builder().name("Bo'sh").slug("bosh").build());
        mvc.perform(post("/admin/centers/" + empty.getId() + "/delete").with(as(admin)).with(csrf()))
            .andExpect(redirectedUrl("/admin/centers"));
        assertThat(centers.findById(empty.getId())).isEmpty();
    }

    private User save(String name, String username, Role role, Center c) {
        return users.save(User.builder().fullName(name).username(username)
            .passwordHash("x").role(role).center(c).active(true).build());
    }

    private static RequestPostProcessor as(User u) {
        return user(new CustomUserDetails(u));
    }
}
