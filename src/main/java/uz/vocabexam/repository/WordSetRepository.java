package uz.vocabexam.repository;
import org.springframework.data.jpa.repository.*;
import uz.vocabexam.entity.*;
import java.util.*;

public interface WordSetRepository extends JpaRepository<WordSet, UUID> {
    List<WordSet> findByTeacherIdOrderByCreatedAtDesc(UUID teacherId);
    @Query("SELECT ws FROM WordSet ws WHERE ws.teacher.id = :teacherId AND SIZE(ws.words) >= :minWords")
    List<WordSet> findByTeacherWithMinWords(@org.springframework.data.repository.query.Param("teacherId") UUID teacherId,
                                            @org.springframework.data.repository.query.Param("minWords") int minWords);
}
