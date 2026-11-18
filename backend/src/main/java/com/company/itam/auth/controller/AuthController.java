package com.company.itam.auth.controller;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.company.itam.auth.dto.request.LoginRequest;
import com.company.itam.auth.dto.response.CurrentUserResponse;
import com.company.itam.auth.dto.response.LoginResponse;
import com.company.itam.auth.service.AuthService;
import com.company.itam.common.response.ApiResponse;

@RestController
@RequestMapping("/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse body = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success("LOGIN_SUCCESS", body));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<CurrentUserResponse>> me() {
        CurrentUserResponse body = authService.getCurrentUser();
        return ResponseEntity.ok(ApiResponse.success("CURRENT_USER_SUCCESS", body));
    }

    /** Clears server context only; the client must discard its token. */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout() {
        authService.logout();
        return ResponseEntity.ok(ApiResponse.success("LOGOUT_SUCCESS", null));
    }
}
