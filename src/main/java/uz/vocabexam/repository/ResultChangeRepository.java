package uz.vocabexam.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.vocabexam.entity.ResultChange;
import java.util.*;

public interface ResultChangeRepository extends JpaRepository<ResultChange, UUID> {
    List<ResultChange> findByResultIdOrderByChangedAtDesc(UUID resultId);
}
