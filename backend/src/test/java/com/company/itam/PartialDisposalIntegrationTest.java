package com.company.itam;

import com.company.itam.asset.repository.AssetRepository;
import com.company.itam.workflow.disposal.service.DisposalDeadlineService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties={"itam.documents.storage-root=target/partial-disposal-storage","itam.email.mode=simulated"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PartialDisposalIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired AssetRepository assets;
    @Autowired DisposalDeadlineService deadlines;
    private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor role(String role) {
        return user(role.equals("ADMIN")?"admin@itam.example":"it01@itam.example").authorities(new SimpleGrantedAuthority(role));
    }
    private JsonNode postJson(String url,Object body,String role) throws Exception {
        return json.readTree(mvc.perform(post(url).with(role(role)).header("X-Override-Reason","Test selected cancellation").contentType("application/json").content(json.writeValueAsString(body)))
                .andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString()).path("data");
    }
    private JsonNode progress(long id) throws Exception {
        return json.readTree(mvc.perform(get("/v1/disposals/"+id+"/progress").with(role("ADMIN")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
    }
    @Test void overdueNotifiesOnceWithoutReleasingAndExtensionKeepsHistory() throws Exception {
        long admin=jdbc.queryForObject("SELECT user_id FROM users WHERE email='admin@itam.example'",Long.class);
        long id=jdbc.queryForObject("INSERT INTO transactions(transaction_code,type,status,requester_id,processed_by,processed_at) VALUES(?,'DISPOSAL','APPROVED',?,?,now()) RETURNING transaction_id",Long.class,"OVERDUE-"+UUID.randomUUID(),admin,admin);
        jdbc.update("INSERT INTO workflow_progress(transaction_id,groups) VALUES (?,?::jsonb)",id,"[{\"groupId\":\"one\",\"status\":\"APPROVED\",\"assetIds\":[],\"allocationIds\":[],\"dueDate\":\"2020-01-01\"}]");
        deadlines.notifyOverdue();
        long notices=jdbc.queryForObject("SELECT count(*) FROM disposal_deadline_notices WHERE transaction_id=?",Long.class,id);
        assertThat(notices).isPositive();deadlines.notifyOverdue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM disposal_deadline_notices WHERE transaction_id=?",Long.class,id)).isEqualTo(notices);
        assertThat(jdbc.queryForObject("SELECT groups->0->>'status' FROM workflow_progress WHERE transaction_id=?",String.class,id)).isEqualTo("APPROVED");
        postJson("/v1/disposals/"+id+"/extend",Map.of("expectedVersion",0,"groupIds",List.of("one"),"dueDate",LocalDate.now().plusDays(5).toString(),"reason","Waiting for collection"),"IT_STAFF");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE transaction_id=? AND action='DISPOSAL_DEADLINE_EXTENDED'",Long.class,id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT content_version FROM transactions WHERE transaction_id=?",Long.class,id)).isEqualTo(1);
    }
    @Test void approvesAndExecutesSelectedAssetsWithoutRetiringRejectedOrCancelledProposal() throws Exception {
        long admin=jdbc.queryForObject("SELECT user_id FROM users WHERE email='admin@itam.example'",Long.class);
        String code="PART-DI-"+UUID.randomUUID();
        long type=jdbc.queryForObject("INSERT INTO asset_types(code,name,category_id,is_active) SELECT ?,?,category_id,true FROM asset_categories WHERE code='DEVICE' RETURNING type_id",Long.class,code,code);
        var ids=new ArrayList<Long>();
        for(int i=0;i<3;i++) ids.add(jdbc.queryForObject("INSERT INTO assets(asset_tag,name,type_id,status_id,created_by) VALUES(?,?,?,(SELECT status_id FROM asset_statuses WHERE code='DAMAGED'),?) RETURNING asset_id",Long.class,code+i,"Broken device",type,admin));
        var check=postJson("/v1/disposals/smart-check",Map.of("assetIds",ids),"IT_STAFF");
        var created=postJson("/v1/disposals",Map.of("assetIds",ids,"reason","Beyond repair","disposalDate",LocalDate.now().toString(),"expectedFingerprint",check.path("fingerprint").asText()),"IT_STAFF");
        long id=created.path("transactionId").asLong();var p=progress(id);
        String first=p.path("groups").get(0).path("groupId").asText();String second=p.path("groups").get(1).path("groupId").asText();String third=p.path("groups").get(2).path("groupId").asText();
        mvc.perform(post("/v1/disposals/"+id+"/approve-selected").with(role("IT_STAFF")).contentType("application/json").content(json.writeValueAsString(Map.of("expectedVersion",p.path("contentVersion").asLong(),"groupIds",List.of(first)))))
                .andExpect(status().isForbidden());
        postJson("/v1/disposals/"+id+"/approve-selected",Map.of("expectedVersion",p.path("contentVersion").asLong(),"groupIds",List.of(first)),"ADMIN");
        assertThat(assets.findById(ids.getFirst()).orElseThrow().getStatus().getCode().name()).isEqualTo("DAMAGED");
        p=progress(id);
        postJson("/v1/disposals/"+id+"/reject-selected",Map.of("expectedVersion",p.path("contentVersion").asLong(),"groupIds",List.of(second),"reason","Keep for repair"),"ADMIN");
        assertThat(assets.hasPendingDisposal(ids.get(1))).isFalse();assertThat(assets.hasPendingDisposal(ids.getFirst())).isTrue();
        p=progress(id);
        var uploaded=json.readTree(mvc.perform(multipart("/v1/documents").file(new MockMultipartFile("file","proof.pdf","application/pdf","%PDF-1.4\n%%EOF".getBytes()))
                .with(role("IT_STAFF")).param("transactionId",Long.toString(id)).param("documentType","DISPOSAL_REPORT").param("expectedVersion",p.path("contentVersion").asText()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
        p=progress(id);long version=p.path("contentVersion").asLong();
        var executed=postJson("/v1/disposals/"+id+"/execute",Map.of("expectedVersion",version,"groupIds",List.of(first),"documentId",uploaded.path("documentId").asLong(),"executionDate",LocalDate.now().toString()),"IT_STAFF");
        assertThat(executed.path("status").asText()).isEqualTo("PARTIAL");
        assertThat(jdbc.queryForObject("SELECT s.code FROM assets a JOIN asset_statuses s USING(status_id) WHERE a.asset_id=?",String.class,ids.getFirst())).isEqualTo("RETIRED");
        mvc.perform(post("/v1/disposals/"+id+"/execute").with(role("ADMIN")).contentType("application/json").content(json.writeValueAsString(Map.of("expectedVersion",version,"groupIds",List.of(first),"documentId",uploaded.path("documentId").asLong(),"executionDate",LocalDate.now().toString())))).andExpect(status().isConflict());
        p=progress(id);postJson("/v1/disposals/"+id+"/cancel-lines",Map.of("expectedVersion",p.path("contentVersion").asLong(),"groupIds",List.of(third),"reason","Not proceeding"),"ADMIN");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transaction_assets WHERE transaction_id=?",Long.class,id)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT jsonb_array_length(snapshot->'assets') FROM transaction_publication_snapshots WHERE transaction_id=?",Integer.class,id)).isEqualTo(1);
        assertThat(assets.hasPendingDisposal(ids.get(2))).isFalse();
    }
}
