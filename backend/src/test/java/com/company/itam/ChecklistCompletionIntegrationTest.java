package com.company.itam;

import com.company.itam.asset.dto.request.CreateHardwareAssetRequest;
import com.company.itam.asset.service.AssetService;
import com.company.itam.common.enums.DocumentType;
import com.company.itam.common.exception.AppException;
import com.company.itam.document.service.DocumentService;
import com.company.itam.importbatch.service.AssetImportValidator;
import com.company.itam.workflow.receiving.service.ImportDraftService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="itam.documents.storage-root=target/checklist-documents")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ChecklistCompletionIntegrationTest {
    @Autowired ImportDraftService drafts;
    @Autowired DocumentService documents;
    @Autowired AssetService assets;
    @Autowired AssetImportValidator validator;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired jakarta.persistence.EntityManager entityManager;
    @Autowired com.company.itam.importbatch.service.AssetImportService imports;
    @Autowired com.company.itam.catalog.service.CatalogService catalogs;

    void login(String email, String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                email,null,List.of(new SimpleGrantedAuthority(role))));
    }
    @BeforeEach void prepare() {
        jdbc.update("INSERT INTO users(email,full_name,role_id,account_status) SELECT 'pur-checklist@itam.example','PUR checklist',role_id,'ACTIVE' FROM roles WHERE code='PUR_STAFF' ON CONFLICT(email) DO NOTHING");
        login("pur01@itam.example","PUR_STAFF");
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    CreateHardwareAssetRequest hardware() {
        var request=new CreateHardwareAssetRequest(); request.setName("Checklist sample device");
        request.setTypeId(jdbc.queryForObject("SELECT type_id FROM asset_types WHERE code='LAPTOP'",Long.class));
        request.setConditionId(jdbc.queryForObject("SELECT min(condition_id) FROM asset_conditions WHERE is_active",Long.class));
        request.setSerialNumber("CHECK-"+UUID.randomUUID()); return request;
    }
    MockMultipartFile file(String name,String text) {
        return new MockMultipartFile("file",name,"application/pdf",("%PDF-1.4\n"+text).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test void twoPurchasersCanEditDraftButOldVersionCannotOverwrite() {
        var draft=drafts.create("sample",null);
        login("pur-checklist@itam.example","PUR_STAFF");
        var doc=documents.upload(file("sample.pdf","v1"),draft.transactionId(),DocumentType.INVOICE,null,draft.expectedVersion());
        assertThat(doc.uploadedByName()).isEqualTo("PUR checklist");
        assertThatThrownBy(()->documents.upload(file("stale.pdf","stale"),draft.transactionId(),DocumentType.INVOICE,null,draft.expectedVersion()))
                .isInstanceOf(AppException.class).hasMessage("TRANSACTION_VERSION_CONFLICT");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM documents WHERE transaction_id=?",Integer.class,draft.transactionId())).isEqualTo(1);
    }

    @Test void submitWithdrawReplaceAndResubmitKeepOriginalFileAndMetadata() throws Exception {
        var t=drafts.create("original notes",null);
        t=drafts.addHardware(t.transactionId(),t.expectedVersion(),hardware());
        long id=t.transactionId();
        var first=documents.upload(file("original.pdf","original bytes"),id,DocumentType.INVOICE,null,t.expectedVersion());
        t=drafts.submit(id,t.expectedVersion()+1);
        long pendingVersion=t.expectedVersion();
        assertThatThrownBy(()->documents.upload(file("blocked.pdf","x"),id,DocumentType.OTHER,null,pendingVersion))
                .isInstanceOf(AppException.class).hasMessage("DOCUMENT_LOCKED");
        assertThatThrownBy(()->documents.delete(first.documentId(),id,pendingVersion)).isInstanceOf(AppException.class);
        var oldSnapshot=drafts.revisions(id).getFirst().get("snapshot").toString();
        login("pur-checklist@itam.example","PUR_STAFF");
        t=drafts.withdraw(id,pendingVersion);
        documents.delete(first.documentId(),id,t.expectedVersion());
        var second=documents.upload(file("replacement.pdf","new bytes"),id,DocumentType.CONTRACT,null,t.expectedVersion()+1);
        t=drafts.submit(id,t.expectedVersion()+2);
        assertThat(t.submittedRevision()).isEqualTo(2);
        assertThat(drafts.revisions(id).get(1).get("snapshot").toString()).isEqualTo(oldSnapshot);
        assertThat(documents.download(first.documentId()).content()).isEqualTo(file("original.pdf","original bytes").getBytes());
        assertThat(documents.getDocuments(id,0,20).getContent()).extracting("documentId").containsExactly(second.documentId());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE transaction_id=?",Integer.class,id)).isGreaterThanOrEqualTo(8);
    }

    @Test void wrongRolesCannotCreateDraftAndUserCannotReadRevisionOrOptions() throws Exception {
        var t=drafts.create("private",null);
        for(String role:List.of("IT_STAFF","USER")) {
            mvc.perform(post("/v1/import-drafts").with(user("user01@itam.example").authorities(new SimpleGrantedAuthority(role)))
                    .contentType("application/json").content("{}")) .andExpect(status().isForbidden());
        }
        mvc.perform(get("/v1/import-drafts/"+t.transactionId()+"/revisions").with(user("user01@itam.example").authorities(new SimpleGrantedAuthority("USER"))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/v1/assets").with(user("pur01@itam.example").authorities(new SimpleGrantedAuthority("PUR_STAFF"))))
                .andExpect(status().isForbidden());
    }

    @Test void generatedTagIsReturnedAndBlankPreviewDoesNotAllocate() {
        login("it01@itam.example","IT_STAFF");
        var request=hardware();
        var saved=assets.createHardwareAsset(request);
        assertThat(saved.getAssetTag()).startsWith("AST-").isNotBlank();
        var before=jdbc.queryForMap("SELECT last_value,is_called FROM asset_tag_sequence");
        var rows=validator.validateRows(List.of(new HashMap<>(Map.of("rowNumber",2,"name","Opening sample","type","LAPTOP","assetTag",""))));
        assertThat(rows.getFirst().getErrors()).isEmpty();
        assertThat(jdbc.queryForMap("SELECT last_value,is_called FROM asset_tag_sequence")).isEqualTo(before);
    }

    @Test void historicalMetadataCannotBeMutatedAndImportAssetCannotBeEditedThroughGeneralApi() throws Exception {
        var t=drafts.create("historical",null);
        t=drafts.addHardware(t.transactionId(),t.expectedVersion(),hardware());
        long assetId=jdbc.queryForObject("SELECT asset_id FROM transaction_assets WHERE transaction_id=?",Long.class,t.transactionId());
        var doc=documents.upload(file("locked.pdf","sample"),t.transactionId(),DocumentType.INVOICE,null,t.expectedVersion());
        drafts.submit(t.transactionId(),t.expectedVersion()+1);
        mvc.perform(put("/v1/assets/"+assetId).with(user("it01@itam.example").authorities(new SimpleGrantedAuthority("IT_STAFF")))
                .contentType("application/json").content("{\"name\":\"Override\",\"typeId\":"+hardware().getTypeId()+"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IMPORT_ASSET_LOCKED"));
        // Last statement deliberately aborts this test transaction; rollback preserves other test data.
        assertThatThrownBy(()->jdbc.update("UPDATE documents SET original_file_name='tampered.pdf' WHERE document_id=?",doc.documentId()))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
    }

    @Test void omittedTagOnUpdateIsPreservedButExplicitChangesAreRejected() throws Exception {
        login("it01@itam.example","IT_STAFF");
        var saved=assets.createHardwareAsset(hardware());
        String body="{\"name\":\"Updated sample\",\"typeId\":"+hardware().getTypeId()+"}";
        mvc.perform(put("/v1/assets/"+saved.getAssetId()).with(user("it01@itam.example").authorities(new SimpleGrantedAuthority("IT_STAFF")))
                .contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.assetTag").value(saved.getAssetTag()));
        mvc.perform(put("/v1/assets/"+saved.getAssetId()).with(user("it01@itam.example").authorities(new SimpleGrantedAuthority("IT_STAFF")))
                .contentType("application/json").content(body.replace("{", "{\"assetTag\":\"CHANGED\",")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ASSET_TAG_IMMUTABLE"));
    }

    @Test void blankTagsAreGeneratedAtConfirmAndValidationUsesEnglish() throws Exception {
        login("it01@itam.example","IT_STAFF");
        var rows=List.of(new HashMap<String,Object>(Map.of("rowNumber",2,"name","Opening A","type","LAPTOP","assetTag","")),
                new HashMap<String,Object>(Map.of("rowNumber",3,"name","Opening B","type","LAPTOP","assetTag","")));
        var batch=imports.confirmImport(new com.company.itam.importbatch.dto.request.ImportConfirmRequest("blank-tags.xlsx",new ArrayList<>(rows)));
        assertThat(batch.getImportedRows()).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT a.asset_tag FROM import_rows r JOIN assets a USING(asset_id) WHERE import_batch_id=?",String.class,batch.getImportBatchId()))
                .hasSize(2).doesNotHaveDuplicates().allMatch(tag->tag.startsWith("AST-"));
        mvc.perform(post("/v1/assets").with(user("it01@itam.example").authorities(new SimpleGrantedAuthority("IT_STAFF")))
                .header("Accept-Language","en").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value(org.hamcrest.Matchers.containsString("Must not be blank")));
    }

    @Test void rejectedSourceCanBeCopiedWithoutDuplicatingAssetsOrLosingOriginalFile() throws Exception {
        var source=drafts.create("source",null);
        source=drafts.addHardware(source.transactionId(),source.expectedVersion(),hardware());
        var doc=documents.upload(file("source.pdf","unchanged"),source.transactionId(),DocumentType.INVOICE,null,source.expectedVersion());
        source=drafts.submit(source.transactionId(),source.expectedVersion()+1);
        long sourceId=source.transactionId();
        jdbc.update("UPDATE transactions SET status='REJECTED',processed_by=requester_id,processed_at=now(),rejection_reason='Test fixture rejection' WHERE transaction_id=?",sourceId);
        entityManager.clear();
        var copy=drafts.create(null,sourceId);
        assertThat(copy.transactionId()).isNotEqualTo(sourceId);
        assertThat(copy.transactionCode()).isNotEqualTo(source.transactionCode());
        assertThat(jdbc.queryForObject("SELECT asset_id FROM transaction_assets WHERE transaction_id=?",Long.class,copy.transactionId()))
                .isEqualTo(jdbc.queryForObject("SELECT asset_id FROM transaction_assets WHERE transaction_id=?",Long.class,sourceId));
        documents.delete(doc.documentId(),copy.transactionId(),copy.expectedVersion());
        assertThat(documents.getDocuments(sourceId,0,20).getContent()).hasSize(1);
        assertThat(documents.download(doc.documentId()).content()).isEqualTo(file("source.pdf","unchanged").getBytes());
        documents.upload(file("replacement.pdf","replacement"),copy.transactionId(),DocumentType.INVOICE,null,copy.expectedVersion()+1);
        assertThat(drafts.submit(copy.transactionId(),copy.expectedVersion()+2).status().name()).isEqualTo("PENDING");
        assertThat(drafts.revisions(sourceId)).hasSize(1);
    }

    @Test void workflowCatalogCodesCannotBeDisabledDeletedOrReplaced() {
        Long status=jdbc.queryForObject("SELECT status_id FROM asset_statuses WHERE code='PENDING_IMPORT'",Long.class);
        assertThatThrownBy(()->catalogs.deleteAssetStatus(status)).isInstanceOf(AppException.class);
        assertThatThrownBy(()->catalogs.toggleActiveStatus(status,false)).isInstanceOf(AppException.class);
        Long oem=jdbc.queryForObject("SELECT license_assignment_type_id FROM license_assignment_types WHERE code='OEM'",Long.class);
        assertThatThrownBy(()->catalogs.deleteLicenseAssignmentType(oem)).isInstanceOf(AppException.class);
        var request=new com.company.itam.catalog.dto.LicenseAssignmentTypeRequest();
        request.setCode("CUSTOM"); request.setName("Changed");
        assertThatThrownBy(()->catalogs.updateLicenseAssignmentType(oem,request)).isInstanceOf(AppException.class);
    }

    @Test void catalogSearchCombinesKeywordAndActiveFilter() {
        Long category=jdbc.queryForObject("SELECT category_id FROM asset_categories WHERE code='DEVICE'",Long.class);
        String marker="CHECKLIST_"+UUID.randomUUID();
        jdbc.update("INSERT INTO asset_types(code,name,category_id,is_active) VALUES (?,?,?,true),(?,?,?,false)",
                marker+"A",marker,category,marker+"B",marker,category);
        var result=catalogs.getAssetTypes(marker,true,org.springframework.data.domain.PageRequest.of(0,20));
        assertThat(result.getContent()).hasSize(1);
    }
}
