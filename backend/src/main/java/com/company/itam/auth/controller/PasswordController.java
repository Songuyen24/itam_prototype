package com.company.itam.auth.controller;

import com.company.itam.common.response.ApiResponse;
import com.company.itam.common.util.MessageHelper;
import com.company.itam.user.service.IdentityService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
public class PasswordController {
    private final IdentityService identities;
    private final MessageHelper messages;
    public PasswordController(IdentityService identities,MessageHelper messages) { this.identities=identities; this.messages=messages; }
    @PostMapping("/v1/auth/password")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> change(@Valid @RequestBody IdentityService.PasswordChange request) {
        identities.changePassword(request); return ApiResponse.success(messages.getMessage("PASSWORD_UPDATED"),null);
    }
}
