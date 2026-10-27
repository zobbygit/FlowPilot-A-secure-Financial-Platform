package com.flowpilot.repo;

import com.flowpilot.domain.Role;
import com.flowpilot.domain.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    boolean existsByRole(Role role);
}
