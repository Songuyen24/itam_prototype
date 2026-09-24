package com.company.itam.user.repository;

import com.company.itam.user.entity.UserEntity;
import com.company.itam.common.enums.AccountStatus;
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
    Page<UserEntity> findByAccountStatus(AccountStatus accountStatus, Pageable pageable);
    boolean existsByDepartmentDepartmentId(Long departmentId);

    /**
     * Tải user theo email kèm role/department để tránh LazyInit "no Session"
     * khi Spring Security đọc role ngay sau khi transaction repo đã đóng.
     */
    @Query("SELECT u FROM UserEntity u LEFT JOIN FETCH u.role LEFT JOIN FETCH u.department WHERE u.email = :email")
    Optional<UserEntity> findByEmailWithDetails(@Param("email") String email);

    @Query("SELECT u FROM UserEntity u LEFT JOIN FETCH u.role LEFT JOIN FETCH u.department WHERE u.userId = :id")
    Optional<UserEntity> findByIdWithDetails(@Param("id") Long id);

    /**
     * List all users with role + department eagerly loaded.
     * Prevents LazyInitializationException when UserResponse.fromEntity() accesses role/department.
     */
    @Query(value = "SELECT u FROM UserEntity u LEFT JOIN FETCH u.role LEFT JOIN FETCH u.department",
           countQuery = "SELECT COUNT(u) FROM UserEntity u")
    Page<UserEntity> findAllWithDetails(Pageable pageable);

    /**
     * Search users by name with role + department eagerly loaded.
     */
    @Query("SELECT u FROM UserEntity u LEFT JOIN FETCH u.role LEFT JOIN FETCH u.department WHERE LOWER(u.fullName) LIKE LOWER(CONCAT('%', :name, '%'))")
    Page<UserEntity> findByFullNameContainingIgnoreCaseWithDetails(@Param("name") String name, Pageable pageable);
}
