package com.company.itam;

import com.company.itam.asset.dto.request.*;
import com.company.itam.asset.service.AssetService;
import com.company.itam.common.enums.*;
import com.company.itam.common.exception.AppException;
import com.company.itam.user.service.IdentityService;
import com.company.itam.workflow.handover.dto.*;
import com.company.itam.workflow.handover.service.HandoverService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
@WithMockUser(username="admin@itam.example",authorities="ADMIN")
class HandoverEmployeeIntegrationTest {
    @Autowired IdentityService identities;
    @Autowired HandoverService handovers;
    @Autowired AssetService assets;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired MockMvc mvc;
    @SpyBean com.company.itam.workflow.handover.repository.HandoverSnapshotRepository snapshots;
    private String marker;

    @BeforeEach void setup() {
        marker="HEI-"+UUID.randomUUID();
        for(String category:List.of("DEVICE","LICENSE")) jdbc.update("INSERT INTO asset_types(code,name,category_id,is_active) SELECT ?,?,category_id,true FROM asset_categories WHERE code=? ON CONFLICT(code) DO NOTHING","HEI_"+category,"Integration "+category,category);
        jdbc.update("INSERT INTO locations(code,name,is_active) VALUES ('HEI','Integration office',true) ON CONFLICT(code) DO NOTHING");
    }
    Long id(String table,String column,String code) { return jdbc.queryForObject("SELECT "+column+" FROM "+table+" WHERE code=?",Long.class,code); }
    com.company.itam.user.entity.UserEntity profile() {
        return identities.saveProfile(null,new IdentityService.Profile("Nhân viên "+marker,marker,marker.toLowerCase()+"@example.test",null,null));
    }
    Long asset(String category) {
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
            new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("admin@itam.example",null,List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ADMIN"))));
        var request=new CreateHardwareAssetRequest();request.setCreationPurpose(AssetCreationPurpose.BASELINE);
        request.setName(marker+" "+category);request.setTypeId(id("asset_types","type_id","HEI_"+category));
        if(category.equals("LICENSE")) {
            Long software=jdbc.queryForObject("INSERT INTO software_catalog(name,manufacturer,is_active) VALUES (?,'Test',true) RETURNING software_catalog_id",Long.class,marker);
            request.setLicense(new LicenseDetailsRequest(software,id("license_assignment_types","license_assignment_type_id","PER_USER"),id("license_term_types","license_term_type_id","PERPETUAL"),3,null,null));
        }
        return assets.createHardwareAsset(request).getAssetId();
    }
    HandoverRequest request(Long recipient,Long device,Long license) {
        return new HandoverRequest(recipient,id("locations","location_id","HEI"),LocalDate.now(),List.of(device),license==null?List.of():List.of(new HandoverRequest.LicenseLine(license,2,null)),marker,null);
    }
    HandoverResponse draft(HandoverRequest request) {
        return handovers.create(new HandoverRequest(request.recipientUserId(),request.destinationLocationId(),request.handoverDate(),request.assetIds(),request.licenses(),request.notes(),handovers.preview(request).fingerprint()));
    }

    @Test void employeeCodeNameAndEmailSearchUseTheExistingProfileAndRespectRoles() throws Exception {
        var person=profile();
        assertThat(person.getPasswordHash()).isNull();
        for(String keyword:List.of(marker,person.getFullName(),person.getEmail())) mvc.perform(get("/v1/handovers/holders").param("keyword",keyword).param("heldOnly","false"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1))
            .andExpect(jsonPath("$.data.content[0].id").value(person.getUserId()))
            .andExpect(jsonPath("$.data.content[0].employeeCode").value(marker));
        mvc.perform(get("/v1/handovers/holders/"+person.getUserId())).andExpect(status().isOk()).andExpect(jsonPath("$.data.holder.employeeCode").value(marker));
        for(String role:List.of("PUR_STAFF","USER")) mvc.perform(get("/v1/handovers/holders").with(user("restricted").authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority(role)))).andExpect(status().isForbidden());
        Long device=asset("DEVICE");identities.profileStatus(person.getUserId(),AccountStatus.LOCKED);
        assertThatThrownBy(()->handovers.preview(request(person.getUserId(),device,null))).isInstanceOf(AppException.class).hasMessage("HANDOVER_RECIPIENT_INACTIVE");
    }

    @Test void lifecycleKeepsProfileIdentityAndFreezesEmployeeCodeInCompletedHistory() {
        var person=profile();Long device=asset("DEVICE"),license=asset("LICENSE");
        var transaction=draft(request(person.getUserId(),device,license));
        assertThat(json.valueToTree(transaction).path("recipientEmployeeCode").asText()).isEqualTo(marker);
        handovers.submit(transaction.transactionId());
        assertThat(assets.getAssetById(device).getAssignedToUserId()).isNull();
        assertThat(assets.getAssetById(license).getLicense().availableSeats()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM license_allocations WHERE handover_transaction_id=? AND status='ACTIVE'",Integer.class,transaction.transactionId())).isZero();
        handovers.change(transaction.transactionId(),"withdraw");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM handover_asset_reservations WHERE transaction_id=?",Integer.class,transaction.transactionId())).isZero();
        assertThat(assets.getAssetById(license).getLicense().availableSeats()).isEqualTo(3);
        handovers.submit(transaction.transactionId());var completed=handovers.confirm(transaction.transactionId());
        assertThat(json.valueToTree(completed).path("recipientEmployeeCode").asText()).isEqualTo(marker);
        assertThat(assets.getAssetById(device).getAssignedToUserId()).isEqualTo(person.getUserId());
        String frozen=jdbc.queryForObject("SELECT snapshot::text FROM handover_snapshots WHERE transaction_id=?",String.class,transaction.transactionId());
        long count=jdbc.queryForObject("SELECT count(*) FROM users",Long.class);
        identities.createAccount(new IdentityService.CreateAccount(person.getUserId(),"LocalTestPassword!45",Role.USER,true));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users",Long.class)).isEqualTo(count);
        identities.saveProfile(person.getUserId(),new IdentityService.Profile(person.getFullName(),marker+"-corrected",person.getEmail(),null,"Correct a test entry"));
        assertThat(json.valueToTree(handovers.get(transaction.transactionId())).path("recipientEmployeeCode").asText()).isEqualTo(marker);
        assertThat(jdbc.queryForObject("SELECT snapshot::text FROM handover_snapshots WHERE transaction_id=?",String.class,transaction.transactionId())).isEqualTo(frozen);
        assertThat(jdbc.queryForObject("SELECT sum(seat_count) FROM license_allocations WHERE handover_transaction_id=? AND user_id=? AND status='ACTIVE'",Integer.class,transaction.transactionId(),person.getUserId())).isEqualTo(2);
    }

    @Test void draftsAndCompletedSnapshotsWithoutEmployeeCodeStayCompatible() {
        var person=profile();var transaction=draft(request(person.getUserId(),asset("DEVICE"),null));
        jdbc.update("UPDATE handover_workflows SET preview=preview-'recipientEmployeeCode' WHERE transaction_id=?",transaction.transactionId());
        // Insert the historical JSON shape once; never edit an immutable snapshot.
        doAnswer(invocation->{
            HandoverResponse value=invocation.getArgument(0);
            var legacy=json.<com.fasterxml.jackson.databind.node.ObjectNode>valueToTree(value);
            legacy.remove("recipientEmployeeCode");
            jdbc.update("INSERT INTO handover_snapshots(transaction_id,snapshot) VALUES (?,?::jsonb)",value.transactionId(),json.writeValueAsString(legacy));
            return null;
        }).when(snapshots).save(any(HandoverResponse.class));
        handovers.submit(transaction.transactionId());handovers.confirm(transaction.transactionId());
        String frozen=jdbc.queryForObject("SELECT snapshot::text FROM handover_snapshots WHERE transaction_id=?",String.class,transaction.transactionId());
        var code=json.valueToTree(handovers.get(transaction.transactionId())).get("recipientEmployeeCode");
        assertThat(code==null||code.isNull()).isTrue();
        assertThat(jdbc.queryForObject("SELECT snapshot::text FROM handover_snapshots WHERE transaction_id=?",String.class,transaction.transactionId())).isEqualTo(frozen);
    }
}
