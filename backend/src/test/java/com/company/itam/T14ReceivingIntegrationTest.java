package com.company.itam;

import com.company.itam.asset.dto.request.*;
import com.company.itam.common.enums.DocumentType;
import com.company.itam.common.exception.AppException;
import com.company.itam.document.service.DocumentService;
import com.company.itam.workflow.core.dto.TransactionSummaryResponse;
import com.company.itam.workflow.receiving.service.*;
import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="itam.documents.storage-root=target/t14-documents")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class T14ReceivingIntegrationTest {
    @Autowired ImportDraftService drafts;
    @Autowired ReceivingLineService lines;
    @Autowired DocumentService documents;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired jakarta.persistence.EntityManager em;
    void login(String role) {
        String email=switch(role){case "ADMIN"->"admin@itam.example";case "IT_STAFF"->"it01@itam.example";default->"pur01@itam.example";};
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(email,null,List.of(new SimpleGrantedAuthority(role))));
    }
    @BeforeEach void setup(){
        login("PUR_STAFF");
        jdbc.update("INSERT INTO software_catalog(name,manufacturer,version,is_active) VALUES ('T14 Demo Software','Fictional','1.0',true) ON CONFLICT DO NOTHING");
    }
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    CreateHardwareAssetRequest hardware() {
        var r=new CreateHardwareAssetRequest();r.setName("T14 sample "+UUID.randomUUID());
        r.setTypeId(jdbc.queryForObject("SELECT type_id FROM asset_types WHERE code='LAPTOP'",Long.class));
        r.setConditionId(jdbc.queryForObject("SELECT min(condition_id) FROM asset_conditions WHERE is_active",Long.class));
        return r;
    }
    TransactionSummaryResponse document(TransactionSummaryResponse t) {
        documents.upload(new MockMultipartFile("file","sample.pdf","application/pdf","%PDF-1.4\nT14 SAMPLE".getBytes()),t.transactionId(),DocumentType.INVOICE,null,t.expectedVersion());
        return drafts.updateNotes(t.transactionId(),t.expectedVersion()+1,"Sample receiving");
    }
    TransactionSummaryResponse ready(int count) {
        var t=drafts.create("T14",null);
        for(int i=0;i<count;i++)t=drafts.addHardware(t.transactionId(),t.expectedVersion(),hardware());
        return document(t);
    }
    long asset(Long id){return drafts.content(id).path("assets").get(0).path("assetId").asLong();}
    String state(long id){return jdbc.queryForObject("SELECT s.code FROM assets a JOIN asset_statuses s USING(status_id) WHERE a.asset_id=?",String.class,id);}

    @Test @Transactional void approvalReceivesAllThreeCategoriesAndKeepsSnapshot() {
        var t=ready(1);
        var component=hardware();component.setTypeId(jdbc.queryForObject("SELECT min(t.type_id) FROM asset_types t JOIN asset_categories c USING(category_id) WHERE c.code='COMPONENT' AND t.is_active",Long.class));
        t=drafts.addHardware(t.transactionId(),t.expectedVersion(),component);
        var license=new CreateHardwareAssetRequest();license.setName("T14 package");
        license.setTypeId(jdbc.queryForObject("SELECT min(t.type_id) FROM asset_types t JOIN asset_categories c USING(category_id) WHERE c.code='LICENSE' AND t.is_active",Long.class));
        license.setLicense(new LicenseDetailsRequest(jdbc.queryForObject("SELECT min(software_catalog_id) FROM software_catalog WHERE is_active",Long.class),
            jdbc.queryForObject("SELECT license_assignment_type_id FROM license_assignment_types WHERE code='PER_USER'",Long.class),
            jdbc.queryForObject("SELECT license_term_type_id FROM license_term_types WHERE code='PERPETUAL'",Long.class),10,null,null));
        t=drafts.addHardware(t.transactionId(),t.expectedVersion(),license);
        t=drafts.submit(t.transactionId(),t.expectedVersion());
        var before=drafts.content(t.transactionId()).toString();
        login("IT_STAFF");var done=drafts.process(t.transactionId(),t.expectedVersion(),t.submittedRevision(),true,null);
        assertThat(done.status().name()).isEqualTo("COMPLETED");
        for(JsonNode a:drafts.content(t.transactionId()).path("assets"))assertThat(state(a.path("assetId").asLong())).isEqualTo("IN_STOCK");
        assertThat(drafts.content(t.transactionId()).toString()).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM import_asset_reservations WHERE transaction_id=?",Integer.class,t.transactionId())).isZero();
        assertThat(drafts.events(t.transactionId())).extracting(e->e.get("action")).containsExactly("SUBMITTED","COMPLETED");
    }

    @Test @Transactional void staleApprovalAndRejectionCannotProcessResubmittedRevision() {
        var t=ready(1);t=drafts.submit(t.transactionId(),t.expectedVersion());var old=t;
        t=drafts.withdraw(t.transactionId(),t.expectedVersion(),t.submittedRevision());
        var input=lines.request(drafts.content(t.transactionId()).path("assets").get(0));input.setName("Revised name");
        t=drafts.editLine(t.transactionId(),t.expectedVersion(),asset(t.transactionId()),input);
        t=drafts.submit(t.transactionId(),t.expectedVersion());
        assertThat(t.transactionCode()).isEqualTo(old.transactionCode());
        assertThat(((JsonNode)drafts.revisions(t.transactionId()).get(1).get("snapshot")).path("assets").get(0).path("name").asText()).isNotEqualTo("Revised name");
        login("IT_STAFF");
        assertThatThrownBy(()->drafts.process(old.transactionId(),old.expectedVersion(),old.submittedRevision(),true,null)).isInstanceOf(AppException.class).hasMessage("TRANSACTION_VERSION_CONFLICT");
        var current=t;
        assertThatThrownBy(()->drafts.process(current.transactionId(),current.expectedVersion(),old.submittedRevision(),false,"Wrong revision")).isInstanceOf(AppException.class).hasMessage("TRANSACTION_VERSION_CONFLICT");
    }

    @Test @Transactional void rejectedCopyReusesIdentityAndPreservesWorkingCopies() {
        var source=ready(1);source=drafts.submit(source.transactionId(),source.expectedVersion());long asset=asset(source.transactionId());
        String tag=lines.current(asset).path("assetTag").asText();login("IT_STAFF");
        source=drafts.process(source.transactionId(),source.expectedVersion(),source.submittedRevision(),false,"Wrong configuration");
        login("PUR_STAFF");var copy=drafts.create(null,source.transactionId());var other=drafts.create(null,source.transactionId());
        var input=lines.request(drafts.content(copy.transactionId()).path("assets").get(0));input.setName("Independent draft");
        copy=drafts.editLine(copy.transactionId(),copy.expectedVersion(),asset,input);
        assertThat(drafts.content(other.transactionId()).path("assets").get(0).path("name").asText()).isNotEqualTo("Independent draft");
        copy=drafts.submit(copy.transactionId(),copy.expectedVersion());
        login("IT_STAFF");drafts.process(copy.transactionId(),copy.expectedVersion(),copy.submittedRevision(),true,null);
        assertThat(lines.current(asset).path("assetTag").asText()).isEqualTo(tag);
        login("PUR_STAFF");var stale=other;
        assertThatThrownBy(()->drafts.submit(stale.transactionId(),stale.expectedVersion())).isInstanceOf(AppException.class).hasMessage("IMPORT_ASSET_UNAVAILABLE");
        assertThat(drafts.content(source.transactionId()).path("assets").get(0).path("name").asText()).isNotEqualTo("Independent draft");
    }

    @Test @Transactional void requiresSampleDocumentAndHardwareCondition() {
        var t=drafts.create(null,null);var request=hardware();request.setConditionId(null);
        t=drafts.addHardware(t.transactionId(),t.expectedVersion(),request);var noDocument=t;
        assertThatThrownBy(()->drafts.submit(noDocument.transactionId(),noDocument.expectedVersion())).isInstanceOf(AppException.class).hasMessage("IMPORT_DOCUMENT_REQUIRED");
        t=document(t);var noCondition=t;
        assertThatThrownBy(()->drafts.submit(noCondition.transactionId(),noCondition.expectedVersion())).isInstanceOf(AppException.class).hasMessage("IMPORT_CONDITION_REQUIRED");
        assertThat(drafts.events(t.transactionId())).isEmpty();
    }

    @Test @Transactional void routesEnforceReviewerRoleAndRequiredRevisionAndReason() throws Exception {
        var t=ready(1);t=drafts.submit(t.transactionId(),t.expectedVersion());String path="/v1/import-drafts/"+t.transactionId();
        String body="{\"expectedVersion\":"+t.expectedVersion()+",\"expectedSubmissionRevision\":"+t.submittedRevision()+"}";
        for(String role:List.of("PUR_STAFF","USER"))mvc.perform(post(path+"/approve").with(user("pur01@itam.example").authorities(new SimpleGrantedAuthority(role))).contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(post(path+"/approve").with(user("it01@itam.example").authorities(new SimpleGrantedAuthority("IT_STAFF"))).contentType("application/json").content("{\"expectedVersion\":"+t.expectedVersion()+"}")).andExpect(status().isBadRequest());
        mvc.perform(post(path+"/reject").with(user("it01@itam.example").authorities(new SimpleGrantedAuthority("IT_STAFF"))).header("Accept-Language","en").contentType("application/json").content(body)).andExpect(status().isConflict()).andExpect(jsonPath("code").value("REJECTION_REASON_REQUIRED"));
    }

    @Test @Transactional void adminCanProcessOwnReceivingAndTagIsLockedAfterSubmit() throws Exception {
        login("ADMIN");var t=ready(1);long asset=asset(t.transactionId());
        var input=lines.request(drafts.content(t.transactionId()).path("assets").get(0));input.setAssetTag("T14-MANUAL-"+UUID.randomUUID());
        t=drafts.editLine(t.transactionId(),t.expectedVersion(),asset,input);
        assertThat(lines.current(asset).path("assetTag").asText()).isEqualTo(input.getAssetTag());
        t=drafts.submit(t.transactionId(),t.expectedVersion());var pending=t;
        assertThatThrownBy(()->drafts.editLine(pending.transactionId(),pending.expectedVersion(),asset,input)).isInstanceOf(AppException.class).hasMessage("DOCUMENT_LOCKED");
        assertThat(drafts.process(t.transactionId(),t.expectedVersion(),t.submittedRevision(),true,null).status().name()).isEqualTo("COMPLETED");
    }

    @Test void failedApprovalRollsBackEarlierAssetUpdatesAndEvents() {
        var t=ready(2);t=drafts.submit(t.transactionId(),t.expectedVersion());var target=t;
        List<Long> ids=jdbc.queryForList("SELECT asset_id FROM transaction_assets WHERE transaction_id=? ORDER BY asset_id",Long.class,t.transactionId());
        // Simulate a conflicting change discovered after the first asset update.
        jdbc.update("UPDATE assets SET status_id=(SELECT status_id FROM asset_statuses WHERE code='RETIRED') WHERE asset_id=?",ids.get(1));
        login("IT_STAFF");
        assertThatThrownBy(()->drafts.process(target.transactionId(),target.expectedVersion(),target.submittedRevision(),true,null)).isInstanceOf(AppException.class);
        assertThat(state(ids.getFirst())).isEqualTo("PENDING_IMPORT");
        assertThat(jdbc.queryForObject("SELECT status FROM transactions WHERE transaction_id=?",String.class,t.transactionId())).isEqualTo("PENDING");
        assertThat(drafts.events(t.transactionId())).hasSize(1);
    }

    @Test void approvalRacingWithdrawalCommitsExactlyOneTransition() throws Exception {
        var t=ready(1);t=drafts.submit(t.transactionId(),t.expectedVersion());final var target=t;
        try(var pool=Executors.newFixedThreadPool(2)) {
            CountDownLatch start=new CountDownLatch(1);
            var approve=pool.submit(()->{login("IT_STAFF");try{start.await();drafts.process(target.transactionId(),target.expectedVersion(),target.submittedRevision(),true,null);return true;}catch(AppException e){assertThat(e.getCode()).isEqualTo("TRANSACTION_VERSION_CONFLICT");return false;}finally{SecurityContextHolder.clearContext();}});
            var withdraw=pool.submit(()->{login("PUR_STAFF");try{start.await();drafts.withdraw(target.transactionId(),target.expectedVersion(),target.submittedRevision());return true;}catch(AppException e){assertThat(e.getCode()).isEqualTo("TRANSACTION_VERSION_CONFLICT");return false;}finally{SecurityContextHolder.clearContext();}});
            start.countDown();assertThat(approve.get(20,TimeUnit.SECONDS)).isNotEqualTo(withdraw.get(20,TimeUnit.SECONDS));
        }
        assertThat(drafts.events(t.transactionId())).hasSize(2);
    }
    @Test void twoDraftsCannotReserveTheSameAssetAndRejectionReleasesIt() throws Exception {
        var first=ready(1);long asset=asset(first.transactionId());
        var second=drafts.create("Another purchaser draft",null);
        second=drafts.reuse(second.transactionId(),second.expectedVersion(),asset);second=document(second);
        final var a=first;final var b=second;
        boolean firstWon;
        try(var pool=Executors.newFixedThreadPool(2)) {
            CountDownLatch start=new CountDownLatch(1);
            var one=pool.submit(()->{login("PUR_STAFF");try{start.await();drafts.submit(a.transactionId(),a.expectedVersion());return true;}catch(AppException e){assertThat(e.getCode()).isEqualTo("IMPORT_ASSET_UNAVAILABLE");return false;}finally{SecurityContextHolder.clearContext();}});
            var two=pool.submit(()->{login("PUR_STAFF");try{start.await();drafts.submit(b.transactionId(),b.expectedVersion());return true;}catch(AppException e){assertThat(e.getCode()).isEqualTo("IMPORT_ASSET_UNAVAILABLE");return false;}finally{SecurityContextHolder.clearContext();}});
            start.countDown();firstWon=one.get(20,TimeUnit.SECONDS);assertThat(firstWon).isNotEqualTo(two.get(20,TimeUnit.SECONDS));
        }
        var winner=firstWon?a:b;var waiting=firstWon?b:a;
        login("IT_STAFF");drafts.process(winner.transactionId(),winner.expectedVersion()+1,1,false,"Use the other draft");
        login("PUR_STAFF");var submitted=drafts.submit(waiting.transactionId(),waiting.expectedVersion());
        assertThat(submitted.status().name()).isEqualTo("PENDING");
        assertThat(drafts.events(winner.transactionId())).hasSize(2);
        assertThat(drafts.events(waiting.transactionId())).hasSize(1);
    }

    @Test @Transactional void emptyDraftAndInvalidReferencesDoNotEmitSubmissionEvents() throws Exception {
        var t=drafts.create(null,null);
        assertThatThrownBy(()->drafts.submit(t.transactionId(),t.expectedVersion())).isInstanceOf(AppException.class).hasMessage("IMPORT_EMPTY");
        mvc.perform(get("/v1/import-drafts/reference-data").with(user("pur01@itam.example").authorities(new SimpleGrantedAuthority("PUR_STAFF"))))
            .andExpect(status().isOk()).andExpect(jsonPath("data.types").isArray()).andExpect(jsonPath("data.software").isArray()).andExpect(jsonPath("data.users").doesNotExist());
        for(String path:List.of("/v1/assets","/v1/users","/v1/assets/1/history"))mvc.perform(get(path).with(user("pur01@itam.example").authorities(new SimpleGrantedAuthority("PUR_STAFF")))).andExpect(status().isForbidden());
        assertThat(drafts.events(t.transactionId())).isEmpty();
    }

}
