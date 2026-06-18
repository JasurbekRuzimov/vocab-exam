package uz.vocabexam.repository;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import uz.vocabexam.entity.*;
import java.util.*;

public interface ExamResultRepository extends JpaRepository<ExamResult, UUID> {
    Optional<ExamResult> findByAssignmentIdAndStudentId(UUID assignmentId, UUID studentId);
    List<ExamResult> findByStudentIdOrderByCheckedAtDesc(UUID studentId);
    @Query("SELECT r FROM ExamResult r WHERE r.assignment.exam.teacher.id = :teacherId ORDER BY r.checkedAt DESC")
    List<ExamResult> findByTeacherId(@Param("teacherId") UUID teacherId);
    @Query("SELECT AVG(r.totalScore) FROM ExamResult r WHERE r.assignment.id = :assignmentId")
    Double avgScoreByAssignmentId(@Param("assignmentId") UUID assignmentId);
    @Query("SELECT AVG(r.totalScore) FROM ExamResult r WHERE r.student.id = :studentId")
    Double avgScoreByStudentId(@Param("studentId") UUID studentId);
}
