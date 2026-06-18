package uz.vocabexam.repository;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import uz.vocabexam.entity.*;
import java.util.*;

public interface ExamAssignmentRepository extends JpaRepository<ExamAssignment, UUID> {
    @Query("SELECT a FROM ExamAssignment a WHERE a.exam.teacher.id = :teacherId ORDER BY a.assignedAt DESC")
    List<ExamAssignment> findByTeacherId(@Param("teacherId") UUID teacherId);
    @Query("SELECT a FROM ExamAssignment a WHERE (a.targetType = 'STUDENT' AND a.student.id = :studentId) OR (a.targetType = 'GROUP' AND a.group.id IN (SELECT g.id FROM Group g JOIN g.students s WHERE s.id = :studentId)) ORDER BY a.assignedAt DESC")
    List<ExamAssignment> findForStudent(@Param("studentId") UUID studentId);
    boolean existsByExamIdAndStatus(UUID examId, AssignmentStatus status);
}
