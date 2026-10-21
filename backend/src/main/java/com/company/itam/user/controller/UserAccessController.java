package com.company.itam.user.controller;

import com.company.itam.common.enums.AccountStatus;
import com.company.itam.common.response.ApiResponse;
import com.company.itam.common.util.MessageHelper;
import com.company.itam.user.dto.response.UserResponse;
import com.company.itam.user.service.IdentityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/users")
@PreAuthorize("hasAuthority('ADMIN')")
public class UserAccessController {
    private final IdentityService identities;
    private final MessageHelper messages;
    public UserAccessController(IdentityService identities,MessageHelper messages) { this.identities=identities; this.messages=messages; }
    public record Access(@NotNull Boolean granted,@NotNull AccountStatus status,@Size(min=8,max=128) String password,
                         @Size(max=255) String username) {}
    @PutMapping("/{id}/access")
    public ApiResponse<UserResponse> update(@PathVariable Long id,@Valid @RequestBody Access request) {
        return ApiResponse.success(messages.getMessage("ACCESS_UPDATED"),UserResponse.fromEntity(
            identities.legacyAccess(id,request.granted(),request.status(),request.password(),request.username())));
    }
}
