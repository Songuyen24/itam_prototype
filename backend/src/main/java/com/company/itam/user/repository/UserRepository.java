package com.company.itam.user.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.company.itam.common.enums.AccountStatus;
import com.company.itam.user.entity.UserEntity;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM UserEntity u WHERE u.userId = :id")
    Optional<UserEntity> findForAccessUpdate(@Param("id") Long id);

    default Optional<UserEntity> findByPrincipal(String principal) {
        if (principal == null || principal.isBlank()) {
            return Optional.empty();
        }
        if (principal != null && principal.startsWith("user:")) {
            try {
                return findByIdWithDetails(Long.parseLong(principal.substring(5)));
            } catch (NumberFormatException invalid) {
                return Optional.empty();
            }
        }
        return findByEmail(principal);
    }

    @Query(
            "SELECT u FROM UserEntity u LEFT JOIN FETCH u.role LEFT JOIN FETCH u.department WHERE LOWER(u.email) = LOWER(:login) OR LOWER(u.employeeCode) = LOWER(:login)")
    Optional<UserEntity> findByLogin(@Param("login") String login);

    @Query(
            value =
                    "SELECT u FROM UserEntity u LEFT JOIN FETCH u.role LEFT JOIN FETCH u.department WHERE u.passwordHash IS NOT NULL AND (LOWER(u.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(u.employeeCode) LIKE LOWER(CONCAT('%', :keyword, '%')))",
            countQuery =
                    "SELECT COUNT(u) FROM UserEntity u WHERE u.passwordHash IS NOT NULL AND (LOWER(u.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(u.employeeCode) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<UserEntity> findAccounts(@Param("keyword") String keyword, Pageable pageable);

    @Query(
            value =
                    "SELECT u FROM UserEntity u LEFT JOIN FETCH u.role LEFT JOIN FETCH u.department WHERE u.passwordHash IS NULL AND (LOWER(u.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(u.employeeCode) LIKE LOWER(CONCAT('%', :keyword, '%')))",
            countQuery =
                    "SELECT COUNT(u) FROM UserEntity u WHERE u.passwordHash IS NULL AND (LOWER(u.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(u.employeeCode) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<UserEntity> findProfilesWithoutAccount(@Param("keyword") String keyword, Pageable pageable);

    @Query(
            value =
                    "SELECT u FROM UserEntity u LEFT JOIN FETCH u.role LEFT JOIN FETCH u.department WHERE u.passwordHash IS NOT NULL AND u.role.code IN ('IT_STAFF', 'PUR_STAFF') AND (LOWER(u.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(u.employeeCode) LIKE LOWER(CONCAT('%', :keyword, '%')))",
            countQuery =
                    "SELECT COUNT(u) FROM UserEntity u WHERE u.passwordHash IS NOT NULL AND u.role.code IN ('IT_STAFF', 'PUR_STAFF') AND (LOWER(u.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(u.employeeCode) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<UserEntity> findReportAccounts(@Param("keyword") String keyword, Pageable pageable);

    Optional<UserEntity> findByEmail(String email);

    boolean existsByEmail(String email);

    Page<UserEntity> findByFullNameContainingIgnoreCase(String name, Pageable pageable);

    Page<UserEntity> findByAccountStatus(AccountStatus accountStatus, Pageable pageable);

    boolean existsByDepartmentDepartmentId(Long departmentId);

    /** Fetches role and department before Spring Security reads them outside the transaction. */
    @Query("SELECT u FROM UserEntity u LEFT JOIN FETCH u.role LEFT JOIN FETCH u.department WHERE u.email = :email")
    Optional<UserEntity> findByEmailWithDetails(@Param("email") String email);

    @Query("SELECT u FROM UserEntity u LEFT JOIN FETCH u.role LEFT JOIN FETCH u.department WHERE u.userId = :id")
    Optional<UserEntity> findByIdWithDetails(@Param("id") Long id);

    /**
     * List all users with role + department eagerly loaded.
     * Prevents LazyInitializationException when UserResponse.fromEntity() accesses role/department.
     */
    @Query(
            value = "SELECT u FROM UserEntity u LEFT JOIN FETCH u.role LEFT JOIN FETCH u.department",
            countQuery = "SELECT COUNT(u) FROM UserEntity u")
    Page<UserEntity> findAllWithDetails(Pageable pageable);

    /**
     * Search users by name with role + department eagerly loaded.
     */
    @Query(
            "SELECT u FROM UserEntity u LEFT JOIN FETCH u.role LEFT JOIN FETCH u.department WHERE LOWER(u.fullName) LIKE LOWER(CONCAT('%', :name, '%')) OR LOWER(u.email) LIKE LOWER(CONCAT('%', :name, '%')) OR LOWER(u.employeeCode) LIKE LOWER(CONCAT('%', :name, '%'))")
    Page<UserEntity> findByFullNameContainingIgnoreCaseWithDetails(@Param("name") String name, Pageable pageable);
}
