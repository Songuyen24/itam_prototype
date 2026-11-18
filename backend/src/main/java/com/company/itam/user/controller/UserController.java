package com.company.itam.user.controller;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.company.itam.asset.dto.response.AssetResponse;
import com.company.itam.asset.service.AssetService;
import com.company.itam.common.enums.AccountStatus;
import com.company.itam.common.exception.AppException;
import com.company.itam.common.pagination.PageResponse;
import com.company.itam.common.response.ApiResponse;
import com.company.itam.common.util.MessageHelper;
import com.company.itam.user.dto.response.UserResponse;
import com.company.itam.user.entity.UserEntity;
import com.company.itam.user.repository.UserRepository;
import com.company.itam.user.service.IdentityService;
import com.company.itam.workflow.handover.service.HandoverHolderService;

@RestController
@RequestMapping("/v1/users")
public class UserController {

    private final UserRepository userRepository;
    private final AssetService assetService;

    private final IdentityService identities;
    private final HandoverHolderService holders;
    private final MessageHelper messages;

    public UserController(
            UserRepository userRepository,
            AssetService assetService,
            IdentityService identities,
            HandoverHolderService holders,
            MessageHelper messages) {
        this.identities = identities;
        this.holders = holders;
        this.messages = messages;
        this.userRepository = userRepository;
        this.assetService = assetService;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'IT_STAFF', 'PUR_STAFF')")
    public ResponseEntity<ApiResponse<PageResponse<UserResponse>>> list(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(defaultValue = "false") boolean withoutAccount) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by("userId"));
        Page<UserEntity> result = withoutAccount
                ? userRepository.findProfilesWithoutAccount(keyword == null ? "" : keyword.trim(), pageable)
                : (keyword != null && !keyword.isBlank())
                        ? userRepository.findByFullNameContainingIgnoreCaseWithDetails(keyword.trim(), pageable)
                        : userRepository.findAllWithDetails(pageable);

        var counts = holders
                .summaries(
                        result.getContent().stream().map(UserEntity::getUserId).toList())
                .stream()
                .collect(Collectors.toMap(HandoverHolderService.Holder::id, Function.identity()));
        PageResponse<UserResponse> mapped = new PageResponse<>(
                result.getContent().stream()
                        .map(u -> {
                            var dto = UserResponse.fromEntity(u);
                            var count = counts.get(u.getUserId());
                            if (count != null)
                                dto.setHoldingCounts(count.deviceCount(), count.componentCount(), count.licenseSeats());
                            return dto;
                        })
                        .toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.isLast());
        return ResponseEntity.ok(ApiResponse.success("USER_LIST_SUCCESS", mapped));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'IT_STAFF', 'PUR_STAFF')")
    public ResponseEntity<ApiResponse<UserResponse>> getById(@PathVariable("id") Long id) {
        UserEntity user = userRepository
                .findByIdWithDetails(id)
                .orElseThrow(() ->
                        new AppException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "User not found with id: " + id));
        var dto = UserResponse.fromEntity(user);
        holders.summaries(List.of(id)).stream()
                .findFirst()
                .ifPresent(c -> dto.setHoldingCounts(c.deviceCount(), c.componentCount(), c.licenseSeats()));
        return ResponseEntity.ok(ApiResponse.success("USER_GET_SUCCESS", dto));
    }

    /** The current-user scope applies to every role, including ADMIN and IT_STAFF. */
    @GetMapping("/me/assets")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'IT_STAFF', 'USER')")
    public ResponseEntity<ApiResponse<PageResponse<AssetResponse>>> myAssets(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 100),
                Sort.by("assetId").descending());
        return ResponseEntity.ok(ApiResponse.success("MY_ASSETS_SUCCESS", assetService.getMyAssets(keyword, pageable)));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ADMIN','IT_STAFF')")
    public ApiResponse<UserResponse> create(@Valid @RequestBody IdentityService.CreateUser request) {
        return ApiResponse.success(
                messages.getMessage("PROFILE_SAVED"), UserResponse.fromEntity(identities.createUser(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','IT_STAFF')")
    public ApiResponse<UserResponse> update(
            @PathVariable Long id, @Valid @RequestBody IdentityService.Profile request) {
        return ApiResponse.success(
                messages.getMessage("PROFILE_SAVED"), UserResponse.fromEntity(identities.saveProfile(id, request)));
    }

    public record ProfileStatus(@NotNull AccountStatus status) {}

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyAuthority('ADMIN','IT_STAFF')")
    public ApiResponse<UserResponse> status(@PathVariable Long id, @Valid @RequestBody ProfileStatus request) {
        return ApiResponse.success(
                messages.getMessage("PROFILE_SAVED"),
                UserResponse.fromEntity(identities.profileStatus(id, request.status())));
    }

    @GetMapping("/{id}/holdings")
    @PreAuthorize("hasAnyAuthority('ADMIN','IT_STAFF','PUR_STAFF')")
    public ApiResponse<HandoverHolderService.Detail> holdings(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(messages.getMessage("PROFILE_READ"), holders.detail(id, page, size));
    }
}
