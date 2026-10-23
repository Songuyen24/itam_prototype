package com.company.itam.audit;

import com.company.itam.auth.security.JwtTokenProvider;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuditLogIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtTokenProvider tokens;

    private String token(String role) {
        var id = jdbc.queryForObject("""
                insert into users(email, full_name, employee_code, role_id, password_hash, account_status, access_granted)
                select ?, ?, ?, role_id, 'test-hash', 'ACTIVE', true from roles where code = ? returning user_id
                """, Long.class, UUID.randomUUID() + "@example.test", role, UUID.randomUUID().toString(), role);
        return tokens.generateToken("user:" + id, role, 0L);
    }

    @Test
    void adminCanPageFilterOldEventsAndSecretsAreRedacted() throws Exception {
        String admin = token("ADMIN");
        var actor = jdbc.queryForObject("select max(user_id) from users where full_name = 'ADMIN'", Long.class);
        jdbc.update("""
                insert into audit_logs(entity_type, entity_id, actor_user_id, action, created_at, new_data)
                values ('ASSET', 555, ?, 'UPDATED', ?::timestamptz,
                        '{"status":"IN_STOCK","passwordHash":"hidden","nested":{"licenseKey":"hidden"}}'::jsonb)
                """, actor, Instant.now().minusSeconds(100L * 86400).toString());
        jdbc.update("insert into audit_logs(entity_type, entity_id, action) values ('USER', 0, 'LOGIN_FAILED')");
        jdbc.update("insert into audit_logs(entity_type, entity_id, actor_user_id, action, created_at) values ('ASSET', 555, ?, 'UPDATED', ?::timestamptz)",
                actor, Instant.now().minusSeconds(5L * 86400).toString());
        jdbc.update("insert into audit_logs(entity_type, entity_id, actor_user_id, action, created_at) values ('ASSET', 555, ?, 'UPDATED', ?::timestamptz)",
                actor, Instant.now().minusSeconds(2L * 86400).toString());
        mvc.perform(get("/v1/audit-logs").header("Authorization", "Bearer " + admin)
                        .param("assetId", "555").param("actorId", actor.toString())
                        .param("action", "UPDATED").param("page", "0").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.content[0].entityId").value(555))
                .andExpect(jsonPath("$.data.page").value(0));
        mvc.perform(get("/v1/audit-logs").header("Authorization", "Bearer " + admin)
                        .param("assetId", "555").param("actorId", actor.toString())
                        .param("action", "UPDATED").param("page", "2").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalPages").value(3))
                .andExpect(jsonPath("$.data.content[0].newData.passwordHash").value("[redacted]"))
                .andExpect(jsonPath("$.data.content[0].newData.nested.licenseKey").value("[redacted]"));
        mvc.perform(get("/v1/audit-logs").header("Authorization", "Bearer " + admin)
                        .param("assetId", "555").param("from", Instant.now().minusSeconds(6L * 86400).toString())
                        .param("to", Instant.now().minusSeconds(3L * 86400).toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1));
        mvc.perform(get("/v1/audit-logs").header("Authorization", "Bearer " + admin)
                        .param("action", "LOGIN_FAILED"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].actor").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void unknownLoginPersistsFailureWithoutInventedActor() throws Exception {
        long before = jdbc.queryForObject("select coalesce(max(audit_log_id), 0) from audit_logs", Long.class);
        try {
            mvc.perform(post("/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"unknown-audit@example.test\",\"password\":\"wrong-password\"}"))
                    .andExpect(status().isUnauthorized());
            var count = jdbc.queryForObject("select count(*) from audit_logs where audit_log_id > ? and action = 'LOGIN_FAILED' and actor_user_id is null", Integer.class, before);
            org.junit.jupiter.api.Assertions.assertEquals(1, count);
        } finally {
            jdbc.update("delete from audit_logs where audit_log_id > ? and action = 'LOGIN_FAILED' and actor_user_id is null", before);
        }
    }

    @Test
    void nonAdminsCannotReadEvents() throws Exception {
        for (String role : new String[]{"IT_STAFF", "PUR_STAFF", "USER"}) {
            mvc.perform(get("/v1/audit-logs").header("Authorization", "Bearer " + token(role)))
                    .andExpect(status().isForbidden());
        }
    }
}
