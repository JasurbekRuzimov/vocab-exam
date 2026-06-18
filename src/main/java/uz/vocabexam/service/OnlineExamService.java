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

    public List<Word> getExamWords(ExamAssignment assignment) {
        return wordRepository.findByWordSetId(assignment.getExam().getWordSet().getId());
    }

    @Transactional
    public OnlineAnswer saveAnswer(UUID assignmentId, UUID wordId, String text, UUID studentId) {
        ExamAssignment a = getAssignmentForStudent(assignmentId, studentId);
        if (a.getStatus() != AssignmentStatus.ACTIVE) throw new BadRequestException("Topshiriq faol emas");
        if (a.getDeadline() != null && a.getDeadline().isBefore(LocalDateTime.now()))
            throw new BadRequestException("Imtihon muddati tugagan");

        Word word = wordRepository.findById(wordId)
            .orElseThrow(() -> new ResourceNotFoundException("So'z topilmadi"));
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
        if (a.getStatus() != AssignmentStatus.ACTIVE) throw new BadRequestException("Topshiriq faol emas");
        List<OnlineAnswer> answers = answerRepository.findByAssignmentIdAndStudentId(assignmentId, studentId);
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
