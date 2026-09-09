package com.company.itam.asset.relationship;

import com.company.itam.asset.entity.*;
import com.company.itam.asset.repository.*;
import com.company.itam.asset.relationship.entity.*;
import com.company.itam.asset.relationship.enums.*;
import com.company.itam.asset.relationship.repository.*;
import com.company.itam.asset.service.*;
import com.company.itam.common.enums.*;
import com.company.itam.common.exception.*;
import com.company.itam.user.repository.UserRepository;
import com.company.itam.user.entity.UserEntity;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@Transactional(readOnly=true)
@PreAuthorize("hasAnyAuthority('ADMIN','IT_STAFF')")
public class AssetRelationshipService {
    private final AssetRepository assets;
    private final AssetRelationshipRepository relationships;
    private final LicenseAllocationService allocations;
    private final UserRepository users;
    private final AssetAuditService audit;
    public AssetRelationshipService(AssetRepository assets,AssetRelationshipRepository relationships,
            LicenseAllocationService allocations,UserRepository users,AssetAuditService audit) {
        this.assets=assets; this.relationships=relationships; this.allocations=allocations; this.users=users; this.audit=audit;
    }
    public record AssetRef(Long assetId,String assetTag,String name,String category,String status,Long assignedToUserId,String assignedToFullName) {}
    public record Relationship(Long relationshipId,AssetRef parent,AssetRef child,RelationshipType type,Long allocationId,boolean removable,String blockedReason) {}
    public Page<Relationship> list(Long id,Pageable page) {
        if (!assets.existsById(id)) throw new ResourceNotFoundException("RESOURCE_NOT_FOUND");
        return relationships.findAllForAsset(id,page).map(this::response);
    }
    @Transactional
    public Relationship create(Long parentId,Long childId,RelationshipType type) {
        if (Objects.equals(parentId,childId)) fail("RELATIONSHIP_INVALID");
        // A stable lock order serializes competing parents and package capacity changes.
        var locked=new HashMap<Long,AssetEntity>();
        for (Long id:new TreeSet<>(List.of(parentId,childId))) locked.put(id,assets.lockById(id).orElseThrow(()->new ResourceNotFoundException("RESOURCE_NOT_FOUND")));
        var parent=locked.get(parentId); var child=locked.get(childId);
        if (category(parent)!=AssetCategory.DEVICE || (type==RelationshipType.COMPONENT_OF?category(child)!=AssetCategory.COMPONENT:category(child)!=AssetCategory.LICENSE)) fail("RELATIONSHIP_INVALID");
        if (!stock(parent) || !stock(child)) fail("ASSET_WORKFLOW_REQUIRED");
        if (relationships.existsByParentAssetAssetIdAndChildAssetAssetIdAndRelationshipType(parentId,childId,type)
                || (type==RelationshipType.COMPONENT_OF && !relationships.findByChildAssetAssetId(childId).isEmpty())) fail("RELATIONSHIP_DUPLICATE");
        var actor=actor(); var r=new AssetRelationshipEntity();
        r.setParentAsset(parent); r.setChildAsset(child); r.setRelationshipType(type); r.setCreatedBy(actor);
        if (type==RelationshipType.INSTALLED_ON) r.setAllocation(allocations.reserveOem(child,parent,actor));
        relationships.saveAndFlush(r);
        var data=Map.<String,Object>of("relationshipId",r.getRelationshipId(),"parentId",parentId,"childId",childId,"type",type.name());
        audit.record(parentId,"LINK",actor,null,data); audit.record(childId,"LINK",actor,null,data);
        return response(r);
    }
    @Transactional
    public void remove(Long id) {
        var endpoints=relationships.endpointIds(id);
        if (endpoints.isEmpty()) throw new ResourceNotFoundException("RESOURCE_NOT_FOUND");
        for (Long assetId:new TreeSet<>(List.of((Long)endpoints.get(0)[0],(Long)endpoints.get(0)[1]))) assets.lockById(assetId).orElseThrow();
        var r=relationships.findById(id).orElseThrow(()->new ResourceNotFoundException("RESOURCE_NOT_FOUND"));
        String reason=blocked(r); if (reason!=null) fail(reason);
        var data=Map.<String,Object>of("relationshipId",id,"parentId",r.getParentAsset().getAssetId(),"childId",r.getChildAsset().getAssetId(),"type",r.getRelationshipType().name());
        var actor=actor(); audit.record(r.getParentAsset().getAssetId(),"UNLINK",actor,data,null); audit.record(r.getChildAsset().getAssetId(),"UNLINK",actor,data,null);
        relationships.delete(r);
    }
    private Relationship response(AssetRelationshipEntity r) {
        String reason=blocked(r);
        return new Relationship(r.getRelationshipId(),ref(r.getParentAsset()),ref(r.getChildAsset()),r.getRelationshipType(),
                r.getAllocation()==null?null:r.getAllocation().getAllocationId(),reason==null,reason);
    }
    private String blocked(AssetRelationshipEntity r) {
        if (r.getRelationshipType()==RelationshipType.INSTALLED_ON)
            return LicenseCodes.OEM.equals(r.getChildAsset().getLicenseDetails().getAssignmentType().getCode())?"OEM_CANNOT_DETACH":"LICENSE_WORKFLOW_REQUIRED";
        return stock(r.getParentAsset()) && stock(r.getChildAsset())?null:"ASSET_WORKFLOW_REQUIRED";
    }
    private AssetRef ref(AssetEntity a) { return new AssetRef(a.getAssetId(),a.getAssetTag(),a.getName(),category(a).name(),a.getStatus().getCode().name(),a.getAssignedTo()==null?null:a.getAssignedTo().getUserId(),a.getAssignedTo()==null?null:a.getAssignedTo().getFullName()); }
    private AssetCategory category(AssetEntity a) { return a.getType().getCategory().getCode(); }
    private boolean stock(AssetEntity a) { return a.getStatus().getCode()==AssetStatus.IN_STOCK && a.getAssignedTo()==null; }
    private UserEntity actor() { return users.findByEmail(SecurityContextHolder.getContext().getAuthentication().getName()).orElseThrow(()->new ResourceNotFoundException("RESOURCE_NOT_FOUND")); }
    private void fail(String code) { throw new AppException(HttpStatus.CONFLICT,code,code); }
}
