package com.company.itam;

import com.company.itam.auth.security.JwtTokenProvider;
import com.company.itam.asset.dto.request.CreateHardwareAssetRequest;
import com.company.itam.workflow.receiving.service.ImportDraftService;
import com.company.itam.document.service.DocumentService;
import com.company.itam.common.enums.DocumentType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.mock.web.MockMultipartFile;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="itam.documents.storage-root=target/p0-documents")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class P0AccessIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtTokenProvider tokens;
    @Autowired ImportDraftService drafts;
    @Autowired DocumentService documents;
    @Autowired ObjectMapper json;
    @Autowired com.company.itam.workflow.disposal.service.DisposalService disposal;
    @Autowired jakarta.persistence.EntityManager em;
    Long person;
    @BeforeEach void setup() {
        person=jdbc.queryForObject("INSERT INTO users(email,full_name,employee_code,role_id,account_status) SELECT ?,'P0 profile',? ,role_id,'ACTIVE' FROM roles WHERE code='USER' RETURNING user_id",Long.class,"p0-"+UUID.randomUUID()+"@example.test","TEST-"+UUID.randomUUID());
        // Commit only fixture setup: login audit runs in its own transaction.
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();
    }
    @AfterEach void cleanup(){SecurityContextHolder.clearContext();}
    org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor role(String role) {
        return user(switch(role){case "ADMIN"->"admin@itam.example";case "IT_STAFF"->"it01@itam.example";default->"pur01@itam.example";}).authorities(new SimpleGrantedAuthority(role));
    }
    void login(String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                role.equals("ADMIN")?"admin@itam.example":role.equals("IT_STAFF")?"it01@itam.example":"pur01@itam.example",null,List.of(new SimpleGrantedAuthority(role))));
    }
    String email(){return jdbc.queryForObject("SELECT email FROM users WHERE user_id=?",String.class,person);}
    String access(boolean grant,String status,boolean password) throws Exception {
        return mvc.perform(put("/v1/users/"+person+"/access").with(role("ADMIN")).contentType("application/json")
                .content("{\"granted\":"+grant+",\"status\":\""+status+"\""+(password?",\"password\":\"P0-test-pass\",\"username\":\"p0-user-"+person+"\"":"")+"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }
    String token() throws Exception {
        SecurityContextHolder.clearContext();
        return json.readTree(mvc.perform(post("/v1/auth/login").contentType("application/json")
                .content(json.writeValueAsString(java.util.Map.of("email",email(),"password","P0-test-pass"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data").path("token").asText();
    }
    @Test void tc08to10ExplicitGrantRevokeLockAndOldTokensPreserveProfile() throws Exception {
        String path="/v1/users/"+person+"/access";
        for(String role:List.of("IT_STAFF","PUR_STAFF","USER")) mvc.perform(put(path).with(role(role)).contentType("application/json")
                .content("{\"granted\":true,\"status\":\"ACTIVE\"}")).andExpect(status().isForbidden());
        mvc.perform(put(path).with(role("ADMIN")).contentType("application/json").content("{\"granted\":true,\"status\":\"ACTIVE\"}"))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT access_granted FROM users WHERE user_id=?",Boolean.class,person)).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE entity_type='USER' AND entity_id=?",Integer.class,person)).isZero();
        String ungranted=tokens.generateToken("user:"+person,"USER");
        mvc.perform(get("/v1/users/me/assets").header("Authorization","Bearer "+ungranted)).andExpect(status().isUnauthorized());
        access(true,"ACTIVE",true); String first=token();
        mvc.perform(get("/v1/users/me/assets").header("Authorization","Bearer "+first)).andExpect(status().isOk());
        access(false,"ACTIVE",false);
        mvc.perform(get("/v1/users/me/assets").header("Authorization","Bearer "+first)).andExpect(status().isUnauthorized());
        access(true,"ACTIVE",false);
        mvc.perform(get("/v1/users/me/assets").header("Authorization","Bearer "+first)).andExpect(status().isUnauthorized());
        String second=token(); access(true,"LOCKED",false);
        em.flush();
        assertThat(jdbc.queryForObject("SELECT account_status FROM users WHERE user_id=?",String.class,person)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT login_locked FROM users WHERE user_id=?",Boolean.class,person)).isTrue();
        mvc.perform(get("/v1/auth/me").header("Authorization","Bearer "+second)).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE user_id=?",Integer.class,person)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE entity_type='USER' AND entity_id=? AND action='ACCESS_CHANGED'",Integer.class,person)).isEqualTo(4);
    }
    @Test void tc09And07UserCannotReadInternalEndpointsOrOtherAssets() throws Exception {
        access(true,"ACTIVE",true); String token=token();
        Long own=asset(person),other=asset(null);
        mvc.perform(get("/v1/users/me/assets?assignedTo=1&size=1").header("Authorization","Bearer "+token))
                .andExpect(status().isOk()).andExpect(jsonPath("data.content[0].assetId").value(own)).andExpect(jsonPath("data.totalElements").value(1));
        mvc.perform(get("/v1/assets/"+other).header("Authorization","Bearer "+token)).andExpect(status().isNotFound());
        for(String path:List.of("/v1/assets","/v1/dashboard","/v1/audit","/v1/reports","/v1/transactions","/v1/documents/1/download","/v1/transactions/1/pdf","/v1/assets/"+own+"/history"))
            mvc.perform(get(path).header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
        for(String path:List.of("/v1/disposals","/v1/handovers","/v1/recoveries"))
            mvc.perform(post(path).with(role("PUR_STAFF")).contentType("application/json").content("{}")) .andExpect(status().isForbidden());
    }
    Long asset(Long assigned) {
        return jdbc.queryForObject("""
                INSERT INTO assets(asset_tag,name,type_id,status_id,assigned_to,created_by,updated_by)
                SELECT ?,'P0 asset',(SELECT type_id FROM asset_types WHERE code='LAPTOP'),
                (SELECT status_id FROM asset_statuses WHERE code=?),?,user_id,user_id FROM users WHERE email='admin@itam.example' RETURNING asset_id
                """,Long.class,"P0-"+UUID.randomUUID(),assigned==null?"IN_STOCK":"IN_USE",assigned);
    }
    @Test void currentLicenseAllocationIsListedWithoutPackageCapacityOrOtherPeople() throws Exception {
        Long license=asset(null);
        Long software=jdbc.queryForObject("INSERT INTO software_catalog(name,manufacturer,is_active) VALUES (?,'P0',true) RETURNING software_catalog_id",Long.class,"P0 software "+UUID.randomUUID());
        jdbc.update("INSERT INTO asset_license_details(asset_id,software_catalog_id,license_assignment_type_id,license_term_type_id,seat_count) VALUES (?,?,(SELECT license_assignment_type_id FROM license_assignment_types WHERE code='PER_USER'),(SELECT license_term_type_id FROM license_term_types WHERE code='PERPETUAL'),10)",license,software);
        jdbc.update("INSERT INTO license_allocations(license_asset_id,user_id,seat_count,status,created_by) VALUES (?,?,1,'ACTIVE',(SELECT user_id FROM users WHERE email='admin@itam.example'))",license,person);
        access(true,"ACTIVE",true);String token=token();
        mvc.perform(get("/v1/users/me/assets").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("data.content[0].assetId").value(license));
        mvc.perform(get("/v1/assets/"+license).header("Authorization","Bearer "+token)).andExpect(status().isOk())
                .andExpect(jsonPath("data.license.seatCount").doesNotExist()).andExpect(jsonPath("data.license.allocatedSeats").doesNotExist())
                .andExpect(jsonPath("data.license.licenseKey").doesNotExist()).andExpect(jsonPath("data.createdByUserId").doesNotExist());
        jdbc.update("UPDATE license_allocations SET status='RELEASED',released_at=now() WHERE license_asset_id=?",license);
        em.clear();
        mvc.perform(get("/v1/users/me/assets").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("data.totalElements").value(0));
        mvc.perform(get("/v1/assets/"+license).header("Authorization","Bearer "+token)).andExpect(status().isNotFound());
    }
    @Test void tc01to05DraftOwnerPendingLockExceptionAuditAndCompletedImmutable() throws Exception {
        login("PUR_STAFF"); var draft=drafts.create("P0",null);
        String path="/v1/import-drafts/"+draft.transactionId();
        var peer=user("peer@example.test").authorities(new SimpleGrantedAuthority("PUR_STAFF"));
        String update="{\"expectedVersion\":"+draft.expectedVersion()+",\"notes\":\"changed\"}";
        mvc.perform(put(path).with(peer).contentType("application/json").content(update)).andExpect(status().isForbidden());
        mvc.perform(put(path).with(role("ADMIN")).contentType("application/json").content(update)).andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("OVERRIDE_REASON_REQUIRED"));
        mvc.perform(put(path).with(role("ADMIN")).header("X-Override-Reason","P0 approved correction").contentType("application/json").content(update)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE transaction_id=? AND action='ADMIN_OVERRIDE'",Integer.class,draft.transactionId())).isEqualTo(1);
        login("PUR_STAFF");
        var hardware=new CreateHardwareAssetRequest();hardware.setName("P0 receiving");
        hardware.setTypeId(jdbc.queryForObject("SELECT type_id FROM asset_types WHERE code='LAPTOP'",Long.class));
        hardware.setConditionId(jdbc.queryForObject("SELECT min(condition_id) FROM asset_conditions WHERE is_active",Long.class));
        var ready=drafts.addHardware(draft.transactionId(),draft.expectedVersion()+1,hardware);
        documents.upload(new MockMultipartFile("file","p0.pdf","application/pdf","%PDF-1.4\nP0".getBytes()),ready.transactionId(),DocumentType.INVOICE,null,ready.expectedVersion());
        var pending=drafts.submit(ready.transactionId(),ready.expectedVersion()+1);
        String current="{\"expectedVersion\":"+pending.expectedVersion()+",\"expectedSubmissionRevision\":"+pending.submittedRevision()+",\"notes\":\"no\"}";
        mvc.perform(put(path).with(role("PUR_STAFF")).contentType("application/json").content(current)).andExpect(status().isConflict());
        mvc.perform(post(path+"/cancel").with(role("PUR_STAFF")).contentType("application/json").content(current)).andExpect(status().isForbidden());
        mvc.perform(post(path+"/withdraw").with(peer).contentType("application/json").content(current)).andExpect(status().isForbidden());
        mvc.perform(post(path+"/withdraw").with(role("PUR_STAFF")).contentType("application/json").content(current)).andExpect(status().isOk());
        login("PUR_STAFF");pending=drafts.submit(pending.transactionId(),pending.expectedVersion()+1);
        login("IT_STAFF");var completed=drafts.process(pending.transactionId(),pending.expectedVersion(),pending.submittedRevision(),true,null);
        String done="{\"expectedVersion\":"+completed.expectedVersion()+",\"expectedSubmissionRevision\":"+completed.submittedRevision()+",\"notes\":\"no\"}";
        for(String action:List.of("withdraw","cancel","approve","reject")) mvc.perform(post(path+"/"+action).with(role("ADMIN")).header("X-Override-Reason","No bypass").contentType("application/json").content(done)).andExpect(status().isConflict());
        mvc.perform(put(path).with(role("ADMIN")).header("X-Override-Reason","No bypass").contentType("application/json").content(done)).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT status FROM transactions WHERE transaction_id=?",String.class,draft.transactionId())).isEqualTo("COMPLETED");
    }

    @Test void disposalPendingMustWithdrawBeforeEditingAndOldFingerprintCannotReplay() throws Exception {
        Long asset=asset(null);
        jdbc.update("UPDATE assets SET status_id=(SELECT status_id FROM asset_statuses WHERE code='DAMAGED') WHERE asset_id=?",asset);
        login("IT_STAFF");
        var checked=disposal.smartCheck(new com.company.itam.workflow.disposal.dto.DisposalModels.SmartCheckRequest(List.of(asset)));
        var pending=disposal.create(new com.company.itam.workflow.disposal.dto.DisposalModels.CreateRequest(List.of(asset),"P0 disposal",java.time.LocalDate.now(),checked.fingerprint(),null));
        String path="/v1/disposals/"+pending.transactionId();
        String reviewed=json.writeValueAsString(java.util.Map.of("expectedFingerprint",pending.fingerprint()));
        mvc.perform(post(path+"/withdraw").with(user("peer-it@example.test").authorities(new SimpleGrantedAuthority("IT_STAFF"))).contentType("application/json").content(reviewed)).andExpect(status().isForbidden());
        mvc.perform(post(path+"/cancel").with(role("IT_STAFF")).contentType("application/json").content(reviewed)).andExpect(status().isForbidden());
        mvc.perform(post(path+"/per-user").with(role("IT_STAFF")).contentType("application/json").content("{\"expectedFingerprint\":\"x\",\"decisions\":[{\"allocationId\":1,\"release\":true}]}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("code").value("DOCUMENT_LOCKED"));
        mvc.perform(post(path+"/withdraw").with(role("ADMIN")).contentType("application/json").content(reviewed)).andExpect(status().isBadRequest());
        mvc.perform(post(path+"/withdraw").with(role("ADMIN")).header("X-Override-Reason","P0 correction").contentType("application/json").content(reviewed)).andExpect(status().isOk());
        em.flush();login("IT_STAFF");var draft=disposal.get(pending.transactionId());
        var editCheck=disposal.smartCheck(new com.company.itam.workflow.disposal.dto.DisposalModels.SmartCheckRequest(List.of(asset)));
        draft=disposal.edit(draft.transactionId(),new com.company.itam.workflow.disposal.dto.DisposalModels.CreateRequest(
                List.of(asset),"Corrected draft",java.time.LocalDate.now(),editCheck.fingerprint(),draft.fingerprint()));
        assertThat(draft.reason()).isEqualTo("Corrected draft");
        var submitted=disposal.transition(draft.transactionId(),"submit",draft.fingerprint());
        assertThat(submitted.fingerprint()).isNotEqualTo(pending.fingerprint());
        mvc.perform(post(path+"/withdraw").with(role("IT_STAFF")).contentType("application/json").content(reviewed)).andExpect(status().isConflict());
        login("IT_STAFF");draft=disposal.transition(submitted.transactionId(),"withdraw",submitted.fingerprint());
        var cancelled=disposal.transition(draft.transactionId(),"cancel",draft.fingerprint());
        assertThat(cancelled.status()).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT s.code FROM assets a JOIN asset_statuses s USING(status_id) WHERE asset_id=?",String.class,asset)).isEqualTo("DAMAGED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE transaction_id=? AND action='ADMIN_OVERRIDE'",Integer.class,pending.transactionId())).isEqualTo(1);
    }
}
