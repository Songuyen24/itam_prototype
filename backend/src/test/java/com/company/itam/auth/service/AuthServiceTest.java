package com.company.itam.auth.service;

import com.company.itam.auth.dto.request.LoginRequest;
import com.company.itam.auth.dto.response.CurrentUserResponse;
import com.company.itam.auth.dto.response.LoginResponse;
import com.company.itam.auth.security.JwtTokenProvider;
import com.company.itam.common.enums.AccountStatus;
import com.company.itam.common.enums.Role;
import com.company.itam.common.exception.AppException;
import com.company.itam.role.entity.RoleEntity;
import com.company.itam.user.entity.UserEntity;
import com.company.itam.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private AuthService authService;

    private UserEntity adminUser;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();

        adminUser = new UserEntity();
        adminUser.setUserId(1L);
        adminUser.setEmail("admin@itam.example");
        adminUser.setFullName("Quản trị viên hệ thống");
        adminUser.setPasswordHash("$2a$12$ap67CmIDlhWC9EQKLs1X4uUi7SRhvFd6C42Umidu2dgtkwv6IPVLO");
        adminUser.setAccountStatus(AccountStatus.ACTIVE);
        RoleEntity adminRole = new RoleEntity(Role.ADMIN, "Administrator", true);
        adminUser.setRole(adminRole);
    }

    @Test
    void login_success_returnsTokenAndUser() {
        LoginRequest req = new LoginRequest();
        req.setEmail("admin@itam.example");
        req.setPassword("Password@123");

        when(userRepository.findByEmail("admin@itam.example")).thenReturn(Optional.of(adminUser));
        when(passwordEncoder.matches("Password@123", adminUser.getPasswordHash())).thenReturn(true);
        when(jwtTokenProvider.generateToken(adminUser.getEmail(), "ADMIN")).thenReturn("jwt.token.value");

        LoginResponse res = authService.login(req);

        assertNotNull(res);
        assertEquals("Bearer", res.getType());
        assertEquals("jwt.token.value", res.getToken());
        assertNotNull(res.getUser());
        assertEquals("admin@itam.example", res.getUser().getEmail());
        assertEquals("ADMIN", res.getUser().getRole());
    }

    @Test
    void login_emailLowercase_isNormalized() {
        LoginRequest req = new LoginRequest();
        req.setEmail("  Admin@ITAM.example  ");
        req.setPassword("Password@123");

        when(userRepository.findByEmail("admin@itam.example")).thenReturn(Optional.of(adminUser));
        when(passwordEncoder.matches("Password@123", adminUser.getPasswordHash())).thenReturn(true);
        when(jwtTokenProvider.generateToken(any(), any())).thenReturn("tkn");

        LoginResponse res = authService.login(req);
        assertEquals("admin@itam.example", res.getUser().getEmail());
    }

    @Test
    void login_userNotFound_throws401() {
        LoginRequest req = new LoginRequest();
        req.setEmail("ghost@example.com");
        req.setPassword("anything");

        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> authService.login(req));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
        assertEquals("LOGIN_INVALID_CREDENTIALS", ex.getCode());
    }

    @Test
    void login_wrongPassword_throws401() {
        LoginRequest req = new LoginRequest();
        req.setEmail("admin@itam.example");
        req.setPassword("WrongPass");

        when(userRepository.findByEmail("admin@itam.example")).thenReturn(Optional.of(adminUser));
        when(passwordEncoder.matches("WrongPass", adminUser.getPasswordHash())).thenReturn(false);

        AppException ex = assertThrows(AppException.class, () -> authService.login(req));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
        assertEquals("LOGIN_INVALID_CREDENTIALS", ex.getCode());
    }

    @Test
    void login_lockedAccount_throws401WithLockedCode() {
        adminUser.setAccountStatus(AccountStatus.LOCKED);
        LoginRequest req = new LoginRequest();
        req.setEmail("admin@itam.example");
        req.setPassword("Password@123");

        when(userRepository.findByEmail("admin@itam.example")).thenReturn(Optional.of(adminUser));
        when(passwordEncoder.matches("Password@123", adminUser.getPasswordHash())).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> authService.login(req));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
        assertEquals("LOGIN_ACCOUNT_LOCKED", ex.getCode());
    }

    @Test
    void getCurrentUser_noAuth_throws401() {
        AppException ex = assertThrows(AppException.class, () -> authService.getCurrentUser());
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
        assertEquals("UNAUTHORIZED", ex.getCode());
    }

    @Test
    void getCurrentUser_authenticated_returnsCurrentUser() {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "admin@itam.example",
                null,
                List.of(new SimpleGrantedAuthority("ADMIN"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        when(userRepository.findByEmail("admin@itam.example")).thenReturn(Optional.of(adminUser));

        CurrentUserResponse res = authService.getCurrentUser();
        assertNotNull(res);
        assertEquals(1L, res.getId());
        assertEquals("admin@itam.example", res.getEmail());
        assertEquals("ADMIN", res.getRoleCode());
    }
}
