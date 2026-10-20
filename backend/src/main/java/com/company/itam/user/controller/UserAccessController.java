package com.company.itam.user.controller;

import com.company.itam.audit.entity.AuditLogEntity;
import com.company.itam.audit.repository.AuditLogRepository;
import com.company.itam.common.enums.AccountStatus;
import com.company.itam.common.exception.AppException;
import com.company.itam.common.response.ApiResponse;
import com.company.itam.user.dto.response.UserResponse;
import com.company.itam.user.repository.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/v1/users")
@PreAuthorize("hasAuthority('ADMIN')")
public class UserAccessController {
    private final UserRepository users;
    private final AuditLogRepository audit;
    private final PasswordEncoder passwords;
    private final com.company.itam.common.util.MessageHelper messages;
    public UserAccessController(UserRepository users, AuditLogRepository audit, PasswordEncoder passwords, com.company.itam.common.util.MessageHelper messages) {
        this.users = users; this.audit = audit; this.passwords = passwords;
        this.messages = messages;
    }
    public record Access(@NotNull Boolean granted, @NotNull AccountStatus status,
                         @Size(min=8,max=128) String password) {}

    @PutMapping("/{id}/access")
    @Transactional
    public ApiResponse<UserResponse> update(@PathVariable Long id, @Valid @RequestBody Access request,
                                            Authentication authentication) {
        var target = users.findForAccessUpdate(id).orElseThrow(() ->
                new AppException(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND","RESOURCE_NOT_FOUND"));
        var actor = users.findByEmail(authentication.getName()).orElseThrow();
        if (target.getRole().getCode() != com.company.itam.common.enums.Role.USER)
            throw new AppException(HttpStatus.BAD_REQUEST,"ACCESS_USER_ONLY","ACCESS_USER_ONLY");
        var before = Map.<String,Object>of("granted",target.isAccessGranted(),"loginLocked",target.isLoginLocked());
        if (request.password() != null) target.setPasswordHash(passwords.encode(request.password()));
        if (request.granted() && target.getPasswordHash() == null)
            throw new AppException(HttpStatus.BAD_REQUEST,"ACCESS_PASSWORD_REQUIRED","ACCESS_PASSWORD_REQUIRED");
        target.setAccessGranted(request.granted());
        target.setLoginLocked(request.status() == AccountStatus.LOCKED);
        target.setAccessVersion(target.getAccessVersion()+1);
        var entry = new AuditLogEntity();
        entry.setEntityType("USER"); entry.setEntityId(id); entry.setActor(actor); entry.setAction("ACCESS_CHANGED");
        entry.setOldData(before);
        entry.setNewData(Map.of("granted",target.isAccessGranted(),"loginLocked",target.isLoginLocked()));
        audit.save(entry);
        return ApiResponse.success(messages.getMessage("ACCESS_UPDATED"),UserResponse.fromEntity(users.save(target)));
    }
}
