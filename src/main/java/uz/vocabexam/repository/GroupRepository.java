package uz.vocabexam.repository;
import org.springframework.data.jpa.repository.*;
import uz.vocabexam.entity.*;
import java.util.*;

public interface GroupRepository extends JpaRepository<Group, UUID> {
    List<Group> findByTeacherId(UUID teacherId);
    long countByTeacherId(UUID teacherId);
    List<Group> findByTeacher_Center_Id(UUID centerId);
    long countByTeacher_Center_Id(UUID centerId);
    @Query("SELECT g FROM Group g JOIN g.students s WHERE s.id = :studentId")
    List<Group> findByStudentId(@org.springframework.data.repository.query.Param("studentId") UUID studentId);
    boolean existsByNameAndTeacherId(String name, UUID teacherId);
}
