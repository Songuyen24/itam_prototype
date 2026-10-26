package com.company.itam;

import com.company.itam.common.enums.Role;
import com.company.itam.user.service.IdentityService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class M9PaginationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired IdentityService identities;
    @Autowired jakarta.persistence.EntityManager em;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired com.company.itam.asset.service.AssetService assets;

    @Test void relationshipCandidatesFilterBeforePaging() throws Exception {
        String marker = "M9-" + UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("admin@itam.example", null, List.of(new SimpleGrantedAuthority("ADMIN"))));
        try {
            Long source = null;
            for (int i = 0; i < 4; i++) {
                var request = new com.company.itam.asset.dto.request.CreateHardwareAssetRequest();
                request.setCreationPurpose(com.company.itam.asset.dto.request.AssetCreationPurpose.BASELINE);
                request.setName(marker);
                request.setTypeId(jdbc.queryForObject("SELECT min(t.type_id) FROM asset_types t JOIN asset_categories c USING(category_id) WHERE c.code=? AND t.is_active", Long.class, i < 2 ? "DEVICE" : "COMPONENT"));
                request.setConditionId(jdbc.queryForObject("SELECT min(condition_id) FROM asset_conditions WHERE is_active",Long.class));
                var created = assets.createHardwareAsset(request);
                if (i == 0) source = created.getAssetId();
            }
            em.flush(); em.clear();
            mvc.perform(get("/v1/assets/" + source + "/relationship-candidates").with(user("admin@itam.example").authorities(new SimpleGrantedAuthority("ADMIN")))
                    .param("keyword", marker).param("size", "1").param("page", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[0].categoryCode").value("COMPONENT"));
            mvc.perform(get("/v1/assets/" + source + "/relationship-candidates").with(user("pur01@itam.example").authorities(new SimpleGrantedAuthority("PUR_STAFF"))))
                .andExpect(status().isForbidden());
        } finally { SecurityContextHolder.clearContext(); }
    }

    @Test void receivingReuseCandidatesReturnFullCountAndLastPage() throws Exception {
        String marker = "M9-reuse-" + UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("admin@itam.example", null, List.of(new SimpleGrantedAuthority("ADMIN"))));
        try {
            Long type = jdbc.queryForObject("SELECT min(t.type_id) FROM asset_types t JOIN asset_categories c USING(category_id) WHERE c.code='DEVICE' AND t.is_active", Long.class);
            Long condition = jdbc.queryForObject("SELECT min(condition_id) FROM asset_conditions WHERE is_active",Long.class);
            for (int i = 0; i < 21; i++) {
                var request = new com.company.itam.asset.dto.request.CreateHardwareAssetRequest();
                request.setCreationPurpose(com.company.itam.asset.dto.request.AssetCreationPurpose.BASELINE);
                request.setName(marker); request.setTypeId(type); request.setConditionId(condition);
                assets.createHardwareAsset(request);
            }
            em.flush();
            jdbc.update("UPDATE assets SET status_id=(SELECT status_id FROM asset_statuses WHERE code='PENDING_IMPORT') WHERE name=?", marker);
            em.clear();
            mvc.perform(get("/v1/import-drafts/candidates").with(user("admin@itam.example").authorities(new SimpleGrantedAuthority("ADMIN")))
                    .param("keyword", marker).param("page", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(21))
                .andExpect(jsonPath("$.data.totalPages").value(2)).andExpect(jsonPath("$.data.content.length()").value(1));
        } finally { SecurityContextHolder.clearContext(); }
    }

    @Test void accountPickerFiltersBeforePagingAndCountsTheWholeMatchingSet() throws Exception {
        String marker = "M9-" + UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("admin@itam.example", null, List.of(new SimpleGrantedAuthority("ADMIN"))));
        try {
            for (int i = 0; i < 5; i++) {
                var profile = identities.saveProfile(null, new IdentityService.Profile(marker + i, marker + i, null, null, null));
                if (i < 3) identities.createAccount(new IdentityService.CreateAccount(profile.getUserId(), marker + i, "M9-test-password", i == 0 ? Role.USER : Role.IT_STAFF, true));
            }
            em.flush(); em.clear();
            mvc.perform(get("/v1/users").with(user("admin@itam.example").authorities(new SimpleGrantedAuthority("ADMIN")))
                    .param("keyword", marker).param("withoutAccount", "true").param("size", "1").param("page", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.totalPages").value(2)).andExpect(jsonPath("$.data.content[0].employeeCode").value(marker + "4"));
            mvc.perform(get("/v1/accounts").with(user("admin@itam.example").authorities(new SimpleGrantedAuthority("ADMIN")))
                    .param("keyword", marker).param("reportEligible", "true").param("size", "1").param("page", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[0].employeeCode").value(marker + "2"));
            mvc.perform(get("/v1/accounts").with(user("pur01@itam.example").authorities(new SimpleGrantedAuthority("PUR_STAFF")))
                    .param("reportEligible", "true")).andExpect(status().isForbidden());
        } finally { SecurityContextHolder.clearContext(); }
    }
}
