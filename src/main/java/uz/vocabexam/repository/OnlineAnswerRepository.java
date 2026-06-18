package uz.vocabexam.repository;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import uz.vocabexam.entity.*;
import java.util.*;

public interface OnlineAnswerRepository extends JpaRepository<OnlineAnswer, UUID> {
    List<OnlineAnswer> findByAssignmentIdAndStudentId(UUID assignmentId, UUID studentId);
    Optional<OnlineAnswer> findByAssignmentIdAndStudentIdAndWordId(UUID assignmentId, UUID studentId, UUID wordId);
    List<OnlineAnswer> findByAssignmentId(UUID assignmentId);
    @Query("SELECT DISTINCT a.student FROM OnlineAnswer a WHERE a.assignment.id = :assignmentId AND a.submitted = true")
    List<User> findSubmittedStudents(@Param("assignmentId") UUID assignmentId);
    long countByAssignmentIdAndStudentIdAndSubmittedTrue(UUID assignmentId, UUID studentId);
}
