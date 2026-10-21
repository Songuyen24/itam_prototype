package com.company.itam;

import com.company.itam.asset.dto.request.AssetCreationPurpose;
import com.company.itam.asset.dto.request.CreateHardwareAssetRequest;
import com.company.itam.asset.service.AssetService;
import com.company.itam.workflow.handover.dto.HandoverRequest;
import com.company.itam.workflow.handover.service.HandoverService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@WithMockUser(username = "admin@itam.example", authorities = "ADMIN")
class M4RecoveryLifecycleIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired AssetService assets;
    @Autowired HandoverService handovers;

    @BeforeEach
    void fixtures() {
        jdbc.update("UPDATE users SET employee_code='TEST-' || user_id WHERE email='user01@itam.example' AND employee_code IS NULL");
        jdbc.update("INSERT INTO asset_types(code,name,category_id,is_active) SELECT 'M4_DEVICE','M4 device',category_id,true FROM asset_categories WHERE code='DEVICE' ON CONFLICT(code) DO NOTHING");
        jdbc.update("INSERT INTO locations(code,name,is_active) VALUES ('M4_TEST','M4 return store',true) ON CONFLICT(code) DO NOTHING");
    }

    @Test
    void createIsDraftAndSubmitReservesWithoutReturningAsset() throws Exception {
        Long device = createDevice();
        handover(device);
        Map<String, Object> request = request(device);

        JsonNode draft = createRecovery(request);
        long transactionId = draft.path("transactionId").asLong();
        assertThat(transactionId).isPositive();
        assertThat(draft.path("status").asText()).isEqualTo("DRAFT");
        assertThat(state(device)).isEqualTo("IN_USE");
        assertThat(holder(device)).isEqualTo(returner());

        mvc.perform(post("/v1/recoveries/{id}/submit", transactionId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PENDING"));
        assertThat(state(device)).isEqualTo("IN_USE");
        assertThat(holder(device)).isEqualTo(returner());
    }

    @Test
    void withdrawReleasesReservationAndConfirmCompletesOnlyOnce() throws Exception {
        Long device = createDevice();
        handover(device);
        Map<String, Object> body = request(device);
        long id = createRecovery(body).path("transactionId").asLong();
        mvc.perform(post("/v1/recoveries/{id}/submit", id)).andExpect(status().isOk());
        mvc.perform(post("/v1/recoveries/{id}/withdraw", id))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("DRAFT"));
        assertThat(state(device)).isEqualTo("IN_USE");
        assertThat(holder(device)).isEqualTo(returner());

        mvc.perform(post("/v1/recoveries/{id}/submit", id)).andExpect(status().isOk());
        mvc.perform(post("/v1/recoveries/{id}/confirm", id))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("COMPLETED"));
        mvc.perform(post("/v1/recoveries/{id}/confirm", id))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("COMPLETED"));
        assertThat(state(device)).isEqualTo("IN_STOCK");
        assertThat(holder(device)).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transaction_assets WHERE transaction_id=? AND asset_id=?", Integer.class, id, device)).isOne();
        mvc.perform(put("/v1/recoveries/{id}", id).contentType("application/json")
                .content(json.writeValueAsString(body))).andExpect(status().isConflict());
    }

    @Test
    void historyPersistsAndDuplicatePendingCannotReserveSameDevice() throws Exception {
        Long device = createDevice();
        handover(device);
        Map<String, Object> body = request(device);
        long first = createRecovery(body).path("transactionId").asLong();
        long second = createRecovery(body).path("transactionId").asLong();
        mvc.perform(post("/v1/recoveries/{id}/submit", first)).andExpect(status().isOk());
        mvc.perform(post("/v1/recoveries/{id}/submit", second)).andExpect(status().isConflict());
        mvc.perform(get("/v1/recoveries/{id}", first))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PENDING"));
        mvc.perform(get("/v1/recoveries").param("status", "PENDING").param("page", "0").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].transactionId").value(first));
        mvc.perform(post("/v1/recoveries/{id}/withdraw", first)).andExpect(status().isOk());
        mvc.perform(post("/v1/recoveries/{id}/submit", second)).andExpect(status().isOk());
    }

    @Test
    void creatorOwnsDraftAndPendingWhilePeerItCanConfirm() throws Exception {
        Long device = createDevice();
        handover(device);
        Map<String, Object> body = request(device);
        String creator = itStaff();
        String peer = otherItStaff();
        String response = mvc.perform(post("/v1/recoveries").with(user(creator).authorities(() -> "IT_STAFF"))
                .contentType("application/json").content(json.writeValueAsString(body)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = json.readTree(response).at("/data/transactionId").asLong();
        mvc.perform(put("/v1/recoveries/{id}", id).with(user(peer).authorities(() -> "IT_STAFF"))
                .contentType("application/json").content(json.writeValueAsString(body))).andExpect(status().isForbidden());
        mvc.perform(put("/v1/recoveries/{id}", id).with(user(creator).authorities(() -> "IT_STAFF"))
                .contentType("application/json").content(json.writeValueAsString(body))).andExpect(status().isOk());
        mvc.perform(post("/v1/recoveries/{id}/submit", id).with(user(creator).authorities(() -> "IT_STAFF"))).andExpect(status().isOk());
        mvc.perform(post("/v1/recoveries/{id}/cancel", id).with(user(creator).authorities(() -> "IT_STAFF"))).andExpect(status().isForbidden());
        mvc.perform(post("/v1/recoveries/{id}/confirm", id).with(user(peer).authorities(() -> "IT_STAFF")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }

    @Test
    void adminOverrideRequiresReasonAndWritesAudit() throws Exception {
        Long device = createDevice();
        handover(device);
        String creator = itStaff();
        String response = mvc.perform(post("/v1/recoveries").with(user(creator).authorities(() -> "IT_STAFF"))
                        .contentType("application/json").content(json.writeValueAsString(request(device))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = json.readTree(response).at("/data/transactionId").asLong();
        mvc.perform(post("/v1/recoveries/{id}/submit", id).with(user(creator).authorities(() -> "IT_STAFF"))).andExpect(status().isOk());
        mvc.perform(post("/v1/recoveries/{id}/cancel", id)).andExpect(status().isBadRequest());
        mvc.perform(post("/v1/recoveries/{id}/cancel", id).header("X-Override-Reason", "Correct scheduled return"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CANCELLED"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE entity_id=? AND action='ADMIN_OVERRIDE'", Integer.class, id)).isOne();
        assertThat(state(device)).isEqualTo("IN_USE");
    }

    private JsonNode createRecovery(Map<String, Object> request) throws Exception {
        String body = mvc.perform(post("/v1/recoveries").contentType("application/json")
                        .content(json.writeValueAsString(request)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).path("data");
    }

    private Map<String, Object> request(Long device) throws Exception {
        String body = mvc.perform(post("/v1/recoveries/smart-check").contentType("application/json")
                        .content(json.writeValueAsString(Map.of("assetIds", List.of(device), "allocationIds", List.of(), "returnerUserId", returner(), "reason", "M4 return"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return Map.of("returnerUserId", returner(), "receivingLocationId", location(), "recoveryDate", "2026-10-21",
                "reason", "M4 return", "assetIds", List.of(device), "allocationIds", List.of(),
                "componentActions", Map.of(), "perUserActions", Map.of(),
                "expectedFingerprint", json.readTree(body).at("/data/fingerprint").asText());
    }

    private Long createDevice() {
        var request = new CreateHardwareAssetRequest();
        request.setCreationPurpose(AssetCreationPurpose.BASELINE);
        request.setName("M4 device " + UUID.randomUUID());
        request.setTypeId(jdbc.queryForObject("SELECT type_id FROM asset_types WHERE code='M4_DEVICE'", Long.class));
        return assets.createHardwareAsset(request).getAssetId();
    }

    private void handover(Long device) {
        var request = new HandoverRequest(returner(), location(), LocalDate.of(2026, 10, 20), List.of(device), List.of(), "M4 setup", null);
        handovers.complete(new HandoverRequest(request.recipientUserId(), request.destinationLocationId(), request.handoverDate(), request.assetIds(), request.licenses(), request.notes(), handovers.preview(request).fingerprint()));
    }

    private Long returner() { return jdbc.queryForObject("SELECT user_id FROM users WHERE email='user01@itam.example'", Long.class); }
    private String itStaff() { return jdbc.queryForObject("SELECT email FROM users u JOIN roles r USING(role_id) WHERE r.code='IT_STAFF' ORDER BY user_id LIMIT 1", String.class); }
    private String otherItStaff() {
        String email = "m4-it-" + UUID.randomUUID() + "@itam.example";
        jdbc.update("INSERT INTO users(email,full_name,employee_code,role_id,account_status) SELECT ?,'M4 peer',?,role_id,'ACTIVE' FROM roles WHERE code='IT_STAFF'", email, "M4-" + UUID.randomUUID());
        return email;
    }
    private Long location() { return jdbc.queryForObject("SELECT location_id FROM locations WHERE code='M4_TEST'", Long.class); }
    private String state(Long assetId) { return jdbc.queryForObject("SELECT s.code FROM assets a JOIN asset_statuses s USING(status_id) WHERE asset_id=?", String.class, assetId); }
    private Long holder(Long assetId) { return jdbc.queryForObject("SELECT assigned_to FROM assets WHERE asset_id=?", Long.class, assetId); }
}
