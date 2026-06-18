package uz.vocabexam.repository;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import uz.vocabexam.entity.*;
import java.util.*;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
    Page<AuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<AuditLog> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
}
