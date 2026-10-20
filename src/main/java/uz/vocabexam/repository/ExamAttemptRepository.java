package uz.vocabexam.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.vocabexam.entity.ExamAttempt;
import java.util.*;

public interface ExamAttemptRepository extends JpaRepository<ExamAttempt, UUID> {
    Optional<ExamAttempt> findByAssignmentIdAndStudentId(UUID assignmentId, UUID studentId);
    List<ExamAttempt> findByAssignmentId(UUID assignmentId);
}
