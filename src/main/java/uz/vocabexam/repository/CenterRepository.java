package uz.vocabexam.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.vocabexam.entity.Center;
import java.util.*;

public interface CenterRepository extends JpaRepository<Center, UUID> {
    boolean existsBySlug(String slug);
    List<Center> findAllByOrderByName();
}
