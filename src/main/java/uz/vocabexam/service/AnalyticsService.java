package uz.vocabexam.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uz.vocabexam.repository.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class AnalyticsService {
    private final UserRepository userRepository;
    private final ExamRepository examRepository;
    private final ExamResultRepository resultRepository;
    private final OnlineAnswerRepository answerRepository;
    private final ExamAssignmentRepository assignmentRepository;

    public Map<String, Object> getSystemSummary() {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalStudents", userRepository.findByRole(uz.vocabexam.entity.Role.STUDENT).size());
        summary.put("totalTeachers", userRepository.findByRole(uz.vocabexam.entity.Role.TEACHER).size());
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
}
