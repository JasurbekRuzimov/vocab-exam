package uz.vocabexam.repository;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import uz.vocabexam.entity.*;
import java.util.*;

public interface ExamRepository extends JpaRepository<Exam, UUID> {
    Page<Exam> findByTeacherIdOrderByCreatedAtDesc(UUID teacherId, Pageable pageable);
    List<Exam> findByTeacherIdAndTypeOrderByCreatedAtDesc(UUID teacherId, ExamType type);
    @Query("SELECT COUNT(e) FROM Exam e WHERE e.teacher.id = :teacherId")
    long countByTeacherId(@Param("teacherId") UUID teacherId);
}
