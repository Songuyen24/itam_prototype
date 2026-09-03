package com.company.itam.department.service;

import com.company.itam.asset.repository.AssetRepository;
import com.company.itam.common.exception.CatalogInUseException;
import com.company.itam.common.exception.DuplicateResourceException;
import com.company.itam.common.exception.ResourceNotFoundException;
import com.company.itam.common.pagination.PageResponse;
import com.company.itam.department.dto.DepartmentRequest;
import com.company.itam.department.dto.DepartmentResponse;
import com.company.itam.department.entity.DepartmentEntity;
import com.company.itam.department.repository.DepartmentRepository;
import com.company.itam.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;
    private final AssetRepository assetRepository;

    public DepartmentService(DepartmentRepository departmentRepository,
                             UserRepository userRepository,
                             AssetRepository assetRepository) {
        this.departmentRepository = departmentRepository;
        this.userRepository = userRepository;
        this.assetRepository = assetRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<DepartmentResponse> getDepartments(String search, Boolean isActive, Pageable pageable) {
        Page<DepartmentEntity> page;
        if (search != null && !search.isBlank()) {
            page = departmentRepository.findByNameContainingIgnoreCase(search.trim(), pageable);
        } else if (isActive != null) {
            page = departmentRepository.findByIsActive(isActive, pageable);
        } else {
            page = departmentRepository.findAll(pageable);
        }
        return PageResponse.of(page.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public DepartmentResponse getDepartmentById(Long id) {
        DepartmentEntity entity = departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phòng ban với ID: " + id));
        return toResponse(entity);
    }

    @Transactional
    public DepartmentResponse createDepartment(DepartmentRequest request) {
        if (departmentRepository.existsByCode(request.getCode().trim())) {
            throw new DuplicateResourceException("Mã phòng ban đã tồn tại: " + request.getCode());
        }

        DepartmentEntity entity = new DepartmentEntity();
        entity.setCode(request.getCode().trim());
        entity.setName(request.getName().trim());
        entity.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);

        DepartmentEntity saved = departmentRepository.save(entity);
        return toResponse(saved);
    }

    @Transactional
    public DepartmentResponse updateDepartment(Long id, DepartmentRequest request) {
        DepartmentEntity entity = departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phòng ban với ID: " + id));

        if (!entity.getCode().equalsIgnoreCase(request.getCode().trim())
                && departmentRepository.existsByCode(request.getCode().trim())) {
            throw new DuplicateResourceException("Mã phòng ban đã tồn tại: " + request.getCode());
        }

        entity.setCode(request.getCode().trim());
        entity.setName(request.getName().trim());
        if (request.getIsActive() != null) {
            entity.setIsActive(request.getIsActive());
        }

        DepartmentEntity updated = departmentRepository.save(entity);
        return toResponse(updated);
    }

    @Transactional
    public void deleteDepartment(Long id) {
        DepartmentEntity entity = departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phòng ban với ID: " + id));

        if (userRepository.existsByDepartmentDepartmentId(id)) {
            throw new CatalogInUseException("Phòng ban đang được gán cho người dùng, không thể xóa");
        }
        if (assetRepository.existsByDepartmentDepartmentId(id)) {
            throw new CatalogInUseException("Phòng ban đang được gán cho tài sản, không thể xóa");
        }

        departmentRepository.delete(entity);
    }

    @Transactional
    public DepartmentResponse toggleActive(Long id, Boolean active) {
        DepartmentEntity entity = departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phòng ban với ID: " + id));
        entity.setIsActive(active != null ? active : !Boolean.TRUE.equals(entity.getIsActive()));
        return toResponse(departmentRepository.save(entity));
    }

    private DepartmentResponse toResponse(DepartmentEntity entity) {
        return new DepartmentResponse(
                entity.getDepartmentId(),
                entity.getCode(),
                entity.getName(),
                entity.getIsActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
