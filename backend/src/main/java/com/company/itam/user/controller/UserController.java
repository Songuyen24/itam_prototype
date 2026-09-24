package com.company.itam.user.controller;

import com.company.itam.asset.dto.response.AssetResponse;
import com.company.itam.asset.service.AssetService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/users")
public class UserController {

    private final UserRepository userRepository;
    private final AssetService assetService;

    public UserController(UserRepository userRepository, AssetService assetService) {
        this.userRepository = userRepository;
        this.assetService = assetService;
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
    @PreAuthorize("hasAnyAuthority('ADMIN', 'IT_STAFF', 'USER')")
    public ResponseEntity<ApiResponse<PageResponse<AssetResponse>>> myAssets(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                org.springframework.data.domain.Sort.by("assetId").descending());
        return ResponseEntity.ok(ApiResponse.success("MY_ASSETS_SUCCESS", assetService.getMyAssets(keyword, pageable)));
    }
}
