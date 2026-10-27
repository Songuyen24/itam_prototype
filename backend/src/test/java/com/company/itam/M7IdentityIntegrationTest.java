package com.company.itam;

import com.company.itam.asset.dto.request.*;
import com.company.itam.asset.service.AssetService;
import com.company.itam.common.enums.*;
import com.company.itam.common.exception.AppException;
import com.company.itam.user.service.IdentityService;
import com.company.itam.workflow.handover.dto.HandoverRequest;
import com.company.itam.workflow.handover.service.HandoverService;
import com.company.itam.workflow.recovery.dto.*;
import com.company.itam.workflow.recovery.service.RecoveryService;
import com.fasterxml.jackson.databind.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.*;
import javax.sql.DataSource;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class M7IdentityIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired IdentityService identities;
    @Autowired AssetService assets;
    @Autowired HandoverService handovers;
    @Autowired RecoveryService recoveries;
    @Autowired jakarta.persistence.EntityManager em;
    @Autowired DataSource dataSource;
    @Autowired org.springframework.transaction.PlatformTransactionManager manager;
    final String password="M7-test-password";
    String marker;
    @BeforeEach void setup() { marker="M7-"+UUID.randomUUID(); loginActor("admin@itam.example","ADMIN"); }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }
    void loginActor(String principal,String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,List.of(new SimpleGrantedAuthority(role))));
    }
    org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor as(String role) {
        return user(switch(role){case "ADMIN"->"admin@itam.example";case "IT_STAFF"->"it01@itam.example";default->"pur01@itam.example";}).authorities(new SimpleGrantedAuthority(role));
    }
    Long profile() {
        // The independently committed login audit must be able to reference this fixture actor.
        var fixture = new org.springframework.transaction.support.TransactionTemplate(manager);
        fixture.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return fixture.execute(status -> identities.saveProfile(null,new IdentityService.Profile(marker,marker,null,null,null)).getUserId());
    }
    String body(Object value) throws Exception { return json.writeValueAsString(value); }
    String token(String identifier,String pass) throws Exception {
        SecurityContextHolder.clearContext();
        return json.readTree(mvc.perform(post("/v1/auth/login").contentType("application/json").content(body(Map.of("email",identifier,"password",pass))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).at("/data/token").asText();
    }
    Long device() {
        var r=new CreateHardwareAssetRequest();r.setCreationPurpose(AssetCreationPurpose.BASELINE);r.setName(marker);
        r.setTypeId(jdbc.queryForObject("SELECT t.type_id FROM asset_types t JOIN asset_categories c USING(category_id) WHERE c.code='DEVICE' AND t.is_active LIMIT 1",Long.class));
        r.setConditionId(jdbc.queryForObject("SELECT min(condition_id) FROM asset_conditions WHERE is_active",Long.class));
        return assets.createHardwareAsset(r).getAssetId();
    }
    Long location() {
        jdbc.update("INSERT INTO locations(code,name,is_active) VALUES (?,?,true) ON CONFLICT (code) DO NOTHING",marker,marker);
        return jdbc.queryForObject("SELECT location_id FROM locations WHERE code=?",Long.class,marker);
    }
    HandoverRequest request(Long person,Long device) { return new HandoverRequest(person,location(),LocalDate.now(),List.of(device),List.of(),marker,null); }

    @Test void profileWithoutEmailOrAccountCanBeSearchedByCodeAndPurReadsOnly() throws Exception {
        Long id=profile();
        mvc.perform(get("/v1/users").with(as("PUR_STAFF")).param("keyword",marker).param("size","1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1))
            .andExpect(jsonPath("$.data.content[0].employeeCode").value(marker)).andExpect(jsonPath("$.data.content[0].hasAccount").value(false));
        mvc.perform(get("/v1/users/"+id+"/holdings").with(as("PUR_STAFF"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.assets.totalElements").value(0));
        mvc.perform(get("/v1/handovers/holders/"+id).with(as("PUR_STAFF"))).andExpect(status().isForbidden());
        mvc.perform(put("/v1/users/"+id+"/status").with(as("PUR_STAFF")).contentType("application/json").content("{\"status\":\"LOCKED\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/v1/users").with(as("IT_STAFF")).contentType("application/json").content("{\"fullName\":\"Name\",\"employeeCode\":\" \"}")).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT password_hash FROM users WHERE user_id=?",String.class,id)).isNull();
    }
    @Test void newAccountLinksExistingProfileAndUsernameOnlyLoginWorks() throws Exception {
        Long id=profile(); Long device=device(); jdbc.update("UPDATE assets SET assigned_to=? WHERE asset_id=?",id,device);
        int before=jdbc.queryForObject("SELECT count(*) FROM users",Integer.class);
        identities.createAccount(new IdentityService.CreateAccount(id," User name "+marker+" ",password,Role.USER,true)); em.flush();em.clear();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users",Integer.class)).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT assigned_to FROM assets WHERE asset_id=?",Long.class,device)).isEqualTo(id);
        String token=token("user name "+marker.toLowerCase(Locale.ROOT),password);
        mvc.perform(get("/v1/users/me/assets").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1));
        String me=mvc.perform(get("/v1/auth/me").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(me).doesNotContain("password", "accessVersion");
        mvc.perform(get("/v1/users").header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
        mvc.perform(get("/v1/accounts").with(as("IT_STAFF"))).andExpect(status().isForbidden());
    }
    @Test void profileLockDoesNotLockAccountButAccountLockInvalidatesOldTokens() throws Exception {
        Long id=profile(); identities.createAccount(new IdentityService.CreateAccount(id,marker,password,Role.IT_STAFF,false));em.flush();em.clear();
        String first=token(marker,password);loginActor("admin@itam.example","ADMIN");
        identities.profileStatus(id,AccountStatus.LOCKED);em.flush();em.clear();
        mvc.perform(get("/v1/auth/me").header("Authorization","Bearer "+first)).andExpect(status().isOk());
        loginActor("admin@itam.example","ADMIN"); identities.updateAccount(id,new IdentityService.UpdateAccount(Role.IT_STAFF,false,true));em.flush();em.clear();
        mvc.perform(get("/v1/auth/me").header("Authorization","Bearer "+first)).andExpect(status().isUnauthorized());
        loginActor("admin@itam.example","ADMIN"); identities.updateAccount(id,new IdentityService.UpdateAccount(Role.IT_STAFF,false,false));em.flush();em.clear();
        mvc.perform(get("/v1/auth/me").header("Authorization","Bearer "+first)).andExpect(status().isUnauthorized());
        assertThat(token(marker,password)).isNotBlank();
    }
    @Test void ownPasswordRequiresCurrentAndConfirmationAndInvalidatesEverySession() throws Exception {
        Long id=profile();identities.createAccount(new IdentityService.CreateAccount(id,marker,password,Role.USER,true));em.flush();em.clear();
        String first=token(marker,password),second=token(marker,password);
        for(var request:List.of(Map.of("currentPassword","wrong","newPassword","M7-new-password","confirmPassword","M7-new-password"),
                Map.of("currentPassword",password,"newPassword","M7-new-password","confirmPassword","different"))) {
            mvc.perform(post("/v1/auth/password").header("Authorization","Bearer "+first).contentType("application/json").content(body(request))).andExpect(status().isBadRequest());
        }
        mvc.perform(post("/v1/auth/password").header("Authorization","Bearer "+first).contentType("application/json")
            .content(body(Map.of("currentPassword",password,"newPassword","M7-new-password","confirmPassword","M7-new-password")))).andExpect(status().isOk());em.flush();em.clear();
        for(String old:List.of(first,second)) mvc.perform(get("/v1/auth/me").header("Authorization","Bearer "+old)).andExpect(status().isUnauthorized());
        assertThat(token(marker,"M7-new-password")).isNotBlank();
        assertThat(jdbc.queryForList("SELECT coalesce(old_data::text,'') || coalesce(new_data::text,'') FROM audit_logs WHERE entity_id=? AND entity_type='USER'",String.class,id))
            .allMatch(s->!s.contains(password)&&!s.contains("M7-new-password")&&!s.contains("$2"));
    }
    @Test void adminCannotChangeOwnPermissionsAndCannotBypassUsernameViaLegacyAccess() throws Exception {
        Long id=profile();
        Long admin=jdbc.queryForObject("SELECT user_id FROM users WHERE email='admin@itam.example'",Long.class);
        mvc.perform(put("/v1/accounts/"+admin).with(as("ADMIN")).contentType("application/json").content("{\"roleCode\":\"USER\",\"accessGranted\":false,\"loginLocked\":true}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ACCOUNT_SELF_PERMISSION_DENIED"));
        loginActor("admin@itam.example","ADMIN");
        mvc.perform(put("/v1/users/"+id+"/access").with(as("ADMIN")).contentType("application/json").content(body(Map.of("granted",true,"status","ACTIVE","password",password))))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("ACCOUNT_USERNAME_REQUIRED"));
        assertThat(jdbc.queryForObject("SELECT password_hash FROM users WHERE user_id=?",String.class,id)).isNull();
    }
    @Test void lockedEmailLessProfileCanReturnAssetsAndCompletedSnapshotDoesNotChange() {
        Long id=profile(),device=device();var req=request(id,device);var preview=handovers.preview(req);
        var completed=handovers.complete(new HandoverRequest(id,location(),req.handoverDate(),req.assetIds(),List.of(),marker,preview.fingerprint()));
        String snapshot=jdbc.queryForObject("SELECT snapshot::text FROM handover_snapshots WHERE transaction_id=?",String.class,completed.transactionId());
        identities.profileStatus(id,AccountStatus.LOCKED);em.flush();em.clear();
        var check=recoveries.smartCheck(new SmartCheckRequest(List.of(device),id,List.of(),"Return"));
        recoveries.complete(new RecoveryRequest(id,location(),LocalDate.now(),"Return",List.of(device),List.of(),Map.of(),Map.of(),check.fingerprint()));
        assertThat(jdbc.queryForObject("SELECT assigned_to FROM assets WHERE asset_id=?",Long.class,device)).isNull();
        assertThat(jdbc.queryForObject("SELECT snapshot::text FROM handover_snapshots WHERE transaction_id=?",String.class,completed.transactionId())).isEqualTo(snapshot);
        assertThatThrownBy(()->handovers.preview(request(id,device))).isInstanceOf(AppException.class).extracting("code").isEqualTo("HANDOVER_RECIPIENT_INACTIVE");
    }
    @Test void legacyMissingCodeRemainsUsableAndCanBeFilledWithoutChangingIdentity() {
        Long id=jdbc.queryForObject("INSERT INTO users(full_name,role_id) SELECT ?,role_id FROM roles WHERE code='USER' RETURNING user_id",Long.class,marker);
        Long device=device();
        assertThat(handovers.preview(request(id,device)).recipientUserId()).isEqualTo(id);
        identities.saveProfile(id,new IdentityService.Profile("Legacy edited",null,null,null,null));
        identities.createAccount(new IdentityService.CreateAccount(id,marker,password,Role.USER,true));
        identities.saveProfile(id,new IdentityService.Profile("Legacy edited",marker,null,null,null));em.flush();
        assertThat(jdbc.queryForObject("SELECT employee_code FROM users WHERE user_id=?",String.class,id)).isEqualTo(marker);
        assertThat(jdbc.queryForObject("SELECT username FROM users WHERE user_id=?",String.class,id)).isEqualTo(marker);
    }
    @Test void usernameAndEmailNamespacesCannotCollideAndCodeCorrectionNeedsReason() throws Exception {
        Long first=profile(); identities.createAccount(new IdentityService.CreateAccount(first,marker+"@example.test",password,Role.USER,true));
        Long second=identities.saveProfile(null,new IdentityService.Profile("Other",marker+"-2",null,null,null)).getUserId();
        mvc.perform(put("/v1/users/"+second).with(as("ADMIN")).contentType("application/json")
            .content(body(Map.of("fullName","Other","employeeCode",marker+"-2","email",marker.toUpperCase(Locale.ROOT)+"@example.test"))))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDENTITY_DUPLICATE"));
        mvc.perform(put("/v1/users/"+first).with(as("IT_STAFF")).contentType("application/json")
            .content(body(Map.of("fullName",marker,"employeeCode",marker+"-corrected"))))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("EMPLOYEE_CODE_CORRECTION_REQUIRED"));
    }
    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void concurrentProfileCreatesCannotDuplicateCompanyCodeOrEmail() throws Exception {
        for(boolean sameCode:List.of(true,false)) {
            String key=marker+(sameCode?"-code":"-email");
            try(var pool=Executors.newFixedThreadPool(2)) {
                var start=new CountDownLatch(1);var results=new ArrayList<Future<Integer>>();
                for(int i=0;i<2;i++) {final int n=i;results.add(pool.submit(()->{
                    start.await();return mvc.perform(post("/v1/users").with(as("ADMIN")).contentType("application/json")
                        .content(body(Map.of("fullName",key,"employeeCode",sameCode?key:key+n,"email",sameCode?key+n+"@example.test":key+"@example.test"))))
                        .andReturn().getResponse().getStatus();
                }));}
                start.countDown();var statuses=new ArrayList<Integer>();for(var result:results) statuses.add(result.get(30,TimeUnit.SECONDS));
                assertThat(statuses).containsExactlyInAnyOrder(200,409);
            } finally {
                jdbc.update("DELETE FROM audit_logs WHERE entity_type='USER' AND entity_id IN (SELECT user_id FROM users WHERE full_name=?)",key);
                jdbc.update("DELETE FROM users WHERE full_name=?",key);
            }
        }
    }
    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void concurrentAdminsCannotBothDisableEachOtherUsingStaleAuthority() throws Exception {
        Long first=identities.saveProfile(null,new IdentityService.Profile(marker+"A",marker+"A",null,null,null)).getUserId();
        Long second=identities.saveProfile(null,new IdentityService.Profile(marker+"B",marker+"B",null,null,null)).getUserId();
        identities.createAccount(new IdentityService.CreateAccount(first,marker+"A",password,Role.ADMIN,false));
        identities.createAccount(new IdentityService.CreateAccount(second,marker+"B",password,Role.ADMIN,false));
        try(var pool=Executors.newFixedThreadPool(2)) {
            var start=new CountDownLatch(1);var results=new ArrayList<Future<Boolean>>();
            for(Long actor:List.of(first,second)) results.add(pool.submit(()->{
                loginActor("user:"+actor,"ADMIN");start.await();
                try { identities.updateAccount(actor.equals(first)?second:first,new IdentityService.UpdateAccount(Role.ADMIN,false,true));return true; }
                catch(org.springframework.security.access.AccessDeniedException denied) { return false; }
                finally { SecurityContextHolder.clearContext(); }
            }));
            start.countDown();var outcomes=new ArrayList<Boolean>();for(var result:results) outcomes.add(result.get(30,TimeUnit.SECONDS));
            assertThat(outcomes).containsExactlyInAnyOrder(true,false);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE user_id IN (?,?) AND NOT login_locked",Integer.class,first,second)).isEqualTo(1);
        } finally {
            jdbc.update("DELETE FROM audit_logs WHERE actor_user_id IN (?,?) OR (entity_type='USER' AND entity_id IN (?,?))",first,second,first,second);
            jdbc.update("DELETE FROM users WHERE user_id IN (?,?)",first,second);
        }
    }
    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void migrationPreservesLegacyIdsAssignedAssetsAndLockedStaffWithoutInventingCodes() {
        String schema="m7_upgrade_"+UUID.randomUUID().toString().replace("-","");
        var latest=Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).cleanDisabled(false).locations("classpath:db/migration").load();
        try {
            Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("28").locations("classpath:db/migration").load().migrate();
            Long id=jdbc.queryForObject("SELECT user_id FROM "+schema+".users WHERE email='it01@itam.example'",Long.class);
            jdbc.update("UPDATE "+schema+".users SET account_status='LOCKED' WHERE user_id=?",id);
            Long userId=jdbc.queryForObject("SELECT user_id FROM "+schema+".users WHERE email='user01@itam.example'",Long.class);
            jdbc.update("UPDATE "+schema+".users SET account_status='LOCKED', login_locked=false WHERE user_id=?",userId);
            Long asset=jdbc.queryForObject("INSERT INTO "+schema+".assets(asset_tag,name,type_id,status_id,assigned_to,created_by) SELECT 'M7-preserved','Assigned',(SELECT min(type_id) FROM "+schema+".asset_types),(SELECT status_id FROM "+schema+".asset_statuses WHERE code='IN_USE'),?,? RETURNING asset_id",Long.class,id,id);
            Long tx=jdbc.queryForObject("INSERT INTO "+schema+".transactions(transaction_code,type,status,requester_id) VALUES ('M7-history','HANDOVER','DRAFT',?) RETURNING transaction_id",Long.class,id);
            jdbc.update("INSERT INTO "+schema+".locations(code,name,is_active) VALUES ('M7-legacy','Legacy location',true)");
            jdbc.update("INSERT INTO "+schema+".transaction_handover_details(transaction_id,recipient_user_id,handover_date,destination_location_id) SELECT ?,?,current_date,min(location_id) FROM "+schema+".locations",tx,id);
            jdbc.update("INSERT INTO "+schema+".handover_snapshots(transaction_id,snapshot) VALUES (?,CAST(? AS jsonb))",tx,"{\"recipientUserId\":"+id+",\"recipientName\":\"Legacy holder\",\"lines\":[]}");
            jdbc.update("UPDATE "+schema+".transactions SET status='COMPLETED',processed_by=?,processed_at=now(),completed_at=now() WHERE transaction_id=?",id,tx);
            String snapshot=jdbc.queryForObject("SELECT snapshot::text FROM "+schema+".handover_snapshots WHERE transaction_id=?",String.class,tx);
            Long allocation=new org.springframework.transaction.support.TransactionTemplate(manager).execute(state->{
                jdbc.execute("SET LOCAL search_path TO "+schema);
                Long software=jdbc.queryForObject("INSERT INTO software_catalog(name,manufacturer,is_active) VALUES ('M7 legacy software','Fixture',true) RETURNING software_catalog_id",Long.class);
                Long type=jdbc.queryForObject("INSERT INTO asset_types(code,name,category_id,is_active) SELECT 'M7_LICENSE','Legacy license',category_id,true FROM asset_categories WHERE code='LICENSE' RETURNING type_id",Long.class);
                Long license=jdbc.queryForObject("INSERT INTO assets(asset_tag,name,type_id,status_id,created_by) SELECT 'M7-license','Legacy package',?,status_id,? FROM asset_statuses WHERE code='IN_STOCK' RETURNING asset_id",Long.class,type,id);
                jdbc.update("INSERT INTO asset_license_details(asset_id,software_catalog_id,license_assignment_type_id,license_term_type_id,seat_count) SELECT ?,?,(SELECT license_assignment_type_id FROM license_assignment_types WHERE code='PER_USER'),(SELECT license_term_type_id FROM license_term_types WHERE code='PERPETUAL'),2",license,software);
                return jdbc.queryForObject("INSERT INTO license_allocations(license_asset_id,user_id,seat_count,status,handover_transaction_id,created_by) VALUES (?,?,1,'ACTIVE',?,?) RETURNING allocation_id",Long.class,license,id,tx,id);
            });
            var ids=jdbc.queryForList("SELECT user_id FROM "+schema+".users ORDER BY user_id",Long.class);
            assertThat(latest.migrate().success).isTrue();
            assertThat(jdbc.queryForList("SELECT user_id FROM "+schema+".users ORDER BY user_id",Long.class)).isEqualTo(ids);
            assertThat(jdbc.queryForObject("SELECT assigned_to FROM "+schema+".assets WHERE asset_id=?",Long.class,asset)).isEqualTo(id);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM "+schema+".users WHERE employee_code IS NOT NULL OR username IS NOT NULL",Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT login_locked FROM "+schema+".users WHERE user_id=?",Boolean.class,id)).isTrue();
            assertThat(jdbc.queryForObject("SELECT login_locked FROM "+schema+".users WHERE user_id=?",Boolean.class,userId)).isFalse();
            assertThat(jdbc.queryForObject("SELECT snapshot::text FROM "+schema+".handover_snapshots WHERE transaction_id=?",String.class,tx)).isEqualTo(snapshot);
            assertThat(jdbc.queryForObject("SELECT recipient_user_id FROM "+schema+".transaction_handover_details WHERE transaction_id=?",Long.class,tx)).isEqualTo(id);
            assertThat(jdbc.queryForObject("SELECT user_id FROM "+schema+".license_allocations WHERE allocation_id=?",Long.class,allocation)).isEqualTo(id);
            assertThat(jdbc.queryForObject("SELECT handover_transaction_id FROM "+schema+".license_allocations WHERE allocation_id=?",Long.class,allocation)).isEqualTo(tx);
        } finally { latest.clean(); }
    }
}
