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
        long seed = assignment.getId().getMostSignificantBits() ^ assignment.getId().getLeastSignificantBits()
            ^ studentId.getMostSignificantBits() ^ studentId.getLeastSignificantBits();
        Collections.shuffle(all, new Random(seed));
        int count = assignment.getExam().getQuestionCount();
        return count > 0 && count < all.size() ? new ArrayList<>(all.subList(0, count)) : all;
    }

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

        Word word = getExamWords(a, studentId).stream()
            .filter(w -> w.getId().equals(wordId)).findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("So'z bu imtihonda yo'q"));
        User student = userRepository.findById(studentId)
            .orElseThrow(() -> new ResourceNotFoundException("Foydalanuvchi topilmadi"));

        Optional<OnlineAnswer> existing = answerRepository.findByAssignmentIdAndStudentIdAndWordId(assignmentId, studentId, wordId);
        if (existing.isPresent()) {
            OnlineAnswer ans = existing.get();
            if (ans.isSubmitted()) throw new BadRequestException("Allaqachon topshirilgan");
            ans.setAnswerText(text);
            ans.setSavedAt(LocalDateTime.now());
            return answerRepository.save(ans);
        } else {
            return answerRepository.save(OnlineAnswer.builder()
                .assignment(a).student(student).word(word)
                .answerText(text).savedAt(LocalDateTime.now()).build());
        }
    }

    @Transactional
    public void submit(UUID assignmentId, UUID studentId) {
        ExamAssignment a = getAssignmentForStudent(assignmentId, studentId);
        assertOpenForAnswers(a, studentId);
        User student = userRepository.findById(studentId)
            .orElseThrow(() -> new ResourceNotFoundException("Foydalanuvchi topilmadi"));

        List<OnlineAnswer> answers = new ArrayList<>(answerRepository.findByAssignmentIdAndStudentId(assignmentId, studentId));
        // Javob yozilmagan savollar uchun ham bo'sh yozuv yaratamiz: shunda topshirish
        // har doim qayd etiladi va o'qituvchi qaysi savol bo'sh qolganini ko'radi.
        Set<UUID> answered = new HashSet<>();
        answers.forEach(ans -> answered.add(ans.getWord().getId()));
        for (Word w : getExamWords(a, studentId)) {
            if (!answered.contains(w.getId()))
                answers.add(OnlineAnswer.builder().assignment(a).student(student).word(w).build());
        }
        LocalDateTime now = LocalDateTime.now();
        answers.forEach(ans -> { ans.setSubmitted(true); ans.setSubmittedAt(now); });
        answerRepository.saveAll(answers);
        notificationService.send(a.getExam().getTeacher().getId(), NotificationType.EXAM_SUBMITTED,
            "Imtihon topshirildi: " + a.getExam().getTitle(),
            "/teacher/exams/" + assignmentId + "/submissions");
    }

    public List<User> getSubmittedStudents(UUID assignmentId) {
        return answerRepository.findSubmittedStudents(assignmentId);
    }

    public List<OnlineAnswer> getStudentAnswersForCheck(UUID assignmentId, UUID studentId) {
        return answerRepository.findByAssignmentIdAndStudentId(assignmentId, studentId);
    }
}
