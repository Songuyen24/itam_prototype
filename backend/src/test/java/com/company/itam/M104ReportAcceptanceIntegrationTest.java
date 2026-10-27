package com.company.itam;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties="itam.email.mode=simulated")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class M104ReportAcceptanceIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;

    private com.fasterxml.jackson.databind.JsonNode read(String path) throws Exception {
        return json.readTree(mvc.perform(get(path).with(user("admin@itam.example").authorities(()->"ADMIN")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
    }

    @Test void allWorkflowStatesReconcileAcrossPagedReportExcelAndCompletedDashboard() throws Exception {
        long actor=jdbc.queryForObject("SELECT user_id FROM users WHERE email='admin@itam.example'",Long.class);
        String prefix="M104-"+UUID.randomUUID();
        var types=List.of("IMPORT","HANDOVER","RECOVERY","DISPOSAL");
        var states=List.of("DRAFT","PENDING","COMPLETED","REJECTED","CANCELLED");
        for(String type:types) for(String state:states) {
            jdbc.update("""
                INSERT INTO transactions(transaction_code,type,status,requester_id,created_at,processed_by,processed_at,completed_at,rejection_reason)
                VALUES(?,?,?,?,'2084-04-02',?,?,?,?)
                """,prefix+type+state,type,state,actor,
                List.of("COMPLETED","REJECTED").contains(state)?actor:null,
                List.of("COMPLETED","REJECTED").contains(state)?java.time.LocalDateTime.of(2084,4,3,23,59,59):null,
                state.equals("COMPLETED")?java.time.LocalDateTime.of(2084,4,3,23,59,59):null,
                state.equals("REJECTED")?"Mẫu prototype":null);
        }
        String created="/v1/reports/transactions?from=2084-04-02&to=2084-04-02&actorId="+actor;
        var all=read(created+"&size=3&page=1");
        assertThat(all.path("total").asInt()).isEqualTo(20);
        assertThat(all.path("items").size()).isEqualTo(3);
        int total=0; for(var row:all.path("summary")) total+=row.path("count").asInt();
        assertThat(total).isEqualTo(20);
        var dashboard=read("/v1/dashboard/summary?period=custom&from=2084-04-03&to=2084-04-03");
        assertThat(dashboard.path("movements").size()).isEqualTo(4);
        for(String type:types) {
            for(String state:states) {
                var filtered=read(created+"&type="+type+"&status="+state);
                assertThat(filtered.path("total").asInt()).isEqualTo(1);
                assertThat(filtered.path("items").get(0).path("transaction_code").asText()).isEqualTo(prefix+type+state);
            }
            var report=read("/v1/reports/transactions?dateField=completed&from=2084-04-03&to=2084-04-03&type="+type);
            assertThat(report.path("total").asInt()).isEqualTo(1);
            for(var movement:dashboard.path("movements")) if(type.equals(movement.path("type").asText()))
                assertThat(movement.path("transactions").asInt()).isEqualTo(report.path("total").asInt());
        }
        assertThat(read("/v1/reports/transactions?dateField=completed&from=2084-04-02&to=2084-04-02").path("total").asInt()).isZero();
        for(String language:List.of("vi","en")) {
            byte[] bytes=mvc.perform(get(created.replace("transactions?","transactions/export?")+"&page=9&size=3")
                    .header("Accept-Language",language).with(user("admin@itam.example").authorities(()->"ADMIN")))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
            try(var book=org.apache.poi.ss.usermodel.WorkbookFactory.create(new java.io.ByteArrayInputStream(bytes))) {
                var sheet=book.getSheetAt(0);
                assertThat(sheet.getLastRowNum()).isEqualTo(20);
                var codes=new java.util.HashSet<String>();
                for(int i=1;i<=20;i++) codes.add(sheet.getRow(i).getCell(1).getStringCellValue());
                for(String type:types) for(String state:states) assertThat(codes).contains(prefix+type+state);
            }
        }
    }

    @Test void reportCurrentInventoryMatchesDashboardRegardlessOfPeriod() throws Exception {
        var report=read("/v1/reports/assets?size=1");
        var dashboard=read("/v1/dashboard/summary?period=custom&from=2084-04-03&to=2084-04-03");
        for(var summary:report.path("summary")) {
            var found=false;
            for(var inventory:dashboard.path("inventory")) if(summary.path("category").equals(inventory.path("category"))) {
                found=true;
                for(String key:List.of("count","total_seats","reserved_seats","available_seats"))
                    assertThat(summary.path(key).asLong()).isEqualTo(inventory.path(key).asLong());
                assertThat(summary.path("allocated_seats").asLong()).isEqualTo(inventory.path("active_seats").asLong());
            }
            assertThat(found).isTrue();
        }
    }
}
