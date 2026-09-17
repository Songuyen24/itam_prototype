package com.company.itam;

import com.company.itam.asset.dto.request.AssetCreationPurpose;
import com.company.itam.asset.dto.request.CreateHardwareAssetRequest;
import com.company.itam.asset.dto.request.LicenseDetailsRequest;
import com.company.itam.asset.relationship.AssetRelationshipService;
import com.company.itam.asset.relationship.enums.RelationshipType;
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
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@WithMockUser(username = "admin@itam.example", authorities = "ADMIN")
class T16RecoveryIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired jakarta.persistence.EntityManager em;
    @Autowired AssetService assets;
    @Autowired AssetRelationshipService relationships;
    @Autowired HandoverService handovers;

    @BeforeEach
    void fixtures() {
        for (String category : List.of("DEVICE", "COMPONENT", "LICENSE")) {
            jdbc.update("INSERT INTO asset_types(code,name,category_id,is_active) SELECT ?,?,category_id,true FROM asset_categories WHERE code=? ON CONFLICT(code) DO NOTHING",
                    "T16_" + category, "T16 " + category, category);
        }
        jdbc.update("INSERT INTO locations(code,name,is_active) VALUES ('T16_TEST','T16 return store',true) ON CONFLICT(code) DO NOTHING");
    }

    @Test
    void recoveryCandidatesFilterByReturnerAndPageOnPostgres() throws Exception {
        String marker = "T16 candidates " + UUID.randomUUID();
        Long owner = otherUser(), first = create("DEVICE", marker), other = create("DEVICE", marker), packageId = license("PER_USER", 2);
        inUse(first, owner); inUse(other, returner());
        jdbc.update("UPDATE assets SET name=? WHERE asset_id=?", marker, packageId);
        handovers.complete(confirmed(owner, List.of(), List.of(new HandoverRequest.LicenseLine(packageId, 1, null))));
        handovers.complete(confirmed(owner, List.of(), List.of(new HandoverRequest.LicenseLine(packageId, 1, null))));

        String page0 = mvc.perform(get("/v1/assets/recovery-candidates").param("userId", owner.toString()).param("size", "1").param("page", "0"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(3)).andReturn().getResponse().getContentAsString();
        String page1 = mvc.perform(get("/v1/assets/recovery-candidates").param("userId", owner.toString()).param("keyword", marker).param("size", "1").param("page", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(3)).andReturn().getResponse().getContentAsString();
        String page2 = mvc.perform(get("/v1/assets/recovery-candidates").param("userId", owner.toString()).param("keyword", marker).param("size", "1").param("page", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(3)).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(page0).at("/data/content/0/assetId").asLong()).isEqualTo(first);
        assertThat(json.readTree(page1).at("/data/content/0/allocationId").asLong()).isPositive();
        assertThat(json.readTree(page2).at("/data/content/0/allocationId").asLong()).isGreaterThan(json.readTree(page1).at("/data/content/0/allocationId").asLong());
        assertThat(json.readTree(page1).at("/data/content/0/deviceTag").isNull()).isTrue();
        assertThat(json.readTree(page1).at("/data/content/0/seats").asInt()).isEqualTo(1);
        mvc.perform(get("/v1/assets/recovery-candidates").param("userId", owner.toString()).param("keyword", tag(first)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void deviceRecoveryReturnsItsBundleAndAppliesEachPerUserChoice() throws Exception {
        Long device = create("DEVICE"), peer = create("DEVICE"), component = create("COMPONENT"), oem = license("OEM", 2), release = license("PER_USER", 2), retain = license("PER_USER", 2);
        relationships.create(device, component, RelationshipType.COMPONENT_OF);
        relationships.create(device, oem, RelationshipType.INSTALLED_ON);
        relationships.create(peer, oem, RelationshipType.INSTALLED_ON);
        handovers.complete(confirmed(List.of(device, peer), List.of(new HandoverRequest.LicenseLine(release, 1, device), new HandoverRequest.LicenseLine(retain, 1, device))));
        Long releasedAllocation = allocation(release, device), retainedAllocation = allocation(retain, device), peerOemAllocation = allocation(oem, peer);

        JsonNode check = smart(List.of(device), List.of());
        JsonNode result = complete(Map.of(
                "returnerUserId", returner(), "receivingLocationId", location(), "recoveryDate", "2026-09-17", "reason", "T16 return bundle",
                "assetIds", List.of(device), "allocationIds", List.of(),
                "componentActions", Map.of(component.toString(), Map.of("componentAction", "KEEP_ATTACHED", "recoverPerUser", false)),
                "perUserActions", Map.of(releasedAllocation.toString(), true, retainedAllocation.toString(), false), "expectedFingerprint", check.path("fingerprint").asText()));

        Long tx = result.at("/data/transactionId").asLong();
        assertThat(result.at("/data/lines").toString()).contains(device.toString(), component.toString(), oem.toString(), release.toString(), retain.toString());
        assertThat(jdbc.queryForObject("SELECT requester_id IS NOT NULL AND processed_by IS NOT NULL FROM transactions WHERE transaction_id=?", Boolean.class, tx)).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*)=count(DISTINCT line_number) AND min(line_number)=1 FROM transaction_assets WHERE transaction_id=?", Boolean.class, tx)).isTrue();
        assertThat(state(device)).isEqualTo("IN_STOCK"); assertThat(state(component)).isEqualTo("IN_STOCK");
        assertThat(jdbc.queryForObject("SELECT status FROM license_allocations WHERE allocation_id=?", String.class, releasedAllocation)).isEqualTo("RELEASED");
        assertThat(jdbc.queryForObject("SELECT recovery_transaction_id IS NOT NULL FROM license_allocations WHERE allocation_id=?", Boolean.class, releasedAllocation)).isTrue();
        assertThat(jdbc.queryForObject("SELECT status FROM license_allocations WHERE allocation_id=?", String.class, retainedAllocation)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT device_asset_id IS NULL FROM license_allocations WHERE allocation_id=?", Boolean.class, retainedAllocation)).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM asset_relationships WHERE allocation_id IN (?,?)", Integer.class, releasedAllocation, retainedAllocation)).isZero();
        assertThat(jdbc.queryForObject("SELECT device_asset_id=? AND user_id IS NULL AND status='RESERVED' FROM license_allocations WHERE license_asset_id=? AND device_asset_id=?", Boolean.class, device, oem, device)).isTrue();
        assertThat(jdbc.queryForObject("SELECT device_asset_id=? AND user_id=? AND status='ACTIVE' FROM license_allocations WHERE allocation_id=?", Boolean.class, peer, returner(), peerOemAllocation)).isTrue();
        assertThat(jdbc.queryForObject("SELECT sum(seat_count) FROM license_allocations WHERE license_asset_id=? AND status<>'RELEASED'", Integer.class, oem)).isEqualTo(2);
        handovers.complete(confirmed(List.of(device), List.of()));
        assertThat(jdbc.queryForObject("SELECT status='ACTIVE' AND device_asset_id=? FROM license_allocations WHERE license_asset_id=? AND device_asset_id=?", Boolean.class, device, oem, device)).isTrue();
        assertThat(jdbc.queryForObject("SELECT sum(seat_count) FROM license_allocations WHERE license_asset_id=? AND status<>'RELEASED'", Integer.class, oem)).isEqualTo(2);
    }

    @Test
    void recoveryGroupsSharedOemPackageOnceForMultipleSelectedDevices() throws Exception {
        Long first = create("DEVICE"), second = create("DEVICE"), oem = license("OEM", 2);
        relationships.create(first, oem, RelationshipType.INSTALLED_ON); relationships.create(second, oem, RelationshipType.INSTALLED_ON);
        handovers.complete(confirmed(List.of(first, second), List.of()));
        JsonNode check = smart(List.of(first, second), List.of());
        JsonNode result = complete(Map.of("returnerUserId", returner(), "receivingLocationId", location(), "recoveryDate", "2026-09-17", "reason", "T16 shared OEM",
                "assetIds", List.of(first, second), "allocationIds", List.of(), "componentActions", Map.of(), "perUserActions", Map.of(), "expectedFingerprint", check.path("fingerprint").asText()));
        Long tx = result.at("/data/transactionId").asLong();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transaction_assets WHERE transaction_id=? AND asset_id=?", Integer.class, tx, oem)).isEqualTo(1);
        assertThat(line(result.path("data"), oem).path("allocations").size()).isEqualTo(2);
    }

    @Test
    void standalonePerUserAllocationAndComponentAreRecoverableWithoutReturningTheirParent() throws Exception {
        Long device = create("DEVICE"), component = create("COMPONENT"), packageId = license("PER_USER", 1);
        relationships.create(device, component, RelationshipType.COMPONENT_OF);
        handovers.complete(confirmed(List.of(device), List.of(new HandoverRequest.LicenseLine(packageId, 1, null))));
        Long allocation = jdbc.queryForObject("SELECT allocation_id FROM license_allocations WHERE license_asset_id=?", Long.class, packageId);

        JsonNode perUserCheck = smart(List.of(), List.of(allocation));
        assertThat(perUserCheck.path("blockedAssets").size()).isZero();
        complete(Map.of("returnerUserId", returner(), "receivingLocationId", location(), "recoveryDate", "2026-09-17", "reason", "T16 standalone license",
                "assetIds", List.of(), "allocationIds", List.of(allocation), "componentActions", Map.of(), "perUserActions", Map.of(allocation.toString(), true), "expectedFingerprint", perUserCheck.path("fingerprint").asText()));
        assertThat(jdbc.queryForObject("SELECT status FROM license_allocations WHERE allocation_id=?", String.class, allocation)).isEqualTo("RELEASED");

        JsonNode componentCheck = smart(List.of(component), List.of());
        assertThat(componentCheck.path("blockedAssets").size()).isZero();
        complete(Map.of("returnerUserId", returner(), "receivingLocationId", location(), "recoveryDate", "2026-09-17", "reason", "T16 detach component",
                "assetIds", List.of(component), "allocationIds", List.of(), "componentActions", Map.of(component.toString(), Map.of("componentAction", "DETACH", "recoverPerUser", false)), "perUserActions", Map.of(), "expectedFingerprint", componentCheck.path("fingerprint").asText()));
        assertThat(state(device)).isEqualTo("IN_USE"); assertThat(state(component)).isEqualTo("IN_STOCK");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM asset_relationships WHERE parent_asset_id=? AND child_asset_id=?", Integer.class, device, component)).isZero();
    }

    @Test
    void rejectsStandaloneAllocationOwnedByAnotherUserOrAlreadyReleased() throws Exception {
        Long other = otherUser(), foreignPackage = license("PER_USER", 1);
        handovers.complete(confirmed(other, List.of(), List.of(new HandoverRequest.LicenseLine(foreignPackage, 1, null))));
        Long foreign = jdbc.queryForObject("SELECT allocation_id FROM license_allocations WHERE license_asset_id=?", Long.class, foreignPackage);
        JsonNode foreignCheck = smart(List.of(), List.of(foreign));
        rejectComplete(Map.of("returnerUserId", returner(), "receivingLocationId", location(), "recoveryDate", "2026-09-17", "reason", "T16 foreign allocation",
                "assetIds", List.of(), "allocationIds", List.of(foreign), "componentActions", Map.of(), "perUserActions", Map.of(foreign.toString(), true), "expectedFingerprint", foreignCheck.path("fingerprint").asText()));
        assertThat(jdbc.queryForObject("SELECT status FROM license_allocations WHERE allocation_id=?", String.class, foreign)).isEqualTo("ACTIVE");

        Long releasedPackage = license("PER_USER", 1);
        handovers.complete(confirmed(List.of(), List.of(new HandoverRequest.LicenseLine(releasedPackage, 1, null))));
        Long released = jdbc.queryForObject("SELECT allocation_id FROM license_allocations WHERE license_asset_id=?", Long.class, releasedPackage);
        jdbc.update("UPDATE license_allocations SET status='RELEASED', released_at=current_timestamp WHERE allocation_id=?", released);
        em.clear();
        JsonNode releasedCheck = smart(List.of(), List.of(released));
        rejectComplete(Map.of("returnerUserId", returner(), "receivingLocationId", location(), "recoveryDate", "2026-09-17", "reason", "T16 released allocation",
                "assetIds", List.of(), "allocationIds", List.of(released), "componentActions", Map.of(), "perUserActions", Map.of(released.toString(), true), "expectedFingerprint", releasedCheck.path("fingerprint").asText()));
        assertThat(jdbc.queryForObject("SELECT status FROM license_allocations WHERE allocation_id=?", String.class, released)).isEqualTo("RELEASED");
    }

    @Test
    void rejectsOemAloneAndRequiresFreshFingerprintBeforeWriting() throws Exception {
        Long device = create("DEVICE"), component = create("COMPONENT"), oem = license("OEM", 1);
        relationships.create(device, component, RelationshipType.COMPONENT_OF);
        relationships.create(device, oem, RelationshipType.INSTALLED_ON);
        handovers.complete(confirmed(List.of(device), List.of()));
        assertThat(smart(List.of(oem), List.of()).path("blockedAssets").size()).isPositive();

        Map<String, Object> request = Map.of("returnerUserId", returner(), "receivingLocationId", location(), "recoveryDate", "2026-09-17", "reason", "T16 fingerprint",
                "assetIds", List.of(device), "allocationIds", List.of(), "componentActions", Map.of(), "perUserActions", Map.of(), "expectedFingerprint", "stale");
        mvc.perform(post("/v1/recoveries").contentType("application/json").content(json.writeValueAsString(request))).andExpect(status().isConflict());
        JsonNode beforeComponentChange = smart(List.of(device), List.of());
        jdbc.update("UPDATE assets SET version=version+1 WHERE asset_id=?", component);
        em.clear();
        rejectComplete(Map.of("returnerUserId", returner(), "receivingLocationId", location(), "recoveryDate", "2026-09-17", "reason", "T16 stale component",
                "assetIds", List.of(device), "allocationIds", List.of(), "componentActions", Map.of(component.toString(), Map.of("componentAction", "KEEP_ATTACHED", "recoverPerUser", false)), "perUserActions", Map.of(), "expectedFingerprint", beforeComponentChange.path("fingerprint").asText()));
        JsonNode check = smart(List.of(device), List.of());
        mvc.perform(post("/v1/recoveries").contentType("application/json").content(json.writeValueAsString(Map.of(
                "returnerUserId", returner(), "receivingLocationId", location(), "recoveryDate", "2026-09-17", "reason", "T16 invalid action", "assetIds", List.of(device), "allocationIds", List.of(),
                "componentActions", Map.of(component.toString(), Map.of("componentAction", "UNSUPPORTED", "recoverPerUser", false)), "perUserActions", Map.of(), "expectedFingerprint", check.path("fingerprint").asText())))).andExpect(status().is4xxClientError());
        mvc.perform(post("/v1/recoveries").contentType("application/json").content(json.writeValueAsString(Map.of("returnerUserId", returner(), "receivingLocationId", location(), "recoveryDate", "2026-09-17", "reason", "T16 missing token", "assetIds", List.of(device), "allocationIds", List.of(), "componentActions", Map.of(), "perUserActions", Map.of()))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/v1/recoveries").contentType("application/json").content(json.writeValueAsString(Map.of("returnerUserId", returner(), "receivingLocationId", location(), "recoveryDate", "2026-09-17", "reason", " ", "assetIds", List.of(device), "allocationIds", List.of(), "componentActions", Map.of(), "perUserActions", Map.of(), "expectedFingerprint", check.path("fingerprint").asText()))))
                .andExpect(status().isBadRequest());
        assertThat(state(device)).isEqualTo("IN_USE");
    }

    @Test
    void writesAnImmutableRecoverySnapshotWithOriginalDecisionData() throws Exception {
        Long device = create("DEVICE"), component = create("COMPONENT"), packageId = license("PER_USER", 1);
        relationships.create(device, component, RelationshipType.COMPONENT_OF);
        handovers.complete(confirmed(List.of(device), List.of(new HandoverRequest.LicenseLine(packageId, 1, device))));
        Long allocation = allocation(packageId, device);
        JsonNode check = smart(List.of(device), List.of());
        JsonNode response = complete(Map.of("returnerUserId", returner(), "receivingLocationId", location(), "recoveryDate", "2026-09-17", "reason", "T16 durable audit",
                "assetIds", List.of(device), "allocationIds", List.of(), "componentActions", Map.of(component.toString(), Map.of("componentAction", "DETACH", "recoverPerUser", false)), "perUserActions", Map.of(allocation.toString(), true), "expectedFingerprint", check.path("fingerprint").asText()));
        Long tx = response.at("/data/transactionId").asLong();
        String snapshot = jdbc.queryForObject("SELECT snapshot::text FROM transaction_publication_snapshots WHERE transaction_id=?", String.class, tx);
        JsonNode saved = json.readTree(snapshot);
        assertThat(saved.path("reason").asText()).isEqualTo("T16 durable audit");
        assertThat(line(saved, component).path("parentAssetId").asLong()).isEqualTo(device);
        assertThat(line(saved, component).path("details").path("componentAction").asText()).isEqualTo("DETACH");
        assertThat(line(saved, packageId).path("seats").asInt()).isEqualTo(1);
        assertThat(line(saved, packageId).at("/allocations/0/allocationId").asLong()).isEqualTo(allocation);
        assertThat(line(saved, packageId).at("/details/allocationDecisions/0/before/deviceId").asLong()).isEqualTo(device);
        assertThat(line(saved, packageId).at("/details/allocationDecisions/0/before/userId").asLong()).isEqualTo(returner());
        assertThat(line(saved, device).path("seats").asInt()).isZero();
        assertThatThrownBy(() -> jdbc.update("UPDATE transaction_publication_snapshots SET snapshot='{}'::jsonb WHERE transaction_id=?", tx)).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }

    @Test
    void standaloneComponentMustBeInUseAndOwnedByTheReturner() throws Exception {
        Long component = create("COMPONENT");
        JsonNode stockCheck = smart(List.of(component), List.of());
        Map<String, Object> stockRequest = Map.of("returnerUserId", returner(), "receivingLocationId", location(), "recoveryDate", "2026-09-17", "reason", "T16 component state",
                "assetIds", List.of(component), "allocationIds", List.of(), "componentActions", Map.of(component.toString(), Map.of("componentAction", "KEEP_ATTACHED", "recoverPerUser", false)), "perUserActions", Map.of(), "expectedFingerprint", stockCheck.path("fingerprint").asText());
        mvc.perform(post("/v1/recoveries").contentType("application/json").content(json.writeValueAsString(stockRequest))).andExpect(status().isConflict());
        inUse(component, otherUser());
        JsonNode ownerCheck = smart(List.of(component), List.of());
        Map<String, Object> ownerRequest = Map.of("returnerUserId", returner(), "receivingLocationId", location(), "recoveryDate", "2026-09-17", "reason", "T16 component owner",
                "assetIds", List.of(component), "allocationIds", List.of(), "componentActions", Map.of(component.toString(), Map.of("componentAction", "KEEP_ATTACHED", "recoverPerUser", false)), "perUserActions", Map.of(), "expectedFingerprint", ownerCheck.path("fingerprint").asText());
        mvc.perform(post("/v1/recoveries").contentType("application/json").content(json.writeValueAsString(ownerRequest))).andExpect(status().isConflict());
    }

    @Test
    void duplicateRecoveryHasOneCommittedTransaction() throws Exception {
        Long device = create("DEVICE"); handovers.complete(confirmed(List.of(device), List.of()));
        JsonNode check = smart(List.of(device), List.of());
        Map<String, Object> request = Map.of("returnerUserId", returner(), "receivingLocationId", location(), "recoveryDate", "2026-09-17", "reason", "T16 once",
                "assetIds", List.of(device), "allocationIds", List.of(), "componentActions", Map.of(), "perUserActions", Map.of(), "expectedFingerprint", check.path("fingerprint").asText());
        complete(request);
        mvc.perform(post("/v1/recoveries").contentType("application/json").content(json.writeValueAsString(request))).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transaction_assets ta JOIN transactions t USING(transaction_id) WHERE t.type='RECOVERY' AND ta.asset_id=?", Integer.class, device)).isEqualTo(1);
    }

    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void competingRecoveriesHaveExactlyOneWinner() throws Exception {
        login();
        Long device = create("DEVICE"); handovers.complete(confirmed(List.of(device), List.of()));
        JsonNode check = smart(List.of(device), List.of());
        String payload = json.writeValueAsString(Map.of("returnerUserId", returner(), "receivingLocationId", location(), "recoveryDate", "2026-09-17", "reason", "T16 race",
                "assetIds", List.of(device), "allocationIds", List.of(), "componentActions", Map.of(), "perUserActions", Map.of(), "expectedFingerprint", check.path("fingerprint").asText()));
        CountDownLatch gate = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        Callable<Boolean> recover = () -> {
            login(); gate.await();
            try { return mvc.perform(post("/v1/recoveries").contentType("application/json").content(payload)).andReturn().getResponse().getStatus() == 201; }
            finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
        };
        try {
            var first = pool.submit(recover); var second = pool.submit(recover); gate.countDown();
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
        } finally { pool.shutdownNow(); }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transaction_assets ta JOIN transactions t USING(transaction_id) WHERE t.type='RECOVERY' AND ta.asset_id=?", Integer.class, device)).isEqualTo(1);
        assertThat(state(device)).isEqualTo("IN_STOCK");
    }

    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void snapshotWriteFailureRollsBackRecoveryStateAndTransaction() throws Exception {
        Long device = create("DEVICE"); handovers.complete(confirmed(List.of(device), List.of()));
        JsonNode check = smart(List.of(device), List.of());
        Long before = jdbc.queryForObject("SELECT count(*) FROM transactions WHERE type='RECOVERY'", Long.class);
        jdbc.execute("CREATE FUNCTION t16_block_snapshot() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'T16 forced snapshot failure'; END; $$");
        jdbc.execute("CREATE TRIGGER t16_block_snapshot BEFORE INSERT ON transaction_publication_snapshots FOR EACH ROW EXECUTE FUNCTION t16_block_snapshot()");
        Map<String, Object> request = Map.of("returnerUserId", returner(), "receivingLocationId", location(), "recoveryDate", "2026-09-17", "reason", "T16 rollback",
                "assetIds", List.of(device), "allocationIds", List.of(), "componentActions", Map.of(), "perUserActions", Map.of(), "expectedFingerprint", check.path("fingerprint").asText());
        try {
            mvc.perform(post("/v1/recoveries").contentType("application/json").content(json.writeValueAsString(request))).andExpect(status().is5xxServerError());
        } finally {
            jdbc.execute("DROP TRIGGER t16_block_snapshot ON transaction_publication_snapshots");
            jdbc.execute("DROP FUNCTION t16_block_snapshot()");
        }
        assertThat(state(device)).isEqualTo("IN_USE");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transactions WHERE type='RECOVERY'", Long.class)).isEqualTo(before);
    }

    @Test
    void recoveryEndpointsRequireItOrAdminRoles() throws Exception {
        Long device = create("DEVICE");
        mvc.perform(post("/v1/recoveries/smart-check").with(user("user01@itam.example").authorities(() -> "USER")).contentType("application/json").content(json.writeValueAsString(Map.of("assetIds", List.of(device), "returnerUserId", returner(), "reason", "T16 authorization"))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/v1/recoveries").with(user("user01@itam.example").authorities(() -> "USER")).contentType("application/json").content(json.writeValueAsString(Map.of("returnerUserId", returner(), "receivingLocationId", location(), "recoveryDate", "2026-09-17", "reason", "T16 authorization", "assetIds", List.of(device), "allocationIds", List.of(), "componentActions", Map.of(), "perUserActions", Map.of(), "expectedFingerprint", "token"))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/v1/assets/recovery-candidates").with(user("user01@itam.example").authorities(() -> "USER")).param("userId", returner().toString()))
                .andExpect(status().isForbidden());
        assertThat(device).isNotNull();
    }

    private JsonNode smart(List<Long> assetIds, List<Long> allocationIds) throws Exception {
        String body = mvc.perform(post("/v1/recoveries/smart-check").contentType("application/json").content(json.writeValueAsString(Map.of(
                "assetIds", assetIds, "allocationIds", allocationIds, "returnerUserId", returner(), "reason", "T16 smart check"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).path("data");
    }

    private JsonNode complete(Map<String, Object> request) throws Exception {
        String body = mvc.perform(post("/v1/recoveries").contentType("application/json").content(json.writeValueAsString(request)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    private void rejectComplete(Map<String, Object> request) throws Exception {
        mvc.perform(post("/v1/recoveries").contentType("application/json").content(json.writeValueAsString(request))).andExpect(status().isConflict());
    }

    private JsonNode line(JsonNode snapshot, Long assetId) {
        for (JsonNode line : snapshot.path("lines")) if (line.path("assetId").asLong() == assetId) return line;
        throw new AssertionError("Missing recovery line for asset " + assetId);
    }

    private Long create(String category) {
        return create(category, "T16 " + UUID.randomUUID());
    }

    private Long create(String category, String name) {
        login();
        var request = new CreateHardwareAssetRequest(); request.setCreationPurpose(AssetCreationPurpose.BASELINE); request.setName(name); request.setTypeId(id("asset_types", "type_id", "T16_" + category));
        return assets.createHardwareAsset(request).getAssetId();
    }

    private Long license(String assignment, int seats) {
        login();
        var request = new CreateHardwareAssetRequest(); request.setCreationPurpose(AssetCreationPurpose.BASELINE); request.setName("T16 license " + UUID.randomUUID()); request.setTypeId(id("asset_types", "type_id", "T16_LICENSE"));
        Long software = jdbc.queryForObject("INSERT INTO software_catalog(name,manufacturer,is_active) VALUES (?,'Demo',true) RETURNING software_catalog_id", Long.class, "T16 " + UUID.randomUUID());
        request.setLicense(new LicenseDetailsRequest(software, id("license_assignment_types", "license_assignment_type_id", assignment), id("license_term_types", "license_term_type_id", "PERPETUAL"), seats, "T16-KEY", null));
        return assets.createHardwareAsset(request).getAssetId();
    }

    private HandoverRequest confirmed(List<Long> assetIds, List<HandoverRequest.LicenseLine> licenses) {
        return confirmed(returner(), assetIds, licenses);
    }

    private HandoverRequest confirmed(Long recipient, List<Long> assetIds, List<HandoverRequest.LicenseLine> licenses) {
        login();
        var request = new HandoverRequest(recipient, location(), LocalDate.of(2026, 9, 16), assetIds, licenses, "T16 setup", null);
        return new HandoverRequest(request.recipientUserId(), request.destinationLocationId(), request.handoverDate(), request.assetIds(), request.licenses(), request.notes(), handovers.preview(request).fingerprint());
    }

    private void inUse(Long assetId, Long userId) { jdbc.update("UPDATE assets SET status_id=(SELECT status_id FROM asset_statuses WHERE code='IN_USE'),assigned_to=? WHERE asset_id=?", userId, assetId); }
    private Long allocation(Long license, Long device) { return jdbc.queryForObject("SELECT allocation_id FROM license_allocations WHERE license_asset_id=? AND device_asset_id=?", Long.class, license, device); }
    private Long id(String table, String column, String code) { return jdbc.queryForObject("SELECT " + column + " FROM " + table + " WHERE code=?", Long.class, code); }
    private Long returner() { return jdbc.queryForObject("SELECT user_id FROM users WHERE email='user01@itam.example'", Long.class); }
    private Long otherUser() {
        Long id = jdbc.queryForObject("SELECT nextval('global_id_seq')", Long.class);
        jdbc.update("INSERT INTO users(user_id,email,full_name,role_id,account_status,password_hash) VALUES (?,?,'T16 other',(SELECT role_id FROM roles WHERE code='USER'), 'ACTIVE', 'unused')", id, "t16-other-" + id + "@itam.example");
        return id;
    }
    private Long location() { return id("locations", "location_id", "T16_TEST"); }
    private String tag(Long assetId) { return jdbc.queryForObject("SELECT asset_tag FROM assets WHERE asset_id=?", String.class, assetId); }
    private String state(Long assetId) { return jdbc.queryForObject("SELECT s.code FROM assets a JOIN asset_statuses s USING(status_id) WHERE asset_id=?", String.class, assetId); }
    private void login() { org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("admin@itam.example", null, List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ADMIN")))); }
}
