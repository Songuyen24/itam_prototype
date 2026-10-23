package com.company.itam;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties="itam.email.mode=simulated")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class M6ReportIntegrationTest {
 @Autowired MockMvc mvc;
 @Autowired JdbcTemplate jdbc;
 private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor role(String role) {
  return user(switch(role){case "ADMIN"->"admin@itam.example";case "IT_STAFF"->"it01@itam.example";case "PUR_STAFF"->"pur01@itam.example";default->"user01@itam.example";}).authorities(new SimpleGrantedAuthority(role));
 }
 @Test void grantsAreSeparateRevocableAndAudited() throws Exception {
  long id=jdbc.queryForObject("SELECT user_id FROM users WHERE email='it01@itam.example'",Long.class);
  jdbc.update("DELETE FROM report_permissions WHERE user_id=?",id);
  mvc.perform(get("/v1/reports/assets").with(role("IT_STAFF"))).andExpect(status().isForbidden());
  mvc.perform(put("/v1/reports/permissions/{id}/ASSETS",id).with(role("ADMIN")).contentType("application/json").content("{\"view\":true,\"export\":false}")).andExpect(status().isOk());
  mvc.perform(get("/v1/reports/assets").with(role("IT_STAFF"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").isNumber());
  mvc.perform(get("/v1/reports/assets/export").with(role("IT_STAFF"))).andExpect(status().isForbidden());
  mvc.perform(get("/v1/reports/transactions").with(role("IT_STAFF"))).andExpect(status().isForbidden());
  mvc.perform(put("/v1/reports/permissions/{id}/ASSETS",id).with(role("ADMIN")).contentType("application/json").content("{\"view\":false,\"export\":false}")).andExpect(status().isOk());
  mvc.perform(get("/v1/reports/assets").with(role("IT_STAFF"))).andExpect(status().isForbidden());
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE entity_id=? AND action='REPORT_PERMISSION_UPDATED'",Long.class,id)).isGreaterThanOrEqualTo(2);
 }
 @Test void blocksUserAndInvalidFiltersAndSupportsAllGroups() throws Exception {
  for(String group:new String[]{"assets","transactions","allocations"}) {
   mvc.perform(get("/v1/reports/"+group).with(role("USER"))).andExpect(status().isForbidden());
   mvc.perform(get("/v1/reports/"+group).with(role("ADMIN"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.summary").isArray());
  }
  mvc.perform(get("/v1/reports/transactions?dateField=completed").with(role("ADMIN"))).andExpect(status().isOk());
  mvc.perform(get("/v1/reports/assets?size=0").with(role("ADMIN"))).andExpect(status().isBadRequest());
  mvc.perform(get("/v1/reports/transactions?dateField=bogus").with(role("ADMIN"))).andExpect(status().isBadRequest());
 }

 @Test void exportHasSafeHeadersEvenWithoutResultsAndIgnoresPaging() throws Exception {
  byte[] bytes=mvc.perform(get("/v1/reports/assets/export?type=NO_SUCH_TYPE&page=9&size=1").header("Accept-Language","en").with(role("ADMIN")))
   .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
  try(var book=org.apache.poi.ss.usermodel.WorkbookFactory.create(new java.io.ByteArrayInputStream(bytes))) {
   var sheet=book.getSheetAt(0);assertThat(sheet.getLastRowNum()).isZero();
   assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Asset tag");
   for(var cell:sheet.getRow(0)) assertThat(cell.getStringCellValue().toLowerCase()).doesNotContain("cost","key","password","token");
  }
 }

 @Test void filteredExportIncludesEveryPageWithoutCost() throws Exception {
  String code="M6_"+java.util.UUID.randomUUID();
  Long type=jdbc.queryForObject("INSERT INTO asset_types(code,name,category_id,is_active) SELECT ?,?,category_id,true FROM asset_categories WHERE code='DEVICE' RETURNING type_id",Long.class,code,code);
  Long admin=jdbc.queryForObject("SELECT user_id FROM users WHERE email='admin@itam.example'",Long.class);
  for(int i=0;i<23;i++) jdbc.update("INSERT INTO assets(asset_tag,name,type_id,status_id,created_by,purchase_cost) VALUES(?,?,?,(SELECT status_id FROM asset_statuses WHERE code='IN_STOCK'),?,999)",code+i,"Fixture",type,admin);
  mvc.perform(get("/v1/reports/assets").param("type",code).param("size","2").with(role("ADMIN"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(23)).andExpect(jsonPath("$.data.items.length()").value(2));
  byte[] bytes=mvc.perform(get("/v1/reports/assets/export").param("type",code).param("size","2").param("page","1").with(role("ADMIN"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
  try(var book=org.apache.poi.ss.usermodel.WorkbookFactory.create(new java.io.ByteArrayInputStream(bytes))) {assertThat(book.getSheetAt(0).getLastRowNum()).isEqualTo(23);}
 }

 @Test void completedDateUsesActualCompletionAndNeverDraftCreation() throws Exception {
  Long admin=jdbc.queryForObject("SELECT user_id FROM users WHERE email='admin@itam.example'",Long.class);
  Long processor=jdbc.queryForObject("SELECT user_id FROM users WHERE email='it01@itam.example'",Long.class);
  String processorName=jdbc.queryForObject("SELECT full_name FROM users WHERE user_id=?",String.class,processor);
  String code="M6-"+java.util.UUID.randomUUID();
  jdbc.update("INSERT INTO transactions(transaction_code,type,status,requester_id,created_at) VALUES(?,'IMPORT','DRAFT',?,'2035-01-01')",code+"D",admin);
  jdbc.update("INSERT INTO transactions(transaction_code,type,status,requester_id,created_at,processed_by,processed_at,completed_at) VALUES(?,'IMPORT','COMPLETED',?,'2034-01-01',?,'2035-01-01','2035-01-01')",code+"C",admin,processor);
  String adminName=jdbc.queryForObject("SELECT full_name FROM users WHERE user_id=?",String.class,admin);
  mvc.perform(get("/v1/reports/transactions?type=IMPORT&dateField=completed&from=2035-01-01&to=2035-01-01").with(role("ADMIN"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].requester_name").value(adminName)).andExpect(jsonPath("$.data.items[0].processor_name").value(processorName)).andExpect(jsonPath("$.data.summary[0].count").value(1));
  byte[] names=mvc.perform(get("/v1/reports/transactions/export?type=IMPORT&dateField=completed&from=2035-01-01&to=2035-01-01").header("Accept-Language","en").with(role("ADMIN"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
  try(var book=org.apache.poi.ss.usermodel.WorkbookFactory.create(new java.io.ByteArrayInputStream(names))) {
   assertThat(book.getSheetAt(0).getRow(0).getCell(7).getStringCellValue()).isEqualTo("Creator");
   assertThat(book.getSheetAt(0).getRow(1).getCell(7).getStringCellValue()).isEqualTo(adminName);
   assertThat(book.getSheetAt(0).getRow(1).getCell(9).getStringCellValue()).isEqualTo(processorName);
  }
  mvc.perform(get("/v1/reports/transactions?type=IMPORT&dateField=completed&from=2035-01-01&to=2035-01-01").with(role("ADMIN"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.items[0].transaction_code").value(code+"C"));
  mvc.perform(get("/v1/reports/transactions?type=IMPORT&dateField=created&from=2035-01-01&to=2035-01-01").with(role("ADMIN"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.items[0].status").value("DRAFT")).andExpect(jsonPath("$.data.items[0].processor_name").value(org.hamcrest.Matchers.nullValue()));
 }
 @Test void licenseCountsSeparateActiveReservedReleasedAndFindHolder() throws Exception {
  Long admin=jdbc.queryForObject("SELECT user_id FROM users WHERE email='admin@itam.example'",Long.class);
  Long holder=jdbc.queryForObject("SELECT user_id FROM users WHERE email='user01@itam.example'",Long.class);
  String code="M6-"+java.util.UUID.randomUUID();
  Long type=jdbc.queryForObject("INSERT INTO asset_types(code,name,category_id,is_active) SELECT ?,?,category_id,true FROM asset_categories WHERE code='LICENSE' RETURNING type_id",Long.class,code,code);
  Long asset=jdbc.queryForObject("INSERT INTO assets(asset_tag,name,type_id,status_id,created_by) VALUES(?,?,?,(SELECT status_id FROM asset_statuses WHERE code='IN_STOCK'),?) RETURNING asset_id",Long.class,code,code,type,admin);
  Long software=jdbc.queryForObject("INSERT INTO software_catalog(name,manufacturer,is_active) VALUES(?,'M6',true) RETURNING software_catalog_id",Long.class,code);
  jdbc.update("INSERT INTO asset_license_details(asset_id,software_catalog_id,license_assignment_type_id,license_term_type_id,seat_count) VALUES(?,?,(SELECT license_assignment_type_id FROM license_assignment_types WHERE code='PER_USER'),(SELECT license_term_type_id FROM license_term_types WHERE code='PERPETUAL'),10)",asset,software);
  for(String status:new String[]{"ACTIVE","RESERVED","RELEASED"}) jdbc.update("INSERT INTO license_allocations(license_asset_id,user_id,seat_count,status,created_by,released_at) VALUES(?,?,2,?,?,CASE WHEN ?='RELEASED' THEN CURRENT_TIMESTAMP ELSE NULL END)",asset,holder,status,admin,status);
  jdbc.update("INSERT INTO license_allocations(license_asset_id,user_id,seat_count,status,created_by) VALUES(?,?,3,'ACTIVE',?)",asset,admin,admin);
  mvc.perform(get("/v1/reports/allocations").param("type",code).param("userId",holder.toString()).with(role("ADMIN"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.items[0].total_seats").value(10)).andExpect(jsonPath("$.data.items[0].allocated_seats").value(5)).andExpect(jsonPath("$.data.items[0].reserved_seats").value(2)).andExpect(jsonPath("$.data.items[0].available_seats").value(3)).andExpect(jsonPath("$.data.items[0].employee_seats").value(2)).andExpect(jsonPath("$.data.summary[0].employee_seats").value(2));
  String adminName=jdbc.queryForObject("SELECT full_name FROM users WHERE user_id=?",String.class,admin);
  String holderName=jdbc.queryForObject("SELECT full_name FROM users WHERE user_id=?",String.class,holder);
  mvc.perform(get("/v1/reports/allocations").param("type",code).param("userId",holder.toString()).with(role("ADMIN"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].license_holders").value(holderName+" (2)"));
  byte[] seats=mvc.perform(get("/v1/reports/allocations/export").param("type",code).param("userId",holder.toString()).header("Accept-Language","en").with(role("ADMIN"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
  try(var book=org.apache.poi.ss.usermodel.WorkbookFactory.create(new java.io.ByteArrayInputStream(seats))) {
   var row=book.getSheetAt(0).getRow(1);
   assertThat(row.getCell(10).getStringCellValue()).isEqualTo(holderName+" (2)");
   assertThat(row.getCell(11).getNumericCellValue()).isEqualTo(2);
   assertThat(row.getCell(15).getNumericCellValue()).isEqualTo(5);
  }
  mvc.perform(get("/v1/reports/allocations").param("type",code).param("userId",admin.toString()).with(role("ADMIN"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].employee_seats").value(3)).andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.summary[0].employee_seats").value(3)).andExpect(jsonPath("$.data.items[0].license_holders").value(adminName+" (3)"));
  byte[] other=mvc.perform(get("/v1/reports/allocations/export").param("type",code).param("userId",admin.toString()).with(role("ADMIN"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
  try(var book=org.apache.poi.ss.usermodel.WorkbookFactory.create(new java.io.ByteArrayInputStream(other))) {
   assertThat(book.getSheetAt(0).getLastRowNum()).isEqualTo(1);
   assertThat(book.getSheetAt(0).getRow(1).getCell(10).getStringCellValue()).isEqualTo(adminName+" (3)");
   assertThat(book.getSheetAt(0).getRow(1).getCell(11).getNumericCellValue()).isEqualTo(3);
  }
 }

 @Test void purReportGrantDoesNotGrantOriginalWorkflowAccess() throws Exception {
  Long id=jdbc.queryForObject("SELECT user_id FROM users WHERE email='pur01@itam.example'",Long.class);
  mvc.perform(put("/v1/reports/permissions/{id}/TRANSACTIONS",id).with(role("ADMIN")).contentType("application/json").content("{\"view\":true,\"export\":true}")).andExpect(status().isOk());
  mvc.perform(get("/v1/reports/transactions").with(role("PUR_STAFF"))).andExpect(status().isOk());
  mvc.perform(get("/v1/handovers").with(role("PUR_STAFF"))).andExpect(status().isForbidden());
  mvc.perform(put("/v1/reports/permissions/{id}/ASSETS",id).with(role("ADMIN")).contentType("application/json").content("{\"view\":false,\"export\":true}")).andExpect(status().isBadRequest());
 }
}
