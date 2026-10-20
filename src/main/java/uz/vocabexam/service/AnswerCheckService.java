package uz.vocabexam.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.vocabexam.entity.*;
import uz.vocabexam.exception.*;
import uz.vocabexam.repository.*;
import uz.vocabexam.security.SecurityUtils;
import java.time.LocalDateTime;
import java.util.*;

@Service @RequiredArgsConstructor
public class AnswerCheckService {
    private final OnlineAnswerRepository answerRepository;
    private final AnswerErrorRepository errorRepository;
    private final ExamResultRepository resultRepository;
    private final ExamAssignmentRepository assignmentRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final ResultChangeRepository changeRepository;

    public static final int MAX_ANSWER_SCORE = 10;

    public OnlineAnswer getAnswer(UUID answerId) {
        return answerRepository.findById(answerId)
            .orElseThrow(() -> new ResourceNotFoundException("Javob topilmadi"));
    }

    /** Javobni faqat imtihon egasi (yoki admin) uchun qaytaradi. */
    public OnlineAnswer getOwnedAnswer(UUID answerId) {
        OnlineAnswer answer = getAnswer(answerId);
        SecurityUtils.assertOwnerOrAdmin(answer.getAssignment().getExam().getTeacher().getId());
        return answer;
    }

    private AnswerError getOwnedError(UUID answerId, UUID errorId) {
        OnlineAnswer answer = getOwnedAnswer(answerId);
        AnswerError err = errorRepository.findById(errorId)
            .orElseThrow(() -> new ResourceNotFoundException("Xato topilmadi"));
        if (!err.getAnswer().getId().equals(answer.getId())) throw new ResourceNotFoundException("Xato topilmadi");
        return err;
    }

    @Transactional
    public AnswerError addError(UUID answerId, int start, int end, String comment, String correctHint) {
        return addError(answerId, start, end, null, comment, correctHint);
    }

    /** Xato qo'shish: tur (masalan TENSE) tanlansa, izoh ixtiyoriy — bo'sh bo'lsa tur nomi yoziladi. */
    @Transactional
    public AnswerError addError(UUID answerId, int start, int end, ErrorCategory category,
                                String comment, String correctHint) {
        OnlineAnswer answer = getOwnedAnswer(answerId);
        if (comment == null || comment.isBlank()) {
            if (category == null) throw new BadRequestException("Xato turini tanlang yoki izoh yozing");
            comment = category.getLabel();
        }
        if (start < 0) throw new BadRequestException("start manfiy bo'lishi mumkin emas");
        if (end <= start) throw new BadRequestException("end > start bo'lishi kerak");
        if (answer.getAnswerText() != null && end > answer.getAnswerText().length())
            throw new BadRequestException("end indeks matn uzunligidan oshib ketdi");
        AnswerError saved = errorRepository.save(AnswerError.builder()
            .answer(answer).errorStart(start).errorEnd(end)
            .category(category).comment(comment.trim())
            .correctHint(correctHint == null || correctHint.isBlank() ? null : correctHint.trim()).build());
        answer.getErrors().add(saved); // shu sessiyada javobni o'qiyotganlar ham yangi xatoni ko'rsin
        return saved;
    }

    @Transactional
    public AnswerError updateError(UUID answerId, UUID errorId, String comment, String correctHint) {
        AnswerError err = getOwnedError(answerId, errorId);
        err.setComment(comment); err.setCorrectHint(correctHint);
        return errorRepository.save(err);
    }

    @Transactional
    public void deleteError(UUID answerId, UUID errorId) {
        AnswerError err = getOwnedError(answerId, errorId);
        err.getAnswer().getErrors().remove(err);
        errorRepository.delete(err);
    }

    @Transactional
    public void setScore(UUID answerId, int score) {
        OnlineAnswer ans = getOwnedAnswer(answerId);
        if (score < 0 || score > MAX_ANSWER_SCORE)
            throw new BadRequestException("Ball 0 dan " + MAX_ANSWER_SCORE + " gacha bo'lishi kerak");
        ans.setScore(score);
        // O'qituvchi avtomatik bahoni qo'lda o'zgartirsa (masalan sinonimni qabul qilsa), belgini ham yangilaymiz
        if (ans.getQuestionType().isAutoGraded()) ans.setCorrect(score > 0);
        answerRepository.save(ans);
    }

    @Transactional
    public ExamResult saveResult(UUID assignmentId, UUID studentId, int totalScore, int maxScore, String comment) {
        return saveResult(assignmentId, studentId, totalScore, maxScore, comment, null);
    }

    /**
     * Natijani saqlaydi. E'lon qilingan natijaning bali o'zgarsa, sababi majburiy: o'zgarish tarixga
     * yoziladi va o'quvchi natija sahifasida kim, qachon, nimadan nimaga o'zgartirganini ko'radi.
     */
    @Transactional
    public ExamResult saveResult(UUID assignmentId, UUID studentId, int totalScore, int maxScore,
                                 String comment, String changeReason) {
        ExamAssignment assignment = assignmentRepository.findById(assignmentId)
            .orElseThrow(() -> new ResourceNotFoundException("Topshiriq topilmadi"));
        SecurityUtils.assertOwnerOrAdmin(assignment.getExam().getTeacher().getId());
        if (maxScore <= 0) throw new BadRequestException("Maksimal ball musbat bo'lishi kerak");
        if (totalScore < 0 || totalScore > maxScore)
            throw new BadRequestException("Jami ball 0 dan " + maxScore + " gacha bo'lishi kerak");
        if (answerRepository.countByAssignmentIdAndStudentIdAndSubmittedTrue(assignmentId, studentId) == 0)
            throw new BadRequestException("O'quvchi bu imtihonni hali topshirmagan");
        User student = userRepository.findById(studentId)
            .orElseThrow(() -> new ResourceNotFoundException("O'quvchi topilmadi"));

        Optional<ExamResult> existing = resultRepository.findByAssignmentIdAndStudentId(assignmentId, studentId);
        ExamResult result;
        ResultChange change = null;
        if (existing.isPresent()) {
            result = existing.get();
            if (result.getTotalScore() != totalScore || result.getMaxScore() != maxScore) {
                if (changeReason == null || changeReason.isBlank())
                    throw new BadRequestException("Natija e'lon qilingan. Ballni o'zgartirish sababini yozing");
                change = ResultChange.builder().result(result)
                    .changedBy(userRepository.getReferenceById(SecurityUtils.currentUserId()))
                    .oldScore(result.getTotalScore()).oldMax(result.getMaxScore())
                    .newScore(totalScore).newMax(maxScore).reason(changeReason.trim()).build();
            }
            result.setTotalScore(totalScore); result.setMaxScore(maxScore);
            result.setComment(comment); result.setCheckedAt(LocalDateTime.now());
        } else {
            result = ExamResult.builder()
                .assignment(assignment).student(student)
                .totalScore(totalScore).maxScore(maxScore).comment(comment).build();
        }
        ExamResult saved = resultRepository.save(result);
        if (change != null) changeRepository.save(change);
        notificationService.send(studentId, NotificationType.RESULT_READY,
            change != null
                ? "Natijangiz o'zgartirildi: " + change.getOldScore() + "/" + change.getOldMax()
                    + " → " + totalScore + "/" + maxScore + " ball"
                : "Imtihon natijangiz tayyor: " + totalScore + "/" + maxScore + " ball",
            "/student/results/" + saved.getId());
        return saved;
    }

    public List<ResultChange> getChanges(UUID resultId) {
        return changeRepository.findByResultIdOrderByChangedAtDesc(resultId);
    }

    public List<AnswerError> getErrors(UUID answerId) {
        return errorRepository.findByAnswerIdOrderByErrorStart(answerId);
    }
}
