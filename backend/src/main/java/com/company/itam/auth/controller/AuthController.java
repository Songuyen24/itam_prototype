package com.company.itam.auth.controller;

import com.company.itam.auth.dto.request.LoginRequest;
import com.company.itam.auth.dto.response.CurrentUserResponse;
import com.company.itam.auth.dto.response.LoginResponse;
import com.company.itam.auth.service.AuthService;
import com.company.itam.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * POST /api/v1/auth/login — công khai. Đăng nhập bằng email + password,
     * trả JWT và thông tin người dùng.
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse body = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success("LOGIN_SUCCESS", body));
    }

    /**
     * GET /api/v1/auth/me — yêu cầu Bearer token. Trả thông tin user hiện tại.
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<CurrentUserResponse>> me() {
        CurrentUserResponse body = authService.getCurrentUser();
        return ResponseEntity.ok(ApiResponse.success("CURRENT_USER_SUCCESS", body));
    }

    /**
     * POST /api/v1/auth/logout — yêu cầu Bearer token. Prototype chỉ xóa context phía server,
     * client có trách nhiệm xóa token.
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout() {
        authService.logout();
        return ResponseEntity.ok(ApiResponse.success("LOGOUT_SUCCESS", null));
    }
}
