package com.eip.backend.repository;
import com.eip.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;

public interface UserRepository extends JpaRepository<User, Integer> {
    Optional<User> findByEmail(String email);
    Optional<User> findByUsername(String username);
    List<User> findByIsActive(Boolean isActive);

    /** Whether any account holds the named role. */
    boolean existsByRoles_Name(String roleName);
}