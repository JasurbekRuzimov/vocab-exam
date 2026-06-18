package uz.vocabexam.repository;
import org.springframework.data.jpa.repository.*;
import uz.vocabexam.entity.*;
import java.util.*;

public interface WordRepository extends JpaRepository<Word, UUID> {
    List<Word> findByWordSetId(UUID wordSetId);
    boolean existsByWordSetIdAndTextIgnoreCase(UUID wordSetId, String text);
    void deleteByWordSetId(UUID wordSetId);
}
