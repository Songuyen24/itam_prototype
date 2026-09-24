package com.company.itam.publication;

import com.company.itam.asset.dto.request.CreateHardwareAssetRequest;
import com.company.itam.common.enums.DocumentType;
import com.company.itam.document.service.DocumentService;
import com.company.itam.publication.service.BilingualPdfGenerator;
import com.company.itam.publication.service.LocalEmailGateway;
import com.company.itam.workflow.core.dto.TransactionSummaryResponse;
import com.company.itam.workflow.receiving.service.ImportDraftService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest(properties = "itam.documents.storage-root=target/t17-import-publication-storage")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ImportPublicationIntegrationTest {
    @Autowired ImportDraftService drafts;
    @Autowired DocumentService documents;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @SpyBean BilingualPdfGenerator pdf;
    @SpyBean LocalEmailGateway emailGateway;

    @BeforeEach
    void authenticate() {
        login("pur01@itam.example", "PUR_STAFF");
    }

    @AfterEach
    void clearAuthentication() {
        reset(pdf, emailGateway);
        SecurityContextHolder.clearContext();
    }

    @Test
    void submitAndResubmitNotifyActiveItStaffWithTheSubmittedRevision() throws Exception {
        String purchaser = createUser("PUR_STAFF", "T17 purchaser");
        String reviewer = createUser("IT_STAFF", "T17 reviewer");
        var draft = ready(purchaser);

        var first = submit(draft, purchaser);
        var withdrawn = request("/v1/import-drafts/" + first.transactionId() + "/withdraw", purchaser, "PUR_STAFF", """
                {"expectedVersion":%d,"expectedSubmissionRevision":%d}
                """.formatted(first.expectedVersion(), first.submittedRevision()));
        var second = submit(withdrawn, purchaser);

        var messages = jdbc.queryForList("""
                SELECT content FROM email_logs
                WHERE transaction_id=? AND event_type='IMPORT_SUBMITTED' AND recipient=?
                ORDER BY email_log_id
                """, String.class, second.transactionId(), reviewer);
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0)).contains("Revision: 1");
        assertThat(messages.get(1)).contains("Revision: 2");
    }

    @Test
    void failedSubmitDoesNotSendReviewEmail() throws Exception {
        String purchaser = createUser("PUR_STAFF", "T17 invalid submitter");
        var draft = ready(purchaser);

        mvc.perform(post("/v1/import-drafts/{id}/submit", draft.transactionId())
                        .with(user(purchaser).authorities(new SimpleGrantedAuthority("PUR_STAFF")))
                        .contentType("application/json")
                        .content("{\"expectedVersion\":" + (draft.expectedVersion() + 1) + "}"))
                .andExpect(status().isConflict());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM email_logs WHERE transaction_id=?", Long.class,
                draft.transactionId())).isZero();
    }

    @Test
    void submittedRevisionFreezesSubmitterEmail() throws Exception {
        String purchaser = createUser("PUR_STAFF", "T17 frozen submitter");
        var submitted = submit(ready(purchaser), purchaser);

        String frozen = jdbc.queryForObject("""
                SELECT snapshot->>'submitterEmail' FROM transaction_revisions
                WHERE transaction_id=? AND revision=?
                """, String.class, submitted.transactionId(), submitted.submittedRevision());
        jdbc.update("UPDATE users SET email=? WHERE email=?", "changed-" + purchaser, purchaser);

        assertThat(frozen).isEqualTo(purchaser);
        assertThat(jdbc.queryForObject("""
                SELECT snapshot->>'submitterEmail' FROM transaction_revisions
                WHERE transaction_id=? AND revision=?
                """, String.class, submitted.transactionId(), submitted.submittedRevision())).isEqualTo(purchaser);
    }

    @Test
    void approvalAndResendUseSubmitterEmailFrozenAtSubmission() throws Exception {
        String purchaser = createUser("PUR_STAFF", "T17 email owner");
        var submitted = submit(ready(purchaser), purchaser);
        String changedOnce = "changed-once-" + purchaser;
        jdbc.update("UPDATE users SET email=? WHERE email=?", changedOnce, purchaser);

        request("/v1/import-drafts/" + submitted.transactionId() + "/approve", "it01@itam.example", "IT_STAFF", """
                {"expectedVersion":%d,"expectedSubmissionRevision":%d}
                """.formatted(submitted.expectedVersion(), submitted.submittedRevision()));

        assertThat(completionRecipients(submitted.transactionId())).containsExactly(purchaser);

        jdbc.update("UPDATE users SET email=? WHERE email=?", "changed-twice-" + purchaser, changedOnce);
        mvc.perform(post("/v1/transactions/{id}/email/resend", submitted.transactionId())
                        .with(user("it01@itam.example").authorities(new SimpleGrantedAuthority("IT_STAFF"))))
                .andExpect(status().isOk());

        assertThat(completionRecipients(submitted.transactionId())).containsExactly(purchaser, purchaser);
    }

    @Test
    void failedFirstApprovalPublicationRetriesWithFrozenProcessorName() throws Exception {
        String purchaser = createUser("PUR_STAFF", "T17 retry purchaser");
        String processor = createUser("IT_STAFF", "Original processor");
        var submitted = submit(ready(purchaser), purchaser);
        doThrow(new IllegalStateException("Simulated PDF failure"))
                .when(pdf).generate(anyString(), anyString(), any(), anyString(), any());

        request("/v1/import-drafts/" + submitted.transactionId() + "/approve", processor, "IT_STAFF", """
                {"expectedVersion":%d,"expectedSubmissionRevision":%d}
                """.formatted(submitted.expectedVersion(), submitted.submittedRevision()));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM transaction_publication_snapshots WHERE transaction_id=?",
                Long.class, submitted.transactionId())).isOne();
        assertThat(jdbc.queryForObject("SELECT snapshot->>'actorName' FROM transaction_publication_snapshots WHERE transaction_id=?",
                String.class, submitted.transactionId())).isEqualTo("Original processor");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM documents WHERE transaction_id=? AND publication_version IS NOT NULL",
                Long.class, submitted.transactionId())).isZero();

        jdbc.update("UPDATE users SET full_name='Changed processor' WHERE email=?", processor);
        reset(pdf);
        mvc.perform(post("/v1/transactions/{id}/pdf/regenerate", submitted.transactionId())
                        .with(user(processor).authorities(new SimpleGrantedAuthority("IT_STAFF"))))
                .andExpect(status().isOk());

        var actor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(pdf).generate(anyString(), eq("IMPORT"), any(), actor.capture(), any());
        assertThat(actor.getValue()).isEqualTo("Original processor");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "IT_STAFF"})
    void authorizedManagerRetriesRejectedEmailWithoutChangingTransactionOrCreatingPdf(String role) throws Exception {
        String purchaser = createUser("PUR_STAFF", "T17 rejected submitter");
        var submitted = submit(ready(purchaser), purchaser);
        String manager = "ADMIN".equals(role) ? "admin@itam.example" : "it01@itam.example";
        String reason = "The serial number does not match";
        reset(emailGateway);
        doThrow(new IllegalStateException("Simulated email failure")).when(emailGateway)
                .send(eq(purchaser), contains("Receiving rejected"), anyString());

        request("/v1/import-drafts/" + submitted.transactionId() + "/reject", manager, role, """
                {"expectedVersion":%d,"expectedSubmissionRevision":%d,"reason":"%s"}
                """.formatted(submitted.expectedVersion(), submitted.submittedRevision(), reason));

        var beforeRetry = jdbc.queryForMap("""
                SELECT status,content_version,submitted_revision,processed_by,rejection_reason,processed_at,completed_at
                FROM transactions WHERE transaction_id=?
                """, submitted.transactionId());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM documents WHERE transaction_id=? AND publication_version IS NOT NULL",
                Long.class, submitted.transactionId())).isZero();
        assertThat(rejectionAttempts(submitted.transactionId())).singleElement().satisfies(attempt -> {
            assertThat(attempt.get("recipient")).isEqualTo(purchaser);
            assertThat(attempt.get("status")).isEqualTo("FAILED");
            assertThat(attempt.get("content").toString()).contains(reason);
        });

        mvc.perform(post("/v1/transactions/{id}/email/resend", submitted.transactionId())
                        .with(user(purchaser).authorities(new SimpleGrantedAuthority("PUR_STAFF"))))
                .andExpect(status().isForbidden());
        reset(emailGateway);
        mvc.perform(post("/v1/transactions/{id}/email/resend", submitted.transactionId())
                        .with(user(manager).authorities(new SimpleGrantedAuthority(role))))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForMap("""
                SELECT status,content_version,submitted_revision,processed_by,rejection_reason,processed_at,completed_at
                FROM transactions WHERE transaction_id=?
                """, submitted.transactionId())).isEqualTo(beforeRetry);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM documents WHERE transaction_id=? AND publication_version IS NOT NULL",
                Long.class, submitted.transactionId())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM receiving_events WHERE transaction_id=? AND event_type='REJECTED'",
                Long.class, submitted.transactionId())).isOne();
        assertThat(rejectionAttempts(submitted.transactionId())).hasSize(2).allSatisfy(attempt -> {
            assertThat(attempt.get("recipient")).isEqualTo(purchaser);
            assertThat(attempt.get("content").toString()).contains(reason);
        }).extracting(attempt -> attempt.get("status")).containsExactly("FAILED", "SENT");
    }

    @Test
    void legacyRejectedResendUsesOriginalEventEmailAndRejectionReason() throws Exception {
        String purchaser = createUser("PUR_STAFF", "Legacy submitter");
        Long purchaserId = jdbc.queryForObject("SELECT user_id FROM users WHERE email=?", Long.class, purchaser);
        Long processorId = jdbc.queryForObject("SELECT user_id FROM users WHERE email='it01@itam.example'", Long.class);
        String originalRecipient = "legacy-original-" + UUID.randomUUID() + "@itam.example";
        String originalReason = "Frozen legacy rejection reason";
        Long id = jdbc.queryForObject("""
                INSERT INTO transactions(transaction_code,type,status,requester_id,processed_by,processed_at,
                    rejection_reason,content_version,submitted_revision)
                VALUES (?,'IMPORT','REJECTED',?,?,now(),'Changed mutable reason',2,1) RETURNING transaction_id
                """, Long.class, "IMP-LEGACY-" + UUID.randomUUID(), purchaserId, processorId);
        jdbc.update("INSERT INTO transaction_publication_snapshots(transaction_id,snapshot) VALUES (?,CAST('{" +
                "\"actorName\":\"Original processor\",\"assets\":[]}' AS jsonb))", id);
        jdbc.update("""
                INSERT INTO receiving_events(transaction_id,revision,event_type,actor_id,reason)
                VALUES (?,1,'REJECTED',?,?)
                """, id, processorId, originalReason);
        jdbc.update("""
                INSERT INTO email_logs(transaction_id,recipient,subject,content,status,error_message,event_type)
                VALUES (?,?,'Legacy rejection','Legacy failure','FAILED','Original delivery failed','IMPORT_REJECTED')
                """, id, originalRecipient);

        mvc.perform(post("/v1/transactions/{id}/email/resend", id)
                        .with(user("it01@itam.example").authorities(new SimpleGrantedAuthority("IT_STAFF"))))
                .andExpect(status().isOk());

        assertThat(rejectionAttempts(id)).hasSize(2);
        var retry = rejectionAttempts(id).get(1);
        assertThat(retry.get("recipient")).isEqualTo(originalRecipient);
        assertThat(retry.get("content").toString()).contains(originalReason).doesNotContain("Changed mutable reason");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM documents WHERE transaction_id=? AND publication_version IS NOT NULL",
                Long.class, id)).isZero();
    }

    @Test
    void legacyCompletedResendWithoutFrozenRecipientFailsBeforeGeneratingPdf() throws Exception {
        String purchaser = createUser("PUR_STAFF", "Legacy completed submitter");
        Long purchaserId = jdbc.queryForObject("SELECT user_id FROM users WHERE email=?", Long.class, purchaser);
        Long processorId = jdbc.queryForObject("SELECT user_id FROM users WHERE email='it01@itam.example'", Long.class);
        Long id = jdbc.queryForObject("""
                INSERT INTO transactions(transaction_code,type,status,requester_id,processed_by,processed_at,completed_at,
                    content_version,submitted_revision)
                VALUES (?,'IMPORT','COMPLETED',?,?,now(),now(),2,1) RETURNING transaction_id
                """, Long.class, "IMP-LEGACY-" + UUID.randomUUID(), purchaserId, processorId);
        jdbc.update("INSERT INTO transaction_publication_snapshots(transaction_id,snapshot) VALUES (?,CAST('{" +
                "\"actorName\":\"Original processor\",\"assets\":[]}' AS jsonb))", id);
        reset(pdf);

        mvc.perform(post("/v1/transactions/{id}/email/resend", id)
                        .with(user("it01@itam.example").authorities(new SimpleGrantedAuthority("IT_STAFF"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PUBLICATION_SNAPSHOT_MISSING"));

        verifyNoInteractions(pdf);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM documents WHERE transaction_id=? AND publication_version IS NOT NULL",
                Long.class, id)).isZero();
    }

    @Test
    void processingLegacyPendingRevisionCommitsBusinessStateWhenRecipientSnapshotIsMissing() throws Exception {
        String purchaser = createUser("PUR_STAFF", "Legacy pending submitter");
        Long purchaserId = jdbc.queryForObject("SELECT user_id FROM users WHERE email=?", Long.class, purchaser);
        Long id = jdbc.queryForObject("""
                INSERT INTO transactions(transaction_code,type,status,requester_id,content_version,submitted_revision)
                VALUES (?,'IMPORT','PENDING',?,5,1) RETURNING transaction_id
                """, Long.class, "IMP-LEGACY-" + UUID.randomUUID(), purchaserId);
        jdbc.update("""
                INSERT INTO transaction_revisions(transaction_id,revision,content_version,submitted_by,snapshot)
                VALUES (?,1,4,?,CAST('{"assets":[]}' AS jsonb))
                """, id, purchaserId);

        request("/v1/import-drafts/" + id + "/reject", "it01@itam.example", "IT_STAFF", """
                {"expectedVersion":5,"expectedSubmissionRevision":1,"reason":"Legacy data is invalid"}
                """);

        assertThat(jdbc.queryForObject("SELECT status FROM transactions WHERE transaction_id=?", String.class, id))
                .isEqualTo("REJECTED");
        assertThat(jdbc.queryForObject("SELECT snapshot->>'actorName' FROM transaction_publication_snapshots WHERE transaction_id=?",
                String.class, id)).isNotBlank();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM email_logs WHERE transaction_id=? AND event_type='IMPORT_REJECTED'",
                Long.class, id)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM publication_attempts WHERE transaction_id=? AND status='FAILED'",
                Long.class, id)).isOne();
    }

    @Test
    void legacyCompletedResendUsesExistingDocumentAndEventEmailWithoutRevision() throws Exception {
        String purchaser = createUser("PUR_STAFF", "Legacy no-revision submitter");
        Long purchaserId = jdbc.queryForObject("SELECT user_id FROM users WHERE email=?", Long.class, purchaser);
        Long processorId = jdbc.queryForObject("SELECT user_id FROM users WHERE email='it01@itam.example'", Long.class);
        String recipient = "legacy-completed-" + UUID.randomUUID() + "@itam.example";
        Long id = jdbc.queryForObject("""
                INSERT INTO transactions(transaction_code,type,status,requester_id,processed_by,processed_at,completed_at,
                    content_version,submitted_revision)
                VALUES (?,'IMPORT','COMPLETED',?,?,now(),now(),2,1) RETURNING transaction_id
                """, Long.class, "IMP-LEGACY-" + UUID.randomUUID(), purchaserId, processorId);
        String storedName = UUID.randomUUID() + ".pdf";
        Long documentId = jdbc.queryForObject("""
                INSERT INTO documents(transaction_id,document_type,original_file_name,stored_file_name,storage_path,
                    mime_type,file_size,checksum,uploaded_by,is_locked,publication_version,template_version,issued_at)
                VALUES (?,'IMPORT_RECEIPT','legacy-report.pdf',?,'import/' || ?,'application/pdf',8,'legacy',?,true,1,'legacy',now())
                RETURNING document_id
                """, Long.class, id, storedName, storedName, processorId);
        jdbc.update("""
                INSERT INTO email_logs(transaction_id,recipient,subject,content,status,event_type,document_id,sent_at)
                VALUES (?,?,'Legacy completion','Legacy sent','SENT','IMPORT_COMPLETED',?,now())
                """, id, recipient, documentId);
        reset(pdf);

        mvc.perform(post("/v1/transactions/{id}/email/resend", id)
                        .with(user("it01@itam.example").authorities(new SimpleGrantedAuthority("IT_STAFF"))))
                .andExpect(status().isOk());

        verifyNoInteractions(pdf);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM documents WHERE transaction_id=? AND document_type='IMPORT_RECEIPT'",
                Long.class, id)).isOne();
        assertThat(jdbc.queryForList("""
                SELECT recipient FROM email_logs WHERE transaction_id=? AND event_type='IMPORT_COMPLETED'
                ORDER BY email_log_id
                """, String.class, id)).containsExactly(recipient, recipient);
        assertThat(jdbc.queryForObject("""
                SELECT document_id FROM email_logs WHERE transaction_id=? AND event_type='IMPORT_COMPLETED'
                ORDER BY email_log_id DESC LIMIT 1
                """, Long.class, id)).isEqualTo(documentId);
    }

    private TransactionSummaryResponse ready(String purchaser) {
        login(purchaser, "PUR_STAFF");
        var draft = drafts.create("T17 publication", null);
        var asset = new CreateHardwareAssetRequest();
        asset.setName("T17 publication asset " + UUID.randomUUID());
        asset.setTypeId(jdbc.queryForObject("SELECT type_id FROM asset_types WHERE code='LAPTOP'", Long.class));
        asset.setConditionId(jdbc.queryForObject("SELECT min(condition_id) FROM asset_conditions WHERE is_active", Long.class));
        draft = drafts.addHardware(draft.transactionId(), draft.expectedVersion(), asset);
        documents.upload(new MockMultipartFile("file", "sample.pdf", "application/pdf",
                "%PDF-1.4\nT17 SAMPLE".getBytes(StandardCharsets.US_ASCII)), draft.transactionId(),
                DocumentType.INVOICE, null, draft.expectedVersion());
        return drafts.updateNotes(draft.transactionId(), draft.expectedVersion() + 1, "Ready for review");
    }

    private TransactionSummaryResponse submit(TransactionSummaryResponse draft, String purchaser) throws Exception {
        return request("/v1/import-drafts/" + draft.transactionId() + "/submit", purchaser, "PUR_STAFF",
                "{\"expectedVersion\":" + draft.expectedVersion() + "}");
    }

    private TransactionSummaryResponse request(String path, String email, String role, String body) throws Exception {
        String response = mvc.perform(post(path)
                        .with(user(email).authorities(new SimpleGrantedAuthority(role)))
                        .contentType("application/json").content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.treeToValue(json.readTree(response).path("data"), TransactionSummaryResponse.class);
    }

    private String createUser(String role, String name) {
        String email = role.toLowerCase() + "-" + UUID.randomUUID() + "@itam.example";
        jdbc.update("""
                INSERT INTO users(email,full_name,role_id,account_status)
                SELECT ?,?,role_id,'ACTIVE' FROM roles WHERE code=?
                """, email, name, role);
        return email;
    }

    private List<String> completionRecipients(Long transactionId) {
        return jdbc.queryForList("""
                SELECT recipient FROM email_logs
                WHERE transaction_id=? AND event_type='IMPORT_COMPLETED'
                ORDER BY email_log_id
                """, String.class, transactionId);
    }

    private List<java.util.Map<String, Object>> rejectionAttempts(Long transactionId) {
        return jdbc.queryForList("""
                SELECT recipient,status,content FROM email_logs
                WHERE transaction_id=? AND event_type='IMPORT_REJECTED'
                ORDER BY email_log_id
                """, transactionId);
    }

    private void login(String email, String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                email, null, List.of(new SimpleGrantedAuthority(role))));
    }
}
