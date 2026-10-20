package uz.vocabexam.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.vocabexam.entity.*;
import uz.vocabexam.repository.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Jonli monitoring va imtihon statistikasi: o'qituvchi har o'quvchining holatini
 * (boshlamagan, jarayonda, topshirgan, baholangan) va eng qiyin so'zlarni ko'radi.
 */
@Service @RequiredArgsConstructor
public class ExamMonitorService {
    private final ExamAttemptRepository attemptRepository;
    private final OnlineAnswerRepository answerRepository;
    private final ExamResultRepository resultRepository;
    private final WordRepository wordRepository;
    private final UserRepository userRepository;
    private final OnlineExamService onlineExamService;

    /** Shu vaqt ichida signal kelgan o'quvchi "onlayn" hisoblanadi (sahifa har 30 soniyada signal yuboradi). */
    static final int ONLINE_SECONDS = 75;

    public enum Status {
        NOT_STARTED("Boshlamagan"), IN_PROGRESS("Jarayonda"), SUBMITTED("Tekshirish kutilmoqda"), GRADED("Baholandi");
        private final String label;
        Status(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    public record StudentRow(User student, Status status, int answered, int total, boolean online,
                             LocalDateTime startedAt, LocalDateTime lastSeenAt, LocalDateTime submittedAt,
                             int focusLost, ExamResult result) {
        public int getPercent() {
            return result == null || result.getMaxScore() == 0 ? 0
                : Math.round(100f * result.getTotalScore() / result.getMaxScore());
        }
    }

    public record WordStat(String word, String translation, int attempts, int wrong, int percent) {}

    public record Stats(int assigned, int started, int submitted, int graded, Integer avgPercent,
                        int[] buckets) {}

    public record Monitor(List<StudentRow> rows, Stats stats, List<WordStat> hardestWords, int questionCount) {
        public boolean isAnyoneInProgress() {
            return rows.stream().anyMatch(r -> r.status() == Status.IN_PROGRESS);
        }
    }

    /** Ball oralig'lari (foizda): statistika diagrammasi uchun. */
    public static final String[] BUCKET_LABELS = {"0–49%", "50–69%", "70–84%", "85–100%"};

    // ── O'quvchi tomoni: imtihon sahifasidan keladigan signallar ─────────────────

    /** O'quvchi imtihon sahifasini ochdi: urinish yoziladi (yoki oxirgi faollik yangilanadi). */
    @Transactional
    public void recordOpen(ExamAssignment assignment, UUID studentId) {
        touch(assignment, studentId, false);
    }

    /** Sahifadan keladigan signal: har 30 soniyada "ping" yoki boshqa oynaga o'tilganda "focus-lost". */
    @Transactional
    public void recordActivity(UUID assignmentId, UUID studentId, boolean focusLost) {
        ExamAssignment a = onlineExamService.getAssignmentForStudent(assignmentId, studentId);
        if (onlineExamService.hasSubmitted(assignmentId, studentId)) return; // topshirilgandan keyin hisoblamaymiz
        touch(a, studentId, focusLost);
    }

    private void touch(ExamAssignment a, UUID studentId, boolean focusLost) {
        ExamAttempt attempt = attemptRepository.findByAssignmentIdAndStudentId(a.getId(), studentId)
            .orElseGet(() -> ExamAttempt.builder().assignment(a)
                .student(userRepository.getReferenceById(studentId)).build());
        attempt.setLastSeenAt(LocalDateTime.now());
        if (focusLost) attempt.setFocusLostCount(attempt.getFocusLostCount() + 1);
        attemptRepository.save(attempt);
    }

    public Optional<ExamAttempt> getAttempt(UUID assignmentId, UUID studentId) {
        return attemptRepository.findByAssignmentIdAndStudentId(assignmentId, studentId);
    }

    // ── O'qituvchi tomoni ────────────────────────────────────────────────────

    /** Har o'quvchiga beriladigan savollar soni (imtihondagi son yoki to'plamdagi barcha so'zlar). */
    public int questionCount(ExamAssignment a) {
        int pool = (int) wordRepository.countByWordSetId(a.getExam().getWordSet().getId());
        int count = a.getExam().getQuestionCount();
        return count > 0 && count < pool ? count : pool;
    }

    public Monitor getMonitor(ExamAssignment a) {
        UUID id = a.getId();
        int total = questionCount(a);
        LocalDateTime onlineSince = LocalDateTime.now().minusSeconds(ONLINE_SECONDS);

        Map<UUID, List<OnlineAnswer>> answersByStudent = answerRepository.findByAssignmentId(id).stream()
            .collect(Collectors.groupingBy(x -> x.getStudent().getId()));
        Map<UUID, ExamAttempt> attempts = attemptRepository.findByAssignmentId(id).stream()
            .collect(Collectors.toMap(x -> x.getStudent().getId(), x -> x));
        Map<UUID, ExamResult> results = resultRepository.findByAssignmentId(id).stream()
            .collect(Collectors.toMap(x -> x.getStudent().getId(), x -> x));

        // Tayinlangan o'quvchilar + guruhdan chiqarilgan bo'lsa ham javob yozganlar
        Map<UUID, User> roster = new LinkedHashMap<>();
        if (a.getTargetType() == TargetType.GROUP && a.getGroup() != null)
            a.getGroup().getStudents().forEach(s -> roster.put(s.getId(), s));
        else if (a.getStudent() != null)
            roster.put(a.getStudent().getId(), a.getStudent());
        answersByStudent.forEach((sid, list) -> roster.putIfAbsent(sid, list.get(0).getStudent()));

        List<StudentRow> rows = new ArrayList<>();
        for (User s : roster.values()) {
            List<OnlineAnswer> answers = answersByStudent.getOrDefault(s.getId(), List.of());
            ExamAttempt attempt = attempts.get(s.getId());
            ExamResult result = results.get(s.getId());
            boolean submitted = answers.stream().anyMatch(OnlineAnswer::isSubmitted);
            int answered = (int) answers.stream()
                .filter(x -> x.getAnswerText() != null && !x.getAnswerText().isBlank()).count();
            LocalDateTime submittedAt = answers.stream().map(OnlineAnswer::getSubmittedAt)
                .filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);

            Status status = result != null ? Status.GRADED
                : submitted ? Status.SUBMITTED
                : attempt != null || !answers.isEmpty() ? Status.IN_PROGRESS
                : Status.NOT_STARTED;
            boolean online = status == Status.IN_PROGRESS && attempt != null
                && attempt.getLastSeenAt().isAfter(onlineSince);
            rows.add(new StudentRow(s, status, answered, total, online,
                attempt != null ? attempt.getStartedAt() : null,
                attempt != null ? attempt.getLastSeenAt() : null,
                submittedAt, attempt != null ? attempt.getFocusLostCount() : 0, result));
        }
        // Avval tekshirish kutayotganlar va jarayondagilar, keyin qolganlar; ichida ism bo'yicha
        rows.sort(Comparator.comparingInt((StudentRow r) -> switch (r.status()) {
                case SUBMITTED -> 0; case IN_PROGRESS -> 1; case NOT_STARTED -> 2; case GRADED -> 3; })
            .thenComparing(r -> r.student().getFullName(), String.CASE_INSENSITIVE_ORDER));

        return new Monitor(rows, stats(rows), hardestWords(answersByStudent.values()), total);
    }

    private static Stats stats(List<StudentRow> rows) {
        int started = 0, submitted = 0, graded = 0;
        int[] buckets = new int[BUCKET_LABELS.length];
        int percentSum = 0;
        for (StudentRow r : rows) {
            if (r.status() != Status.NOT_STARTED) started++;
            if (r.status() == Status.SUBMITTED || r.status() == Status.GRADED) submitted++;
            if (r.status() == Status.GRADED) {
                graded++;
                int p = r.getPercent();
                percentSum += p;
                buckets[p < 50 ? 0 : p < 70 ? 1 : p < 85 ? 2 : 3]++;
            }
        }
        Integer avg = graded == 0 ? null : Math.round((float) percentSum / graded);
        return new Stats(rows.size(), started, submitted, graded, avg, buckets);
    }

    /** Ball qo'yilgan javoblar bo'yicha eng past natijali so'zlar (eng ko'pi 8 ta). */
    private static List<WordStat> hardestWords(Collection<List<OnlineAnswer>> all) {
        record Acc(Word word, int[] v) {} // v = [urinishlar, xatolar, ballar yig'indisi]
        Map<UUID, Acc> byWord = new HashMap<>();
        for (List<OnlineAnswer> list : all) for (OnlineAnswer x : list) {
            if (!x.isSubmitted() || x.getScore() == null) continue;
            Acc acc = byWord.computeIfAbsent(x.getWord().getId(), k -> new Acc(x.getWord(), new int[3]));
            acc.v()[0]++;
            if (x.getScore() < AnswerCheckService.MAX_ANSWER_SCORE) acc.v()[1]++;
            acc.v()[2] += x.getScore();
        }
        return byWord.values().stream()
            .filter(acc -> acc.v()[1] > 0)
            .map(acc -> new WordStat(acc.word().getText(), acc.word().getTranslation(), acc.v()[0], acc.v()[1],
                Math.round(100f * acc.v()[2] / (acc.v()[0] * AnswerCheckService.MAX_ANSWER_SCORE))))
            .sorted(Comparator.comparingInt(WordStat::percent).thenComparing(WordStat::word))
            .limit(8)
            .toList();
    }

    /** O'qituvchi bosh sahifasi uchun: topshirilgan, lekin hali baholanmagan ishlar. */
    public record PendingCheck(ExamAssignment assignment, User student, LocalDateTime submittedAt) {}

    public List<PendingCheck> getPendingChecks(UUID teacherId) {
        return answerRepository.findPendingChecks(teacherId).stream()
            .map(r -> new PendingCheck((ExamAssignment) r[0], (User) r[1], (LocalDateTime) r[2]))
            .toList();
    }
}
