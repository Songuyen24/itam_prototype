package com.company.itam;

import com.company.itam.importbatch.service.AssetImportService;
import com.company.itam.importbatch.service.ExcelHelperService;
import com.company.itam.importbatch.dto.request.ImportConfirmRequest;
import com.company.itam.importbatch.dto.response.ImportBatchDetailResponse;
import com.company.itam.common.enums.ValidationStatus;
import com.company.itam.common.exception.AppException;
import com.company.itam.workflow.receiving.service.ImportDraftService;
import com.company.itam.workflow.core.dto.TransactionSummaryResponse;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.context.i18n.LocaleContextHolder;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@ActiveProfiles("test")
class Section2ExcelIntegrationTest {
    @Autowired org.springframework.test.web.servlet.MockMvc mvc;
    @Autowired AssetImportService imports;
    @Autowired ImportDraftService drafts;
    @Autowired ExcelHelperService excel;
    @Autowired JdbcTemplate jdbc;
    void login(String role) {
        String email = role.equals("PUR_STAFF") ? "pur01@itam.example" : "it01@itam.example";
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(email, null, List.of(new SimpleGrantedAuthority(role))));
    }
    @BeforeEach void setup() { login("IT_STAFF"); }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); LocaleContextHolder.resetLocaleContext(); }
    Map<String,Object> row(int number) {
        var row = new LinkedHashMap<String,Object>();
        row.put("rowNumber", number); row.put("name", "Excel test " + UUID.randomUUID()); row.put("type", "LAPTOP");
        row.put("condition", jdbc.queryForObject("SELECT code FROM asset_conditions WHERE is_active ORDER BY condition_id LIMIT 1", String.class));
        return row;
    }
    ImportConfirmRequest request(List<Map<String,Object>> rows) {
        var request = new ImportConfirmRequest("test.xlsx", rows); request.setRequestId(UUID.randomUUID()); return request;
    }
    String status(Long id) { return jdbc.queryForObject("SELECT s.code FROM assets a JOIN asset_statuses s USING(status_id) WHERE asset_id=?", String.class,id); }

    @Test @Transactional void mixedRowsBlankTagsRetryAndLostResponse() {
        var valid = row(2); var invalid = row(3); invalid.put("name", "");
        var req = request(List.of(valid, invalid));
        var first = imports.confirmImport(req);
        assertThat(first.getImportedRows()).isEqualTo(1);
        assertThat(first.getRows()).extracting(r -> r.getValidationStatus()).containsExactly(ValidationStatus.IMPORTED, ValidationStatus.INVALID);
        var id = first.getRows().getFirst().getAssetId();
        assertThat(status(id)).isEqualTo("IN_STOCK");
        assertThat(first.getRows().getFirst().getRawData().get("assetTag")).asString().startsWith("AST-");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transaction_assets WHERE asset_id=?", Integer.class,id)).isZero();
        assertThat(imports.confirmImport(req).getImportBatchId()).isEqualTo(first.getImportBatchId());
        invalid.put("name", "Repaired row");
        var repaired = request(List.of(valid, invalid)); repaired.setRetryBatchId(first.getImportBatchId());
        var second = imports.confirmImport(repaired);
        assertThat(second.getImportedRows()).isEqualTo(1);
        assertThat(second.getDuplicateRows()).isEqualTo(1);
        assertThat(second.getRows().getFirst().getErrors().getFirst().getCode()).isEqualTo("ALREADY_IMPORTED");
        assertThat(second.getRows().get(1).getRawData().get("assetTag")).isNotEqualTo(first.getRows().getFirst().getRawData().get("assetTag"));
        var changed = request(List.of(row(2))); changed.setRequestId(req.getRequestId());
        assertThatThrownBy(() -> imports.confirmImport(changed)).isInstanceOf(AppException.class).hasMessage("IMPORT_REQUEST_CHANGED");
    }

    @Test @Transactional void repairedFileCanReorderAnonymousRowsWithoutLosingIdenticalPurchaseCounts() {
        var first=row(2);var identical=new LinkedHashMap<String,Object>(first);identical.put("rowNumber",3);
        var original=imports.confirmImport(request(List.of(first,identical)));
        var moved=new LinkedHashMap<String,Object>(first);moved.put("rowNumber",4);
        var extra=new LinkedHashMap<String,Object>(first);extra.put("rowNumber",5);
        var retry=request(List.of(identical,moved,extra));retry.setRetryBatchId(original.getImportBatchId());
        var result=imports.confirmImport(retry);
        assertThat(result.getDuplicateRows()).isEqualTo(2);assertThat(result.getImportedRows()).isEqualTo(1);
        var again=request(List.of(identical,moved,extra,row(6)));again.setRetryBatchId(result.getImportBatchId());
        var next=imports.confirmImport(again);
        assertThat(next.getDuplicateRows()).isEqualTo(3);assertThat(next.getImportedRows()).isEqualTo(1);
    }

    @Test @Transactional void allInvalidAndInternalDuplicatesWriteNothing() {
        int before = jdbc.queryForObject("SELECT count(*) FROM import_batches",Integer.class);
        var bad=row(2); bad.put("type","missing");
        assertThatThrownBy(() -> imports.confirmImport(request(List.of(bad)))).isInstanceOf(AppException.class);
        var first=row(2);var second=row(3);first.put("assetTag","DUPE");second.put("assetTag","dupe");
        assertThatThrownBy(() -> imports.confirmImport(request(List.of(first,second)))).isInstanceOf(AppException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM import_batches",Integer.class)).isEqualTo(before);
    }

    @Test @Transactional void stalePreviewRevalidatesAndKeepsValidSubset() {
        var first=row(2);first.put("assetTag","S2-"+UUID.randomUUID());
        imports.confirmImport(request(List.of(first)));
        var second=row(3);
        var result=imports.confirmImport(request(List.of(first,second)));
        assertThat(result.getImportedRows()).isEqualTo(1);assertThat(result.getDuplicateRows()).isEqualTo(1);
    }

    @Test @Transactional void receivingExcelRemainsPendingUntilItConfirmsWithoutDocuments() {
        login("PUR_STAFF");var draft=drafts.create("Excel purchase",null);
        var req=request(List.of(row(2)));
        var result=imports.confirmReceiving(draft.transactionId(),draft.expectedVersion(),req);
        var batch=(ImportBatchDetailResponse)result.get("batch");
        var current=(TransactionSummaryResponse)result.get("transaction");
        var id=batch.getRows().getFirst().getAssetId();assertThat(status(id)).isEqualTo("PENDING_IMPORT");
        assertThat(((ImportBatchDetailResponse)imports.confirmReceiving(draft.transactionId(),draft.expectedVersion(),req).get("batch")).getImportBatchId()).isEqualTo(batch.getImportBatchId());
        var pending=drafts.submit(current.transactionId(),current.expectedVersion());
        assertThat(status(id)).isEqualTo("PENDING_IMPORT");login("IT_STAFF");
        drafts.process(pending.transactionId(),pending.expectedVersion(),pending.submittedRevision(),true,null);
        assertThat(status(id)).isEqualTo("IN_STOCK");
    }

    @Test @Transactional void retriesCannotCrossWorkflowOrActor() {
        var req=request(List.of(row(2)));var batch=imports.confirmImport(req);
        login("PUR_STAFF");var draft=drafts.create(null,null);
        var retry=request(List.of(row(2)));retry.setRetryBatchId(batch.getImportBatchId());
        assertThatThrownBy(() -> imports.confirmReceiving(draft.transactionId(),draft.expectedVersion(),retry)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(() -> imports.confirmImport(request(List.of(row(2))))).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test @Transactional void directExcelSupportsComponentsAndLicensesAndRejectsWorkflowStatus() {
        var component=row(2);component.put("type",jdbc.queryForObject("SELECT t.code FROM asset_types t JOIN asset_categories c USING(category_id) WHERE c.code='COMPONENT' AND t.is_active LIMIT 1",String.class));
        var license=row(3);license.remove("condition");license.put("type",jdbc.queryForObject("SELECT t.code FROM asset_types t JOIN asset_categories c USING(category_id) WHERE c.code='LICENSE' AND t.is_active LIMIT 1",String.class));
        jdbc.update("INSERT INTO software_catalog(name,manufacturer,version,is_active) VALUES ('Section2Software','Test','1',true) ON CONFLICT DO NOTHING");
        license.put("software","Section2Software");license.put("licenseAssignment","PER_USER");license.put("licenseTerm","PERPETUAL");license.put("seatCount","5");
        var bad=row(4);bad.put("status","IN_USE");
        var batch=imports.confirmImport(request(List.of(component,license,bad)));
        assertThat(batch.getImportedRows()).isEqualTo(2);assertThat(batch.getInvalidRows()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT seat_count FROM asset_license_details WHERE asset_id=?",Integer.class,batch.getRows().get(1).getAssetId())).isEqualTo(5);
    }

    @Test @Transactional void receivingMixedCategoriesOnlyAddsValidRows() {
        login("PUR_STAFF");var draft=drafts.create(null,null);
        var component=row(2);component.put("type",jdbc.queryForObject("SELECT t.code FROM asset_types t JOIN asset_categories c USING(category_id) WHERE c.code='COMPONENT' AND t.is_active LIMIT 1",String.class));
        var license=row(3);license.remove("condition");license.put("type",jdbc.queryForObject("SELECT t.code FROM asset_types t JOIN asset_categories c USING(category_id) WHERE c.code='LICENSE' AND t.is_active LIMIT 1",String.class));
        jdbc.update("INSERT INTO software_catalog(name,manufacturer,version,is_active) VALUES ('Section2ReceivingSoftware','Test','1',true) ON CONFLICT DO NOTHING");
        license.put("software","Section2ReceivingSoftware");license.put("licenseAssignment","PER_USER");license.put("licenseTerm","PERPETUAL");license.put("seatCount","4");
        var invalid=new LinkedHashMap<String,Object>(license);invalid.put("rowNumber",4);invalid.put("condition","NEW");
        var result=imports.confirmReceiving(draft.transactionId(),draft.expectedVersion(),request(List.of(component,license,invalid)));
        var batch=(ImportBatchDetailResponse)result.get("batch");var current=(TransactionSummaryResponse)result.get("transaction");
        assertThat(batch.getImportedRows()).isEqualTo(2);assertThat(batch.getInvalidRows()).isEqualTo(1);
        var pending=drafts.submit(current.transactionId(),current.expectedVersion());login("IT_STAFF");
        drafts.process(pending.transactionId(),pending.expectedVersion(),pending.submittedRevision(),true,null);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transaction_assets WHERE transaction_id=?",Integer.class,draft.transactionId())).isEqualTo(2);
        assertThat(status(batch.getRows().get(1).getAssetId())).isEqualTo("IN_STOCK");
    }

    @Test void excelRoutesKeepWriterRolesSeparate() throws Exception {
        for(String role:List.of("USER","PUR_STAFF")) {
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/v1/asset-imports/template")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("pur01@itam.example").authorities(new SimpleGrantedAuthority(role))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        }
        for(String role:List.of("USER","IT_STAFF")) {
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/v1/import-drafts/excel/template")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("it01@itam.example").authorities(new SimpleGrantedAuthority(role))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/v1/import-drafts/1/excel/confirm?expectedVersion=1")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("it01@itam.example").authorities(new SimpleGrantedAuthority(role)))
                .contentType("application/json").content("{\"fileName\":\"test.xlsx\",\"validRows\":[{\"rowNumber\":2}]}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        }
    }

    @Test void bilingualTemplatesRoundTripThroughParser() {
        for (var locale:List.of(Locale.ENGLISH,Locale.forLanguageTag("vi"))) {
            LocaleContextHolder.setLocale(locale);
            var data=excel.parseExcel(new MockMultipartFile("file","template.xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",excel.generateTemplate()));
            assertThat(data).hasSize(2);assertThat(data.getFirst()).containsKeys("assetTag","name","type");
        }
    }

    @Test void simultaneousConfirmWithSameRequestCreatesOneBatch() throws Exception {
        var req=request(List.of(row(2)));var pool=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
        Callable<Long> task=()->{login("IT_STAFF");try {gate.await();return imports.confirmImport(req).getImportBatchId();} finally {SecurityContextHolder.clearContext();}};
        try {
            var a=pool.submit(task);var b=pool.submit(task);gate.countDown();
            assertThat(a.get(30,TimeUnit.SECONDS)).isEqualTo(b.get(30,TimeUnit.SECONDS));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM import_batches WHERE request_id=?",Integer.class,req.getRequestId())).isEqualTo(1);
        } finally {pool.shutdownNow();}
    }
}
