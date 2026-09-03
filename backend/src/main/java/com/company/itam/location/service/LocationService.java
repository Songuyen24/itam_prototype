package com.company.itam.location.service;

import com.company.itam.asset.repository.AssetRepository;
import com.company.itam.common.exception.CatalogInUseException;
import com.company.itam.common.exception.DuplicateResourceException;
import com.company.itam.common.exception.ResourceNotFoundException;
import com.company.itam.common.pagination.PageResponse;
import com.company.itam.location.dto.LocationRequest;
import com.company.itam.location.dto.LocationResponse;
import com.company.itam.location.entity.LocationEntity;
import com.company.itam.location.repository.LocationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LocationService {

    private final LocationRepository locationRepository;
    private final AssetRepository assetRepository;

    public LocationService(LocationRepository locationRepository, AssetRepository assetRepository) {
        this.locationRepository = locationRepository;
        this.assetRepository = assetRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<LocationResponse> getLocations(String search, Boolean isActive, Pageable pageable) {
        Page<LocationEntity> page;
        if (search != null && !search.isBlank()) {
            page = locationRepository.findByNameContainingIgnoreCase(search.trim(), pageable);
        } else if (isActive != null) {
            page = locationRepository.findByIsActive(isActive, pageable);
        } else {
            page = locationRepository.findAll(pageable);
        }
        return PageResponse.of(page.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public LocationResponse getLocationById(Long id) {
        LocationEntity entity = locationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy vị trí với ID: " + id));
        return toResponse(entity);
    }

    @Transactional
    public LocationResponse createLocation(LocationRequest request) {
        if (locationRepository.existsByCode(request.getCode().trim())) {
            throw new DuplicateResourceException("Mã vị trí đã tồn tại: " + request.getCode());
        }

        LocationEntity entity = new LocationEntity();
        entity.setCode(request.getCode().trim());
        entity.setName(request.getName().trim());
        entity.setAddress(request.getAddress() != null ? request.getAddress().trim() : null);
        entity.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);

        LocationEntity saved = locationRepository.save(entity);
        return toResponse(saved);
    }

    @Transactional
    public LocationResponse updateLocation(Long id, LocationRequest request) {
        LocationEntity entity = locationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy vị trí với ID: " + id));

        if (!entity.getCode().equalsIgnoreCase(request.getCode().trim())
                && locationRepository.existsByCode(request.getCode().trim())) {
            throw new DuplicateResourceException("Mã vị trí đã tồn tại: " + request.getCode());
        }

        entity.setCode(request.getCode().trim());
        entity.setName(request.getName().trim());
        entity.setAddress(request.getAddress() != null ? request.getAddress().trim() : null);
        if (request.getIsActive() != null) {
            entity.setIsActive(request.getIsActive());
        }

        LocationEntity updated = locationRepository.save(entity);
        return toResponse(updated);
    }

    @Transactional
    public void deleteLocation(Long id) {
        LocationEntity entity = locationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy vị trí với ID: " + id));

        if (assetRepository.existsByLocationLocationId(id)) {
            throw new CatalogInUseException("Vị trí đang được sử dụng bởi tài sản, không thể xóa");
        }

        locationRepository.delete(entity);
    }

    @Transactional
    public LocationResponse toggleActive(Long id, Boolean active) {
        LocationEntity entity = locationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy vị trí với ID: " + id));
        entity.setIsActive(active != null ? active : !Boolean.TRUE.equals(entity.getIsActive()));
        return toResponse(locationRepository.save(entity));
    }

    private LocationResponse toResponse(LocationEntity entity) {
        return new LocationResponse(
                entity.getLocationId(),
                entity.getCode(),
                entity.getName(),
                entity.getAddress(),
                entity.getIsActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
