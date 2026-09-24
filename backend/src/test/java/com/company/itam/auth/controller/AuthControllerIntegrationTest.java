package com.company.itam.auth.controller;

import com.company.itam.auth.security.JwtTokenProvider;
import com.company.itam.common.enums.AccountStatus;
import com.company.itam.common.enums.Role;
import com.company.itam.common.exception.AppException;
import com.company.itam.role.entity.RoleEntity;
import com.company.itam.user.entity.UserEntity;
import com.company.itam.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private UserRepository userRepository;

    private UserEntity adminUser;

    @BeforeEach
    void setUp() {
        adminUser = new UserEntity();
        adminUser.setUserId(1L);
        adminUser.setEmail("admin@itam.example");
        adminUser.setFullName("Admin Test");
        adminUser.setPasswordHash("$2a$12$ap67CmIDlhWC9EQKLs1X4uUi7SRhvFd6C42Umidu2dgtkwv6IPVLO");
        adminUser.setAccountStatus(AccountStatus.ACTIVE);
        adminUser.setRole(new RoleEntity(Role.ADMIN, "Administrator", true));
    }

    @Test
    void login_returnsToken() throws Exception {
        when(userRepository.findByEmail("admin@itam.example")).thenReturn(Optional.of(adminUser));

        String body = """
                {
                  "email": "admin@itam.example",
                  "password": "Password@123"
                }
                """;

        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.type").value("Bearer"))
                .andExpect(jsonPath("$.data.user.email").value("admin@itam.example"))
                .andExpect(jsonPath("$.data.user.role").value("ADMIN"));
    }

    @Test
    void login_invalidPassword_returns401() throws Exception {
        when(userRepository.findByEmail("admin@itam.example")).thenReturn(Optional.of(adminUser));

        String body = """
                {
                  "email": "admin@itam.example",
                  "password": "WrongPassword"
                }
                """;

        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("LOGIN_INVALID_CREDENTIALS"));
    }

    @Test
    void me_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void me_withValidToken_returnsUser() throws Exception {
        when(userRepository.findByEmail("admin@itam.example")).thenReturn(Optional.of(adminUser));
        when(userRepository.findByEmailWithDetails("admin@itam.example")).thenReturn(Optional.of(adminUser));

        String token = jwtTokenProvider.generateToken("admin@itam.example", "ADMIN");
        mockMvc.perform(get("/v1/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("admin@itam.example"))
                .andExpect(jsonPath("$.data.roleCode").value("ADMIN"));
    }

    @Test
    void me_withInvalidToken_returns401() throws Exception {
        mockMvc.perform(get("/v1/auth/me")
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void login_validationError_returns400() throws Exception {
        String body = """
                {
                  "email": "not-an-email",
                  "password": ""
                }
                """;

        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors", hasSize(greaterThanOrEqualTo(1))));
    }
}
