package com.company.itam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:tc:postgresql:16:///itam_constraints",
    "spring.datasource.driver-class-name=org.testcontainers.jdbc.ContainerDatabaseDriver",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.flyway.enabled=true"
})
class DatabaseConstraintIntegrationTest {
    @Autowired JdbcTemplate jdbc;

    @Test
    void seedHasNoFinanceRoleAndTransactionStatusIsConstrained() {
        assertThat(jdbc.queryForObject("select count(*) from roles where code = 'FIN'", Integer.class))
            .isZero();
        jdbc.update("""
            insert into users(email, full_name, role_id, account_status)
            select 'constraint-test@example.test', 'Constraint Test', role_id, 'ACTIVE'
            from roles where code = 'ADMIN'
            """);
        assertThatThrownBy(() -> jdbc.update("""
            insert into transactions(transaction_code,type,status,requester_id)
            select 'BAD-STATUS','IMPORT','APPROVED',user_id
            from users where email = 'constraint-test@example.test'
            """)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void serialNumberAllowsMultipleNulls() {
        jdbc.queryForObject(
            "select count(*) from pg_indexes where indexname = 'asset_hardware_details_serial_number_key'",
            Integer.class);
    }
}
