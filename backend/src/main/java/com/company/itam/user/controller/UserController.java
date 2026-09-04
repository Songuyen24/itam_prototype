package com.company.itam.user.controller;

import com.company.itam.asset.entity.AssetEntity;
import com.company.itam.asset.repository.AssetRepository;
import com.company.itam.common.exception.AppException;
import com.company.itam.common.pagination.PageResponse;
import com.company.itam.common.response.ApiResponse;
import com.company.itam.user.dto.response.UserResponse;
import com.company.itam.user.entity.UserEntity;
import com.company.itam.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1/users")
public class UserController {

    private final UserRepository userRepository;
    private final AssetRepository assetRepository;

    public UserController(UserRepository userRepository, AssetRepository assetRepository) {
        this.userRepository = userRepository;
        this.assetRepository = assetRepository;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'IT_STAFF')")
    public ResponseEntity<ApiResponse<PageResponse<UserResponse>>> list(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Pageable pageable = PageRequest.of(safePage, safeSize);
        Page<UserEntity> result = (keyword != null && !keyword.isBlank())
                ? userRepository.findByFullNameContainingIgnoreCaseWithDetails(keyword.trim(), pageable)
                : userRepository.findAllWithDetails(pageable);

        PageResponse<UserResponse> mapped = new PageResponse<>(
                result.getContent().stream().map(UserResponse::fromEntity).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.isLast()
        );
        return ResponseEntity.ok(ApiResponse.success("USER_LIST_SUCCESS", mapped));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'IT_STAFF')")
    public ResponseEntity<ApiResponse<UserResponse>> getById(@PathVariable("id") Long id) {
        UserEntity user = userRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new AppException(
                        HttpStatus.NOT_FOUND,
                        "RESOURCE_NOT_FOUND",
                        "User not found with id: " + id
                ));
        return ResponseEntity.ok(ApiResponse.success("USER_GET_SUCCESS", UserResponse.fromEntity(user)));
    }

    /**
     * Xem tài sản đang được gán cho người dùng hiện tại.
     * USER được phép xem asset của chính mình; ADMIN/IT_STAFF có thể dùng endpoint này
     * để nhận diện tài sản của họ (nếu có).
     */
    @GetMapping("/me/assets")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<Long>>> myAssets() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null
                || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required");
        }
        UserEntity user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new AppException(
                        HttpStatus.UNAUTHORIZED,
                        "UNAUTHORIZED",
                        "User not found"
                ));
        Pageable pageable = PageRequest.of(0, 200);
        Page<AssetEntity> assets = assetRepository.findByAssignedToUserId(user.getUserId(), pageable);
        List<Long> ids = assets.getContent().stream().map(AssetEntity::getAssetId).toList();
        return ResponseEntity.ok(ApiResponse.success("MY_ASSETS_SUCCESS", ids));
    }
}
