package com.company.itam.repository;

import com.company.itam.role.entity.RoleEntity;
import com.company.itam.role.repository.RoleRepository;
import com.company.itam.common.enums.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:tc:postgresql:16:///itam_test",
    "spring.datasource.driver-class-name=org.testcontainers.jdbc.ContainerDatabaseDriver",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.flyway.enabled=true",
    "spring.flyway.baseline-on-migrate=true"
})
class RoleRepositoryTest {

    @Autowired
    private RoleRepository roleRepository;

    @Test
    void findByCode_shouldReturnRole_whenExists() {
        Optional<RoleEntity> found = roleRepository.findByCode("ADMIN");
        assertThat(found).isPresent();
        assertThat(found.get().getCode()).isEqualTo(Role.ADMIN);
    }

    @Test
    void findByCode_shouldReturnEmpty_whenNotExists() {
        Optional<RoleEntity> found = roleRepository.findByCode("NONEXISTENT");
        assertThat(found).isEmpty();
    }

    @Test
    void existsByCode_shouldReturnTrue_whenRoleExists() {
        assertThat(roleRepository.existsByCode("IT")).isTrue();
        assertThat(roleRepository.existsByCode("PUR")).isTrue();
        assertThat(roleRepository.existsByCode("USER")).isTrue();
    }

    @Test
    void seedData_shouldContainFourRoles() {
        var allRoles = roleRepository.findAll();
        assertThat(allRoles).hasSize(4);
    }

    @Test
    void seedData_shouldNotContainFINRole() {
        assertThat(roleRepository.existsByCode("FIN")).isFalse();
    }
}
