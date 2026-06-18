package uz.vocabexam.repository;
import org.springframework.data.jpa.repository.*;
import uz.vocabexam.entity.*;
import java.util.*;

public interface ExamVariantRepository extends JpaRepository<ExamVariant, UUID> {
    List<ExamVariant> findByExamIdOrderByVariantNumber(UUID examId);
    void deleteByExamId(UUID examId);
}
