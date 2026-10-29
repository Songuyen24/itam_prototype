package com.company.itam;

import com.company.itam.catalog.service.CatalogImportService;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.io.ByteArrayOutputStream;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CatalogImportIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired CatalogImportService imports;
    private final String marker="CI-"+UUID.randomUUID();
    private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor as(String role){
        return user(role.equals("ADMIN")?"admin@itam.example":role.equals("IT_STAFF")?"it01@itam.example":"pur01@itam.example")
            .authorities(new SimpleGrantedAuthority(role));
    }
    private MockMultipartFile file(String kind,String[][] values) throws Exception {
        try(var book=new XSSFWorkbook(new java.io.ByteArrayInputStream(imports.template(kind)));var out=new ByteArrayOutputStream()){
            var sheet=book.getSheet("IMPORT");
            for(int index=0;index<values.length;index++){var row=sheet.createRow(index+1);for(int col=0;col<values[index].length;col++)if(values[index][col]!=null)row.createCell(col,CellType.STRING).setCellValue(values[index][col]);}
            book.write(out);return new MockMultipartFile("file","catalog.xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",out.toByteArray());
        }
    }
    @Test void departmentPreviewConfirmSkipsDuplicatesAndPurCannotImport() throws Exception {
        var file=file("departments",new String[][]{{marker+"-01","First"},{marker+"-02","Second"},{marker+"-01","Repeated"},{"","Missing"}});
        try {
            mvc.perform(multipart("/v1/catalog-imports/departments/preview").file(file).with(as("PUR_STAFF"))).andExpect(status().isForbidden());
            mvc.perform(multipart("/v1/catalog-imports/departments/preview").file(file).with(as("IT_STAFF")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.valid").value(2)).andExpect(jsonPath("$.data.invalid").value(2))
                .andExpect(jsonPath("$.data.columns.length()").value(2)).andExpect(jsonPath("$.data.rows[0].values[0]").value(marker+"-01"));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM departments WHERE code LIKE ?",Integer.class,marker+"%")).isZero();
            mvc.perform(multipart("/v1/catalog-imports/departments/confirm").file(file).with(as("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.created").value(2));
            mvc.perform(multipart("/v1/catalog-imports/departments/confirm").file(file).with(as("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.created").value(0));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM departments WHERE code LIKE ?",Integer.class,marker+"%")).isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE entity_type='MASTER_DATA' AND action='CATALOG_IMPORTED' AND new_data->>'key' LIKE ?",Integer.class,marker+"%")).isEqualTo(2);
        } finally { jdbc.update("DELETE FROM departments WHERE code LIKE ?",marker+"%"); }
    }
    @Test void typeUsesCategoryCodeAndRejectsFormulaAndUnsupportedKind() throws Exception {
        String category=jdbc.queryForObject("SELECT code FROM asset_categories WHERE is_active=true LIMIT 1",String.class);
        var file=file("types",new String[][]{{marker+"-TYPE","New type",category},{marker+"-BAD","Bad ref","NO_CATEGORY"}});
        try {
            mvc.perform(multipart("/v1/catalog-imports/types/preview").file(file).with(as("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.valid").value(1)).andExpect(jsonPath("$.data.invalid").value(1));
            mvc.perform(multipart("/v1/catalog-imports/types/confirm").file(file).with(as("IT_STAFF")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.created").value(1));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM asset_types WHERE lower(code)=lower(?)",Integer.class,marker+"-TYPE")).isEqualTo(1);
            try(var book=new XSSFWorkbook(file.getInputStream());var out=new ByteArrayOutputStream()){
                book.getSheet("IMPORT").getRow(1).getCell(0).setCellFormula("1+1");book.write(out);
                mvc.perform(multipart("/v1/catalog-imports/types/preview").file(new MockMultipartFile("file","formula.xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",out.toByteArray())).with(as("ADMIN")))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.rows[0].status").value("INVALID"));
            }
            mvc.perform(get("/v1/catalog-imports/categories/template").with(as("ADMIN"))).andExpect(status().isBadRequest());
        } finally { jdbc.update("DELETE FROM asset_types WHERE code LIKE ?",marker+"%"); }
    }
    @Test void createsLocationSupplierModelAndSoftwareWithoutChangingExistingRows() throws Exception {
        String typeCode=jdbc.queryForObject("SELECT code FROM asset_types WHERE is_active=true LIMIT 1",String.class);
        try {
            var fixtures=Map.of(
                "locations",new String[]{marker+"-LOC","New location","Street"},
                "suppliers",new String[]{marker+"-SUP","New supplier",null,"Street","123","new@example.test"},
                "models",new String[]{marker+"-MODEL","Brand",typeCode,"CPU","16GB","1TB","GPU"},
                "software",new String[]{marker+"-SOFT","Manufacturer","v1"});
            for(var entry:fixtures.entrySet()){
                var file=file(entry.getKey(),new String[][]{entry.getValue()});
                mvc.perform(multipart("/v1/catalog-imports/"+entry.getKey()+"/confirm").file(file).with(as("ADMIN")))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.created").value(1));
                mvc.perform(multipart("/v1/catalog-imports/"+entry.getKey()+"/confirm").file(file).with(as("ADMIN")))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.created").value(0));
            }
            assertThat(jdbc.queryForObject("SELECT count(*) FROM locations WHERE code=?",Integer.class,marker+"-LOC")).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM suppliers WHERE code=?",Integer.class,marker+"-SUP")).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM models WHERE name=?",Integer.class,marker+"-MODEL")).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM software_catalog WHERE name=?",Integer.class,marker+"-SOFT")).isEqualTo(1);
        } finally {
            jdbc.update("DELETE FROM models WHERE name=?",marker+"-MODEL");
            jdbc.update("DELETE FROM software_catalog WHERE name=?",marker+"-SOFT");
            jdbc.update("DELETE FROM suppliers WHERE code=?",marker+"-SUP");
            jdbc.update("DELETE FROM locations WHERE code=?",marker+"-LOC");
        }
    }
}
