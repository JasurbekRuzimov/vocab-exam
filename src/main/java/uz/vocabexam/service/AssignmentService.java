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
public class AssignmentService {
    private final ExamAssignmentRepository assignmentRepository;
    private final ExamRepository examRepository;
    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final DataCleanupService cleanupService;

    public List<ExamAssignment> getByTeacher(UUID teacherId) {
        return assignmentRepository.findByTeacherId(teacherId);
    }
    public List<ExamAssignment> getByExam(UUID examId) {
        return assignmentRepository.findByExamIdOrderByAssignedAtDesc(examId);
    }
    public List<ExamAssignment> getForStudent(UUID studentId) {
        return assignmentRepository.findForStudent(studentId);
    }
    public ExamAssignment getById(UUID id) {
        return assignmentRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Topshiriq topilmadi"));
    }
    /** Topshiriqni faqat imtihon egasi (yoki admin) uchun qaytaradi. */
    public ExamAssignment getOwned(UUID id) {
        ExamAssignment a = getById(id);
        SecurityUtils.assertOwnerOrAdmin(a.getExam().getTeacher().getId());
        return a;
    }

    @Transactional
    public List<ExamAssignment> assign(UUID examId, TargetType targetType, UUID targetId,
                                        LocalDateTime deadline, UUID teacherId) {
        Exam exam = examRepository.findById(examId).orElseThrow(() -> new ResourceNotFoundException("Imtihon topilmadi"));
        SecurityUtils.assertOwnerOrAdmin(exam.getTeacher().getId());

        List<ExamAssignment> created = new ArrayList<>();
        if (targetType == TargetType.GROUP) {
            Group group = groupRepository.findById(targetId).orElseThrow(() -> new ResourceNotFoundException("Guruh topilmadi"));
            SecurityUtils.assertOwnerOrAdmin(group.getTeacher().getId());
            ExamAssignment a = assignmentRepository.save(ExamAssignment.builder()
                .exam(exam).targetType(TargetType.GROUP).group(group).deadline(deadline).build());
            created.add(a);
            group.getStudents().forEach(s -> notificationService.send(s.getId(),
                NotificationType.EXAM_ASSIGNED, "Sizga yangi imtihon topshirildi: " + exam.getTitle(),
                "/student/exams/" + a.getId()));
        } else {
            User student = userRepository.findById(targetId).orElseThrow(() -> new ResourceNotFoundException("Student topilmadi"));
            if (student.getRole() != Role.STUDENT) throw new BadRequestException("Tanlangan foydalanuvchi o'quvchi emas");
            if (!Objects.equals(student.getCenterId(), exam.getTeacher().getCenterId()))
                throw new AccessForbiddenException("O'quvchi boshqa markazda");
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
        ExamAssignment a = getOwned(id);
        a.setStatus(AssignmentStatus.CANCELLED);
        assignmentRepository.save(a);
    }

    /** Topshiriq o'quvchilar javoblari va natijalari bilan birga o'chiriladi. */
    @Transactional
    public void delete(UUID id) {
        getOwned(id);
        cleanupService.purgeAssignments(List.of(id));
    }
}
