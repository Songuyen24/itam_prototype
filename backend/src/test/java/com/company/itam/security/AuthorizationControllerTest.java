package com.company.itam.security;

import com.company.itam.auth.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthorizationControllerTest {

    private static final List<String> CATALOGS = List.of(
            "departments", "locations", "suppliers", "asset-categories", "asset-types",
            "asset-statuses", "asset-conditions", "models", "software-catalog",
            "license-assignment-types", "license-term-types");
    private static final String PASSWORD_HASH =
            "$2a$12$ap67CmIDlhWC9EQKLs1X4uUi7SRhvFd6C42Umidu2dgtkwv6IPVLO";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private MessageSource messageSource;

    private final Map<String, Long> users = new HashMap<>();
    private final Map<String, String> emails = new HashMap<>();
    private String fixturePrefix;
    private long typeId;
    private long ownAssetId;
    private long otherAssetId;
    private long warehouseAssetId;

    @BeforeEach
    void createFixtures() {
        fixturePrefix = "AUTH-" + UUID.randomUUID().toString().substring(0, 12);
        for (String role : List.of("ADMIN", "IT_STAFF", "PUR_STAFF", "USER")) {
            createUser(role, role);
        }
        createUser("OTHER_USER", "USER");
        typeId = jdbc.queryForObject(
                "select type_id from asset_types where code = 'LAPTOP'", Long.class);
        ownAssetId = createAsset("OWN", users.get("USER"));
        otherAssetId = createAsset("OTHER", users.get("OTHER_USER"));
        warehouseAssetId = createAsset("WAREHOUSE", null);
    }

    @Test
    void unauthenticatedRequests_areRejectedBeforeProtectedControllers() throws Exception {
        for (String path : List.of("/v1/assets", "/v1/assets/" + ownAssetId,
                "/v1/users", "/v1/users/me/assets", "/v1/asset-imports/template")) {
            mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
        for (String catalog : CATALOGS) {
            mockMvc.perform(get("/v1/" + catalog)).andExpect(status().isUnauthorized());
            mockMvc.perform(post("/v1/" + catalog)
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "IT_STAFF"})
    void managers_canReadWarehouseUsersAndCatalogs(String role) throws Exception {
        String token = login(role);
        api(HttpMethod.GET, "/v1/assets?keyword=" + fixturePrefix, token, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(3));
        for (long id : List.of(ownAssetId, otherAssetId, warehouseAssetId)) {
            api(HttpMethod.GET, "/v1/assets/" + id, token, null)
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.assetId").value(id));
        }
        api(HttpMethod.GET, "/v1/users", token, null).andExpect(status().isOk());
        api(HttpMethod.GET, "/v1/users/" + users.get("USER"), token, null)
                .andExpect(status().isOk());
        for (String catalog : CATALOGS) {
            api(HttpMethod.GET, "/v1/" + catalog, token, null).andExpect(status().isOk());
        }
        api(HttpMethod.GET, "/v1/asset-imports/template", token, null).andExpect(status().isOk());
        api(HttpMethod.POST, "/v1/assets/validate-uniqueness", token,
                Map.of("assetTag", fixturePrefix + "-OWN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assetTagAvailable").value(false));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "IT_STAFF"})
    void managers_canManageDepartmentLocationSupplierContacts(String role) throws Exception {
        String token = login(role);
        for (String catalog : List.of("departments", "locations", "suppliers")) {
            String idField = switch (catalog) {
                case "departments" -> "departmentId";
                case "locations" -> "locationId";
                default -> "supplierId";
            };
            Map<String, Object> payload = Map.of("code", fixturePrefix, "name", "Authorization catalog");
            JsonNode created = responseData(api(HttpMethod.POST, "/v1/" + catalog, token, payload)
                    .andExpect(status().isCreated()));
            long id = created.path(idField).asLong();
            assertThat(id).isPositive();
            api(HttpMethod.GET, "/v1/" + catalog + "/" + id, token, null)
                    .andExpect(status().isOk());
            api(HttpMethod.PUT, "/v1/" + catalog + "/" + id, token, payload)
                    .andExpect(status().isOk());
            api(HttpMethod.PATCH, "/v1/" + catalog + "/" + id + "/active?active=false", token, null)
                    .andExpect(status().isOk());
            if (catalog.equals("suppliers")) {
                verifyManagerContacts(token, id);
            }
            api(HttpMethod.DELETE, "/v1/" + catalog + "/" + id, token, null)
                    .andExpect(status().isOk());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"PUR_STAFF", "USER"})
    void restrictedRoles_cannotReadWarehouseUsersOrProbeAssetUniqueness(String role) throws Exception {
        String token = login(role);
        for (String path : List.of("/v1/assets", "/v1/assets?assignedTo=" + users.get(role),
                "/v1/assets?userId=" + users.get("ADMIN") + "&role=ADMIN",
                "/v1/users", "/v1/users/" + users.get("ADMIN"))) {
            api(HttpMethod.GET, path, token, null).andExpect(status().isForbidden());
        }
        Map<String, Object> asset = Map.of("assetTag", fixturePrefix + "-FORBIDDEN",
                "name", "Forbidden", "typeId", typeId);
        api(HttpMethod.POST, "/v1/assets", token, asset).andExpect(status().isForbidden());
        api(HttpMethod.PUT, "/v1/assets/" + ownAssetId, token, asset)
                .andExpect(status().isForbidden());
        api(HttpMethod.POST, "/v1/assets/validate-uniqueness", token,
                Map.of("assetTag", fixturePrefix + "-OTHER", "excludeAssetId", otherAssetId))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PUR_STAFF", "USER"})
    void restrictedRoles_cannotReadOrMutateAnyCatalog(String role) throws Exception {
        String token = login(role);
        for (String catalog : CATALOGS) {
            String path = "/v1/" + catalog;
            api(HttpMethod.GET, path, token, null).andExpect(status().isForbidden());
            api(HttpMethod.GET, path + "/1", token, null).andExpect(status().isForbidden());
            api(HttpMethod.POST, path, token, Map.of()).andExpect(status().isForbidden());
            api(HttpMethod.PUT, path + "/1", token, Map.of()).andExpect(status().isForbidden());
            api(HttpMethod.DELETE, path + "/1", token, null).andExpect(status().isForbidden());
            api(HttpMethod.PATCH, path + "/1/active?active=false", token, null)
                    .andExpect(status().isForbidden());
        }
        api(HttpMethod.GET, "/v1/suppliers/1/contacts", token, null)
                .andExpect(status().isForbidden());
        api(HttpMethod.POST, "/v1/suppliers/1/contacts", token, Map.of("name", "Contact"))
                .andExpect(status().isForbidden());
        for (String path : List.of("/v1/supplier-contacts/1", "/v1/suppliers/1/contacts/1")) {
            api(HttpMethod.PUT, path, token, Map.of("name", "Contact"))
                    .andExpect(status().isForbidden());
            api(HttpMethod.DELETE, path, token, null).andExpect(status().isForbidden());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"PUR_STAFF", "USER"})
    void restrictedRoles_cannotAccessImportEndpoints(String role) throws Exception {
        String token = login(role);
        for (String suffix : List.of("", "/template", "/1", "/1/errors")) {
            api(HttpMethod.GET, "/v1/asset-imports" + suffix, token, null)
                    .andExpect(status().isForbidden());
        }
        api(HttpMethod.POST, "/v1/asset-imports/preview", token, null)
                .andExpect(status().isForbidden());
        api(HttpMethod.POST, "/v1/asset-imports/confirm", token, Map.of("batchId", 1))
                .andExpect(status().isForbidden());
    }

    @Test
    void user_readsOwnAssetsAndCannotWidenScopeWithIdsQueriesOrRole() throws Exception {
        String token = login("USER");
        api(HttpMethod.GET, "/v1/users/me/assets", token, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].assetId").value(ownAssetId));
        api(HttpMethod.GET, "/v1/users/me/assets?assignedTo=" + users.get("OTHER_USER")
                        + "&assignedToUserId=" + users.get("OTHER_USER")
                        + "&userId=" + users.get("ADMIN") + "&role=ADMIN", token, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].assetId").value(ownAssetId));
        api(HttpMethod.GET, "/v1/assets/" + ownAssetId, token, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assignedToUserId").value(users.get("USER")));
        for (long id : List.of(otherAssetId, warehouseAssetId, Long.MAX_VALUE)) {
            api(HttpMethod.GET, "/v1/assets/" + id + "?userId=" + users.get("OTHER_USER")
                    + "&assignedTo=" + users.get("USER") + "&role=ADMIN", token, null)
                    .andExpect(status().isNotFound());
        }
        api(HttpMethod.GET, "/v1/users/me/assets?keyword=" + fixturePrefix + "-OTHER", token, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void myAssets_paginatesBeyondTwoHundredWithoutLeakingOtherAssignments() throws Exception {
        jdbc.update("""
                insert into assets(asset_tag, name, type_id, status_id, assigned_to, created_by)
                select ? || '-' || n, 'Authorization paged asset', ?, s.status_id, ?, ?
                from generate_series(1, 205) n cross join asset_statuses s where s.code = 'IN_USE'
                """, fixturePrefix + "-PAGE", typeId, users.get("USER"), users.get("ADMIN"));
        String token = login("USER");
        api(HttpMethod.GET, "/v1/users/me/assets?size=20&page=10&sort=assetId,asc", token, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(206))
                .andExpect(jsonPath("$.data.totalPages").value(11))
                .andExpect(jsonPath("$.data.pageNumber").value(10))
                .andExpect(jsonPath("$.data.content", hasSize(6)))
                .andExpect(jsonPath("$.data.content[*].assignedToUserId", everyItem(is(users.get("USER").intValue()))));
    }

    @Test
    void switchingTokens_changesDataAndPermissionsOnTheNextRequest() throws Exception {
        String adminToken = login("ADMIN");
        String userToken = login("USER");
        String otherToken = login("OTHER_USER");
        String purchasingToken = login("PUR_STAFF");

        api(HttpMethod.GET, "/v1/assets?keyword=" + fixturePrefix, adminToken, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(3));
        api(HttpMethod.GET, "/v1/assets", userToken, null).andExpect(status().isForbidden());
        api(HttpMethod.GET, "/v1/users/me/assets", userToken, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].assetId").value(ownAssetId));
        api(HttpMethod.GET, "/v1/users/me/assets", otherToken, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].assetId").value(otherAssetId));
        api(HttpMethod.GET, "/v1/assets/" + ownAssetId, otherToken, null)
                .andExpect(status().isNotFound());
        api(HttpMethod.GET, "/v1/users/me/assets", purchasingToken, null)
                .andExpect(status().isForbidden());
        api(HttpMethod.GET, "/v1/assets/" + ownAssetId, purchasingToken, null)
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/v1/users/me/assets")).andExpect(status().isUnauthorized());
    }

    @Test
    void authorityComesFromAuthenticatedAccount_evenWhenTokenRoleClaimsAdmin() throws Exception {
        String token = jwtTokenProvider.generateToken(emails.get("USER"), "ADMIN");
        api(HttpMethod.GET, "/v1/assets", token, null).andExpect(status().isForbidden());
        api(HttpMethod.GET, "/v1/users/me/assets", token, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].assetId").value(ownAssetId));
    }

    @ParameterizedTest
    @ValueSource(strings = {"en", "vi"})
    void securityErrors_useLanguageFromRequestBeforeMvc(String language) throws Exception {
        String token = login("USER");
        Locale locale = Locale.forLanguageTag(language);
        String unauthorized = messageSource.getMessage("UNAUTHORIZED", null, locale);
        String forbidden = messageSource.getMessage("ACCESS_DENIED", null, locale);

        mockMvc.perform(get("/v1/assets").header("Accept-Language", language))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value(unauthorized));
        mockMvc.perform(get("/v1/assets").header("Accept-Language", language)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.message").value(forbidden));

        String otherLanguage = language.equals("vi") ? "en" : "vi";
        mockMvc.perform(get("/v1/assets").param("lang", language)
                        .header("Accept-Language", otherLanguage))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(unauthorized));
        mockMvc.perform(get("/v1/assets").param("lang", language)
                        .header("Accept-Language", otherLanguage)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(forbidden));
    }

    private void verifyManagerContacts(String token, long supplierId) throws Exception {
        String nestedPath = "/v1/suppliers/" + supplierId + "/contacts";
        Map<String, Object> payload = Map.of("name", "Authorization contact");
        long contactId = responseData(api(HttpMethod.POST, nestedPath, token, payload)
                .andExpect(status().isCreated())).path("contactId").asLong();
        assertThat(contactId).isPositive();
        api(HttpMethod.GET, nestedPath, token, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data", hasSize(1)));
        api(HttpMethod.PUT, nestedPath + "/" + contactId, token, payload).andExpect(status().isOk());
        api(HttpMethod.PUT, "/v1/supplier-contacts/" + contactId, token, payload).andExpect(status().isOk());
        api(HttpMethod.DELETE, "/v1/supplier-contacts/" + contactId, token, null).andExpect(status().isOk());
        long secondId = responseData(api(HttpMethod.POST, nestedPath, token, payload)
                .andExpect(status().isCreated())).path("contactId").asLong();
        api(HttpMethod.DELETE, nestedPath + "/" + secondId, token, null).andExpect(status().isOk());
    }

    private void createUser(String key, String role) {
        String email = fixturePrefix.toLowerCase() + "-" + key.toLowerCase() + "@example.test";
        Long id = jdbc.queryForObject("""
                insert into users(email, full_name, role_id, password_hash, account_status)
                select ?, ?, role_id, ?, 'ACTIVE' from roles where code = ? returning user_id
                """, Long.class, email, "Authorization " + key, PASSWORD_HASH, role);
        users.put(key, id);
        emails.put(key, email);
    }

    private long createAsset(String suffix, Long assignedTo) {
        return jdbc.queryForObject("""
                insert into assets(asset_tag, name, type_id, status_id, assigned_to, created_by)
                select ?, ?, ?, status_id, ?, ? from asset_statuses where code = ? returning asset_id
                """, Long.class, fixturePrefix + "-" + suffix, fixturePrefix + " " + suffix,
                typeId, assignedTo, users.get("ADMIN"), assignedTo == null ? "IN_STOCK" : "IN_USE");
    }

    private String login(String key) throws Exception {
        JsonNode data = responseData(mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", emails.get(key), "password", "Password@123", "role", "ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.role").value(key.equals("OTHER_USER") ? "USER" : key)));
        return data.path("token").asText();
    }

    private ResultActions api(HttpMethod method, String path, String token, Object body) throws Exception {
        var builder = request(method, path).header("Authorization", "Bearer " + token);
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
        }
        return mockMvc.perform(builder);
    }

    private JsonNode responseData(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString()).path("data");
    }
}
