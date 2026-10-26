package com.company.itam;

import com.company.itam.dashboard.DashboardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class M9DashboardIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired DashboardService dashboard;

    @Test void blocksNonInventoryRolesAndValidatesPeriod() throws Exception {
        for (String role : new String[]{"USER", "PUR_STAFF"})
            mvc.perform(get("/v1/dashboard/summary").with(user("admin@itam.example").authorities(() -> role))).andExpect(status().isForbidden());
        for (String role : new String[]{"ADMIN", "IT_STAFF"})
            mvc.perform(get("/v1/dashboard/summary").with(user("admin@itam.example").authorities(() -> role))).andExpect(status().isOk());
        mvc.perform(get("/v1/dashboard/summary?period=custom&from=2026-10-02&to=2026-10-01").with(user("admin@itam.example").authorities(() -> "ADMIN"))).andExpect(status().isBadRequest());
    }

    @Test @org.springframework.security.test.context.support.WithMockUser(authorities="ADMIN")
    @SuppressWarnings("unchecked")
    void countsCompletedEventsByCompletionBoundaryWithoutMultiplyingTransactions() {
        var from=LocalDate.of(2090,1,1); var to=from.plusDays(1);
        long actor=jdbc.queryForObject("SELECT user_id FROM users WHERE email='admin@itam.example'",Long.class);
        long a=transaction(actor,"COMPLETED",from.atStartOfDay());
        transaction(actor,"COMPLETED",to.plusDays(1).atStartOfDay());
        transaction(actor,"PENDING",null);
        long type=jdbc.queryForObject("SELECT ty.type_id FROM asset_types ty JOIN asset_categories c USING(category_id) WHERE c.code='DEVICE' LIMIT 1",Long.class);
        for(int i=0;i<2;i++) {
            long asset=jdbc.queryForObject("INSERT INTO assets(asset_tag,name,type_id,status_id,created_by) VALUES (?, 'M9 device',?,(SELECT status_id FROM asset_statuses WHERE code='IN_STOCK'),?) RETURNING asset_id",Long.class,"M9-"+UUID.randomUUID(),type,actor);
            jdbc.update("INSERT INTO transaction_assets(transaction_id,asset_id,line_number) VALUES (?,?,?)",a,asset,i+1);
        }
        var result=dashboard.summary("custom",from,to);
        var movements=(java.util.List<java.util.Map<String,Object>>)result.get("movements");
        assertThat(movements).hasSize(1);
        assertThat(((Number)movements.getFirst().get("transactions")).longValue()).isEqualTo(1);
        assertThat(((Number)movements.getFirst().get("devices")).longValue()).isEqualTo(2);
        assertThat(result.get("byStatus")).isEqualTo(dashboard.summary("custom",from.minusYears(1),to.minusYears(1)).get("byStatus"));
        assertThat((java.util.List<?>)result.get("daily")).hasSize(1);
    }
    @Test @org.springframework.security.test.context.support.WithMockUser(authorities="ADMIN")
    @SuppressWarnings("unchecked")
    void separatesPackagesAndCurrentSeatStatesFromFrozenImportSeats() {
        long actor=jdbc.queryForObject("SELECT user_id FROM users WHERE email='admin@itam.example'",Long.class);
        long type=jdbc.queryForObject("INSERT INTO asset_types(code,name,category_id,is_active) SELECT ?, 'M9 License',category_id,true FROM asset_categories WHERE code='LICENSE' RETURNING type_id",Long.class,"M9-"+UUID.randomUUID());
        long asset=jdbc.queryForObject("INSERT INTO assets(asset_tag,name,type_id,status_id,created_by) VALUES (?,'M9 license',?,(SELECT status_id FROM asset_statuses WHERE code='IN_STOCK'),?) RETURNING asset_id",Long.class,"M9-"+UUID.randomUUID(),type,actor);
        long software=jdbc.queryForObject("INSERT INTO software_catalog(name,manufacturer,is_active) VALUES (?,'M9',true) RETURNING software_catalog_id",Long.class,"M9-"+UUID.randomUUID());
        jdbc.update("INSERT INTO asset_license_details(asset_id,software_catalog_id,license_assignment_type_id,license_term_type_id,seat_count) VALUES (?,?,(SELECT license_assignment_type_id FROM license_assignment_types WHERE code='PER_USER'),(SELECT license_term_type_id FROM license_term_types WHERE code='PERPETUAL'),10)",asset,software);
        var day=LocalDate.of(2091,1,1);
        long tx=transaction(actor,"COMPLETED",day.atTime(23,59,59));
        jdbc.update("INSERT INTO transaction_assets(transaction_id,asset_id,line_number) VALUES (?,?,1)",tx,asset);
        jdbc.update("INSERT INTO transaction_publication_snapshots(transaction_id,snapshot) VALUES (?,CAST(? AS jsonb))",tx,"{\"assets\":[{\"license\":{\"seat_count\":7}}]}");
        var before=(java.util.List<java.util.Map<String,Object>>)dashboard.summary("custom",day,day).get("inventory");
        var baseline=before.stream().filter(r->r.get("category").equals("LICENSE")).findFirst().orElseThrow();
        jdbc.update("INSERT INTO license_allocations(license_asset_id,user_id,seat_count,status,created_by) VALUES (?,?,2,'ACTIVE',?)",asset,actor,actor);
        jdbc.update("INSERT INTO license_allocations(license_asset_id,user_id,seat_count,status,created_by) VALUES (?,?,3,'RESERVED',?)",asset,actor,actor);
        jdbc.update("INSERT INTO license_allocations(license_asset_id,user_id,seat_count,status,released_at,created_by) VALUES (?,?,4,'RELEASED',now(),?)",asset,actor,actor);
        var result=dashboard.summary("custom",day,day);
        var inventory=(java.util.List<java.util.Map<String,Object>>)result.get("inventory");
        var license=inventory.stream().filter(r->r.get("category").equals("LICENSE")).findFirst().orElseThrow();
        assertThat(((Number)license.get("active_seats")).longValue()-((Number)baseline.get("active_seats")).longValue()).isEqualTo(2);
        assertThat(((Number)license.get("reserved_seats")).longValue()-((Number)baseline.get("reserved_seats")).longValue()).isEqualTo(3);
        assertThat(((Number)license.get("available_seats")).longValue()-((Number)baseline.get("available_seats")).longValue()).isEqualTo(-5);
        assertThat(license.get("count")).isEqualTo(baseline.get("count"));
        var movement=((java.util.List<java.util.Map<String,Object>>)result.get("movements")).getFirst();
        assertThat(((Number)movement.get("packages")).longValue()).isOne();
        assertThat(((Number)movement.get("seats")).longValue()).isEqualTo(7);
        assertThat(((Number)movement.get("devices")).longValue()).isZero();
    }
    private long transaction(long actor,String status,java.time.LocalDateTime completed) {
        return jdbc.queryForObject("INSERT INTO transactions(transaction_code,type,status,requester_id,processed_by,processed_at,completed_at) VALUES (?,'IMPORT',?,?,?, ?,?) RETURNING transaction_id",Long.class,
            "M9-"+UUID.randomUUID(),status,actor,completed==null?null:actor,completed,completed);
    }
}

