package uz.vocabexam.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uz.vocabexam.entity.*;
import uz.vocabexam.repository.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class AnalyticsService {
    private final UserRepository userRepository;
    private final ExamRepository examRepository;
    private final ExamResultRepository resultRepository;
    private final OnlineAnswerRepository answerRepository;
    private final ExamAssignmentRepository assignmentRepository;
    private final CenterRepository centerRepository;
    private final GroupRepository groupRepository;
    private final ExamMonitorService monitorService;

    public Map<String, Object> getSystemSummary() {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalCenters", centerRepository.count());
        summary.put("totalStudents", userRepository.findByRole(Role.STUDENT).size());
        summary.put("totalTeachers", userRepository.findByRole(Role.TEACHER).size());
        summary.put("totalExams", examRepository.count());
        summary.put("totalResults", resultRepository.count());
        return summary;
    }

    public Map<String, Object> getStudentStats(UUID studentId) {
        Map<String, Object> stats = new LinkedHashMap<>();
        List<uz.vocabexam.entity.ExamResult> results = resultRepository.findByStudentIdOrderByCheckedAtDesc(studentId);
        stats.put("totalExams", results.size());
        stats.put("avgScore", resultRepository.avgScoreByStudentId(studentId));
        stats.put("results", results);
        return stats;
    }

    public Map<String, Object> getAssignmentStats(UUID assignmentId) {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("avgScore", resultRepository.avgScoreByAssignmentId(assignmentId));
        stats.put("submittedStudents", answerRepository.findSubmittedStudents(assignmentId).size());
        return stats;
    }

    /** Direktor bosh sahifasi: o'qituvchi bo'yicha imtihonlar, baholangan ishlar, o'rtacha natija va kutilayotgan tekshiruvlar. */
    public record TeacherRow(User teacher, long exams, long graded, Integer avgPercent, int pending) {}

    public record CenterSummary(long students, long teachers, long groups, long exams, long graded,
                                Integer avgPercent, int pending, List<TeacherRow> rows) {}

    public CenterSummary getCenterSummary(UUID centerId) {
        Map<UUID, Long> exams = new HashMap<>();
        examRepository.countByTeacherInCenter(centerId).forEach(r -> exams.put((UUID) r[0], (Long) r[1]));
        Map<UUID, Object[]> results = new HashMap<>();
        resultRepository.statsByTeacherInCenter(centerId).forEach(r -> results.put((UUID) r[0], r));

        List<TeacherRow> rows = new ArrayList<>();
        long examTotal = 0, gradedTotal = 0; double percentSum = 0; int pendingTotal = 0;
        for (User t : userRepository.findByCenter_IdAndRoleAndActiveTrueOrderByFullName(centerId, Role.TEACHER)) {
            long e = exams.getOrDefault(t.getId(), 0L);
            Object[] r = results.get(t.getId());
            long graded = r == null ? 0 : (Long) r[1];
            Double avg = r == null ? null : ((Number) r[2]).doubleValue();
            int pending = monitorService.getPendingChecks(t.getId()).size();
            rows.add(new TeacherRow(t, e, graded, avg == null ? null : (int) Math.round(avg), pending));
            examTotal += e; gradedTotal += graded; pendingTotal += pending;
            if (avg != null) percentSum += avg * graded;
        }
        Integer avgAll = gradedTotal == 0 ? null : (int) Math.round(percentSum / gradedTotal);
        return new CenterSummary(
            userRepository.countByCenter_IdAndRole(centerId, Role.STUDENT),
            userRepository.countByCenter_IdAndRole(centerId, Role.TEACHER),
            groupRepository.countByTeacher_Center_Id(centerId),
            examTotal, gradedTotal, avgAll, pendingTotal, rows);
    }
}
