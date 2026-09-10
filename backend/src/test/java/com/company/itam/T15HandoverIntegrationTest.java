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
class T15HandoverIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired jakarta.persistence.EntityManager em;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired AssetService assets;
    @Autowired AssetRelationshipService relationships;
    @Autowired HandoverService handovers;
    @SpyBean HandoverSnapshotRepository snapshots;

    @BeforeEach void fixtures() {
        for(String category:List.of("DEVICE","COMPONENT","LICENSE")) jdbc.update("INSERT INTO asset_types(code,name,category_id,is_active) SELECT ?,?,category_id,true FROM asset_categories WHERE code=? ON CONFLICT(code) DO NOTHING","T15_"+category,"T15 "+category,category);
        jdbc.update("INSERT INTO locations(code,name,is_active) VALUES ('T15_TEST','T15 destination',true) ON CONFLICT(code) DO NOTHING");
    }
    Long id(String table,String column,String code) { return jdbc.queryForObject("SELECT "+column+" FROM "+table+" WHERE code=?",Long.class,code); }
    Long recipient() { return jdbc.queryForObject("SELECT user_id FROM users WHERE email='user01@itam.example'",Long.class); }
    Long create(String category) {
        login();
        var r=new CreateHardwareAssetRequest();r.setName("T15 "+UUID.randomUUID()); r.setTypeId(id("asset_types","type_id","T15_"+category));
        return assets.createHardwareAsset(r).getAssetId();
    }
    Long license(String type,int seats) {
        var r=new CreateHardwareAssetRequest(); r.setName("T15 license "+UUID.randomUUID());r.setTypeId(id("asset_types","type_id","T15_LICENSE"));
        Long software=jdbc.queryForObject("INSERT INTO software_catalog(name,manufacturer,is_active) VALUES (?,'Demo',true) RETURNING software_catalog_id",Long.class,"T15 "+UUID.randomUUID());
        r.setLicense(new LicenseDetailsRequest(software,id("license_assignment_types","license_assignment_type_id",type),id("license_term_types","license_term_type_id","PERPETUAL"),seats,"T15-SECRET-KEY",null));
        return assets.createHardwareAsset(r).getAssetId();
    }
    HandoverRequest request(List<Long> assets,List<HandoverRequest.LicenseLine> licenses) {
        return new HandoverRequest(recipient(),id("locations","location_id","T15_TEST"),LocalDate.of(2026,9,10),assets,licenses,"T15 demo",null);
    }
    HandoverRequest confirmed(HandoverRequest req) {
        return new HandoverRequest(req.recipientUserId(),req.destinationLocationId(),req.handoverDate(),req.assetIds(),req.licenses(),req.notes(),handovers.preview(req).fingerprint());
    }
    void login() { org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("admin@itam.example",null,List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ADMIN")))); }
    String state(Long id) { return jdbc.queryForObject("SELECT s.code FROM assets a JOIN asset_statuses s USING(status_id) WHERE asset_id=?",String.class,id); }

    @Test void completesBundleAndActivatesOnlyReservedOemSeats() throws Exception {
        Long a=create("DEVICE"),b=create("DEVICE"),ram=create("COMPONENT"),pkg=license("OEM",2);
        relationships.create(a,ram,RelationshipType.COMPONENT_OF); relationships.create(a,pkg,RelationshipType.INSTALLED_ON);relationships.create(b,pkg,RelationshipType.INSTALLED_ON);
        var req=confirmed(request(List.of(a,b),List.of()));
        String body=mvc.perform(post("/v1/handovers").contentType("application/json").content(json.writeValueAsString(req)).header("Accept-Language","en"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.message").value("Handover completed.")).andExpect(jsonPath("$.data.lines.length()").value(4)).andReturn().getResponse().getContentAsString();
        Long tx=json.readTree(body).path("data").path("transactionId").asLong();
        for(Long asset:List.of(a,b,ram)) { assertThat(state(asset)).isEqualTo("IN_USE"); assertThat(jdbc.queryForObject("SELECT assigned_to FROM assets WHERE asset_id=?",Long.class,asset)).isEqualTo(recipient()); }
        assertThat(state(pkg)).isEqualTo("IN_STOCK");
        assertThat(jdbc.queryForObject("SELECT sum(seat_count) FROM license_allocations WHERE license_asset_id=? AND status='ACTIVE'",Integer.class,pkg)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM asset_relationships WHERE parent_asset_id IN (?,?)",Integer.class,a,b)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action='HANDOVER' AND new_data->>'transactionId'=?",Integer.class,tx.toString())).isEqualTo(4);
        mvc.perform(get("/v1/handovers/"+tx)).andExpect(status().isOk()).andExpect(jsonPath("$.data.recipientUserId").value(recipient()));
        assertThat(body).doesNotContain("T15-SECRET-KEY");
    }
    @Test void allocatesPerUserAndLinksOneSeatToNewlyAssignedDevice() {
        Long device=create("DEVICE"),pkg=license("PER_USER",10);
        var result=handovers.complete(confirmed(request(List.of(device),List.of(new HandoverRequest.LicenseLine(pkg,1,device)))));
        assertThat(result.lines().stream().filter(l->l.assetId().equals(pkg)).findFirst().orElseThrow().allocations().get(0).allocationId()).isNotNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM asset_relationships WHERE parent_asset_id=? AND child_asset_id=?",Integer.class,device,pkg)).isEqualTo(1);
        assertThat(assets.getAssetById(pkg).getLicense().availableSeats()).isEqualTo(9);
    }
    @Test void allocatesMultipleSeatsWithoutChangingWholePackage() {
        Long pkg=license("PER_USER",10);
        var r=handovers.complete(confirmed(request(List.of(),List.of(new HandoverRequest.LicenseLine(pkg,3,null)))));
        assertThat(r.lines().get(0).seats()).isEqualTo(3); assertThat(assets.getAssetById(pkg).getLicense().availableSeats()).isEqualTo(7); assertThat(state(pkg)).isEqualTo("IN_STOCK");
        assertThat(r.lines().get(0).details().get("availableSeatsBefore")).isEqualTo(10L);
        assertThat(r.lines().get(0).details().get("availableSeatsAfter")).isEqualTo(7L);
        assertThat(r.lines().get(0).allocations()).hasSize(3).allMatch(a->a.seats()==1);
        assertThat(r.lines().get(0).allocations().stream().map(a->a.allocationId()).distinct()).hasSize(3);
    }
    @Test void perUserLinkKeepsDeviceIdentityInSnapshot() {
        Long device=create("DEVICE"),pkg=license("PER_USER",10);
        handovers.complete(confirmed(request(List.of(device),List.of())));
        var result=handovers.complete(confirmed(request(List.of(),List.of(new HandoverRequest.LicenseLine(pkg,1,device)))));
        String tag=assets.getAssetById(device).getAssetTag();
        jdbc.update("UPDATE assets SET name='Renamed later' WHERE asset_id=?",device);
        var allocation=handovers.get(result.transactionId()).lines().get(0).allocations().get(0);
        assertThat(allocation.deviceTag()).isEqualTo(tag);assertThat(allocation.deviceName()).isNotEqualTo("Renamed later");
    }
    @ParameterizedTest @ValueSource(strings={"IN_USE","PENDING_IMPORT","IN_REPAIR","DAMAGED","RETIRED"})
    void blocksNonStock(String state) throws Exception {
        Long a=create("DEVICE");jdbc.update("UPDATE assets SET status_id=(SELECT status_id FROM asset_statuses WHERE code=?),assigned_to=? WHERE asset_id=?",state,state.equals("IN_USE")?recipient():null,a); em.clear();
        mvc.perform(post("/v1/handovers/preview").contentType("application/json").content(json.writeValueAsString(request(List.of(a),List.of())))).andExpect(status().isConflict());
    }
    @Test void rejectsEmptyDuplicateAndMissingConfirmation() throws Exception {
        mvc.perform(post("/v1/handovers/preview").contentType("application/json").content(json.writeValueAsString(request(List.of(),List.of())))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMPTY_TRANSACTION"));
        Long a=create("DEVICE");
        mvc.perform(post("/v1/handovers/preview").contentType("application/json").content(json.writeValueAsString(request(List.of(a,a),List.of())))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("HANDOVER_DUPLICATE"));
        mvc.perform(post("/v1/handovers").contentType("application/json").content(json.writeValueAsString(request(List.of(a),List.of())))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("HANDOVER_CHANGED"));
    }
    @Test void blocksStandaloneAttachedComponentAndOem() throws Exception {
        Long a=create("DEVICE"),ram=create("COMPONENT"),pkg=license("OEM",1);relationships.create(a,ram,RelationshipType.COMPONENT_OF);relationships.create(a,pkg,RelationshipType.INSTALLED_ON);
        for(Long child:List.of(ram,pkg)) mvc.perform(post("/v1/handovers/preview").contentType("application/json").content(json.writeValueAsString(request(List.of(child),List.of())))).andExpect(status().isConflict());
    }
    @Test void rejectsChangedBundleAfterPreview() throws Exception {
        Long a=create("DEVICE"),ram=create("COMPONENT");var req=confirmed(request(List.of(a),List.of()));relationships.create(a,ram,RelationshipType.COMPONENT_OF);
        mvc.perform(post("/v1/handovers").contentType("application/json").content(json.writeValueAsString(req))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("HANDOVER_CHANGED"));
        assertThat(state(a)).isEqualTo("IN_STOCK");
    }
    @Test void rejectsInactiveRecipientAndInvalidLocation() throws Exception {
        Long a=create("DEVICE"); var req=request(List.of(a),List.of());
        jdbc.update("UPDATE users SET account_status='LOCKED' WHERE user_id=?",recipient());
        mvc.perform(post("/v1/handovers/preview").contentType("application/json").content(json.writeValueAsString(req))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("HANDOVER_RECIPIENT_INACTIVE"));
    }
    @Test void blocksMissingTagsOnParentComponentAndLicense() throws Exception {
        Long a=create("DEVICE"),ram=create("COMPONENT"),pkg=license("OEM",1);relationships.create(a,ram,RelationshipType.COMPONENT_OF);relationships.create(a,pkg,RelationshipType.INSTALLED_ON);
        for(Long id:List.of(a,ram,pkg)) {
            String old=jdbc.queryForObject("SELECT asset_tag FROM assets WHERE asset_id=?",String.class,id);jdbc.update("UPDATE assets SET asset_tag=' ' WHERE asset_id=?",id); em.clear();
            mvc.perform(post("/v1/handovers/preview").contentType("application/json").content(json.writeValueAsString(request(List.of(a),List.of())))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("HANDOVER_TAG_REQUIRED"));
            jdbc.update("UPDATE assets SET asset_tag=? WHERE asset_id=?",old,id);
        }
    }
    @Test void rejectsCapacityAndMultiSeatDeviceLink() throws Exception {
        Long pkg=license("PER_USER",1),device=create("DEVICE");
        mvc.perform(post("/v1/handovers/preview").contentType("application/json").content(json.writeValueAsString(request(List.of(),List.of(new HandoverRequest.LicenseLine(pkg,2,null)))))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LICENSE_CAPACITY_EXCEEDED"));
        mvc.perform(post("/v1/handovers/preview").contentType("application/json").content(json.writeValueAsString(request(List.of(),List.of(new HandoverRequest.LicenseLine(pkg,1,device)))))).andExpect(status().isConflict());
    }
    @ParameterizedTest @ValueSource(strings={"PUR_STAFF","USER"})
    void forbidsUnauthorizedReadsAndWrites(String role) throws Exception {
        var who=user("user01@itam.example").authorities(()->role);
        for(String endpoint:List.of("/v1/handovers/candidates","/v1/handovers/1")) mvc.perform(get(endpoint).with(who)).andExpect(status().isForbidden());
        for(String endpoint:List.of("/v1/handovers","/v1/handovers/preview")) mvc.perform(post(endpoint).with(who).contentType("application/json").content(json.writeValueAsString(request(List.of(),List.of())))).andExpect(status().isForbidden());
    }
    @Test void candidateApiFiltersStockAndAttachedComponents() throws Exception {
        Long a=create("DEVICE"),ram=create("COMPONENT"),oem=license("OEM",1);relationships.create(a,ram,RelationshipType.COMPONENT_OF);
        mvc.perform(get("/v1/handovers/candidates").param("keyword",assets.getAssetById(a).getAssetTag())).andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1));
        for(Long child:List.of(ram,oem)) mvc.perform(get("/v1/handovers/candidates").param("keyword",jdbc.queryForObject("SELECT asset_tag FROM assets WHERE asset_id=?",String.class,child))).andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
    }
    @Test void snapshotKeepsOriginalNamesConfigurationAndRecipient() throws Exception {
        Long a=create("DEVICE");var result=handovers.complete(confirmed(request(List.of(a),List.of())));
        jdbc.update("UPDATE assets SET name='Changed later' WHERE asset_id=?",a);
        jdbc.update("UPDATE users SET full_name='Changed later' WHERE user_id=?",recipient());
        var read=handovers.get(result.transactionId());assertThat(read.recipientName()).isEqualTo(result.recipientName());assertThat(read.lines().get(0).name()).isEqualTo(result.lines().get(0).name());
    }
    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void completedSnapshotCannotBeUpdatedOrDeleted() {
        login(); Long a=create("DEVICE"); var result=handovers.complete(confirmed(request(List.of(a),List.of())));
        assertThatThrownBy(()->jdbc.update("UPDATE handover_snapshots SET snapshot='{}'::jsonb WHERE transaction_id=?",result.transactionId())).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("DELETE FROM handover_snapshots WHERE transaction_id=?",result.transactionId())).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(handovers.get(result.transactionId()).transactionCode()).isEqualTo(result.transactionCode());
    }
    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void failureAfterUpdatesRollsBackAssetsAllocationsAuditAndTransaction() {
        login();Long a=create("DEVICE"),pkg=license("PER_USER",10);var req=confirmed(request(List.of(a),List.of(new HandoverRequest.LicenseLine(pkg,2,null))));
        Long txCount=jdbc.queryForObject("SELECT count(*) FROM transactions",Long.class);
        doThrow(new IllegalStateException("Simulated snapshot failure")).when(snapshots).save(any());
        try { assertThatThrownBy(()->handovers.complete(req)).hasMessageContaining("Simulated snapshot failure"); }
        finally { reset(snapshots); }
        assertThat(state(a)).isEqualTo("IN_STOCK");assertThat(assets.getAssetById(pkg).getLicense().availableSeats()).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transactions",Long.class)).isEqualTo(txCount);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE entity_id=? AND action='HANDOVER'",Integer.class,a)).isZero();
    }
    @ParameterizedTest @ValueSource(booleans={false,true}) @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void competingHandoversHaveExactlyOneWinner(boolean seat) throws Exception {
        login();Long a=seat?license("PER_USER",1):create("DEVICE");
        var req=confirmed(seat?request(List.of(),List.of(new HandoverRequest.LicenseLine(a,1,null))):request(List.of(a),List.of()));
        var gate=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        Callable<Boolean> work=()->{login();gate.await();try {handovers.complete(req);return true;}catch(AppException e){return false;}finally{org.springframework.security.core.context.SecurityContextHolder.clearContext();}};
        try { var first=pool.submit(work);var second=pool.submit(work);gate.countDown();assertThat(List.of(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false); }
        finally {pool.shutdownNow();}
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transaction_assets ta JOIN transactions t USING(transaction_id) WHERE ta.asset_id=? AND t.type='HANDOVER'",Integer.class,a)).isEqualTo(1);
    }
}
