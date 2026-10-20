package uz.vocabexam.repository;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import uz.vocabexam.entity.*;
import java.util.*;

public interface OnlineAnswerRepository extends JpaRepository<OnlineAnswer, UUID> {
    List<OnlineAnswer> findByAssignmentIdAndStudentId(UUID assignmentId, UUID studentId);
    Optional<OnlineAnswer> findByAssignmentIdAndStudentIdAndWordId(UUID assignmentId, UUID studentId, UUID wordId);
    List<OnlineAnswer> findByAssignmentId(UUID assignmentId);
    boolean existsByWordId(UUID wordId);
    @Query("SELECT DISTINCT a.student FROM OnlineAnswer a WHERE a.assignment.id = :assignmentId AND a.submitted = true")
    List<User> findSubmittedStudents(@Param("assignmentId") UUID assignmentId);
    long countByAssignmentIdAndStudentIdAndSubmittedTrue(UUID assignmentId, UUID studentId);
    /** O'qituvchining topshirilgan, lekin hali natija qo'yilmagan ishlari: [topshiriq, o'quvchi, topshirilgan vaqt]. */
    @Query("""
        SELECT a.assignment, a.student, MAX(a.submittedAt) FROM OnlineAnswer a
        WHERE a.assignment.exam.teacher.id = :teacherId AND a.submitted = true
          AND NOT EXISTS (SELECT r.id FROM ExamResult r WHERE r.assignment = a.assignment AND r.student = a.student)
        GROUP BY a.assignment, a.student
        ORDER BY MAX(a.submittedAt)""")
    List<Object[]> findPendingChecks(@Param("teacherId") UUID teacherId);
}
