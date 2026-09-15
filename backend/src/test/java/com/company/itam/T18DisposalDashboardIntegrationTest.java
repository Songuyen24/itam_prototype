package com.company.itam;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "itam.documents.storage-root=target/t18-storage")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class T18DisposalDashboardIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired com.company.itam.workflow.disposal.service.DisposalService disposalService;
    @SpyBean com.company.itam.audit.repository.AuditLogRepository auditRepository;

    private Long admin;
    private Long deviceType;
    private Long componentType;

    @BeforeEach
    void fixtures() {
        admin = jdbc.queryForObject("SELECT user_id FROM users WHERE email='admin@itam.example'", Long.class);
        for (String category : new String[]{"DEVICE", "COMPONENT", "LICENSE"}) {
            jdbc.update("INSERT INTO asset_types(code,name,category_id,is_active) SELECT ?,?,category_id,true FROM asset_categories WHERE code=? ON CONFLICT(code) DO NOTHING",
                    "T18_" + category, "T18 " + category, category);
        }
        deviceType = id("asset_types", "type_id", "T18_DEVICE");
        componentType = id("asset_types", "type_id", "T18_COMPONENT");
    }

    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void createsBundledPendingDisposalAndOnlyAdminCanApprove() throws Exception {
        Long laptop = asset(deviceType, "DAMAGED", LocalDate.now(), "Laptop");
        Long ram = asset(componentType, "DAMAGED", LocalDate.now(), "RAM");
        Long oem = license("OEM", 1);
        jdbc.update("INSERT INTO asset_relationships(parent_asset_id,child_asset_id,relationship_type,created_by) VALUES (?,?,'COMPONENT_OF',?)",
                laptop, ram, admin);
        Long allocation = jdbc.queryForObject("INSERT INTO license_allocations(license_asset_id,device_asset_id,seat_count,status,created_by) VALUES (?,?,1,'ACTIVE',?) RETURNING allocation_id", Long.class, oem, laptop, admin);
        jdbc.update("INSERT INTO asset_relationships(parent_asset_id,child_asset_id,relationship_type,allocation_id,created_by) VALUES (?,?,'INSTALLED_ON',?,?)", laptop, oem, allocation, admin);

        JsonNode check = body(mvc.perform(post("/v1/disposals/smart-check")
                        .with(role("IT_STAFF")).contentType("application/json")
                        .content("{\"assetIds\":[" + laptop + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assets.length()").value(3))
                .andReturn().getResponse().getContentAsString()).path("data");

        String request = "{\"assetIds\":[" + laptop + "],\"reason\":\"Beyond repair\",\"disposalDate\":\"2026-09-15\",\"expectedFingerprint\":\""
                + check.path("fingerprint").asText() + "\"}";
        JsonNode disposal = body(mvc.perform(post("/v1/disposals").with(role("IT_STAFF"))
                        .contentType("application/json").content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.assets.length()").value(3))
                .andReturn().getResponse().getContentAsString()).path("data");
        long transactionId = disposal.path("transactionId").asLong();

        for (String unauthorized : new String[]{"IT_STAFF", "PUR_STAFF", "USER"}) {
            mvc.perform(post("/v1/disposals/{id}/approve", transactionId).with(role(unauthorized))).andExpect(status().isForbidden());
            mvc.perform(post("/v1/disposals/{id}/reject", transactionId).with(role(unauthorized))
                    .contentType("application/json").content("{\"reason\":\"No\"}")).andExpect(status().isForbidden());
        }
        mvc.perform(post("/v1/disposals/{id}/approve", transactionId).with(role("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("COMPLETED"));

        assertThat(assetStatus(laptop)).isEqualTo("RETIRED");
        assertThat(assetStatus(ram)).isEqualTo("RETIRED");
        assertThat(assetStatus(oem)).isEqualTo("RETIRED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE transaction_id=? AND action='DISPOSAL_APPROVED'", Integer.class, transactionId)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM documents WHERE transaction_id=? AND document_type='DISPOSAL_REPORT'", Integer.class, transactionId)).isOne();
        assertThat(jdbc.queryForList("SELECT recipient FROM email_logs WHERE transaction_id=? AND event_type='DISPOSAL_COMPLETED' ORDER BY recipient", String.class, transactionId))
                .containsExactly("accounting@itam.example", "pur@itam.example");
    }

    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void warnsAboutPerUserLinkAndBlocksApprovalUntilItIsDetached() throws Exception {
        Long laptop = asset(deviceType, "DAMAGED", LocalDate.now(), "Licensed laptop");
        Long licenseType = id("asset_types", "type_id", "T18_LICENSE");
        Long license = asset(licenseType, "IN_STOCK", LocalDate.now(), "Per-user package");
        Long software = jdbc.queryForObject("INSERT INTO software_catalog(name,manufacturer,is_active) VALUES (?,'T18',true) RETURNING software_catalog_id", Long.class, "T18 " + UUID.randomUUID());
        jdbc.update("INSERT INTO asset_license_details(asset_id,software_catalog_id,license_assignment_type_id,license_term_type_id,seat_count) VALUES (?,?,(SELECT license_assignment_type_id FROM license_assignment_types WHERE code='PER_USER'),(SELECT license_term_type_id FROM license_term_types WHERE code='PERPETUAL'),10)", license, software);
        Long allocation = jdbc.queryForObject("INSERT INTO license_allocations(license_asset_id,device_asset_id,user_id,seat_count,status,created_by) VALUES (?,?,(SELECT user_id FROM users WHERE email='user01@itam.example'),1,'ACTIVE',?) RETURNING allocation_id", Long.class, license, laptop, admin);
        jdbc.update("INSERT INTO asset_relationships(parent_asset_id,child_asset_id,relationship_type,allocation_id,created_by) VALUES (?,?,'INSTALLED_ON',?,?)", laptop, license, allocation, admin);

        JsonNode check = body(mvc.perform(post("/v1/disposals/smart-check").with(role("IT_STAFF"))
                        .contentType("application/json").content("{\"assetIds\":[" + laptop + "]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.warnings.length()").value(1))
                .andReturn().getResponse().getContentAsString()).path("data");
        JsonNode pending = body(mvc.perform(post("/v1/disposals").with(role("IT_STAFF"))
                        .contentType("application/json").content("{\"assetIds\":[" + laptop + "],\"reason\":\"Broken\",\"disposalDate\":\"2026-09-15\",\"expectedFingerprint\":\"" + check.path("fingerprint").asText() + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("data");
        long transactionId = pending.path("transactionId").asLong();
        mvc.perform(post("/v1/disposals/{id}/approve", transactionId).with(role("ADMIN")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DISPOSAL_PER_USER_LINKED"));

        jdbc.update("DELETE FROM asset_relationships WHERE allocation_id=?", allocation);
        jdbc.update("UPDATE license_allocations SET device_asset_id=NULL WHERE allocation_id=?", allocation);
        mvc.perform(post("/v1/disposals/{id}/approve", transactionId).with(role("ADMIN"))).andExpect(status().isOk());
        assertThat(assetStatus(laptop)).isEqualTo("RETIRED");
        assertThat(assetStatus(license)).isEqualTo("IN_STOCK");
        assertThat(jdbc.queryForObject("SELECT status FROM license_allocations WHERE allocation_id=?", String.class, allocation)).isEqualTo("ACTIVE");
    }

    @Test
    void rejectsIneligibleAssetsAndRejectingPendingDisposalPreservesState() throws Exception {
        Long inUse = asset(deviceType, "IN_USE", LocalDate.now().minusYears(8), "Assigned laptop");
        Long recentStock = asset(deviceType, "IN_STOCK", LocalDate.now().minusYears(4), "Recent laptop");
        Long oldStock = asset(deviceType, "IN_STOCK", LocalDate.now().minusYears(5), "Old laptop");

        for (Long id : new Long[]{inUse, recentStock}) {
            mvc.perform(post("/v1/disposals/smart-check").with(role("IT_STAFF"))
                            .contentType("application/json").content("{\"assetIds\":[" + id + "]}"))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DISPOSAL_ASSET_NOT_ELIGIBLE"));
        }

        JsonNode check = body(mvc.perform(post("/v1/disposals/smart-check").with(role("IT_STAFF"))
                        .contentType("application/json").content("{\"assetIds\":[" + oldStock + "]}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
        JsonNode pending = body(mvc.perform(post("/v1/disposals").with(role("IT_STAFF"))
                        .contentType("application/json").content("{\"assetIds\":[" + oldStock + "],\"reason\":\"End of life\",\"disposalDate\":\"2026-09-15\",\"expectedFingerprint\":\"" + check.path("fingerprint").asText() + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("data");

        mvc.perform(post("/v1/disposals/{id}/reject", pending.path("transactionId").asLong()).with(role("ADMIN"))
                        .contentType("application/json").content("{\"reason\":\"Repair first\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("REJECTED"));
        assertThat(assetStatus(oldStock)).isEqualTo("IN_STOCK");
    }

    @Test
    void dashboardAggregatesAndAdminExportsOneAssetPerExcelRow() throws Exception {
        Long oldStock = asset(deviceType, "IN_STOCK", LocalDate.now().minusYears(6), "Report laptop");
        asset(componentType, "PENDING_IMPORT", LocalDate.now(), "Pending RAM");
        asset(deviceType, "RETIRED", LocalDate.now().minusYears(8), "Retired laptop");
        Long license = license("PER_USER", 10);
        jdbc.update("INSERT INTO license_allocations(license_asset_id,user_id,seat_count,status,created_by) VALUES (?,(SELECT user_id FROM users WHERE email='user01@itam.example'),3,'ACTIVE',?)", license, admin);

        mvc.perform(get("/v1/dashboard/summary").with(role("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.byStatus.IN_STOCK").isNumber())
                .andExpect(jsonPath("$.data.byStatus.PENDING_IMPORT").isNumber())
                .andExpect(jsonPath("$.data.byStatus.RETIRED").isNumber())
                .andExpect(jsonPath("$.data.byType.T18_DEVICE").isNumber())
                .andExpect(jsonPath("$.data.pendingReceivings").isNumber())
                .andExpect(jsonPath("$.data.pendingDisposals").isNumber());
        mvc.perform(get("/v1/dashboard/summary").with(role("PUR_STAFF"))).andExpect(status().isForbidden());
        mvc.perform(get("/v1/reports/assets/export").with(role("IT_STAFF"))).andExpect(status().isForbidden());

        byte[] bytes = mvc.perform(get("/v1/reports/assets/export").with(role("ADMIN")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Asset Tag");
            assertThat(sheet.getRow(0).getCell(10).getStringCellValue()).isEqualTo("Available Seats");
            boolean found = false, licenseFound = false;
            for (int row = 1; row <= sheet.getLastRowNum(); row++) {
                if (sheet.getRow(row).getCell(0).getStringCellValue().equals(tag(oldStock))) found = true;
                if (sheet.getRow(row).getCell(0).getStringCellValue().equals(tag(license))) {
                    licenseFound = true;
                    assertThat(sheet.getRow(row).getCell(9).getNumericCellValue()).isEqualTo(10);
                    assertThat(sheet.getRow(row).getCell(10).getNumericCellValue()).isEqualTo(7);
                    assertThat(sheet.getRow(row).getCell(11).getNumericCellValue()).isEqualTo(3);
                }
            }
            assertThat(found).isTrue(); assertThat(licenseFound).isTrue();
        }
    }

    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void approvalRollsBackWhenAuditPersistenceFails() throws Exception {
        Long asset = asset(deviceType, "DAMAGED", LocalDate.now(), "Rollback laptop");
        JsonNode check = body(mvc.perform(post("/v1/disposals/smart-check").with(role("IT_STAFF"))
                .contentType("application/json").content("{\"assetIds\":[" + asset + "]}"))
                .andReturn().getResponse().getContentAsString()).path("data");
        JsonNode pending = body(mvc.perform(post("/v1/disposals").with(role("IT_STAFF"))
                .contentType("application/json").content("{\"assetIds\":[" + asset + "],\"reason\":\"Broken\",\"disposalDate\":\"2026-09-15\",\"expectedFingerprint\":\"" + check.path("fingerprint").asText() + "\"}"))
                .andReturn().getResponse().getContentAsString()).path("data");
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("admin@itam.example", null,
                        java.util.List.of(new SimpleGrantedAuthority("ADMIN"))));
        doThrow(new IllegalStateException("Simulated audit failure")).when(auditRepository)
                .save(argThat(log -> "DISPOSAL_APPROVED".equals(log.getAction())));
        try { assertThatThrownBy(() -> disposalService.approve(pending.path("transactionId").asLong())).hasMessageContaining("Simulated audit failure"); }
        finally { reset(auditRepository); org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
        assertThat(assetStatus(asset)).isEqualTo("DAMAGED");
        assertThat(jdbc.queryForObject("SELECT status FROM transactions WHERE transaction_id=?", String.class, pending.path("transactionId").asLong())).isEqualTo("PENDING");
    }

    private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor role(String role) {
        String email = switch (role) {
            case "ADMIN" -> "admin@itam.example";
            case "IT_STAFF" -> "it01@itam.example";
            case "USER" -> "user01@itam.example";
            default -> "pur01@itam.example";
        };
        return user(email).authorities(new SimpleGrantedAuthority(role));
    }

    private Long asset(Long type, String status, LocalDate purchaseDate, String name) {
        return jdbc.queryForObject("""
                INSERT INTO assets(asset_tag,name,type_id,status_id,purchase_date,purchase_cost,created_by)
                VALUES (?,?,?,(SELECT status_id FROM asset_statuses WHERE code=?),?,1000,?) RETURNING asset_id
                """, Long.class, "T18-" + UUID.randomUUID(), name, type, status, purchaseDate, admin);
    }

    private Long license(String assignmentType, int seats) {
        Long value = asset(id("asset_types", "type_id", "T18_LICENSE"), "IN_STOCK", LocalDate.now(), assignmentType + " package");
        Long software = jdbc.queryForObject("INSERT INTO software_catalog(name,manufacturer,is_active) VALUES (?,'T18',true) RETURNING software_catalog_id", Long.class, "T18 " + UUID.randomUUID());
        jdbc.update("INSERT INTO asset_license_details(asset_id,software_catalog_id,license_assignment_type_id,license_term_type_id,seat_count) VALUES (?,?,(SELECT license_assignment_type_id FROM license_assignment_types WHERE code=?),(SELECT license_term_type_id FROM license_term_types WHERE code='PERPETUAL'),?)", value, software, assignmentType, seats);
        return value;
    }

    private Long id(String table, String column, String code) {
        return jdbc.queryForObject("SELECT " + column + " FROM " + table + " WHERE code=?", Long.class, code);
    }

    private String assetStatus(Long asset) {
        return jdbc.queryForObject("SELECT s.code FROM assets a JOIN asset_statuses s USING(status_id) WHERE asset_id=?", String.class, asset);
    }

    private String tag(Long asset) {
        return jdbc.queryForObject("SELECT asset_tag FROM assets WHERE asset_id=?", String.class, asset);
    }

    private JsonNode body(String body) throws Exception { return json.readTree(body); }
}
