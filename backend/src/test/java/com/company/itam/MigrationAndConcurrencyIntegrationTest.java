package com.company.itam;

import com.company.itam.workflow.receiving.service.ImportDraftService;
import com.company.itam.common.exception.AppException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class MigrationAndConcurrencyIntegrationTest {
    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    @Autowired ImportDraftService drafts;
    @Autowired com.company.itam.document.service.DocumentService documents;

    void login(String email) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(email,null,
                List.of(new SimpleGrantedAuthority("PUR_STAFF"))));
    }

    @Test void allMigrationsRunOnAnEmptyIsolatedSchema() {
        String schema="checklist_"+UUID.randomUUID().toString().replace("-","");
        Flyway flyway=Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                .cleanDisabled(false).locations("classpath:db/migration").load();
        try {
            var result=flyway.migrate();
            assertThat(result.success).isTrue();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM "+schema+".asset_statuses WHERE code='PENDING_IMPORT'",Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM "+schema+".roles WHERE code='FIN'",Integer.class)).isZero();
        } finally { flyway.clean(); }
    }

    @Test void simultaneousAllocationsAreUniqueAndSkipManuallyOccupiedTags() throws Exception {
        String marker="tag-race-"+UUID.randomUUID();
        TransactionTemplate tx=new TransactionTemplate(manager);
        Long type=jdbc.queryForObject("SELECT type_id FROM asset_types WHERE code='LAPTOP'",Long.class);
        Long status=jdbc.queryForObject("SELECT status_id FROM asset_statuses WHERE code='IN_STOCK'",Long.class);
        Long actor=jdbc.queryForObject("SELECT user_id FROM users WHERE email='admin@itam.example'",Long.class);
        String occupied=tx.execute(s->{
            String candidate=jdbc.queryForObject("SELECT next_asset_tag()",String.class);
            // Occupy the NEXT candidate to demonstrate skipping a manual tag.
            String next="AST-"+String.format("%010d",Long.parseLong(candidate.substring(4))+1);
            jdbc.update("INSERT INTO assets(asset_tag,name,type_id,status_id,created_by) VALUES (?,?,?,?,?)",next,marker,type,status,actor);
            return next;
        });
        try (var pool=Executors.newFixedThreadPool(6)) {
            CountDownLatch start=new CountDownLatch(1);
            List<Future<String>> requests=new ArrayList<>();
            for(int i=0;i<6;i++) requests.add(pool.submit(()->{
                start.await(); return tx.execute(s->{
                    String tag=jdbc.queryForObject("SELECT next_asset_tag()",String.class);
                    jdbc.update("INSERT INTO assets(asset_tag,name,type_id,status_id,created_by) VALUES (?,?,?,?,?)",tag,marker,type,status,actor);
                    return tag;
                });
            }));
            start.countDown(); Set<String> tags=new HashSet<>();
            for(var request:requests) tags.add(request.get(20,TimeUnit.SECONDS));
            assertThat(tags).hasSize(6).doesNotContain(occupied);
        } finally { jdbc.update("DELETE FROM assets WHERE name=?",marker); }
    }

    @Test void twoPurchasersEditingTheSameVersionOnlyCommitOneChange() throws Exception {
        String second="pur-race-"+UUID.randomUUID()+"@itam.example";
        jdbc.update("INSERT INTO users(email,full_name,role_id,account_status) SELECT ?,'Race PUR',role_id,'ACTIVE' FROM roles WHERE code='PUR_STAFF'",second);
        login("pur01@itam.example");
        var draft=drafts.create("before",null);
        SecurityContextHolder.clearContext();
        try (var pool=Executors.newFixedThreadPool(2)) {
            CountDownLatch start=new CountDownLatch(1);
            List<Future<Boolean>> requests=new ArrayList<>();
            for(String email:List.of("pur01@itam.example",second)) requests.add(pool.submit(()->{
                login(email);
                try {
                    start.await(); drafts.updateNotes(draft.transactionId(),draft.expectedVersion(),email); return true;
                } catch(AppException ex) { assertThat(ex.getCode()).isEqualTo("TRANSACTION_VERSION_CONFLICT"); return false; }
                finally { SecurityContextHolder.clearContext(); }
            }));
            start.countDown(); int successes=0;
            for(var request:requests) if(request.get(20,TimeUnit.SECONDS)) successes++;
            assertThat(successes).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT content_version FROM transactions WHERE transaction_id=?",Long.class,draft.transactionId()))
                    .isEqualTo(draft.expectedVersion()+1);
        } finally {
            jdbc.update("DELETE FROM audit_logs WHERE transaction_id=?",draft.transactionId());
            jdbc.update("DELETE FROM transactions WHERE transaction_id=?",draft.transactionId());
            jdbc.update("DELETE FROM users WHERE email=?",second);
        }
    }

    @Test void submitRacingUploadCommitsOneCompleteVersion() throws Exception {
        login("pur01@itam.example");
        var draft=drafts.create("concurrency sample",null);
        var hardware=new com.company.itam.asset.dto.request.CreateHardwareAssetRequest();
        hardware.setName("Concurrent upload sample");
        hardware.setTypeId(jdbc.queryForObject("SELECT type_id FROM asset_types WHERE code='LAPTOP'",Long.class));
        draft=drafts.addHardware(draft.transactionId(),draft.expectedVersion(),hardware);
        final var target=draft;
        SecurityContextHolder.clearContext();
        try(var pool=Executors.newFixedThreadPool(2)) {
            CountDownLatch start=new CountDownLatch(1);
            Future<Boolean> submit=pool.submit(()->{
                login("pur01@itam.example");
                try { start.await(); drafts.submit(target.transactionId(),target.expectedVersion()); return true; }
                catch(AppException ex) { assertThat(ex.getCode()).isEqualTo("TRANSACTION_VERSION_CONFLICT"); return false; }
                finally { SecurityContextHolder.clearContext(); }
            });
            Future<Boolean> upload=pool.submit(()->{
                login("pur01@itam.example");
                try {
                    start.await(); documents.upload(new org.springframework.mock.web.MockMultipartFile("file","race.pdf","application/pdf","%PDF-1.4\nSAMPLE".getBytes()),
                            target.transactionId(),com.company.itam.common.enums.DocumentType.INVOICE,null,target.expectedVersion()); return true;
                } catch(AppException ex) { assertThat(ex.getCode()).isIn("DOCUMENT_LOCKED","TRANSACTION_VERSION_CONFLICT"); return false; }
                finally { SecurityContextHolder.clearContext(); }
            });
            start.countDown();
            boolean submitted=submit.get(20,TimeUnit.SECONDS), uploaded=upload.get(20,TimeUnit.SECONDS);
            assertThat(submitted).isNotEqualTo(uploaded);
            if(uploaded) { login("pur01@itam.example"); drafts.submit(target.transactionId(),target.expectedVersion()+1); SecurityContextHolder.clearContext(); }
            assertThat(jdbc.queryForObject("SELECT jsonb_array_length(snapshot->'documents') FROM transaction_revisions WHERE transaction_id=? AND revision=1",Integer.class,target.transactionId()))
                    .isEqualTo(uploaded?1:0);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM transaction_revisions WHERE transaction_id=?",Integer.class,target.transactionId())).isEqualTo(1);
        }
        // Intentionally retain this tiny fictional submitted fixture: immutable history must not be removed for test cleanup.
    }
}
