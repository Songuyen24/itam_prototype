package com.company.itam.document;

import com.company.itam.auth.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.nio.file.Path;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class AdjustmentIntegrationTest {
    @TempDir static Path root;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r){r.add("itam.documents.storage-root",()->root.toString());}
    @Autowired MockMvc mvc; @Autowired JdbcTemplate jdbc; @Autowired JwtTokenProvider jwt; @Autowired ObjectMapper json;
    Map<String,Long> ids=new HashMap<>(); Map<String,String> tokens=new HashMap<>(); long tx,imp;
    @BeforeEach void setup(){
        for(String role:List.of("ADMIN","IT_STAFF","PUR_STAFF","USER")){
            long id=jdbc.queryForObject("INSERT INTO users(email,full_name,employee_code,role_id,account_status,password_hash,access_granted) SELECT ?,?,?,role_id,'ACTIVE','test-only',true FROM roles WHERE code=? RETURNING user_id",Long.class,UUID.randomUUID()+"@example.test","Prototype "+role,UUID.randomUUID().toString(),role);
            ids.put(role,id);tokens.put(role,"Bearer "+jwt.generateToken("user:"+id,role));
        }
        tx=transaction("HANDOVER",ids.get("IT_STAFF")); imp=transaction("IMPORT",ids.get("PUR_STAFF"));
    }
    long transaction(String type,long requester){return jdbc.queryForObject("INSERT INTO transactions(transaction_code,type,status,requester_id,processed_by,processed_at,completed_at) VALUES(?,?,'COMPLETED',?,?,now(),now()) RETURNING transaction_id",Long.class,"ADJ-"+UUID.randomUUID(),type,requester,ids.get("ADMIN"));}
    String url(long id){return "/v1/transactions/"+id+"/adjustments";}
    long create(long id,String role,String kind)throws Exception{
        var req=multipart(url(id)); req.param("kind",kind).param("reason","Prototype correction reason").param("proposedText","Corrected description / resolution plan").header("Authorization",tokens.get(role));
        if(kind.equals("DOCUMENT"))req.file(new MockMultipartFile("files","prototype.pdf","application/pdf","%PDF-1.4\nPrototype correction\n%%EOF".getBytes()));
        return json.readTree(mvc.perform(req).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data").path("id").asLong();
    }
    org.springframework.test.web.servlet.ResultActions decide(long id,int version,boolean approve,String role,String impacts)throws Exception{
        return mvc.perform(post(url(tx)+"/"+id+"/decision").header("Authorization",tokens.get(role)).contentType("application/json").content("{\"expectedVersion\":"+version+",\"approve\":"+approve+",\"reason\":\"Reviewed prototype impacts\",\"reviewedTransactions\":"+impacts+"}"));
    }
    @Test void ownershipRoleScopeAndCompletedOnly()throws Exception{
        mvc.perform(get(url(tx)).header("Authorization",tokens.get("USER"))).andExpect(status().isForbidden());
        mvc.perform(get(url(tx)).header("Authorization",tokens.get("PUR_STAFF"))).andExpect(status().isForbidden());
        mvc.perform(get(url(imp)).header("Authorization",tokens.get("PUR_STAFF"))).andExpect(status().isOk());
        mvc.perform(multipart(url(imp)).param("kind","DESCRIPTION").param("reason","reason").param("proposedText","text").header("Authorization",tokens.get("IT_STAFF"))).andExpect(status().isForbidden());
        long id=create(tx,"IT_STAFF","DESCRIPTION");decide(id,1,true,"IT_STAFF","[]").andExpect(status().isForbidden());
        imp=jdbc.queryForObject("INSERT INTO transactions(transaction_code,type,status,requester_id) VALUES(?,'IMPORT','PENDING',?) RETURNING transaction_id",Long.class,"PENDING-"+UUID.randomUUID(),ids.get("PUR_STAFF"));
        mvc.perform(multipart(url(imp)).param("kind","DESCRIPTION").param("reason","reason").param("proposedText","text").header("Authorization",tokens.get("PUR_STAFF"))).andExpect(status().isConflict());
    }
    @Test void versionsAuditSelfApprovalAndOriginalPreservation()throws Exception{
        String before=jdbc.queryForObject("SELECT row_to_json(t)::text FROM transactions t WHERE transaction_id=?",String.class,tx);
        long id=create(tx,"ADMIN","DESCRIPTION");decide(id,1,true,"ADMIN","[]").andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("APPROVED"));
        decide(id,1,true,"ADMIN","[]").andExpect(status().isConflict());
        long second=create(tx,"IT_STAFF","DESCRIPTION");decide(second,1,false,"ADMIN","[]").andExpect(status().isConflict());decide(second,2,false,"ADMIN","[]").andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT row_to_json(t)::text FROM transactions t WHERE transaction_id=?",String.class,tx)).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE transaction_id=? AND action IN ('ADJUSTMENT_REQUESTED_ON_BEHALF','ADJUSTMENT_SELF_APPROVED','ADJUSTMENT_REJECTED')",Integer.class,tx)).isEqualTo(3);
        mvc.perform(get(url(tx)+"?page=1").header("Authorization",tokens.get("ADMIN"))).andExpect(jsonPath("$.data.total").value(2)).andExpect(jsonPath("$.data.requests.length()").value(0));
    }
    @Test void filesDownloadIsolationHeadersAndInvalidUpload()throws Exception{
        long id=create(tx,"IT_STAFF","DOCUMENT");long file=jdbc.queryForObject("SELECT file_id FROM adjustment_files WHERE adjustment_id=?",Long.class,id);
        mvc.perform(get(url(tx)+"/"+id+"/files/"+file).header("Authorization",tokens.get("ADMIN"))).andExpect(status().isOk()).andExpect(header().string("X-Content-Type-Options","nosniff")).andExpect(header().string("Cache-Control","no-store"));
        mvc.perform(get(url(imp)+"/"+id+"/files/"+file).header("Authorization",tokens.get("PUR_STAFF"))).andExpect(status().isNotFound());
        mvc.perform(multipart(url(tx)).file(new MockMultipartFile("files","../evil.pdf","application/pdf","%PDF-1.4\n".getBytes())).param("kind","DOCUMENT").param("reason","reason").param("proposedText","text").header("Authorization",tokens.get("IT_STAFF"))).andExpect(status().isBadRequest());
    }
    @Test void staleImpactCannotDecideAndContentCannotBeEdited()throws Exception{
        long id=create(tx,"IT_STAFF","DESCRIPTION");decide(id,1,true,"ADMIN","[{\"transactionId\":999,\"transactionCode\":\"stale\",\"type\":\"HANDOVER\",\"status\":\"PENDING\"}]").andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT status FROM transaction_adjustments WHERE adjustment_id=?",String.class,id)).isEqualTo("PENDING");
        assertThatThrownBy(()->jdbc.update("UPDATE transaction_adjustments SET proposed_text='tampered' WHERE adjustment_id=?",id)).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
    @Test void purDoesNotSeeRestrictedSnapshotOrCorrectiveLink()throws Exception{
        long id=create(imp,"ADMIN","DESCRIPTION");
        jdbc.update("UPDATE transaction_adjustments SET status='APPROVED',decided_by=?,decided_at=now(),decision_reason='Reviewed',impact_snapshot=?::jsonb WHERE adjustment_id=?",ids.get("ADMIN"),"[{\"transactionId\":"+tx+",\"transactionCode\":\"SECRET-HANDOVER\",\"type\":\"HANDOVER\",\"status\":\"COMPLETED\"}]",id);
        String response=mvc.perform(get(url(imp)).header("Authorization",tokens.get("PUR_STAFF"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();assertThat(response).doesNotContain("SECRET-HANDOVER");
    }
    @Test
    @org.springframework.transaction.annotation.Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void concurrentVersionsAndDecisionsSerialize() throws Exception {
        var executor=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var start=new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<Long> submit=()->{start.await();return create(tx,"IT_STAFF","DESCRIPTION");};
            var first=executor.submit(submit);var second=executor.submit(submit);start.countDown();
            long a=first.get(30,java.util.concurrent.TimeUnit.SECONDS);second.get(30,java.util.concurrent.TimeUnit.SECONDS);
            assertThat(jdbc.queryForList("SELECT version FROM transaction_adjustments WHERE transaction_id=? ORDER BY version",Integer.class,tx)).containsExactly(1,2);
            int version=jdbc.queryForObject("SELECT version FROM transaction_adjustments WHERE adjustment_id=?",Integer.class,a);
            var decisionStart=new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<Integer> approve=()->{decisionStart.await();return decide(a,version,true,"ADMIN","[]").andReturn().getResponse().getStatus();};
            var x=executor.submit(approve);var y=executor.submit(approve);decisionStart.countDown();
            assertThat(List.of(x.get(30,java.util.concurrent.TimeUnit.SECONDS),y.get(30,java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);
        } finally { executor.shutdownNow(); }
    }    @Test void actualLaterTransactionsRequireReviewAndLinksRespectScope() throws Exception {
        long type=jdbc.queryForObject("SELECT type_id FROM asset_types LIMIT 1",Long.class);
        long asset=jdbc.queryForObject("INSERT INTO assets(asset_tag,name,type_id,status_id,created_by) VALUES(?,'Prototype asset',?,(SELECT status_id FROM asset_statuses WHERE code='IN_STOCK'),?) RETURNING asset_id",Long.class,"ADJ-"+UUID.randomUUID(),type,ids.get("ADMIN"));
        jdbc.update("INSERT INTO transaction_assets(transaction_id,asset_id,line_number) VALUES(?,?,1)",imp,asset);
        jdbc.update("INSERT INTO transaction_assets(transaction_id,asset_id,line_number) VALUES(?,?,1)",tx,asset);
        String data=mvc.perform(get(url(imp)).header("Authorization",tokens.get("ADMIN"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.subsequentTransactions.length()").value(1)).andReturn().getResponse().getContentAsString();
        mvc.perform(get(url(imp)).header("Authorization",tokens.get("PUR_STAFF"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.subsequentTransactions.length()").value(0));
        mvc.perform(multipart(url(imp)).param("kind","WORKFLOW").param("reason","Correct recipient").param("proposedText","Use recovery workflow").param("correctiveTransactionId",String.valueOf(tx)).header("Authorization",tokens.get("PUR_STAFF"))).andExpect(status().isForbidden());
        long id=create(imp,"PUR_STAFF","DESCRIPTION");
        long saved=tx;tx=imp;
        decide(id,1,true,"ADMIN","[]").andExpect(status().isConflict());
        String impacts=json.readTree(data).path("data").path("subsequentTransactions").toString();
        decide(id,1,true,"ADMIN",impacts).andExpect(status().isOk());tx=saved;
        mvc.perform(get(url(imp)).header("Authorization",tokens.get("PUR_STAFF"))).andExpect(jsonPath("$.data.requests[0].impactSnapshot").value("[]"));
    }}




