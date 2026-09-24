package com.company.itam;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.company.itam.publication.service.EmailGateway;
import com.company.itam.publication.service.LocalEmailGateway;
import com.company.itam.publication.service.BilingualPdfGenerator;
import com.company.itam.asset.repository.AssetRepository;
import com.company.itam.common.exception.AppException;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.ArgumentMatchers.anyLong;
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
    @Autowired PlatformTransactionManager transactionManager;
    @SpyBean com.company.itam.audit.repository.AuditLogRepository auditRepository;
    @SpyBean AssetRepository assetRepository;
    @SpyBean LocalEmailGateway emailGateway;
    @SpyBean BilingualPdfGenerator pdf;

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
    void retiresPhysicalBundleButKeepsEverySharedOemSeatConsumed() throws Exception {
        Long laptop = asset(deviceType, "DAMAGED", LocalDate.now(), "Laptop");
        Long otherLaptop = asset(deviceType, "IN_STOCK", LocalDate.now(), "Other laptop");
        Long ram = asset(componentType, "DAMAGED", LocalDate.now(), "RAM");
        Long oem = license("OEM", 2);
        jdbc.update("INSERT INTO asset_relationships(parent_asset_id,child_asset_id,relationship_type,created_by) VALUES (?,?,'COMPONENT_OF',?)",
                laptop, ram, admin);
        Long allocation = jdbc.queryForObject("INSERT INTO license_allocations(license_asset_id,device_asset_id,user_id,seat_count,status,created_by) VALUES (?,?,(SELECT user_id FROM users WHERE email='user01@itam.example'),1,'ACTIVE',?) RETURNING allocation_id", Long.class, oem, laptop, admin);
        Long otherAllocation = jdbc.queryForObject("INSERT INTO license_allocations(license_asset_id,device_asset_id,seat_count,status,created_by) VALUES (?,?,1,'RESERVED',?) RETURNING allocation_id", Long.class, oem, otherLaptop, admin);
        jdbc.update("INSERT INTO asset_relationships(parent_asset_id,child_asset_id,relationship_type,allocation_id,created_by) VALUES (?,?,'INSTALLED_ON',?,?)", laptop, oem, allocation, admin);
        jdbc.update("INSERT INTO asset_relationships(parent_asset_id,child_asset_id,relationship_type,allocation_id,created_by) VALUES (?,?,'INSTALLED_ON',?,?)", otherLaptop, oem, otherAllocation, admin);

        JsonNode check = body(mvc.perform(post("/v1/disposals/smart-check")
                        .with(role("IT_STAFF")).contentType("application/json")
                        .content("{\"assetIds\":[" + laptop + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assets.length()").value(2))
                .andExpect(jsonPath("$.data.oemAllocations.length()").value(1))
                .andExpect(jsonPath("$.data.oemAllocations[0].allocationId").value(allocation))
                .andReturn().getResponse().getContentAsString()).path("data");

        String request = "{\"assetIds\":[" + laptop + "],\"reason\":\"Beyond repair\",\"disposalDate\":\"2026-09-15\",\"expectedFingerprint\":\""
                + check.path("fingerprint").asText() + "\"}";
        JsonNode disposal = body(mvc.perform(post("/v1/disposals").with(role("IT_STAFF"))
                        .contentType("application/json").content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.assets.length()").value(2))
                .andExpect(jsonPath("$.data.oemAllocations.length()").value(1))
                .andReturn().getResponse().getContentAsString()).path("data");
        long transactionId = disposal.path("transactionId").asLong();

        JsonNode detail = detail(transactionId, "IT_STAFF");

        for (String unauthorized : new String[]{"IT_STAFF", "PUR_STAFF", "USER"}) {
            mvc.perform(post("/v1/disposals/{id}/approve", transactionId).with(role(unauthorized))
                    .contentType("application/json").content("{\"expectedFingerprint\":\"" + detail.path("fingerprint").asText() + "\"}"))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/v1/disposals/{id}/reject", transactionId).with(role(unauthorized))
                    .contentType("application/json").content("{\"reason\":\"No\"}")).andExpect(status().isForbidden());
        }
        reset(emailGateway);
        mvc.perform(post("/v1/disposals/{id}/approve", transactionId).with(role("ADMIN"))
                        .contentType("application/json").content("{\"expectedFingerprint\":\"" + detail.path("fingerprint").asText() + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("COMPLETED"));

        assertThat(assetStatus(laptop)).isEqualTo("RETIRED");
        assertThat(assetStatus(ram)).isEqualTo("RETIRED");
        assertThat(assetStatus(oem)).isEqualTo("IN_STOCK");
        assertThat(assetStatus(otherLaptop)).isEqualTo("IN_STOCK");
        assertThat(jdbc.queryForObject("SELECT status FROM license_allocations WHERE allocation_id=?", String.class, allocation)).isEqualTo("RESERVED");
        assertThat(jdbc.queryForObject("SELECT user_id FROM license_allocations WHERE allocation_id=?", Long.class, allocation)).isNull();
        assertThat(jdbc.queryForObject("SELECT status FROM license_allocations WHERE allocation_id=?", String.class, otherAllocation)).isEqualTo("RESERVED");
        assertThat(jdbc.queryForObject("SELECT COALESCE(SUM(seat_count),0) FROM license_allocations WHERE license_asset_id=? AND status<>'RELEASED'", Integer.class, oem)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE transaction_id=? AND action='DISPOSAL_APPROVED'", Integer.class, transactionId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM documents WHERE transaction_id=? AND document_type='DISPOSAL_REPORT'", Integer.class, transactionId)).isOne();
        assertThat(jdbc.queryForObject("SELECT template_version FROM documents WHERE transaction_id=? AND document_type='DISPOSAL_REPORT'", String.class, transactionId)).isEqualTo("T18-1");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transaction_publication_snapshots WHERE transaction_id=?", Integer.class, transactionId)).isOne();
        assertThat(jdbc.queryForList("SELECT recipient FROM email_logs WHERE transaction_id=? AND event_type='DISPOSAL_COMPLETED' ORDER BY recipient", String.class, transactionId))
                .containsExactly("accounting@itam.example", "pur@itam.example");
        var attachment = org.mockito.ArgumentCaptor.forClass(EmailGateway.Attachment.class);
        verify(emailGateway, atLeastOnce()).send(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), attachment.capture());
        assertThat(attachment.getAllValues()).allSatisfy(value -> {
            assertThat(value.fileName()).endsWith(".pdf");
            assertThat(value.mimeType()).isEqualTo("application/pdf");
            assertThat(value.content()).startsWith("%PDF".getBytes());
        });
    }

    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void resolvesEachPerUserLinkInsidePendingDisposalAndRejectsStaleApproval() throws Exception {
        Long laptop = asset(deviceType, "DAMAGED", LocalDate.now(), "Licensed laptop");
        Long releasedLicense = license("PER_USER", 10);
        Long retainedLicense = license("PER_USER", 10);
        Long released = jdbc.queryForObject("INSERT INTO license_allocations(license_asset_id,device_asset_id,user_id,seat_count,status,created_by) VALUES (?,?,(SELECT user_id FROM users WHERE email='user01@itam.example'),1,'ACTIVE',?) RETURNING allocation_id", Long.class, releasedLicense, laptop, admin);
        Long retained = jdbc.queryForObject("INSERT INTO license_allocations(license_asset_id,device_asset_id,user_id,seat_count,status,created_by) VALUES (?,?,(SELECT user_id FROM users WHERE email='user01@itam.example'),1,'ACTIVE',?) RETURNING allocation_id", Long.class, retainedLicense, laptop, admin);
        jdbc.update("INSERT INTO asset_relationships(parent_asset_id,child_asset_id,relationship_type,allocation_id,created_by) VALUES (?,?,'INSTALLED_ON',?,?)", laptop, releasedLicense, released, admin);
        jdbc.update("INSERT INTO asset_relationships(parent_asset_id,child_asset_id,relationship_type,allocation_id,created_by) VALUES (?,?,'INSTALLED_ON',?,?)", laptop, retainedLicense, retained, admin);

        JsonNode check = body(mvc.perform(post("/v1/disposals/smart-check").with(role("IT_STAFF"))
                        .contentType("application/json").content("{\"assetIds\":[" + laptop + "]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.warnings.length()").value(2))
                .andExpect(jsonPath("$.data.perUserLinks.length()").value(2))
                .andReturn().getResponse().getContentAsString()).path("data");
        JsonNode pending = body(mvc.perform(post("/v1/disposals").with(role("IT_STAFF"))
                        .contentType("application/json").content("{\"assetIds\":[" + laptop + "],\"reason\":\"Broken\",\"disposalDate\":\"2026-09-15\",\"expectedFingerprint\":\"" + check.path("fingerprint").asText() + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("data");
        long transactionId = pending.path("transactionId").asLong();
        JsonNode before = detail(transactionId, "IT_STAFF");
        mvc.perform(post("/v1/disposals/{id}/approve", transactionId).with(role("ADMIN"))
                        .contentType("application/json").content("{\"expectedFingerprint\":\"" + before.path("fingerprint").asText() + "\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DISPOSAL_PER_USER_LINKED"));

        String decisions = json.writeValueAsString(java.util.Map.of(
                "expectedFingerprint", before.path("fingerprint").asText(),
                "decisions", java.util.List.of(java.util.Map.of("allocationId", released, "release", true),
                        java.util.Map.of("allocationId", retained, "release", false))));
        mvc.perform(post("/v1/disposals/{id}/per-user", transactionId).with(role("USER"))
                .contentType("application/json").content(decisions)).andExpect(status().isForbidden());
        JsonNode resolved = body(mvc.perform(post("/v1/disposals/{id}/per-user", transactionId).with(role("IT_STAFF"))
                        .contentType("application/json").content(decisions))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.perUserLinks.length()").value(0))
                .andReturn().getResponse().getContentAsString()).path("data");

        mvc.perform(post("/v1/disposals/{id}/approve", transactionId).with(role("ADMIN"))
                        .contentType("application/json").content("{\"expectedFingerprint\":\"" + before.path("fingerprint").asText() + "\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DISPOSAL_CHANGED"));
        mvc.perform(post("/v1/disposals/{id}/approve", transactionId).with(role("ADMIN"))
                        .contentType("application/json").content("{\"expectedFingerprint\":\"" + resolved.path("fingerprint").asText() + "\"}"))
                .andExpect(status().isOk());
        assertThat(assetStatus(laptop)).isEqualTo("RETIRED");
        assertThat(assetStatus(releasedLicense)).isEqualTo("IN_STOCK");
        assertThat(assetStatus(retainedLicense)).isEqualTo("IN_STOCK");
        assertThat(jdbc.queryForObject("SELECT status FROM license_allocations WHERE allocation_id=?", String.class, released)).isEqualTo("RELEASED");
        assertThat(jdbc.queryForObject("SELECT user_id FROM license_allocations WHERE allocation_id=?", Long.class, released)).isNotNull();
        assertThat(jdbc.queryForObject("SELECT status FROM license_allocations WHERE allocation_id=?", String.class, retained)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT user_id FROM license_allocations WHERE allocation_id=?", Long.class, retained)).isNotNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM asset_relationships WHERE allocation_id IN (?,?)", Integer.class, released, retained)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE transaction_id=? AND action IN ('DISPOSAL_PER_USER_RELEASED','DISPOSAL_PER_USER_UNLINKED')", Integer.class, transactionId)).isEqualTo(2);
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
        jdbc.update("INSERT INTO locations(code,name,is_active) VALUES (?, 'Unassigned', true) ON CONFLICT DO NOTHING", "T18U-" + UUID.randomUUID().toString().substring(0, 24));
        jdbc.update("INSERT INTO departments(code,name,is_active) VALUES (?, 'Unassigned', true) ON CONFLICT DO NOTHING", "T18U-" + UUID.randomUUID().toString().substring(0, 24));
        jdbc.update("UPDATE assets SET location_id=(SELECT location_id FROM locations WHERE name='Unassigned' LIMIT 1), department_id=(SELECT department_id FROM departments WHERE name='Unassigned' LIMIT 1) WHERE asset_id=?", oldStock);
        asset(componentType, "PENDING_IMPORT", LocalDate.now(), "Pending RAM");
        asset(deviceType, "RETIRED", LocalDate.now().minusYears(8), "Retired laptop");
        Long license = license("PER_USER", 10);
        jdbc.update("INSERT INTO license_allocations(license_asset_id,user_id,seat_count,status,created_by) VALUES (?,(SELECT user_id FROM users WHERE email='user01@itam.example'),3,'ACTIVE',?)", license, admin);

        JsonNode dashboard = body(mvc.perform(get("/v1/dashboard/summary").with(role("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.byStatus.IN_STOCK").isNumber())
                .andExpect(jsonPath("$.data.byStatus.PENDING_IMPORT").isNumber())
                .andExpect(jsonPath("$.data.byStatus.RETIRED").isNumber())
                .andExpect(jsonPath("$.data.byType.T18_DEVICE").isNumber())
                .andExpect(jsonPath("$.data.pendingReceivings").isNumber())
                .andExpect(jsonPath("$.data.pendingDisposals").isNumber())
                .andReturn().getResponse().getContentAsString()).path("data");
        assertThat(dashboard.path("byLocation").has("")).isTrue();
        assertThat(dashboard.path("byLocation").has("Unassigned")).isTrue();
        assertThat(dashboard.path("byDepartment").has("")).isTrue();
        assertThat(dashboard.path("byDepartment").has("Unassigned")).isTrue();
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
        JsonNode detail = detail(pending.path("transactionId").asLong(), "ADMIN");
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("admin@itam.example", null,
                        java.util.List.of(new SimpleGrantedAuthority("ADMIN"))));
        doThrow(new IllegalStateException("Simulated audit failure")).when(auditRepository)
                .save(argThat(log -> "DISPOSAL_APPROVED".equals(log.getAction())));
        try { assertThatThrownBy(() -> disposalService.approve(pending.path("transactionId").asLong(),
                new com.company.itam.workflow.disposal.dto.DisposalModels.ApproveRequest(detail.path("fingerprint").asText())))
                .hasMessageContaining("Simulated audit failure"); }
        finally { reset(auditRepository); org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
        assertThat(assetStatus(asset)).isEqualTo("DAMAGED");
        assertThat(jdbc.queryForObject("SELECT status FROM transactions WHERE transaction_id=?", String.class, pending.path("transactionId").asLong())).isEqualTo("PENDING");
    }

    @Test
    void blocksStandaloneLicensePackageWhileAnySeatIsOutstanding() throws Exception {
        Long device = asset(deviceType, "IN_USE", LocalDate.now(), "Allocated laptop");
        Long oem = license("OEM", 2);
        jdbc.update("UPDATE assets SET purchase_date=? WHERE asset_id=?", LocalDate.now().minusYears(6), oem);
        Long allocation = jdbc.queryForObject("INSERT INTO license_allocations(license_asset_id,device_asset_id,seat_count,status,created_by) VALUES (?,?,1,'RESERVED',?) RETURNING allocation_id", Long.class, oem, device, admin);
        jdbc.update("INSERT INTO asset_relationships(parent_asset_id,child_asset_id,relationship_type,allocation_id,created_by) VALUES (?,?,'INSTALLED_ON',?,?)", device, oem, allocation, admin);

        mvc.perform(post("/v1/disposals/smart-check").with(role("IT_STAFF"))
                        .contentType("application/json").content("{\"assetIds\":[" + oem + "]}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DISPOSAL_LICENSE_ALLOCATED"))
                .andExpect(jsonPath("$.message").value("Gói license còn suất đang cấp hoặc giữ nên không thể thanh lý"));
        mvc.perform(post("/v1/disposals/smart-check").with(role("IT_STAFF"))
                        .header("Accept-Language", "en").contentType("application/json")
                        .content("{\"assetIds\":[" + oem + "]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("The license package still has allocated seats and cannot be disposed"));
    }

    @Test
    void rejectsNullAndNonPositiveIdentifiersAtTheApiBoundary() throws Exception {
        mvc.perform(post("/v1/disposals/smart-check").with(role("IT_STAFF"))
                        .contentType("application/json").content("{\"assetIds\":[null]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/v1/disposals/1/per-user").with(role("IT_STAFF"))
                        .contentType("application/json").content("{\"expectedFingerprint\":\"x\",\"decisions\":[null]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/v1/disposals/1/per-user").with(role("IT_STAFF"))
                        .contentType("application/json").content("{\"expectedFingerprint\":\"x\",\"decisions\":[{\"allocationId\":0,\"release\":true}]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void legacyPendingOemLineRemainsInformationalAndIsNeverRetired() throws Exception {
        Long laptop = asset(deviceType, "DAMAGED", LocalDate.now(), "Legacy laptop");
        Long oem = license("OEM", 1);
        Long allocation = jdbc.queryForObject("INSERT INTO license_allocations(license_asset_id,device_asset_id,seat_count,status,created_by) VALUES (?,?,1,'RESERVED',?) RETURNING allocation_id", Long.class, oem, laptop, admin);
        jdbc.update("INSERT INTO asset_relationships(parent_asset_id,child_asset_id,relationship_type,allocation_id,created_by) VALUES (?,?,'INSTALLED_ON',?,?)", laptop, oem, allocation, admin);
        Long transactionId = jdbc.queryForObject("INSERT INTO transactions(transaction_code,type,status,requester_id) VALUES (?,'DISPOSAL','PENDING',?) RETURNING transaction_id", Long.class, "DI-LEGACY-" + UUID.randomUUID(), admin);
        jdbc.update("INSERT INTO transaction_disposal_details(transaction_id,reason,disposal_date) VALUES (?,?,?)", transactionId, "Legacy pending", LocalDate.parse("2026-09-15"));
        jdbc.update("INSERT INTO transaction_assets(transaction_id,asset_id,line_number,notes) VALUES (?,?,1,NULL),(?,?,2,'AUTO_ADDED')", transactionId, laptop, transactionId, oem);

        JsonNode detail = detail(transactionId, "ADMIN");
        assertThat(detail.path("assets").size()).isOne();
        assertThat(detail.path("oemAllocations").size()).isOne();
        mvc.perform(post("/v1/disposals/{id}/approve", transactionId).with(role("ADMIN"))
                        .contentType("application/json").content("{\"expectedFingerprint\":\"" + detail.path("fingerprint").asText() + "\"}"))
                .andExpect(status().isOk());
        assertThat(assetStatus(laptop)).isEqualTo("RETIRED");
        assertThat(assetStatus(oem)).isEqualTo("IN_STOCK");
    }

    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void serialOnlyChangeInvalidatesReviewedDisposalFingerprint() throws Exception {
        Long laptop = asset(deviceType, "DAMAGED", LocalDate.now(), "Serial guarded laptop");
        String serialA = "SER-A-" + UUID.randomUUID();
        String serialB = "SER-B-" + UUID.randomUUID();
        jdbc.update("INSERT INTO asset_hardware_details(asset_id,serial_number) VALUES (?,?)", laptop, serialA);
        JsonNode check = body(mvc.perform(post("/v1/disposals/smart-check").with(role("IT_STAFF"))
                .contentType("application/json").content("{\"assetIds\":[" + laptop + "]}"))
                .andReturn().getResponse().getContentAsString()).path("data");
        long transactionId = body(mvc.perform(post("/v1/disposals").with(role("IT_STAFF"))
                .contentType("application/json").content("{\"assetIds\":[" + laptop
                        + "],\"reason\":\"Serial guard\",\"disposalDate\":\"2026-09-15\",\"expectedFingerprint\":\""
                        + check.path("fingerprint").asText() + "\"}"))
                .andReturn().getResponse().getContentAsString()).path("data").path("transactionId").asLong();
        JsonNode reviewed = detail(transactionId, "ADMIN");
        jdbc.update("UPDATE asset_hardware_details SET serial_number=? WHERE asset_id=?", serialB, laptop);

        mvc.perform(post("/v1/disposals/{id}/approve", transactionId).with(role("ADMIN"))
                        .contentType("application/json").content("{\"expectedFingerprint\":\""
                                + reviewed.path("fingerprint").asText() + "\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DISPOSAL_CHANGED"));
        JsonNode refreshed = detail(transactionId, "ADMIN");
        assertThat(refreshed.path("assets").get(0).path("serialNumber").asText()).isEqualTo(serialB);
        assertThat(refreshed.path("fingerprint").asText()).isNotEqualTo(reviewed.path("fingerprint").asText());
        mvc.perform(post("/v1/disposals/{id}/approve", transactionId).with(role("ADMIN"))
                        .contentType("application/json").content("{\"expectedFingerprint\":\""
                                + refreshed.path("fingerprint").asText() + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void approvalRechecksTopologyAfterWaitingForConcurrentAllocationChange() throws Exception {
        Long laptop = asset(deviceType, "DAMAGED", LocalDate.now(), "Concurrent laptop");
        Long license = license("PER_USER", 1);
        Long allocation = jdbc.queryForObject("INSERT INTO license_allocations(license_asset_id,device_asset_id,user_id,seat_count,status,created_by) VALUES (?,?,(SELECT user_id FROM users WHERE email='user01@itam.example'),1,'ACTIVE',?) RETURNING allocation_id", Long.class, license, laptop, admin);
        jdbc.update("INSERT INTO asset_relationships(parent_asset_id,child_asset_id,relationship_type,allocation_id,created_by) VALUES (?,?,'INSTALLED_ON',?,?)", laptop, license, allocation, admin);
        JsonNode check = body(mvc.perform(post("/v1/disposals/smart-check").with(role("IT_STAFF"))
                .contentType("application/json").content("{\"assetIds\":[" + laptop + "]}"))
                .andReturn().getResponse().getContentAsString()).path("data");
        long transactionId = body(mvc.perform(post("/v1/disposals").with(role("IT_STAFF"))
                .contentType("application/json").content("{\"assetIds\":[" + laptop
                        + "],\"reason\":\"Concurrent guard\",\"disposalDate\":\"2026-09-15\",\"expectedFingerprint\":\""
                        + check.path("fingerprint").asText() + "\"}"))
                .andReturn().getResponse().getContentAsString()).path("data").path("transactionId").asLong();
        JsonNode reviewed = detail(transactionId, "ADMIN");

        var locked = new java.util.concurrent.CountDownLatch(1);
        var approvalLockAttempt = new java.util.concurrent.CountDownLatch(1);
        doAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            if (java.util.Objects.equals(id, laptop)) approvalLockAttempt.countDown();
            jdbc.queryForObject("SELECT asset_id FROM assets WHERE asset_id=? FOR UPDATE", Long.class, id);
            return assetRepository.findById(id);
        }).when(assetRepository).lockById(anyLong());
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var mutation = pool.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                jdbc.queryForObject("SELECT asset_id FROM assets WHERE asset_id=? FOR UPDATE", Long.class, laptop);
                locked.countDown();
                await(approvalLockAttempt);
                jdbc.update("DELETE FROM asset_relationships WHERE allocation_id=?", allocation);
                jdbc.update("UPDATE license_allocations SET device_asset_id=NULL,status='RELEASED',released_at=now() WHERE allocation_id=?", allocation);
            }));
            assertThat(locked.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            var approval = pool.submit(() -> {
                org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                        new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                                "admin@itam.example", null, java.util.List.of(new SimpleGrantedAuthority("ADMIN"))));
                try {
                    disposalService.approve(transactionId,
                            new com.company.itam.workflow.disposal.dto.DisposalModels.ApproveRequest(reviewed.path("fingerprint").asText()));
                    return "OK";
                } catch (AppException ex) { return ex.getCode(); }
                finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
            });
            assertThat(approvalLockAttempt.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            mutation.get(20, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(approval.get(20, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo("DISPOSAL_CHANGED");
        } finally { reset(assetRepository); }

        assertThat(assetStatus(laptop)).isEqualTo("DAMAGED");
        assertThat(jdbc.queryForObject("SELECT status FROM transactions WHERE transaction_id=?", String.class, transactionId)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT status FROM license_allocations WHERE allocation_id=?", String.class, allocation)).isEqualTo("RELEASED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM asset_relationships WHERE allocation_id=?", Integer.class, allocation)).isZero();
    }

    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void approvalPersistsFrozenSnapshotBeforeFirstPublicationAttempt() throws Exception {
        Long laptop = asset(deviceType, "DAMAGED", LocalDate.now(), "Frozen laptop");
        String serial = "SER-T18-" + UUID.randomUUID();
        String originalActor = jdbc.queryForObject("SELECT full_name FROM users WHERE user_id=?", String.class, admin);
        jdbc.update("INSERT INTO asset_hardware_details(asset_id,serial_number) VALUES (?,?)", laptop, serial);
        JsonNode check = body(mvc.perform(post("/v1/disposals/smart-check").with(role("IT_STAFF"))
                .contentType("application/json").content("{\"assetIds\":[" + laptop + "]}"))
                .andReturn().getResponse().getContentAsString()).path("data");
        JsonNode pending = body(mvc.perform(post("/v1/disposals").with(role("IT_STAFF")).contentType("application/json")
                .content("{\"assetIds\":[" + laptop + "],\"reason\":\"Frozen business reason\",\"disposalDate\":\"2026-09-15\",\"expectedFingerprint\":\"" + check.path("fingerprint").asText() + "\"}"))
                .andReturn().getResponse().getContentAsString()).path("data");
        long transactionId = pending.path("transactionId").asLong();
        JsonNode detail = detail(transactionId, "ADMIN");
        doThrow(new IllegalStateException("Simulated PDF failure")).when(pdf)
                .generate(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
        mvc.perform(post("/v1/disposals/{id}/approve", transactionId).with(role("ADMIN"))
                        .contentType("application/json").content("{\"expectedFingerprint\":\"" + detail.path("fingerprint").asText() + "\"}"))
                .andExpect(status().isOk());
        reset(pdf);

        String frozen = jdbc.queryForObject("SELECT snapshot::text FROM transaction_publication_snapshots WHERE transaction_id=?", String.class, transactionId);
        JsonNode snapshot = json.readTree(frozen);
        assertThat(snapshot.path("actorName").asText()).isEqualTo(originalActor);
        assertThat(snapshot.path("reason").asText()).isEqualTo("Frozen business reason");
        assertThat(snapshot.path("disposalDate").asText()).isEqualTo("2026-09-15");
        assertThat(snapshot.path("assets").get(0).path("serialNumber").asText()).isEqualTo(serial);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM documents WHERE transaction_id=?", Integer.class, transactionId)).isZero();

        try {
            jdbc.update("UPDATE users SET full_name='Changed Admin' WHERE user_id=?", admin);
            jdbc.update("UPDATE asset_hardware_details SET serial_number=? WHERE asset_id=?", "CHANGED-" + UUID.randomUUID(), laptop);
            mvc.perform(post("/v1/transactions/{id}/pdf/regenerate", transactionId).with(role("ADMIN")))
                    .andExpect(status().isOk());
            assertThat(jdbc.queryForObject("SELECT snapshot::text FROM transaction_publication_snapshots WHERE transaction_id=?", String.class, transactionId)).isEqualTo(frozen);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM documents WHERE transaction_id=?", Integer.class, transactionId)).isOne();
        } finally {
            jdbc.update("UPDATE users SET full_name=? WHERE user_id=?", originalActor, admin);
        }
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

    private JsonNode detail(Long transactionId, String role) throws Exception {
        return body(mvc.perform(get("/v1/disposals/{id}", transactionId).with(role(role)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
    }

    private static void await(java.util.concurrent.CountDownLatch latch) {
        try {
            if (!latch.await(10, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("Timed out waiting for concurrent disposal step");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for concurrent disposal step", ex);
        }
    }

    private JsonNode body(String body) throws Exception { return json.readTree(body); }
}
