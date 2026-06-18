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
public class AssignmentService {
    private final ExamAssignmentRepository assignmentRepository;
    private final ExamRepository examRepository;
    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public List<ExamAssignment> getByTeacher(UUID teacherId) {
        return assignmentRepository.findByTeacherId(teacherId);
    }
    public List<ExamAssignment> getForStudent(UUID studentId) {
        return assignmentRepository.findForStudent(studentId);
    }
    public ExamAssignment getById(UUID id) {
        return assignmentRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Topshiriq topilmadi"));
    }

    @Transactional
    public List<ExamAssignment> assign(UUID examId, TargetType targetType, UUID targetId,
                                        LocalDateTime deadline, UUID teacherId) {
        Exam exam = examRepository.findById(examId).orElseThrow(() -> new ResourceNotFoundException("Imtihon topilmadi"));
        if (!exam.getTeacher().getId().equals(teacherId)) throw new AccessForbiddenException("Ruxsat yo'q");

        List<ExamAssignment> created = new ArrayList<>();
        if (targetType == TargetType.GROUP) {
            Group group = groupRepository.findById(targetId).orElseThrow(() -> new ResourceNotFoundException("Guruh topilmadi"));
            ExamAssignment a = assignmentRepository.save(ExamAssignment.builder()
                .exam(exam).targetType(TargetType.GROUP).group(group).deadline(deadline).build());
            created.add(a);
            group.getStudents().forEach(s -> notificationService.send(s.getId(),
                NotificationType.EXAM_ASSIGNED, "Sizga yangi imtihon topshirildi: " + exam.getTitle(),
                "/student/exams/" + a.getId()));
        } else {
            User student = userRepository.findById(targetId).orElseThrow(() -> new ResourceNotFoundException("Student topilmadi"));
            ExamAssignment a = assignmentRepository.save(ExamAssignment.builder()
                .exam(exam).targetType(TargetType.STUDENT).student(student).deadline(deadline).build());
            created.add(a);
            notificationService.send(student.getId(), NotificationType.EXAM_ASSIGNED,
                "Sizga yangi imtihon topshirildi: " + exam.getTitle(), "/student/exams/" + a.getId());
        }
        return created;
    }

    @Transactional
    public void cancel(UUID id, UUID teacherId) {
        ExamAssignment a = getById(id);
        if (!a.getExam().getTeacher().getId().equals(teacherId)) throw new AccessForbiddenException("Ruxsat yo'q");
        a.setStatus(AssignmentStatus.CANCELLED);
        assignmentRepository.save(a);
    }
}
