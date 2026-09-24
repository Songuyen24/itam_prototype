package com.company.itam.publication;

import com.company.itam.asset.dto.request.*;
import com.company.itam.asset.service.AssetService;
import com.company.itam.publication.service.BilingualPdfGenerator;
import com.company.itam.publication.service.TransactionPublicationService;
import com.company.itam.workflow.handover.dto.*;
import com.company.itam.workflow.handover.service.HandoverService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "itam.documents.storage-root=target/t15-publication-storage")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HandoverPublicationIntegrationTest {
    @Autowired AssetService assets;
    @Autowired HandoverService handovers;
    @Autowired TransactionPublicationService publications;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired MockMvc mvc;
    @SpyBean BilingualPdfGenerator pdf;

    @BeforeEach void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "admin@itam.example", null, List.of(new SimpleGrantedAuthority("ADMIN"))));
    }

    private HandoverRequest request() {
        for (String category : List.of("DEVICE", "LICENSE")) {
            jdbc.update("INSERT INTO asset_types(code,name,category_id,is_active) SELECT ?,?,category_id,true FROM asset_categories WHERE code=? ON CONFLICT(code) DO NOTHING",
                    "T15_PDF_" + category, "Publication " + category, category);
        }
        jdbc.update("INSERT INTO locations(code,name,is_active) VALUES ('T15_PDF','Publication destination',true) ON CONFLICT(code) DO NOTHING");
        Long recipient = jdbc.queryForObject("INSERT INTO users(email,full_name,role_id,account_status) SELECT ?,'Original recipient',role_id,'ACTIVE' FROM roles WHERE code='USER' RETURNING user_id",
                Long.class, "handover-" + UUID.randomUUID() + "@itam.example");
        var device = new CreateHardwareAssetRequest();
        device.setCreationPurpose(AssetCreationPurpose.BASELINE); device.setName("Publication device");
        device.setTypeId(id("asset_types", "type_id", "T15_PDF_DEVICE"));
        Long deviceId = assets.createHardwareAsset(device).getAssetId();
        var license = new CreateHardwareAssetRequest();
        license.setCreationPurpose(AssetCreationPurpose.BASELINE); license.setName("Publication license");
        license.setTypeId(id("asset_types", "type_id", "T15_PDF_LICENSE"));
        Long software = jdbc.queryForObject("INSERT INTO software_catalog(name,manufacturer,is_active) VALUES (?,'Demo',true) RETURNING software_catalog_id", Long.class, "PDF " + UUID.randomUUID());
        license.setLicense(new LicenseDetailsRequest(software,
                id("license_assignment_types", "license_assignment_type_id", "PER_USER"),
                id("license_term_types", "license_term_type_id", "PERPETUAL"), 10, "DO-NOT-PUBLISH", null));
        Long packageId = assets.createHardwareAsset(license).getAssetId();
        var request = new HandoverRequest(recipient, id("locations", "location_id", "T15_PDF"),
                LocalDate.of(2026, 9, 10), List.of(deviceId), List.of(new HandoverRequest.LicenseLine(packageId, 2, null)), "Sample", null);
        return new HandoverRequest(request.recipientUserId(), request.destinationLocationId(), request.handoverDate(),
                request.assetIds(), request.licenses(), request.notes(), handovers.preview(request).fingerprint());
    }

    private Long id(String table, String column, String code) {
        return jdbc.queryForObject("SELECT " + column + " FROM " + table + " WHERE code=?", Long.class, code);
    }

    private Long complete(HandoverRequest request) throws Exception {
        String response = mvc.perform(post("/v1/handovers")
                .with(user("admin@itam.example").authorities(new SimpleGrantedAuthority("ADMIN")))
                .contentType("application/json").content(json.writeValueAsString(request)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        authenticate();
        return json.readTree(response).path("data").path("transactionId").asLong();
    }

    @ParameterizedTest @ValueSource(strings = {"regenerate", "resend"})
    void retriesFailedFirstPublicationFromCommittedHandover(String action) throws Exception {
        var request = request();
        String originalActor = jdbc.queryForObject("SELECT full_name FROM users WHERE email='admin@itam.example'", String.class);
        doThrow(new IllegalStateException("Simulated PDF failure")).when(pdf).generate(anyString(), anyString(), any(), anyString(), any());
        Long transactionId;
        try { transactionId = complete(request); }
        finally { reset(pdf); }
        assertThat(publications.status(transactionId).pdf().status()).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transaction_publication_snapshots WHERE transaction_id=?", Long.class, transactionId)).isZero();
        String frozen = jdbc.queryForObject("SELECT snapshot::text FROM handover_snapshots WHERE transaction_id=?", String.class, transactionId);
        String originalEmail = json.readTree(frozen).path("recipientEmail").asText();
        Long auditCount = jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE new_data->>'transactionId'=? OR (entity_type='TRANSACTION' AND entity_id=?)", Long.class, transactionId.toString(), transactionId);

        jdbc.update("UPDATE users SET full_name='Changed after handover' WHERE email='admin@itam.example'");
        try {
            var retried = action.equals("regenerate") ? publications.regenerate(transactionId) : publications.resend(transactionId);
            var actor = org.mockito.ArgumentCaptor.forClass(String.class);
            verify(pdf).generate(anyString(), eq("HANDOVER"), any(), actor.capture(), any());
            assertThat(actor.getValue()).isEqualTo(originalActor);
            assertThat(retried.pdf().status()).isEqualTo("READY");
            assertThat(retried.pdf().version()).isEqualTo(1);
            publications.resend(transactionId);
            assertThat(publications.status(transactionId).emails()).isNotEmpty().allSatisfy(email -> {
                assertThat(email.recipient()).isEqualTo(originalEmail);
                assertThat(email.status()).isEqualTo("SENT");
            });
            assertThat(jdbc.queryForObject("SELECT snapshot::text FROM handover_snapshots WHERE transaction_id=?", String.class, transactionId)).isEqualTo(frozen);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM license_allocations WHERE handover_transaction_id=?", Long.class, transactionId)).isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM transaction_assets WHERE transaction_id=?", Long.class, transactionId)).isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE new_data->>'transactionId'=? OR (entity_type='TRANSACTION' AND entity_id=?)", Long.class, transactionId.toString(), transactionId)).isEqualTo(auditCount);
            assertThat(jdbc.queryForObject("SELECT status FROM transactions WHERE transaction_id=?", String.class, transactionId)).isEqualTo("COMPLETED");
        } finally {
            jdbc.update("UPDATE users SET full_name=? WHERE email='admin@itam.example'", originalActor);
            reset(pdf);
            authenticate();
        }
    }

    @Test void resendUsesFrozenRecipientAfterProfileChanges() throws Exception {
        var request = request();
        Long transactionId = complete(request);
        String originalEmail = handovers.get(transactionId).recipientEmail();
        var first = publications.status(transactionId);
        String originalChecksum = jdbc.queryForObject("SELECT checksum FROM documents WHERE document_id=?", String.class, first.pdf().documentId());
        jdbc.update("UPDATE users SET email=?,full_name='Changed recipient' WHERE user_id=?", "changed-" + UUID.randomUUID() + "@itam.example", request.recipientUserId());
        var result = publications.resend(transactionId);
        assertThat(result.emails()).hasSize(2).allSatisfy(email -> assertThat(email.recipient()).isEqualTo(originalEmail));
        assertThat(result.pdf().documentId()).isEqualTo(first.pdf().documentId());
        assertThat(jdbc.queryForObject("SELECT checksum FROM documents WHERE document_id=?", String.class, first.pdf().documentId())).isEqualTo(originalChecksum);
    }
}
