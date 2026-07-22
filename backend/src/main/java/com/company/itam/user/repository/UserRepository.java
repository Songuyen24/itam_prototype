package com.company.itam.user.repository;

import com.company.itam.user.entity.UserEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, Long> {
    Optional<UserEntity> findByEmail(String email);
    boolean existsByEmail(String email);
    Page<UserEntity> findByFullNameContainingIgnoreCase(String name, Pageable pageable);
    Page<UserEntity> findByIsActive(Boolean isActive, Pageable pageable);

    @Query("SELECT u FROM UserEntity u LEFT JOIN FETCH u.role LEFT JOIN FETCH u.department WHERE u.userId = :id")
    Optional<UserEntity> findByIdWithDetails(@Param("id") Long id);
}
