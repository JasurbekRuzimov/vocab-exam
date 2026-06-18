package uz.vocabexam.repository;
import org.springframework.data.jpa.repository.*;
import uz.vocabexam.entity.*;
import java.util.*;

public interface AnswerErrorRepository extends JpaRepository<AnswerError, UUID> {
    List<AnswerError> findByAnswerIdOrderByErrorStart(UUID answerId);
    void deleteByAnswerId(UUID answerId);
}
