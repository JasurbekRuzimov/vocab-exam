package uz.vocabexam.repository;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import uz.vocabexam.entity.*;
import java.util.*;

public interface ExamResultRepository extends JpaRepository<ExamResult, UUID> {
    Optional<ExamResult> findByAssignmentIdAndStudentId(UUID assignmentId, UUID studentId);
    List<ExamResult> findByStudentIdOrderByCheckedAtDesc(UUID studentId);
    List<ExamResult> findByAssignmentId(UUID assignmentId);
    @Query("SELECT r FROM ExamResult r WHERE r.assignment.exam.teacher.id = :teacherId ORDER BY r.checkedAt DESC")
    List<ExamResult> findByTeacherId(@Param("teacherId") UUID teacherId);
    @Query("SELECT AVG(r.totalScore) FROM ExamResult r WHERE r.assignment.id = :assignmentId")
    Double avgScoreByAssignmentId(@Param("assignmentId") UUID assignmentId);
    @Query("SELECT AVG(r.totalScore) FROM ExamResult r WHERE r.student.id = :studentId")
    Double avgScoreByStudentId(@Param("studentId") UUID studentId);
    /** Markaz statistikasi: [o'qituvchi id, natijalar soni, o'rtacha foiz]. */
    @Query("""
        SELECT r.assignment.exam.teacher.id, COUNT(r), AVG(100.0 * r.totalScore / r.maxScore) FROM ExamResult r
        WHERE r.assignment.exam.teacher.center.id = :centerId AND r.maxScore > 0
        GROUP BY r.assignment.exam.teacher.id""")
    List<Object[]> statsByTeacherInCenter(@Param("centerId") UUID centerId);
}
