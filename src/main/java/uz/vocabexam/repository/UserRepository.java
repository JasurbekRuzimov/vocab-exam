package uz.vocabexam.repository;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.jpa.domain.Specification;
import uz.vocabexam.entity.*;
import java.util.*;

public interface UserRepository extends JpaRepository<User, UUID>,
    JpaSpecificationExecutor<User> {
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
    Optional<User> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    List<User> findByRole(Role role);
    Page<User> findByRole(Role role, Pageable pageable);
}