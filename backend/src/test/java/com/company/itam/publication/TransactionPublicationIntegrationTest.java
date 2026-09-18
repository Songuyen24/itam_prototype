package com.company.itam.publication;

import com.company.itam.publication.service.TransactionPublicationService;
import com.company.itam.publication.service.EmailGateway;
import com.company.itam.publication.service.LocalEmailGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.Loader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.mock.mockito.SpyBean;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

@SpringBootTest(properties = "itam.documents.storage-root=target/t17-storage")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TransactionPublicationIntegrationTest {
    @Autowired TransactionPublicationService publications;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired MockMvc mockMvc;
    @SpyBean LocalEmailGateway emailGateway;

    @BeforeEach
    void authenticate() {
        jdbc.update("INSERT INTO locations(code,name,is_active) VALUES ('T17_TEST','T17 test location',true) ON CONFLICT(code) DO NOTHING");
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    "admin@itam.example", "n/a", List.of(new SimpleGrantedAuthority("ADMIN"))));
        }
    }

    @Test
    void publishesVersionsAndEmailAttemptsWithoutChangingTransaction() throws Exception {
        Long admin = jdbc.queryForObject("SELECT user_id FROM users WHERE email='admin@itam.example'", Long.class);
        Long recipient = jdbc.queryForObject("SELECT user_id FROM users WHERE email='user01@itam.example'", Long.class);
        Long location = jdbc.queryForObject("SELECT location_id FROM locations WHERE code='T17_TEST'", Long.class);
        String code = "HO-T17-" + UUID.randomUUID();
        Long id = jdbc.queryForObject("""
                INSERT INTO transactions(transaction_code,type,status,requester_id,processed_by,processed_at,completed_at)
                VALUES (?,'HANDOVER','COMPLETED',?,?,now(),now()) RETURNING transaction_id
                """, Long.class, code, admin, admin);
        jdbc.update("INSERT INTO transaction_handover_details(transaction_id,recipient_user_id,handover_date,destination_location_id) VALUES (?,?,?,?)",
                id, recipient, LocalDate.now(), location);
        var snapshot = mapper.readTree("""
                {"recipientName":"Nguyễn Văn A","recipientEmail":"user01@itam.example",
                "handoverDate":"2026-09-10","destinationLocationName":"T17 test location",
                "lines":[{"assetId":1,"assetTag":"AST-T17","name":"Laptop mẫu","category":"DEVICE",
                "seats":0,"details":{"serialNumber":"SER-T17","actorName":"System Administrator"}}]}
                """);

        reset(emailGateway);
        publications.onCompleted(id, snapshot);
        var first = publications.status(id);
        assertThat(first.pdf().status()).isEqualTo("READY");
        assertThat(first.pdf().version()).isEqualTo(1);
        assertThat(first.emails()).hasSize(1);
        assertThat(first.emails().getFirst().recipient()).isEqualTo("user01@itam.example");
        assertThat(first.emails().getFirst().status()).isEqualTo("SENT");
        var attachment = org.mockito.ArgumentCaptor.forClass(EmailGateway.Attachment.class);
        verify(emailGateway).send(org.mockito.ArgumentMatchers.eq("user01@itam.example"),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), attachment.capture());
        assertThat(attachment.getValue().fileName()).endsWith(".pdf");
        assertThat(attachment.getValue().mimeType()).isEqualTo("application/pdf");
        assertThat(attachment.getValue().content()).startsWith("%PDF".getBytes());
        byte[] pdf = jdbc.queryForObject("SELECT storage_path FROM documents WHERE document_id=?", String.class, first.pdf().documentId()) == null
                ? new byte[0] : java.nio.file.Files.readAllBytes(java.nio.file.Path.of("target/t17-storage").toAbsolutePath().normalize()
                    .resolve(jdbc.queryForObject("SELECT storage_path FROM documents WHERE document_id=?", String.class, first.pdf().documentId())));
        try (var document = Loader.loadPDF(pdf)) { assertThat(document.getNumberOfPages()).isOne(); }

        publications.regenerate(id);
        publications.resend(id);
        var retried = publications.status(id);
        assertThat(retried.pdf().version()).isEqualTo(2);
        assertThat(retried.emails()).hasSize(2);
        assertThat(jdbc.queryForObject("SELECT status FROM transactions WHERE transaction_id=?", String.class, id)).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transaction_publication_snapshots WHERE transaction_id=?", Long.class, id)).isOne();
        mockMvc.perform(get("/v1/transactions/{id}/pdf", id)).andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
        mockMvc.perform(get("/v1/transactions/{id}/email-logs", id)).andExpect(status().isOk());
    }

    @Test
    void purchasingCannotRegenerateOrResend() throws Exception {
        var purchaser = user("pur01@itam.example").authorities(new SimpleGrantedAuthority("PUR_STAFF"));
        mockMvc.perform(post("/v1/transactions/1/pdf/regenerate").with(purchaser)).andExpect(status().isForbidden());
        mockMvc.perform(post("/v1/transactions/1/email/resend").with(purchaser)).andExpect(status().isForbidden());
    }

    @Test
    void endUserCannotEnumeratePublicationStatus() throws Exception {
        mockMvc.perform(get("/v1/transactions/1/publication")
                        .with(user("user01@itam.example").authorities(new SimpleGrantedAuthority("USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void purchasingCanReadOnlyImportPublicationEndpoints() throws Exception {
        Long importId = publishedImport();
        Long handoverId = publishedHandover("user01@itam.example");

        for (String suffix : List.of("/publication", "/email-logs", "/pdf")) {
            mockMvc.perform(get("/v1/transactions/{id}" + suffix, importId)
                            .with(user("pur01@itam.example").authorities(new SimpleGrantedAuthority("PUR_STAFF"))))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/v1/transactions/{id}" + suffix, handoverId)
                            .with(user("pur01@itam.example").authorities(new SimpleGrantedAuthority("PUR_STAFF"))))
                    .andExpect(status().isForbidden());
        }

        for (String suffix : List.of("/publication", "/email-logs", "/pdf")) {
            mockMvc.perform(get("/v1/transactions/{id}" + suffix, importId)
                            .with(user("it01@itam.example").authorities(new SimpleGrantedAuthority("IT_STAFF"))))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(get("/api/v1/transactions/{id}/publication", importId).contextPath("/api")
                        .with(user("pur01@itam.example").authorities(new SimpleGrantedAuthority("PUR_STAFF"))))
                .andExpect(status().isOk());
    }

    @Test
    void endUserCanDownloadOnlyOwnPublicationPdf() throws Exception {
        Long ownHandoverId = publishedHandover("user01@itam.example");
        Long otherHandoverId = publishedHandover(createUser("USER"));
        Long ownRecoveryId = publishedRecovery("user01@itam.example");
        Long otherRecoveryId = publishedRecovery(createUser("USER"));
        var endUser = user("user01@itam.example").authorities(new SimpleGrantedAuthority("USER"));

        mockMvc.perform(get("/v1/transactions/{id}/pdf", ownHandoverId).with(endUser))
                .andExpect(status().isOk());
        mockMvc.perform(get("/v1/transactions/{id}/pdf", otherHandoverId).with(endUser))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/v1/transactions/{id}/pdf", ownRecoveryId).with(endUser))
                .andExpect(status().isOk());
        mockMvc.perform(get("/v1/transactions/{id}/pdf", otherRecoveryId).with(endUser))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/v1/documents/{id}/download", publicationDocumentId(ownHandoverId)).with(endUser))
                .andExpect(status().isOk());
        mockMvc.perform(get("/v1/transactions/{id}/publication", ownHandoverId).with(endUser))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/v1/transactions/{id}/email-logs", ownHandoverId).with(endUser))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/v1/transactions/{id}/pdf", ownHandoverId).with(anonymous()))
                .andExpect(status().isUnauthorized());
    }

    private Long publishedImport() {
        Long purchaser = jdbc.queryForObject("SELECT user_id FROM users WHERE email='pur01@itam.example'", Long.class);
        Long admin = jdbc.queryForObject("SELECT user_id FROM users WHERE email='admin@itam.example'", Long.class);
        Long id = jdbc.queryForObject("""
                INSERT INTO transactions(transaction_code,type,status,requester_id,processed_by,processed_at,completed_at,
                    content_version,submitted_revision)
                VALUES (?,'IMPORT','COMPLETED',?,?,now(),now(),1,1) RETURNING transaction_id
                """, Long.class, "IMP-PUBLICATION-" + UUID.randomUUID(), purchaser, admin);
        jdbc.update("""
                INSERT INTO transaction_revisions(transaction_id,revision,content_version,submitted_by,snapshot)
                VALUES (?,1,1,?,CAST(? AS jsonb))
                """, id, purchaser, "{\"actorName\":\"System Administrator\",\"submitterEmail\":\"pur01@itam.example\",\"assets\":[]}");
        publications.onImportProcessed(id, true);
        return id;
    }

    private Long publishedHandover(String recipientEmail) throws Exception {
        Long admin = jdbc.queryForObject("SELECT user_id FROM users WHERE email='admin@itam.example'", Long.class);
        Long recipient = jdbc.queryForObject("SELECT user_id FROM users WHERE email=?", Long.class, recipientEmail);
        Long location = jdbc.queryForObject("SELECT location_id FROM locations WHERE code='T17_TEST'", Long.class);
        Long id = jdbc.queryForObject("""
                INSERT INTO transactions(transaction_code,type,status,requester_id,processed_by,processed_at,completed_at)
                VALUES (?,'HANDOVER','COMPLETED',?,?,now(),now()) RETURNING transaction_id
                """, Long.class, "HO-PUBLICATION-" + UUID.randomUUID(), admin, admin);
        jdbc.update("INSERT INTO transaction_handover_details(transaction_id,recipient_user_id,handover_date,destination_location_id) VALUES (?,?,?,?)",
                id, recipient, LocalDate.now(), location);
        publications.onCompleted(id, mapper.readTree("""
                {"recipientEmail":"%s","actorName":"System Administrator","lines":[]}
                """.formatted(recipientEmail)));
        return id;
    }

    private Long publishedRecovery(String returnerEmail) throws Exception {
        Long admin = jdbc.queryForObject("SELECT user_id FROM users WHERE email='admin@itam.example'", Long.class);
        Long returner = jdbc.queryForObject("SELECT user_id FROM users WHERE email=?", Long.class, returnerEmail);
        Long location = jdbc.queryForObject("SELECT location_id FROM locations WHERE code='T17_TEST'", Long.class);
        Long id = jdbc.queryForObject("""
                INSERT INTO transactions(transaction_code,type,status,requester_id,processed_by,processed_at,completed_at)
                VALUES (?,'RECOVERY','COMPLETED',?,?,now(),now()) RETURNING transaction_id
                """, Long.class, "RC-PUBLICATION-" + UUID.randomUUID(), admin, admin);
        jdbc.update("INSERT INTO transaction_recovery_details(transaction_id,returner_user_id,recovery_date,receiving_location_id,reason) VALUES (?,?,?,?,'Test')",
                id, returner, LocalDate.now(), location);
        publications.onCompleted(id, mapper.readTree("""
                {"returnerEmail":"%s","actorName":"System Administrator","lines":[]}
                """.formatted(returnerEmail)));
        return id;
    }

    private Long publicationDocumentId(Long transactionId) {
        return jdbc.queryForObject("SELECT document_id FROM documents WHERE transaction_id=? AND publication_version IS NOT NULL",
                Long.class, transactionId);
    }

    private String createUser(String role) {
        String email = role.toLowerCase() + "-publication-" + UUID.randomUUID() + "@itam.example";
        jdbc.update("""
                INSERT INTO users(email,full_name,role_id,account_status)
                SELECT ?,?,role_id,'ACTIVE' FROM roles WHERE code=?
                """, email, "Publication " + role, role);
        return email;
    }
}
