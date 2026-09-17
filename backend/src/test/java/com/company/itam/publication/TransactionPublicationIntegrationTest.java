package com.company.itam.publication;

import com.company.itam.publication.service.TransactionPublicationService;
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
import org.springframework.security.test.context.support.WithMockUser;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

@SpringBootTest(properties = "itam.documents.storage-root=target/t17-storage")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TransactionPublicationIntegrationTest {
    @Autowired TransactionPublicationService publications;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired MockMvc mockMvc;

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

        publications.onCompleted(id, snapshot);
        var first = publications.status(id);
        assertThat(first.pdf().status()).isEqualTo("READY");
        assertThat(first.pdf().version()).isEqualTo(1);
        assertThat(first.emails()).hasSize(1);
        assertThat(first.emails().getFirst().recipient()).isEqualTo("user01@itam.example");
        assertThat(first.emails().getFirst().status()).isEqualTo("SENT");
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
    @WithMockUser(authorities = "PUR_STAFF")
    void purchasingCannotRegenerateOrResend() throws Exception {
        mockMvc.perform(post("/v1/transactions/1/pdf/regenerate")).andExpect(status().isForbidden());
        mockMvc.perform(post("/v1/transactions/1/email/resend")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "USER")
    void endUserCannotEnumeratePublicationStatus() throws Exception {
        mockMvc.perform(get("/v1/transactions/1/publication")).andExpect(status().isForbidden());
    }
}
