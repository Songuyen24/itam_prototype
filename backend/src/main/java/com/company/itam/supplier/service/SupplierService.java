package com.company.itam.supplier.service;

import com.company.itam.asset.repository.AssetRepository;
import com.company.itam.common.exception.CatalogInUseException;
import com.company.itam.common.exception.DuplicateResourceException;
import com.company.itam.common.exception.ResourceNotFoundException;
import com.company.itam.common.pagination.PageResponse;
import com.company.itam.supplier.dto.SupplierContactRequest;
import com.company.itam.supplier.dto.SupplierContactResponse;
import com.company.itam.supplier.dto.SupplierRequest;
import com.company.itam.supplier.dto.SupplierResponse;
import com.company.itam.supplier.entity.SupplierContactEntity;
import com.company.itam.supplier.entity.SupplierEntity;
import com.company.itam.supplier.repository.SupplierContactRepository;
import com.company.itam.supplier.repository.SupplierRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class SupplierService {

    private final SupplierRepository supplierRepository;
    private final SupplierContactRepository supplierContactRepository;
    private final AssetRepository assetRepository;

    public SupplierService(SupplierRepository supplierRepository,
                           SupplierContactRepository supplierContactRepository,
                           AssetRepository assetRepository) {
        this.supplierRepository = supplierRepository;
        this.supplierContactRepository = supplierContactRepository;
        this.assetRepository = assetRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<SupplierResponse> getSuppliers(String search, Boolean isActive, Pageable pageable) {
        Page<SupplierEntity> page;
        if (search != null && !search.isBlank()) {
            page = supplierRepository.findByNameContainingIgnoreCase(search.trim(), pageable);
        } else if (isActive != null) {
            page = supplierRepository.findByIsActive(isActive, pageable);
        } else {
            page = supplierRepository.findAll(pageable);
        }
        return PageResponse.of(page.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public SupplierResponse getSupplierById(Long id) {
        SupplierEntity entity = supplierRepository.findByIdWithContacts(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhà cung cấp với ID: " + id));
        return toResponse(entity);
    }

    @Transactional
    public SupplierResponse createSupplier(SupplierRequest request) {
        if (supplierRepository.existsByCode(request.getCode().trim())) {
            throw new DuplicateResourceException("Mã nhà cung cấp đã tồn tại: " + request.getCode());
        }

        SupplierEntity entity = new SupplierEntity();
        entity.setCode(request.getCode().trim());
        entity.setName(request.getName().trim());
        entity.setTaxCode(request.getTaxCode());
        entity.setAddress(request.getAddress());
        entity.setPhone(request.getPhone());
        entity.setEmail(request.getEmail());
        entity.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);

        SupplierEntity saved = supplierRepository.save(entity);
        return toResponse(saved);
    }

    @Transactional
    public SupplierResponse updateSupplier(Long id, SupplierRequest request) {
        SupplierEntity entity = supplierRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhà cung cấp với ID: " + id));

        if (!entity.getCode().equalsIgnoreCase(request.getCode().trim())
                && supplierRepository.existsByCode(request.getCode().trim())) {
            throw new DuplicateResourceException("Mã nhà cung cấp đã tồn tại: " + request.getCode());
        }

        entity.setCode(request.getCode().trim());
        entity.setName(request.getName().trim());
        entity.setTaxCode(request.getTaxCode());
        entity.setAddress(request.getAddress());
        entity.setPhone(request.getPhone());
        entity.setEmail(request.getEmail());
        if (request.getIsActive() != null) {
            entity.setIsActive(request.getIsActive());
        }

        SupplierEntity updated = supplierRepository.save(entity);
        return toResponse(updated);
    }

    @Transactional
    public void deleteSupplier(Long id) {
        SupplierEntity entity = supplierRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhà cung cấp với ID: " + id));

        if (assetRepository.existsBySupplierSupplierId(id)) {
            throw new CatalogInUseException("Nhà cung cấp đang được sử dụng bởi tài sản, không thể xóa");
        }

        supplierRepository.delete(entity);
    }

    @Transactional
    public SupplierResponse toggleActive(Long id, Boolean active) {
        SupplierEntity entity = supplierRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhà cung cấp với ID: " + id));
        entity.setIsActive(active != null ? active : !Boolean.TRUE.equals(entity.getIsActive()));
        return toResponse(supplierRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public List<SupplierContactResponse> getContacts(Long supplierId) {
        if (!supplierRepository.existsById(supplierId)) {
            throw new ResourceNotFoundException("Không tìm thấy nhà cung cấp với ID: " + supplierId);
        }
        return supplierContactRepository.findBySupplierSupplierId(supplierId).stream()
                .map(this::toContactResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public SupplierContactResponse addContact(Long supplierId, SupplierContactRequest request) {
        SupplierEntity supplier = supplierRepository.findById(supplierId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhà cung cấp với ID: " + supplierId));

        SupplierContactEntity contact = new SupplierContactEntity();
        contact.setSupplier(supplier);
        contact.setName(request.getName().trim());
        contact.setPosition(request.getPosition());
        contact.setPhone(request.getPhone());
        contact.setEmail(request.getEmail());

        SupplierContactEntity saved = supplierContactRepository.save(contact);
        return toContactResponse(saved);
    }

    @Transactional
    public SupplierContactResponse updateContact(Long contactId, SupplierContactRequest request) {
        SupplierContactEntity contact = supplierContactRepository.findById(contactId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người liên hệ với ID: " + contactId));

        contact.setName(request.getName().trim());
        contact.setPosition(request.getPosition());
        contact.setPhone(request.getPhone());
        contact.setEmail(request.getEmail());

        return toContactResponse(supplierContactRepository.save(contact));
    }

    @Transactional
    public void deleteContact(Long contactId) {
        SupplierContactEntity contact = supplierContactRepository.findById(contactId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người liên hệ với ID: " + contactId));
        supplierContactRepository.delete(contact);
    }

    private SupplierResponse toResponse(SupplierEntity entity) {
        SupplierResponse resp = new SupplierResponse();
        resp.setSupplierId(entity.getSupplierId());
        resp.setCode(entity.getCode());
        resp.setName(entity.getName());
        resp.setTaxCode(entity.getTaxCode());
        resp.setAddress(entity.getAddress());
        resp.setPhone(entity.getPhone());
        resp.setEmail(entity.getEmail());
        resp.setIsActive(entity.getIsActive());
        resp.setCreatedAt(entity.getCreatedAt());
        resp.setUpdatedAt(entity.getUpdatedAt());
        if (entity.getContacts() != null) {
            resp.setContacts(entity.getContacts().stream().map(this::toContactResponse).collect(Collectors.toList()));
        }
        return resp;
    }

    private SupplierContactResponse toContactResponse(SupplierContactEntity contact) {
        return new SupplierContactResponse(
                contact.getContactId(),
                contact.getSupplier() != null ? contact.getSupplier().getSupplierId() : null,
                contact.getName(),
                contact.getPosition(),
                contact.getPhone(),
                contact.getEmail()
        );
    }
}
