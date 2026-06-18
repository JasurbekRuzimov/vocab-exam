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
public class AnswerCheckService {
    private final OnlineAnswerRepository answerRepository;
    private final AnswerErrorRepository errorRepository;
    private final ExamResultRepository resultRepository;
    private final ExamAssignmentRepository assignmentRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public OnlineAnswer getAnswer(UUID answerId) {
        return answerRepository.findById(answerId)
            .orElseThrow(() -> new ResourceNotFoundException("Javob topilmadi"));
    }

    @Transactional
    public AnswerError addError(UUID answerId, int start, int end, String comment, String correctHint) {
        OnlineAnswer answer = getAnswer(answerId);
        if (end <= start) throw new BadRequestException("end > start bo'lishi kerak");
        if (answer.getAnswerText() != null && end > answer.getAnswerText().length())
            throw new BadRequestException("end indeks matn uzunligidan oshib ketdi");
        return errorRepository.save(AnswerError.builder()
            .answer(answer).errorStart(start).errorEnd(end)
            .comment(comment).correctHint(correctHint).build());
    }

    @Transactional
    public AnswerError updateError(UUID errorId, String comment, String correctHint) {
        AnswerError err = errorRepository.findById(errorId)
            .orElseThrow(() -> new ResourceNotFoundException("Xato topilmadi"));
        err.setComment(comment); err.setCorrectHint(correctHint);
        return errorRepository.save(err);
    }

    @Transactional
    public void deleteError(UUID errorId) { errorRepository.deleteById(errorId); }

    @Transactional
    public void setScore(UUID answerId, int score) {
        OnlineAnswer ans = getAnswer(answerId);
        ans.setScore(score);
        answerRepository.save(ans);
    }

    @Transactional
    public ExamResult saveResult(UUID assignmentId, UUID studentId, int totalScore, int maxScore, String comment) {
        ExamAssignment assignment = assignmentRepository.findById(assignmentId)
            .orElseThrow(() -> new ResourceNotFoundException("Topshiriq topilmadi"));
        User student = userRepository.findById(studentId)
            .orElseThrow(() -> new ResourceNotFoundException("O'quvchi topilmadi"));

        Optional<ExamResult> existing = resultRepository.findByAssignmentIdAndStudentId(assignmentId, studentId);
        ExamResult result;
        if (existing.isPresent()) {
            result = existing.get();
            result.setTotalScore(totalScore); result.setMaxScore(maxScore);
            result.setComment(comment); result.setCheckedAt(LocalDateTime.now());
        } else {
            result = ExamResult.builder()
                .assignment(assignment).student(student)
                .totalScore(totalScore).maxScore(maxScore).comment(comment).build();
        }
        ExamResult saved = resultRepository.save(result);
        notificationService.send(studentId, NotificationType.RESULT_READY,
            "Imtihon natijangiz tayyor: " + totalScore + "/" + maxScore + " ball",
            "/student/results/" + saved.getId());
        return saved;
    }

    public List<AnswerError> getErrors(UUID answerId) {
        return errorRepository.findByAnswerIdOrderByErrorStart(answerId);
    }
}
