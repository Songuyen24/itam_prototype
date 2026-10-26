package com.company.itam;

import com.company.itam.importbatch.dto.request.ImportConfirmRequest;
import com.company.itam.importbatch.service.AssetImportService;
import com.company.itam.workflow.receiving.service.ImportDraftService;
import com.company.itam.importbatch.dto.response.ImportBatchDetailResponse;
import com.company.itam.workflow.core.dto.TransactionSummaryResponse;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static com.company.itam.importbatch.service.PdfAssetReaderTest.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class M103OcrIntegrationTest {
    @Autowired AssetImportService imports;
    @Autowired ImportDraftService drafts;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    void login(String role) {SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(role.equals("PUR_STAFF")?"pur01@itam.example":"it01@itam.example",null,List.of(new SimpleGrantedAuthority(role))));}
    @AfterEach void cleanup(){SecurityContextHolder.clearContext();}
    ImportConfirmRequest request(List<Map<String,Object>> rows) {var request=new ImportConfirmRequest("prototype.pdf",rows);request.setRequestId(UUID.randomUUID());return request;}
    @Test @Transactional void previewEditConfirmAndRetryUseExistingValidationAndNeverAutoCreateTransaction() throws Exception {
        login("IT_STAFF");
        int assets=jdbc.queryForObject("SELECT count(*) FROM assets",Integer.class);
        int transactions=jdbc.queryForObject("SELECT count(*) FROM transactions",Integer.class);
        var preview=imports.previewPdf(file(pdf(PAGES,false)));
        assertThat(preview.getTotalRows()).isEqualTo(7);
        assertThat(preview.getValidRows()).isEqualTo(2);
        assertThat(preview.getDuplicateRows()).isEqualTo(2);
        assertThat(preview.getInvalidRows()).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM assets",Integer.class)).isEqualTo(assets);
        var rows=preview.getRows().stream().map(row->new LinkedHashMap<String,Object>(row.getRawData())).map(row->(Map<String,Object>)row).toList();
        rows.get(4).put("name","Repaired name");
        var checked=imports.previewRows("prototype.pdf",rows);assertThat(checked.getValidRows()).isEqualTo(3);
        var req=request(rows);var batch=imports.confirmImport(req);
        assertThat(batch.getImportedRows()).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE entity_type='IMPORT_BATCH' AND entity_id=? AND action='IMPORT_PDF'",Integer.class,batch.getImportBatchId())).isEqualTo(1);
        assertThat(imports.confirmImport(req).getImportBatchId()).isEqualTo(batch.getImportBatchId());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transactions",Integer.class)).isEqualTo(transactions);
        var first=batch.getRows().stream().filter(row->row.getAssetId()!=null).findFirst().orElseThrow();
        assertThat(jdbc.queryForObject("SELECT s.code FROM assets a JOIN asset_statuses s USING(status_id) WHERE asset_id=?",String.class,first.getAssetId())).isEqualTo("IN_STOCK");
        rows.get(5).put("type","LAPTOP");var retry=request(rows);retry.setRetryBatchId(batch.getImportBatchId());
        assertThat(imports.confirmImport(retry).getImportedRows()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE entity_id=? AND action='CREATED_VIA_IMPORT'",Integer.class,first.getAssetId())).isGreaterThan(0);
    }
    @Test @Transactional void receivingPdfConfirmationOnlyAddsPendingAssetsToDraft() throws Exception {
        login("PUR_STAFF");var draft=drafts.create("OCR prototype",null);
        var preview=imports.previewPdf(file(pdf(List.of("name | type | condition\nOCR purchase | LAPTOP | NEW"),false)));
        var req=request(preview.getRows().stream().map(row->row.getRawData()).toList());
        var result=imports.confirmReceiving(draft.transactionId(),draft.expectedVersion(),req);
        var batch=(ImportBatchDetailResponse)result.get("batch");var transaction=(TransactionSummaryResponse)result.get("transaction");
        assertThat(transaction.status().name()).isEqualTo("DRAFT");
        Long assetId=batch.getRows().getFirst().getAssetId();
        assertThat(jdbc.queryForObject("SELECT s.code FROM assets a JOIN asset_statuses s USING(status_id) WHERE asset_id=?",String.class,assetId)).isEqualTo("PENDING_IMPORT");
    }
    @Test void routesKeepAddAssetAndReceivingRolesSeparate() throws Exception {
        for(String role:List.of("USER","PUR_STAFF")) {
            mvc.perform(multipart("/v1/asset-imports/pdf/preview").file(file(pdf(PAGES,false))).with(user("pur01@itam.example").authorities(new SimpleGrantedAuthority(role)))).andExpect(status().isForbidden());
            mvc.perform(post("/v1/asset-imports/validate").contentType("application/json").content("{\"fileName\":\"x.pdf\",\"validRows\":[{\"rowNumber\":2}]}").with(user("pur01@itam.example").authorities(new SimpleGrantedAuthority(role)))).andExpect(status().isForbidden());
        }
        for(String role:List.of("USER","IT_STAFF")) {
            mvc.perform(multipart("/v1/import-drafts/pdf/preview").file(file(pdf(PAGES,false))).with(user("it01@itam.example").authorities(new SimpleGrantedAuthority(role)))).andExpect(status().isForbidden());
            mvc.perform(post("/v1/import-drafts/validate").contentType("application/json").content("{\"fileName\":\"x.pdf\",\"validRows\":[{\"rowNumber\":2}]}").with(user("it01@itam.example").authorities(new SimpleGrantedAuthority(role)))).andExpect(status().isForbidden());
        }
    }
    @Test void authorizedRolesCanPreviewAndRevalidateWithoutImporting() throws Exception {
        for(String path:List.of("/v1/asset-imports","/v1/import-drafts")) {
            for(String role:List.of("ADMIN",path.contains("asset-imports")?"IT_STAFF":"PUR_STAFF")) {
                var principal=user("it01@itam.example").authorities(new SimpleGrantedAuthority(role));
                mvc.perform(multipart(path+"/pdf/preview").file(file(pdf(PAGES,false))).with(principal))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalRows").value(7));
                mvc.perform(post(path+"/validate").contentType("application/json").content("{\"fileName\":\"x.pdf\",\"validRows\":[{\"rowNumber\":2,\"name\":\"Repaired\",\"type\":\"LAPTOP\",\"condition\":\"NEW\"}]}").with(principal))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.validRows").value(1));
            }
        }
    }
}
