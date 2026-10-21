package com.company.itam.user.controller;

import com.company.itam.common.pagination.PageResponse;
import com.company.itam.common.response.ApiResponse;
import com.company.itam.common.util.MessageHelper;
import com.company.itam.user.dto.response.UserResponse;
import com.company.itam.user.repository.UserRepository;
import com.company.itam.user.service.IdentityService;
import jakarta.validation.Valid;
import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/accounts")
@PreAuthorize("hasAuthority('ADMIN')")
public class AccountController {
    private final UserRepository users;
    private final IdentityService identities;
    private final MessageHelper messages;
    public AccountController(UserRepository users,IdentityService identities,MessageHelper messages) {
        this.users=users; this.identities=identities; this.messages=messages;
    }
    @GetMapping
    public ApiResponse<PageResponse<UserResponse>> list(@RequestParam(defaultValue="") String keyword,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return ApiResponse.success(messages.getMessage("ACCOUNT_READ"),PageResponse.of(users.findAccounts(keyword.trim(),
            PageRequest.of(Math.max(page,0),Math.min(Math.max(size,1),100),Sort.by("userId"))).map(UserResponse::fromEntity)));
    }
    @PostMapping
    public ApiResponse<UserResponse> create(@Valid @RequestBody IdentityService.CreateAccount request) {
        return ApiResponse.success(messages.getMessage("ACCOUNT_SAVED"),UserResponse.fromEntity(identities.createAccount(request)));
    }
    @PutMapping("/{id}")
    public ApiResponse<UserResponse> update(@PathVariable Long id,@Valid @RequestBody IdentityService.UpdateAccount request) {
        return ApiResponse.success(messages.getMessage("ACCOUNT_SAVED"),UserResponse.fromEntity(identities.updateAccount(id,request)));
    }
    @PutMapping("/{id}/password")
    public ApiResponse<Void> password(@PathVariable Long id,@Valid @RequestBody IdentityService.PasswordReset request) {
        identities.resetPassword(id,request); return ApiResponse.success(messages.getMessage("PASSWORD_UPDATED"),null);
    }
}
