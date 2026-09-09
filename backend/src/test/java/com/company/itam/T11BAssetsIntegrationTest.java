package com.company.itam;

import com.company.itam.asset.dto.request.*;
import com.company.itam.asset.service.*;
import com.company.itam.asset.relationship.AssetRelationshipService;
import com.company.itam.asset.relationship.enums.RelationshipType;
import com.company.itam.user.repository.UserRepository;
import com.company.itam.common.exception.AppException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
@WithMockUser(username="admin@itam.example",authorities="ADMIN")
class T11BAssetsIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired AssetService assets;
    @Autowired AssetRelationshipService relationships;
    @Autowired LicenseAllocationService allocations;
    @Autowired UserRepository users;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    @BeforeEach void catalogFixtures() {
        for(String category:List.of("DEVICE","COMPONENT","LICENSE")) jdbc.update("INSERT INTO asset_types(code,name,category_id,is_active) SELECT ?,?,category_id,true FROM asset_categories WHERE code=? ON CONFLICT(code) DO NOTHING","T11B_"+category,"T11B "+category,category);
        jdbc.update("INSERT INTO locations(code,name,is_active) VALUES ('T11B_TEST','T11B test location',true) ON CONFLICT(code) DO NOTHING");
    }
    void login() { org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("admin@itam.example",null,List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ADMIN")))); }
    Long type(String category) { return jdbc.queryForObject("SELECT type_id FROM asset_types t JOIN asset_categories c USING(category_id) WHERE c.code=? ORDER BY type_id LIMIT 1",Long.class,category); }
    Long lookup(String table,String column,String code) { return jdbc.queryForObject("SELECT "+column+" FROM "+table+" WHERE code=?",Long.class,code); }
    CreateHardwareAssetRequest request(String category) {
        var r=new CreateHardwareAssetRequest();r.setName("T11B test "+UUID.randomUUID());r.setTypeId(type(category));return r;
    }
    LicenseDetailsRequest license(String assignment,int seats) {
        Long software=jdbc.queryForObject("INSERT INTO software_catalog(name,manufacturer,is_active) VALUES (?, 'Demo',true) RETURNING software_catalog_id",Long.class,"T11B "+UUID.randomUUID());
        return new LicenseDetailsRequest(software,lookup("license_assignment_types","license_assignment_type_id",assignment),lookup("license_term_types","license_term_type_id","PERPETUAL"),seats,"DEMO-PRIVATE-KEY",null);
    }
    Long createLicense(String assignment,int seats) { var r=request("LICENSE");r.setLicense(license(assignment,seats));return assets.createHardwareAsset(r).getAssetId(); }
    Long transaction(String kind,Long licenseId) {
        var actor=users.findByEmail("admin@itam.example").orElseThrow();var recipient=users.findByEmail("user01@itam.example").orElseThrow();
        Long tx=jdbc.queryForObject("INSERT INTO transactions(transaction_code,type,status,requester_id,processed_by,processed_at,completed_at) VALUES (?,?,'COMPLETED',?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP) RETURNING transaction_id",Long.class,"T11B-"+UUID.randomUUID(),kind,actor.getUserId(),actor.getUserId());
        Long location=jdbc.queryForObject("SELECT location_id FROM locations ORDER BY location_id LIMIT 1",Long.class);
        if (kind.equals("HANDOVER")) jdbc.update("INSERT INTO transaction_handover_details VALUES (?,?,CURRENT_DATE,?)",tx,recipient.getUserId(),location);
        else jdbc.update("INSERT INTO transaction_recovery_details VALUES (?,?,CURRENT_DATE,?,'Demo recovery')",tx,recipient.getUserId(),location);
        jdbc.update("INSERT INTO transaction_assets(transaction_id,asset_id,line_number) VALUES (?,?,1)",tx,licenseId);
        return tx;
    }
    @Test void createReadUpdateAllTypesAndRejectTypeConversion() throws Exception {
        for(String category:List.of("DEVICE","COMPONENT","LICENSE")) {
            var r=request(category);if(category.equals("LICENSE"))r.setLicense(license("OEM",10));
            String body=mvc.perform(post("/v1/assets").contentType("application/json").content(json.writeValueAsString(r)))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.data.categoryCode").value(category)).andReturn().getResponse().getContentAsString();
            Long id=json.readTree(body).path("data").path("assetId").asLong();
            var update=json.convertValue(r,UpdateHardwareAssetRequest.class);update.setName("Updated "+category);
            mvc.perform(put("/v1/assets/"+id).contentType("application/json").content(json.writeValueAsString(update))).andExpect(status().isOk());
            update.setTypeId(type(category.equals("DEVICE")?"COMPONENT":"DEVICE"));
            mvc.perform(put("/v1/assets/"+id).contentType("application/json").content(json.writeValueAsString(update))).andExpect(status().isConflict());
            mvc.perform(get("/v1/assets/"+id+"/history")).andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(2));
            assertThat(jdbc.queryForObject("SELECT string_agg(COALESCE(new_data::text,'')||COALESCE(old_data::text,''),'') FROM audit_logs WHERE entity_type='ASSET' AND entity_id=?",String.class,id)).doesNotContain("DEMO-PRIVATE-KEY");
        }
    }
    @Test void oemReservesSeatAndCannotBeDetachedOrOverbooked() throws Exception {
        Long pkg=createLicense("OEM",1);Long device=assets.createHardwareAsset(request("DEVICE")).getAssetId();
        var link=relationships.create(device,pkg,RelationshipType.INSTALLED_ON);
        assertThat(assets.getAssetById(pkg).getLicense().availableSeats()).isZero();
        assertThat(link.removable()).isFalse();
        mvc.perform(delete("/v1/asset-relationships/"+link.relationshipId()).header("Accept-Language","en"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("OEM_CANNOT_DETACH"))
                .andExpect(jsonPath("$.message").value("OEM cannot be detached independently from its parent device."));
        login();
        Long another=assets.createHardwareAsset(request("DEVICE")).getAssetId();
        assertThatThrownBy(()->relationships.create(another,pkg,RelationshipType.INSTALLED_ON)).isInstanceOf(AppException.class);
        mvc.perform(get("/v1/assets/"+pkg+"/allocations")).andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].status").value("RESERVED"));
    }
    @Test void componentRelationshipValidatesTypesDuplicatesAndStock() throws Exception {
        Long device=assets.createHardwareAsset(request("DEVICE")).getAssetId();Long ram=assets.createHardwareAsset(request("COMPONENT")).getAssetId();
        var link=relationships.create(device,ram,RelationshipType.COMPONENT_OF);
        mvc.perform(post("/v1/assets/"+device+"/relationships").contentType("application/json").content(json.writeValueAsString(Map.of("childAssetId",ram,"type","COMPONENT_OF"))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RELATIONSHIP_DUPLICATE"));
        mvc.perform(post("/v1/assets/"+ram+"/relationships").contentType("application/json").content(json.writeValueAsString(Map.of("childAssetId",device,"type","COMPONENT_OF"))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RELATIONSHIP_INVALID"));
        mvc.perform(post("/v1/assets/"+device+"/relationships").contentType("application/json").content(json.writeValueAsString(Map.of("childAssetId",device,"type","COMPONENT_OF"))))
                .andExpect(status().isConflict());
        login();
        relationships.remove(link.relationshipId());
        assertThat(relationships.list(device,org.springframework.data.domain.PageRequest.of(0,20))).isEmpty();
    }
    @Test void tenSeatsAllocateThreeReleaseOneKeepOtherAllocationsAndPersonalScope() throws Exception {
        Long pkg=createLicense("PER_USER",10);Long handover=transaction("HANDOVER",pkg);
        var recipient=users.findByEmail("user01@itam.example").orElseThrow();var actor=users.findByEmail("admin@itam.example").orElseThrow();
        List<Long> ids=new ArrayList<>();for(int i=0;i<3;i++)ids.add(allocations.allocatePerUser(pkg,recipient,1,handover,actor).getAllocationId());
        assertThat(assets.getAssetById(pkg).getLicense().availableSeats()).isEqualTo(7);
        mvc.perform(get("/v1/users/me/assets").with(user("user01@itam.example").authorities(()->"USER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content[?(@.assetId == "+pkg+")]").isNotEmpty());
        mvc.perform(get("/v1/assets/"+pkg).with(user("user01@itam.example").authorities(()->"USER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.license.licenseKey").isEmpty());
        login();
        allocations.releasePerUser(ids.get(0),transaction("RECOVERY",pkg),actor);
        assertThat(assets.getAssetById(pkg).getLicense().allocatedSeats()).isEqualTo(2);
        assertThat(assets.getAssetById(pkg).getLicense().availableSeats()).isEqualTo(8);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM license_allocations WHERE license_asset_id=? AND status='ACTIVE'",Integer.class,pkg)).isEqualTo(2);
    }
    @Test @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void invalidLicenseDetailsRollbackAssetAndAreBilingual() throws Exception {
        var r=request("LICENSE");var l=license("PER_USER",1);
        r.setLicense(new LicenseDetailsRequest(l.softwareCatalogId(),l.assignmentTypeId(),lookup("license_term_types","license_term_type_id","SUBSCRIPTION"),1,"key",null));
        for(String language:List.of("vi","en")) mvc.perform(post("/v1/assets").header("Accept-Language",language).contentType("application/json").content(json.writeValueAsString(r)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LICENSE_EXPIRY_REQUIRED"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM assets WHERE name=?",Integer.class,r.getName())).isZero();
        jdbc.update("DELETE FROM software_catalog WHERE software_catalog_id=?",l.softwareCatalogId());
    }
    @Test void purAndUserCannotReadOrWriteRelationshipsAllocationsOrHistory() throws Exception {
        for(String role:List.of("PUR_STAFF","USER")) {
            for(String suffix:List.of("relationships","allocations","history")) mvc.perform(get("/v1/assets/1/"+suffix).with(user("outsider").authorities(()->role))).andExpect(status().isForbidden());
            mvc.perform(post("/v1/assets/1/relationships").with(user("outsider").authorities(()->role)).contentType("application/json").content("{\"childAssetId\":2,\"type\":\"COMPONENT_OF\"}")).andExpect(status().isForbidden());
            mvc.perform(delete("/v1/asset-relationships/1").with(user("outsider").authorities(()->role))).andExpect(status().isForbidden());
        }
    }
    @Test @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void concurrentLastSeatOnlyOneReservationCommits() throws Exception {
        Long pkg=createLicense("OEM",1);
        Long first=assets.createHardwareAsset(request("DEVICE")).getAssetId();
        Long second=assets.createHardwareAsset(request("DEVICE")).getAssetId();
        Long software=assets.getAssetById(pkg).getLicense().softwareCatalogId();
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var start=new java.util.concurrent.CountDownLatch(1);
            List<java.util.concurrent.Future<Boolean>> jobs=new ArrayList<>();
            for(Long device:List.of(first,second)) jobs.add(pool.submit(()->{
                login();start.await();
                try { relationships.create(device,pkg,RelationshipType.INSTALLED_ON); return true; }
                catch(AppException ex) { assertThat(ex.getCode()).isEqualTo("LICENSE_CAPACITY_EXCEEDED"); return false; }
                finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
            }));
            start.countDown();int successes=0;for(var job:jobs)if(job.get(20,java.util.concurrent.TimeUnit.SECONDS))successes++;
            assertThat(successes).isEqualTo(1);
            assertThat(assets.getAssetById(pkg).getLicense().availableSeats()).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM asset_relationships WHERE child_asset_id=?",Integer.class,pkg)).isEqualTo(1);
        } finally {
            jdbc.update("DELETE FROM asset_relationships WHERE child_asset_id=?",pkg);
            jdbc.update("DELETE FROM license_allocations WHERE license_asset_id=?",pkg);
            for(Long id:List.of(pkg,first,second)) {
                jdbc.update("DELETE FROM audit_logs WHERE entity_type='ASSET' AND entity_id=?",id);
                jdbc.update("DELETE FROM asset_hardware_details WHERE asset_id=?",id);
                jdbc.update("DELETE FROM asset_license_details WHERE asset_id=?",id);
                jdbc.update("DELETE FROM assets WHERE asset_id=?",id);
            }
            jdbc.update("DELETE FROM software_catalog WHERE software_catalog_id=?",software);
        }
    }
    @Test void loweringTotalBelowReservationsIsRejected() throws Exception {
        Long pkg=createLicense("OEM",2);
        relationships.create(assets.createHardwareAsset(request("DEVICE")).getAssetId(),pkg,RelationshipType.INSTALLED_ON);
        relationships.create(assets.createHardwareAsset(request("DEVICE")).getAssetId(),pkg,RelationshipType.INSTALLED_ON);
        var detail=assets.getAssetById(pkg);var l=detail.getLicense();
        var update=new UpdateHardwareAssetRequest(); update.setName(detail.getName());update.setTypeId(detail.getTypeId());
        update.setLicense(new LicenseDetailsRequest(l.softwareCatalogId(),l.assignmentTypeId(),l.termTypeId(),1,l.licenseKey(),l.expiryDate()));
        mvc.perform(put("/v1/assets/"+pkg).contentType("application/json").content(json.writeValueAsString(update)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LICENSE_CAPACITY_EXCEEDED"));
    }
    @Test void oemHandoverActivatesReservationWithoutConsumingAnotherSeat() {
        Long pkg=createLicense("OEM",2);Long device=assets.createHardwareAsset(request("DEVICE")).getAssetId();
        var relation=relationships.create(device,pkg,RelationshipType.INSTALLED_ON);
        Long handover=transaction("HANDOVER",pkg);
        jdbc.update("INSERT INTO transaction_assets(transaction_id,asset_id,line_number) VALUES (?,?,2)",handover,device);
        var actor=users.findByEmail("admin@itam.example").orElseThrow();var recipient=users.findByEmail("user01@itam.example").orElseThrow();
        allocations.activateOem(relation.allocationId(),recipient,handover,actor);
        assertThat(assets.getAssetById(pkg).getLicense().allocatedSeats()).isEqualTo(1);
        assertThat(assets.getAssetById(pkg).getLicense().availableSeats()).isEqualTo(1);
        assertThatThrownBy(()->allocations.activateOem(relation.allocationId(),recipient,handover,actor)).isInstanceOf(AppException.class);
    }
    @Test void perUserRecoveryUnlinksOnlyItsAllocationAndRejectsRepeats() {
        Long pkg=createLicense("PER_USER",10);Long handover=transaction("HANDOVER",pkg);
        var actor=users.findByEmail("admin@itam.example").orElseThrow();var recipient=users.findByEmail("user01@itam.example").orElseThrow();
        var deviceRequest=request("DEVICE");deviceRequest.setAssignedToUserId(recipient.getUserId());
        deviceRequest.setStatusId(lookup("asset_statuses","status_id","IN_USE"));
        Long device=assets.createHardwareAsset(deviceRequest).getAssetId();
        var first=allocations.allocatePerUser(pkg,recipient,1,handover,actor);
        allocations.allocatePerUser(pkg,recipient,2,handover,actor);
        allocations.attachPerUser(first.getAllocationId(),device,actor);
        Long recovery=transaction("RECOVERY",pkg);
        allocations.releasePerUser(first.getAllocationId(),recovery,actor);
        assertThat(relationships.list(device,org.springframework.data.domain.PageRequest.of(0,20))).isEmpty();
        assertThat(assets.getAssetById(device).getAssignedToUserId()).isEqualTo(recipient.getUserId());
        assertThat(assets.getAssetById(pkg).getLicense().availableSeats()).isEqualTo(8);
        assertThatThrownBy(()->allocations.releasePerUser(first.getAllocationId(),recovery,actor)).isInstanceOf(AppException.class);
    }
    @Test void inUseComponentCannotBeLinkedThroughInventoryApi() throws Exception {
        Long device=assets.createHardwareAsset(request("DEVICE")).getAssetId();
        var r=request("COMPONENT");r.setAssignedToUserId(users.findByEmail("user01@itam.example").orElseThrow().getUserId());
        r.setStatusId(lookup("asset_statuses","status_id","IN_USE"));
        Long component=assets.createHardwareAsset(r).getAssetId();
        mvc.perform(post("/v1/assets/"+device+"/relationships").contentType("application/json").content(json.writeValueAsString(Map.of("childAssetId",component,"type","COMPONENT_OF"))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ASSET_WORKFLOW_REQUIRED"));
    }
}
