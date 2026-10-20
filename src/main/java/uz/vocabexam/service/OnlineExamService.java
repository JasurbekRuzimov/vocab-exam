package uz.vocabexam.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.vocabexam.entity.*;
import uz.vocabexam.exception.*;
import uz.vocabexam.repository.*;
import java.time.LocalDateTime;
import java.util.*;

@Service @RequiredArgsConstructor
public class OnlineExamService {
    private final ExamAssignmentRepository assignmentRepository;
    private final OnlineAnswerRepository answerRepository;
    private final WordRepository wordRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final ExamResultRepository resultRepository;

    public ExamAssignment getAssignmentForStudent(UUID assignmentId, UUID studentId) {
        ExamAssignment a = assignmentRepository.findById(assignmentId)
            .orElseThrow(() -> new ResourceNotFoundException("Topshiriq topilmadi"));
        boolean hasAccess =
            (a.getTargetType() == TargetType.STUDENT && a.getStudent() != null && a.getStudent().getId().equals(studentId)) ||
            (a.getTargetType() == TargetType.GROUP   && a.getGroup()   != null && a.getGroup().getStudents().stream().anyMatch(s -> s.getId().equals(studentId)));
        if (!hasAccess) throw new AccessForbiddenException("Bu imtihonga ruxsat yo'q");
        return a;
    }

    public List<OnlineAnswer> getStudentAnswers(UUID assignmentId, UUID studentId) {
        return answerRepository.findByAssignmentIdAndStudentId(assignmentId, studentId);
    }

    /**
     * O'quvchiga tegishli savollar: to'plamdan {@code questionCount} ta so'z, tasodifiy tartibda.
     * Seed topshiriq + o'quvchidan olinadi, shuning uchun sahifa yangilansa ham ro'yxat o'zgarmaydi,
     * lekin har o'quvchida tartib (va so'zlar) har xil bo'ladi.
     */
    public List<Word> getExamWords(ExamAssignment assignment, UUID studentId) {
        List<Word> all = new ArrayList<>(wordRepository.findByWordSetId(assignment.getExam().getWordSet().getId()));
        all.sort(Comparator.comparing(Word::getId));
        Collections.shuffle(all, new Random(seedOf(assignment.getId(), studentId)));
        int count = assignment.getExam().getQuestionCount();
        return count > 0 && count < all.size() ? new ArrayList<>(all.subList(0, count)) : all;
    }

    /**
     * O'quvchining savollari: har so'zga imtihondagi turlardan biri navbat bilan beriladi,
     * test savollari uchun to'plamdagi boshqa so'zlarning tarjimalaridan 3 ta chalg'ituvchi variant olinadi.
     * Hammasi bir xil seed bilan, shuning uchun sahifa yangilansa ham savollar o'zgarmaydi.
     */
    public List<ExamQuestion> getQuestions(ExamAssignment assignment, UUID studentId) {
        List<Word> words = getExamWords(assignment, studentId);
        List<QuestionType> types = assignment.getExam().getQuestionTypeList();
        List<Word> pool = wordRepository.findByWordSetId(assignment.getExam().getWordSet().getId());
        Random rnd = new Random(seedOf(assignment.getId(), studentId) * 31 + 7);

        List<ExamQuestion> questions = new ArrayList<>();
        for (int i = 0; i < words.size(); i++) {
            Word w = words.get(i);
            QuestionType type = types.get(i % types.size());
            if (type.isAutoGraded() && isBlank(w.getTranslation())) type = QuestionType.SENTENCE;
            questions.add(switch (type) {
                case SENTENCE        -> new ExamQuestion(w, type, w.getText(), List.of(), null);
                case TRANSLATE_EN_UZ -> new ExamQuestion(w, type, w.getText(), List.of(), w.getTranslation());
                case TRANSLATE_UZ_EN -> new ExamQuestion(w, type, w.getTranslation(), List.of(), w.getText());
                case MULTIPLE_CHOICE -> new ExamQuestion(w, type, w.getText(), choiceOptions(w, pool, rnd), w.getTranslation());
            });
        }
        return questions;
    }

    private static List<String> choiceOptions(Word correct, List<Word> pool, Random rnd) {
        Set<String> seen = new HashSet<>(List.of(AnswerGrader.normalize(correct.getTranslation())));
        List<String> distractors = new ArrayList<>();
        List<Word> shuffled = new ArrayList<>(pool);
        shuffled.sort(Comparator.comparing(Word::getId));
        Collections.shuffle(shuffled, rnd);
        for (Word w : shuffled) {
            if (distractors.size() == 3) break;
            if (isBlank(w.getTranslation()) || !seen.add(AnswerGrader.normalize(w.getTranslation()))) continue;
            distractors.add(w.getTranslation().trim());
        }
        List<String> options = new ArrayList<>(distractors);
        options.add(correct.getTranslation().trim());
        Collections.shuffle(options, rnd);
        return options;
    }

    private static long seedOf(UUID a, UUID b) {
        return a.getMostSignificantBits() ^ a.getLeastSignificantBits()
            ^ b.getMostSignificantBits() ^ b.getLeastSignificantBits();
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }

    public boolean hasSubmitted(UUID assignmentId, UUID studentId) {
        return answerRepository.countByAssignmentIdAndStudentIdAndSubmittedTrue(assignmentId, studentId) > 0;
    }

    private void assertOpenForAnswers(ExamAssignment a, UUID studentId) {
        if (a.getExam().getType() != ExamType.ONLINE) throw new BadRequestException("Bu imtihon onlayn emas");
        if (a.getStatus() != AssignmentStatus.ACTIVE) throw new BadRequestException("Topshiriq faol emas");
        if (a.getDeadline() != null && a.getDeadline().isBefore(LocalDateTime.now()))
            throw new BadRequestException("Imtihon muddati tugagan");
        if (hasSubmitted(a.getId(), studentId)) throw new BadRequestException("Allaqachon topshirilgan");
    }

    @Transactional
    public OnlineAnswer saveAnswer(UUID assignmentId, UUID wordId, String text, UUID studentId) {
        ExamAssignment a = getAssignmentForStudent(assignmentId, studentId);
        assertOpenForAnswers(a, studentId);

        ExamQuestion question = getQuestions(a, studentId).stream()
            .filter(q -> q.word().getId().equals(wordId)).findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("So'z bu imtihonda yo'q"));
        if (question.type() == QuestionType.MULTIPLE_CHOICE && text != null && !text.isBlank()
                && !question.options().contains(text))
            throw new BadRequestException("Bunday variant yo'q");
        User student = userRepository.findById(studentId)
            .orElseThrow(() -> new ResourceNotFoundException("Foydalanuvchi topilmadi"));

        Optional<OnlineAnswer> existing = answerRepository.findByAssignmentIdAndStudentIdAndWordId(assignmentId, studentId, wordId);
        if (existing.isPresent()) {
            OnlineAnswer ans = existing.get();
            if (ans.isSubmitted()) throw new BadRequestException("Allaqachon topshirilgan");
            ans.setAnswerText(text);
            ans.setQuestionType(question.type());
            ans.setSavedAt(LocalDateTime.now());
            return answerRepository.save(ans);
        } else {
            return answerRepository.save(OnlineAnswer.builder()
                .assignment(a).student(student).word(question.word()).questionType(question.type())
                .answerText(text).savedAt(LocalDateTime.now()).build());
        }
    }

    @Transactional
    public void submit(UUID assignmentId, UUID studentId) {
        ExamAssignment a = getAssignmentForStudent(assignmentId, studentId);
        assertOpenForAnswers(a, studentId);
        User student = userRepository.findById(studentId)
            .orElseThrow(() -> new ResourceNotFoundException("Foydalanuvchi topilmadi"));

        Map<UUID, OnlineAnswer> byWord = new HashMap<>();
        answerRepository.findByAssignmentIdAndStudentId(assignmentId, studentId)
            .forEach(ans -> byWord.put(ans.getWord().getId(), ans));

        // Har savol uchun yozuv bo'ladi (javob yozilmagan bo'lsa ham): shunda topshirish
        // har doim qayd etiladi va o'qituvchi qaysi savol bo'sh qolganini ko'radi.
        List<ExamQuestion> questions = getQuestions(a, studentId);
        List<OnlineAnswer> answers = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        int autoScore = 0;
        boolean needsTeacher = false;
        for (ExamQuestion q : questions) {
            OnlineAnswer ans = byWord.computeIfAbsent(q.word().getId(), id ->
                OnlineAnswer.builder().assignment(a).student(student).word(q.word()).build());
            ans.setQuestionType(q.type());
            ans.setSubmitted(true);
            ans.setSubmittedAt(now);
            if (q.type().isAutoGraded()) {
                boolean ok = q.type() == QuestionType.MULTIPLE_CHOICE
                    ? AnswerGrader.isExactChoice(ans.getAnswerText(), q.expected())
                    : AnswerGrader.isCorrect(ans.getAnswerText(), q.expected());
                ans.setCorrect(ok);
                ans.setScore(ok ? AnswerCheckService.MAX_ANSWER_SCORE : 0);
                autoScore += ans.getScore();
            } else {
                needsTeacher = true;
            }
            answers.add(ans);
        }
        answerRepository.saveAll(answers);

        if (needsTeacher) {
            notificationService.send(a.getExam().getTeacher().getId(), NotificationType.EXAM_SUBMITTED,
                "Imtihon topshirildi: " + a.getExam().getTitle(),
                "/teacher/exams/" + assignmentId + "/submissions");
        } else {
            // Hamma savol avtomatik tekshirildi — natija darhol tayyor
            int max = questions.size() * AnswerCheckService.MAX_ANSWER_SCORE;
            ExamResult result = resultRepository.save(ExamResult.builder()
                .assignment(a).student(student).totalScore(autoScore).maxScore(max)
                .comment("Avtomatik tekshirildi").build());
            notificationService.send(studentId, NotificationType.RESULT_READY,
                "Imtihon natijangiz tayyor: " + autoScore + "/" + max + " ball",
                "/student/results/" + result.getId());
        }
    }

    public List<User> getSubmittedStudents(UUID assignmentId) {
        return answerRepository.findSubmittedStudents(assignmentId);
    }

    public List<OnlineAnswer> getStudentAnswersForCheck(UUID assignmentId, UUID studentId) {
        // Avval o'qituvchi tekshiradigan gaplar, keyin avtomatik baholanganlar; ichida alifbo bo'yicha
        List<OnlineAnswer> list = new ArrayList<>(answerRepository.findByAssignmentIdAndStudentId(assignmentId, studentId));
        list.sort(Comparator.comparing((OnlineAnswer x) -> x.getQuestionType().isAutoGraded())
            .thenComparing(x -> x.getWord().getText()));
        return list;
    }
}
