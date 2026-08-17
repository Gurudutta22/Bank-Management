package com.gurudutta.bank.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByPublicId(String publicId);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByPhone(String phone);

    long countByRole(Role role);

    long countByCreatedAtAfter(Instant instant);

    @Query("""
            SELECT u FROM User u
            WHERE (:search IS NULL OR :search = ''
                   OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(u.email)    LIKE LOWER(CONCAT('%', :search, '%'))
                   OR u.phone           LIKE CONCAT('%', :search, '%'))
              AND (:role IS NULL OR u.role = :role)
            """)
    Page<User> search(@Param("search") String search, @Param("role") Role role, Pageable pageable);
}
