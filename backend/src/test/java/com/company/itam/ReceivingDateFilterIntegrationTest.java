package com.company.itam;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ReceivingDateFilterIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test void filtersCreatedDayBeforePagingWithStatusAndCode() throws Exception {
        var marker="RDF-"+UUID.randomUUID();
        Long actor=jdbc.queryForObject("SELECT user_id FROM users WHERE email='admin@itam.example'",Long.class);
        var day=LocalDate.of(2097,4,12);
        var start=day.atStartOfDay(ZoneId.systemDefault()).toInstant();
        for(int index=0;index<4;index++) {
            var at=switch(index) { case 0 -> start.minusNanos(1000); case 1 -> start; case 2 -> day.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().minusNanos(1000); default -> day.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant(); };
            jdbc.update("INSERT INTO transactions(transaction_code,type,status,requester_id,created_at,processed_by,processed_at,completed_at) VALUES (?,'IMPORT',?,?,?,?,?,?)",
                marker+index,index==2?"COMPLETED":"DRAFT",actor,java.sql.Timestamp.from(at),
                index==2?actor:null,index==2?java.sql.Timestamp.from(at):null,index==2?java.sql.Timestamp.from(at):null);
        }
        var admin=user("admin@itam.example").authorities(new SimpleGrantedAuthority("ADMIN"));
        mvc.perform(get("/v1/transactions").with(admin).param("type","IMPORT").param("keyword",marker).param("from",day.toString()).param("to",day.toString()).param("size","1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(2)).andExpect(jsonPath("$.data.totalPages").value(2));
        mvc.perform(get("/v1/transactions").with(admin).param("type","IMPORT").param("keyword",marker).param("from",day.toString()).param("to",day.toString()).param("status","COMPLETED"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1)).andExpect(jsonPath("$.data.content[0].transactionCode").value(marker+2));
        mvc.perform(get("/v1/transactions").with(admin).param("from",day.plusDays(1).toString()).param("to",day.toString()))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/v1/transactions").with(admin).param("from","not-a-date")).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transactions WHERE transaction_code LIKE ?",Integer.class,marker+"%")).isEqualTo(4);
    }
}
