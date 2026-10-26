package com.company.itam;

import com.company.itam.asset.dto.request.*;
import com.company.itam.asset.service.AssetService;
import com.company.itam.asset.relationship.AssetRelationshipService;
import com.company.itam.asset.relationship.enums.RelationshipType;
import com.company.itam.common.exception.AppException;
import com.company.itam.workflow.handover.dto.*;
import com.company.itam.workflow.handover.repository.HandoverSnapshotRepository;
import com.company.itam.workflow.handover.service.HandoverService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
@WithMockUser(username="admin@itam.example",authorities="ADMIN")
class M3HandoverLifecycleIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired jakarta.persistence.EntityManager em;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired AssetService assets;
    @Autowired AssetRelationshipService relationships;
    @Autowired HandoverService handovers;
    @SpyBean HandoverSnapshotRepository snapshots;

    @BeforeEach void fixtures() {
        jdbc.update("UPDATE users SET employee_code='TEST-' || user_id WHERE email='user01@itam.example' AND employee_code IS NULL");
        for(String category:List.of("DEVICE","COMPONENT","LICENSE")) jdbc.update("INSERT INTO asset_types(code,name,category_id,is_active) SELECT ?,?,category_id,true FROM asset_categories WHERE code=? ON CONFLICT(code) DO NOTHING","M3_"+category,"M3 "+category,category);
        jdbc.update("INSERT INTO locations(code,name,is_active) VALUES ('M3_TEST','M3 destination',true) ON CONFLICT(code) DO NOTHING");
    }
    Long id(String table,String column,String code) { return jdbc.queryForObject("SELECT "+column+" FROM "+table+" WHERE code=?",Long.class,code); }
    Long recipient() { return jdbc.queryForObject("SELECT user_id FROM users WHERE email='user01@itam.example'",Long.class); }
    Long create(String category) {
        login();
        var r=new CreateHardwareAssetRequest();r.setCreationPurpose(AssetCreationPurpose.BASELINE);r.setName("M3 "+UUID.randomUUID()); r.setTypeId(id("asset_types","type_id","M3_"+category));
        return assets.createHardwareAsset(r).getAssetId();
    }
    Long license(String type,int seats) {
        var r=new CreateHardwareAssetRequest(); r.setCreationPurpose(AssetCreationPurpose.BASELINE);r.setName("M3 license "+UUID.randomUUID());r.setTypeId(id("asset_types","type_id","M3_LICENSE"));
        Long software=jdbc.queryForObject("INSERT INTO software_catalog(name,manufacturer,is_active) VALUES (?,'Demo',true) RETURNING software_catalog_id",Long.class,"M3 "+UUID.randomUUID());
        r.setLicense(new LicenseDetailsRequest(software,id("license_assignment_types","license_assignment_type_id",type),id("license_term_types","license_term_type_id","PERPETUAL"),seats,"M3-SECRET-KEY",null));
        return assets.createHardwareAsset(r).getAssetId();
    }
    HandoverRequest request(List<Long> assets,List<HandoverRequest.LicenseLine> licenses) {
        return new HandoverRequest(recipient(),id("locations","location_id","M3_TEST"),LocalDate.of(2026,9,10),assets,licenses,"M3 demo",null);
    }
    HandoverRequest confirmed(HandoverRequest req) {
        return new HandoverRequest(req.recipientUserId(),req.destinationLocationId(),req.handoverDate(),req.assetIds(),req.licenses(),req.notes(),handovers.preview(req).fingerprint());
    }
    void login() { org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("admin@itam.example",null,List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ADMIN")))); }
    String state(Long id) { return jdbc.queryForObject("SELECT s.code FROM assets a JOIN asset_statuses s USING(status_id) WHERE asset_id=?",String.class,id); }


    @Autowired com.company.itam.workflow.handover.service.HandoverHolderService holders;
    @Autowired com.company.itam.workflow.recovery.service.RecoveryService recoveries;
    @Autowired com.company.itam.workflow.disposal.service.DisposalService disposals;
    @Test void pendingReservesBundleAndSeatsWithoutAssigningAndWithdrawReleases() throws Exception {
        Long device=create("DEVICE"),component=create("COMPONENT"),oem=license("OEM",1),perUser=license("PER_USER",2);
        var componentLink=relationships.create(device,component,RelationshipType.COMPONENT_OF);
        relationships.create(device,oem,RelationshipType.INSTALLED_ON);
        var req=request(List.of(device),List.of(new HandoverRequest.LicenseLine(perUser,2,null)));
        var draft=handovers.create(confirmed(req));assertThat(draft.status()).isEqualTo("DRAFT");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM handover_asset_reservations WHERE transaction_id=?",Integer.class,draft.transactionId())).isZero();
        var pending=handovers.submit(draft.transactionId());assertThat(pending.status()).isEqualTo("PENDING");
        handovers.submit(draft.transactionId());
        assertThat(assets.getAssetById(device).isPendingHandover()).isTrue();
        var criteria=new AssetSearchCriteria();criteria.setAssetTag(assets.getAssetById(device).getAssetTag());
        assertThat(assets.getAssets(criteria,org.springframework.data.domain.PageRequest.of(0,20)).getContent()).allMatch(a->a.isPendingHandover());
        assertThat(handovers.candidates(assets.getAssetById(device).getAssetTag(),0,20).getContent()).isEmpty();
        assertThatThrownBy(()->relationships.remove(componentLink.relationshipId())).isInstanceOf(AppException.class);
        for(var id:List.of(device,component)) {assertThat(state(id)).isEqualTo("IN_STOCK");assertThat(jdbc.queryForObject("SELECT assigned_to FROM assets WHERE asset_id=?",Long.class,id)).isNull();}
        assertThat(assets.getAssetById(perUser).getLicense().availableSeats()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM license_allocations WHERE handover_transaction_id=? AND status='ACTIVE'",Integer.class,draft.transactionId())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM handover_snapshots WHERE transaction_id=?",Integer.class,draft.transactionId())).isZero();
        assertThatThrownBy(()->handovers.preview(req)).isInstanceOf(AppException.class);
        assertThatThrownBy(()->assets.updateHardwareAsset(device,new UpdateHardwareAssetRequest())).isInstanceOf(AppException.class);
        assertThatThrownBy(()->relationships.create(device,create("COMPONENT"),RelationshipType.COMPONENT_OF)).isInstanceOf(AppException.class);
        assertThatThrownBy(()->handovers.update(draft.transactionId(),req)).isInstanceOf(AppException.class);
        handovers.change(draft.transactionId(),"withdraw");
        assertThat(assets.getAssetById(perUser).getLicense().availableSeats()).isEqualTo(2);
        assertThat(assets.getAssetById(device).isPendingHandover()).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM license_allocations WHERE license_asset_id=? AND status='RESERVED'",Integer.class,oem)).isEqualTo(1);
        assertThat(handovers.preview(req).lines()).hasSize(4);
        handovers.submit(draft.transactionId());var completed=handovers.confirm(draft.transactionId());
        assertThat(completed.status()).isEqualTo("COMPLETED");assertThat(state(device)).isEqualTo("IN_USE");
        assertThat(handovers.confirm(draft.transactionId()).transactionId()).isEqualTo(draft.transactionId());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM license_allocations WHERE handover_transaction_id=? AND status='ACTIVE'",Integer.class,draft.transactionId())).isEqualTo(3);
        assertThatThrownBy(()->handovers.change(draft.transactionId(),"withdraw")).isInstanceOf(AppException.class);
        assertThatThrownBy(()->handovers.update(draft.transactionId(),req)).isInstanceOf(AppException.class);
    }
    @Test void httpRightsAndAdminOverrideAreEnforced() throws Exception {
        Long a=create("DEVICE");var req=confirmed(request(List.of(a),List.of()));
        String actualIt=jdbc.queryForObject("SELECT email FROM users u JOIN roles r USING(role_id) WHERE r.code='IT_STAFF' ORDER BY user_id LIMIT 1",String.class);
        var body=mvc.perform(post("/v1/handovers").with(user(actualIt).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("IT_STAFF")))
            .contentType("application/json").content(json.writeValueAsString(req))).andExpect(status().isCreated()).andExpect(jsonPath("$.data.status").value("DRAFT")).andReturn().getResponse().getContentAsString();
        long id=json.readTree(body).path("data").path("transactionId").asLong();
        mvc.perform(put("/v1/handovers/"+id).contentType("application/json").content(json.writeValueAsString(req))).andExpect(status().isBadRequest());
        mvc.perform(put("/v1/handovers/"+id).header("X-Override-Reason","Correction").contentType("application/json").content(json.writeValueAsString(req))).andExpect(status().isOk());
        mvc.perform(post("/v1/handovers/"+id+"/submit").with(user(actualIt).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("IT_STAFF")))).andExpect(status().isOk());
        mvc.perform(post("/v1/handovers/"+id+"/cancel").with(user(actualIt).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("IT_STAFF")))).andExpect(status().isForbidden());
        mvc.perform(post("/v1/handovers/"+id+"/cancel")).andExpect(status().isBadRequest());
        mvc.perform(post("/v1/handovers/"+id+"/cancel").header("X-Override-Reason","Cancelled exception")).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CANCELLED"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE entity_id=? AND action='ADMIN_OVERRIDE'",Long.class,id)).isEqualTo(2);
        for(String role:List.of("PUR_STAFF","USER")) {
            mvc.perform(get("/v1/handovers/holders").with(user("nobody").authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority(role)))).andExpect(status().isForbidden());
            mvc.perform(get("/v1/handovers/"+id).with(user("nobody").authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority(role)))).andExpect(status().isForbidden());
        }
    }
    @Test void holdersCountAllAssetsAndActiveSeatsEvenForLockedProfiles() {
        Long device=create("DEVICE"),component=create("COMPONENT"),pkg=license("PER_USER",5);
        var req=request(List.of(device,component),List.of(new HandoverRequest.LicenseLine(pkg,2,null)));
        var tx=handovers.create(confirmed(req));handovers.submit(tx.transactionId());
        var before=holders.detail(recipient(),0,1).holder();
        handovers.confirm(tx.transactionId());em.flush();
        jdbc.update("UPDATE users SET account_status='LOCKED' WHERE user_id=?",recipient());em.clear();
        var detail=holders.detail(recipient(),0,1);
        assertThat(detail.holder().deviceCount()).isEqualTo(before.deviceCount()+1);
        assertThat(detail.holder().componentCount()).isEqualTo(before.componentCount()+1);
        assertThat(detail.holder().licenseSeats()).isEqualTo(before.licenseSeats()+2);
        assertThat(detail.assets().getContent()).hasSize(1);
        assertThat(detail.assets().getTotalElements()).isGreaterThanOrEqualTo(4);
        assertThat(holders.list("user01@itam.example",true,0,1).getTotalElements()).isEqualTo(1);
    }
    @ParameterizedTest @ValueSource(booleans={false,true}) @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void concurrentPendingSubmissionsReserveOnlyOneLastUnit(boolean seat) throws Exception {
        login();Long a=seat?license("PER_USER",1):create("DEVICE");
        var req=seat?request(List.of(),List.of(new HandoverRequest.LicenseLine(a,1,null))):request(List.of(a),List.of());
        var first=handovers.create(confirmed(req));var second=handovers.create(confirmed(req));
        var gate=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {
            var jobs=new ArrayList<Future<Boolean>>();
            for(var id:List.of(first.transactionId(),second.transactionId())) jobs.add(pool.submit(()->{login();gate.await();try {handovers.submit(id);return true;}catch(AppException e){return false;}finally{org.springframework.security.core.context.SecurityContextHolder.clearContext();}}));
            gate.countDown();assertThat(List.of(jobs.get(0).get(30,TimeUnit.SECONDS),jobs.get(1).get(30,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
            login();long winner=jdbc.queryForObject("SELECT transaction_id FROM transactions WHERE transaction_id IN (?,?) AND status='PENDING'",Long.class,first.transactionId(),second.transactionId());
            handovers.change(winner,"withdraw");
        } finally {pool.shutdownNow();}
    }
    @Test void pendingPerUserDeviceBlocksRecoveryUntilWithdraw() {
        Long device=create("DEVICE"),pkg=license("PER_USER",2);
        var physical=handovers.create(confirmed(request(List.of(device),List.of())));handovers.submit(physical.transactionId());handovers.confirm(physical.transactionId());
        var check=new com.company.itam.workflow.recovery.dto.SmartCheckRequest(List.of(device),recipient(),List.of(),"Return device");
        assertThat(recoveries.smartCheck(check).blockedAssets()).isEmpty();
        var pending=handovers.create(confirmed(request(List.of(),List.of(new HandoverRequest.LicenseLine(pkg,1,device)))));
        handovers.submit(pending.transactionId());
        assertThat(recoveries.smartCheck(check).blockedAssets()).extracting("assetId").contains(device);
        handovers.change(pending.transactionId(),"withdraw");
        assertThat(recoveries.smartCheck(check).blockedAssets()).isEmpty();
    }
    @Test void disposalAndHandoverCannotReserveTheSameDevice() {
        Long device=create("DEVICE");
        jdbc.update("UPDATE assets SET purchase_date='2010-01-01' WHERE asset_id=?",device);em.clear();
        var req=confirmed(request(List.of(device),List.of()));var tx=handovers.create(req);handovers.submit(tx.transactionId());
        var check=new com.company.itam.workflow.disposal.dto.DisposalModels.SmartCheckRequest(List.of(device));
        assertThatThrownBy(()->disposals.smartCheck(check)).isInstanceOf(AppException.class);
        handovers.change(tx.transactionId(),"withdraw");
        var preview=disposals.smartCheck(check);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("it01@itam.example",null,List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("IT_STAFF"))));
        disposals.create(new com.company.itam.workflow.disposal.dto.DisposalModels.CreateRequest(List.of(device),"Old device",LocalDate.now(),preview.fingerprint(),null));
        login();
        assertThatThrownBy(()->handovers.submit(tx.transactionId())).isInstanceOf(AppException.class);
    }
    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void simultaneousConfirmPublishesAndAllocatesExactlyOnce() throws Exception {
        login();Long pkg=license("PER_USER",1);
        var tx=handovers.create(confirmed(request(List.of(),List.of(new HandoverRequest.LicenseLine(pkg,1,null)))));handovers.submit(tx.transactionId());
        var gate=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        Callable<Long> work=()->{login();gate.await();try{return handovers.confirm(tx.transactionId()).transactionId();}finally{org.springframework.security.core.context.SecurityContextHolder.clearContext();}};
        try {var a=pool.submit(work);var b=pool.submit(work);gate.countDown();assertThat(a.get(40,TimeUnit.SECONDS)).isEqualTo(tx.transactionId());assertThat(b.get(40,TimeUnit.SECONDS)).isEqualTo(tx.transactionId());}
        finally {pool.shutdownNow();}
        assertThat(jdbc.queryForObject("SELECT count(*) FROM license_allocations WHERE handover_transaction_id=? AND status='ACTIVE'",Long.class,tx.transactionId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM documents WHERE transaction_id=?",Long.class,tx.transactionId())).isEqualTo(1);
    }

}
