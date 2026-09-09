package com.company.itam.asset.controller;

import com.company.itam.asset.relationship.AssetRelationshipService;
import com.company.itam.asset.relationship.enums.RelationshipType;
import com.company.itam.asset.service.AssetActivityService;
import com.company.itam.common.pagination.PageResponse;
import com.company.itam.common.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.*;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1")
@PreAuthorize("hasAnyAuthority('ADMIN','IT_STAFF')")
public class AssetRelationshipController {
    private final AssetRelationshipService relationships;
    private final AssetActivityService activity;
    public AssetRelationshipController(AssetRelationshipService relationships,AssetActivityService activity) { this.relationships=relationships; this.activity=activity; }
    public record CreateRelationship(@NotNull(message="{validation.required}") Long childAssetId,@NotNull(message="{validation.required}") RelationshipType type) {}
    @GetMapping("/assets/{id}/relationships")
    public ApiResponse<PageResponse<AssetRelationshipService.Relationship>> list(@PathVariable Long id,@PageableDefault(size=20,sort="relationshipId") Pageable page) {
        return ApiResponse.success(PageResponse.of(relationships.list(id,page)));
    }
    @PostMapping("/assets/{id}/relationships") @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AssetRelationshipService.Relationship> create(@PathVariable Long id,@Valid @RequestBody CreateRelationship request) {
        return ApiResponse.success(relationships.create(id,request.childAssetId(),request.type()));
    }
    @DeleteMapping("/asset-relationships/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable Long id) { relationships.remove(id); }
    @GetMapping("/assets/{id}/allocations")
    public ApiResponse<PageResponse<AssetActivityService.Allocation>> allocations(@PathVariable Long id,@PageableDefault(size=20,sort="allocationId",direction=Sort.Direction.DESC) Pageable page) {
        return ApiResponse.success(activity.allocations(id,page));
    }
    @GetMapping("/assets/{id}/history")
    public ApiResponse<PageResponse<AssetActivityService.History>> history(@PathVariable Long id,@PageableDefault(size=20,sort="createdAt",direction=Sort.Direction.DESC) Pageable page) {
        return ApiResponse.success(activity.history(id,page));
    }
}
