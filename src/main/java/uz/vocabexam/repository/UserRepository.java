package uz.vocabexam.repository;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.jpa.domain.Specification;
import uz.vocabexam.entity.*;
import java.util.*;

public interface UserRepository extends JpaRepository<User, UUID>,
    JpaSpecificationExecutor<User> {
    @EntityGraph(attributePaths = "center") // kirishda markaz holati tekshiriladi
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
    @EntityGraph(attributePaths = "center")
    Optional<User> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    List<User> findByRole(Role role);
    Page<User> findByRole(Role role, Pageable pageable);
    long countByCenter_IdAndRole(UUID centerId, Role role);
    /** Markazlar ro'yxati uchun: [markaz id, rol, soni]. */
    @Query("SELECT u.center.id, u.role, COUNT(u) FROM User u WHERE u.center IS NOT NULL GROUP BY u.center.id, u.role")
    List<Object[]> countByCenterAndRole();
    List<User> findByCenter_IdAndRoleAndActiveTrueOrderByFullName(UUID centerId, Role role);
}