package com.company.itam.asset.specification;

import com.company.itam.asset.dto.request.AssetSearchCriteria;
import com.company.itam.asset.entity.AssetEntity;
import com.company.itam.asset.entity.AssetHardwareDetailsEntity;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class AssetSpecification implements Specification<AssetEntity> {

    private final AssetSearchCriteria criteria;

    public AssetSpecification(AssetSearchCriteria criteria) {
        this.criteria = criteria;
    }

    @Override
    public Predicate toPredicate(Root<AssetEntity> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        List<Predicate> predicates = new ArrayList<>();

        // Fetch joins for entity queries to eliminate N+1
        if (query.getResultType() != Long.class && query.getResultType() != long.class) {
            root.fetch("type", JoinType.LEFT).fetch("category", JoinType.LEFT);
            root.fetch("status", JoinType.LEFT);
            root.fetch("department", JoinType.LEFT);
            root.fetch("location", JoinType.LEFT);
            root.fetch("supplier", JoinType.LEFT);
            root.fetch("assignedTo", JoinType.LEFT);
            Fetch<AssetEntity, AssetHardwareDetailsEntity> hwFetch = root.fetch("hardwareDetails", JoinType.LEFT);
            hwFetch.fetch("model", JoinType.LEFT);
            hwFetch.fetch("condition", JoinType.LEFT);
            query.distinct(true);
        }

        // Join for criteria predicates
        Join<Object, Object> typeJoin = root.join("type", JoinType.LEFT);
        Join<Object, Object> hwJoin = root.join("hardwareDetails", JoinType.LEFT);

        if (criteria != null) {
            // General keyword search
            if (criteria.getKeyword() != null && !criteria.getKeyword().trim().isEmpty()) {
                String pattern = "%" + criteria.getKeyword().trim().toLowerCase() + "%";
                Predicate tagMatch = cb.like(cb.lower(root.get("assetTag")), pattern);
                Predicate nameMatch = cb.like(cb.lower(root.get("name")), pattern);
                Predicate serialMatch = cb.like(cb.lower(hwJoin.get("serialNumber")), pattern);
                predicates.add(cb.or(tagMatch, nameMatch, serialMatch));
            }

            // Asset Tag specific search
            if (criteria.getAssetTag() != null && !criteria.getAssetTag().trim().isEmpty()) {
                String pattern = "%" + criteria.getAssetTag().trim().toLowerCase() + "%";
                predicates.add(cb.like(cb.lower(root.get("assetTag")), pattern));
            }

            // Serial Number specific search
            if (criteria.getSerialNumber() != null && !criteria.getSerialNumber().trim().isEmpty()) {
                String pattern = "%" + criteria.getSerialNumber().trim().toLowerCase() + "%";
                predicates.add(cb.like(cb.lower(hwJoin.get("serialNumber")), pattern));
            }

            // Type filter
            if (criteria.getTypeId() != null) {
                predicates.add(cb.equal(typeJoin.get("typeId"), criteria.getTypeId()));
            }

            // Category filter
            if (criteria.getCategoryId() != null) {
                Join<Object, Object> catJoin = typeJoin.join("category", JoinType.LEFT);
                predicates.add(cb.equal(catJoin.get("categoryId"), criteria.getCategoryId()));
            }

            // Status filter
            if (criteria.getStatusId() != null) {
                predicates.add(cb.equal(root.get("status").get("statusId"), criteria.getStatusId()));
            }

            // Condition filter
            if (criteria.getConditionId() != null) {
                predicates.add(cb.equal(hwJoin.get("condition").get("conditionId"), criteria.getConditionId()));
            }

            // Model filter
            if (criteria.getModelId() != null) {
                predicates.add(cb.equal(hwJoin.get("model").get("modelId"), criteria.getModelId()));
            }

            // Department filter
            if (criteria.getDepartmentId() != null) {
                predicates.add(cb.equal(root.get("department").get("departmentId"), criteria.getDepartmentId()));
            }

            // Location filter
            if (criteria.getLocationId() != null) {
                predicates.add(cb.equal(root.get("location").get("locationId"), criteria.getLocationId()));
            }

            // Supplier filter
            if (criteria.getSupplierId() != null) {
                predicates.add(cb.equal(root.get("supplier").get("supplierId"), criteria.getSupplierId()));
            }

            // Assigned To user filter
            if (criteria.getAssignedTo() != null) {
                var allocation = query.subquery(Long.class);
                var ar = allocation.from(com.company.itam.asset.entity.LicenseAllocationEntity.class);
                allocation.select(ar.get("allocationId")).where(cb.equal(ar.get("license").get("assetId"),root.get("assetId")),
                        cb.equal(ar.get("user").get("userId"),criteria.getAssignedTo()),cb.equal(ar.get("status"),com.company.itam.asset.entity.LicenseAllocationStatus.ACTIVE));
                predicates.add(cb.or(cb.equal(root.join("assignedTo",JoinType.LEFT).get("userId"), criteria.getAssignedTo()),cb.exists(allocation)));
            }
        }

        return cb.and(predicates.toArray(new Predicate[0]));
    }
}
