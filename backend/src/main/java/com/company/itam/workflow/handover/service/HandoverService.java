package com.company.itam.workflow.handover.service;

import com.company.itam.asset.entity.*;
import com.company.itam.asset.repository.*;
import com.company.itam.asset.service.*;
import com.company.itam.asset.relationship.entity.AssetRelationshipEntity;
import com.company.itam.audit.entity.AuditLogEntity;
import com.company.itam.audit.repository.AuditLogRepository;
import com.company.itam.catalog.repository.AssetStatusRepository;
import com.company.itam.common.enums.*;
import com.company.itam.common.exception.*;
import com.company.itam.common.pagination.PageResponse;
import com.company.itam.location.repository.LocationRepository;
import com.company.itam.user.repository.UserRepository;
import com.company.itam.workflow.core.entity.*;
import com.company.itam.workflow.core.enums.*;
import com.company.itam.workflow.core.repository.*;
import com.company.itam.workflow.handover.dto.*;
import com.company.itam.workflow.handover.dto.HandoverResponse.*;
import com.company.itam.workflow.handover.entity.TransactionHandoverDetailEntity;
import com.company.itam.workflow.handover.repository.*;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

@Service
@Transactional(readOnly=true)
@PreAuthorize("hasAnyAuthority('ADMIN','IT_STAFF')")
public class HandoverService {
    private final AssetRepository assets;
    private final LicenseAllocationRepository allocations;
    private final LicenseAllocationService licensing;
    private final AssetAuditService audit;
    private final AuditLogRepository logs;
    private final UserRepository users;
    private final LocationRepository locations;
    private final AssetStatusRepository statuses;
    private final TransactionRepository transactions;
    private final TransactionAssetRepository lines;
    private final TransactionHandoverDetailRepository details;
    private final HandoverSnapshotRepository snapshots;
    private final JdbcTemplate jdbc;

    public HandoverService(AssetRepository assets, LicenseAllocationRepository allocations, LicenseAllocationService licensing,
            AssetAuditService audit, AuditLogRepository logs, UserRepository users, LocationRepository locations,
            AssetStatusRepository statuses, TransactionRepository transactions, TransactionAssetRepository lines,
            TransactionHandoverDetailRepository details, HandoverSnapshotRepository snapshots, JdbcTemplate jdbc) {
        this.assets=assets; this.allocations=allocations; this.licensing=licensing; this.audit=audit; this.logs=logs;
        this.users=users; this.locations=locations; this.statuses=statuses; this.transactions=transactions;
        this.lines=lines; this.details=details; this.snapshots=snapshots; this.jdbc=jdbc;
    }

    public record Candidate(Long assetId,String assetTag,String name,String category,String assignmentType,Long availableSeats) {}
    public PageResponse<Candidate> candidates(String keyword,int page,int size) {
        if (page<0 || size<1 || size>100 || keyword.length()>100) invalid();
        String search=keyword.trim().toLowerCase(Locale.ROOT).replace("\\","\\\\").replace("%","\\%").replace("_","\\_");
        var result=assets.findAll((root,query,b)-> {
            var rel=query.subquery(Long.class); var r=rel.from(AssetRelationshipEntity.class);
            rel.select(r.get("relationshipId")).where(b.equal(r.get("childAsset"),root));
            var category=root.get("type").get("category").get("code");
            var ld=root.join("licenseDetails",jakarta.persistence.criteria.JoinType.LEFT);
            var assignment=ld.join("assignmentType",jakarta.persistence.criteria.JoinType.LEFT);
            return b.and(b.equal(root.get("status").get("code"),AssetStatus.IN_STOCK),b.isNull(root.get("assignedTo")),
                    b.notEqual(b.trim(root.get("assetTag")),""),
                    b.or(b.equal(category,AssetCategory.DEVICE),b.and(b.equal(category,AssetCategory.COMPONENT),b.not(b.exists(rel))),
                            b.and(b.equal(category,AssetCategory.LICENSE),b.equal(assignment.get("code"),LicenseCodes.PER_USER))),
                    b.or(b.like(b.lower(root.get("name")),"%"+search+"%",'\\'),b.like(b.lower(root.get("assetTag")),"%"+search+"%",'\\')));
        },PageRequest.of(page,size,Sort.by("assetId")));
        var used=new HashMap<Long,Long>();
        var packageIds=result.getContent().stream().filter(a->a.getLicenseDetails()!=null).map(AssetEntity::getAssetId).toList();
        if(!packageIds.isEmpty()) for(var row:allocations.usedSeatsByLicenseIds(packageIds)) used.put((Long)row[0],(Long)row[1]);
        return PageResponse.of(result.map(a->new Candidate(a.getAssetId(),a.getAssetTag(),a.getName(),category(a).name(),
                a.getLicenseDetails()==null?null:a.getLicenseDetails().getAssignmentType().getCode(),
                a.getLicenseDetails()==null?null:a.getLicenseDetails().getSeatCount()-used.getOrDefault(a.getAssetId(),0L))));
    }

    public HandoverResponse preview(HandoverRequest request) { return prepare(request,false); }
    public HandoverResponse get(Long id) {
        return snapshots.find(id).orElseThrow(()->new ResourceNotFoundException("HANDOVER_NOT_FOUND"));
    }

    @Transactional
    public HandoverResponse complete(HandoverRequest request) {
        var preview=prepare(request,true);
        if (!Objects.equals(request.expectedFingerprint(),preview.fingerprint())) fail("HANDOVER_CHANGED");
        var actor=users.findByEmail(SecurityContextHolder.getContext().getAuthentication().getName()).orElseThrow(()->new ResourceNotFoundException("RESOURCE_NOT_FOUND"));
        var recipient=users.findById(request.recipientUserId()).orElseThrow();
        var location=locations.findById(request.destinationLocationId()).orElseThrow();
        var tx=new TransactionEntity(); var now=Instant.now();
        tx.setTransactionCode("HO-"+UUID.randomUUID()); tx.setType(TransactionType.HANDOVER); tx.setStatus(TransactionStatus.COMPLETED);
        tx.setRequester(actor); tx.setProcessedBy(actor); tx.setProcessedAt(now); tx.setCompletedAt(now); tx.setNotes(request.notes());
        transactions.saveAndFlush(tx);
        var detail=new TransactionHandoverDetailEntity(); detail.setTransaction(tx); detail.setRecipient(recipient);
        detail.setDestinationLocation(location); detail.setHandoverDate(request.handoverDate()); details.saveAndFlush(detail);
        int index=0;
        for (var line:preview.lines()) {
            var asset=assets.findById(line.assetId()).orElseThrow();
            var item=new TransactionAssetEntity(); item.setTransaction(tx); item.setAsset(asset); item.setLineNumber(++index); lines.save(item);
        }
        lines.flush(); // T11B allocation contract validates the persisted transaction and package lines.
        var inUse=statuses.findByCode(AssetStatus.IN_USE).orElseThrow();
        for (var line:preview.lines()) {
            var asset=assets.findById(line.assetId()).orElseThrow();
            if (category(asset)!=AssetCategory.LICENSE) {
                var before=audit.snapshot(asset); asset.setStatus(inUse); asset.setAssignedTo(recipient); asset.setLocation(location); asset.setUpdatedBy(actor);
                var after=audit.snapshot(asset); after.put("transactionId",tx.getTransactionId()); after.put("recipientName",recipient.getFullName());
                after.put("handoverDate",request.handoverDate().toString()); audit.record(asset.getAssetId(),"HANDOVER",actor,before,after);
            }
        }
        assets.flush();
        var completedLines=new ArrayList<Line>();
        for (var line:preview.lines()) {
            var issued=new ArrayList<Allocation>();
            for (var allocation:line.allocations()) {
                if (LicenseCodes.OEM.equals(allocation.assignmentType())) {
                    licensing.activateOem(allocation.allocationId(),recipient,tx.getTransactionId(),actor); issued.add(allocation);
                } else {
                    // One package line, independently recoverable seats. T16 can select one allocation
                    // without releasing all seats handed to the same recipient in this transaction.
                    for (int seat=0;seat<allocation.seats();seat++) {
                        var a=licensing.allocatePerUser(line.assetId(),recipient,1,tx.getTransactionId(),actor);
                        if (allocation.deviceId()!=null) licensing.attachPerUser(a.getAllocationId(),allocation.deviceId(),actor);
                        issued.add(new Allocation(a.getAllocationId(),allocation.deviceId(),1,LicenseCodes.PER_USER,allocation.deviceTag(),allocation.deviceName()));
                    }
                }
            }
            var data=new LinkedHashMap<>(line.details());
            data.put("status",line.category().equals("LICENSE")?"IN_STOCK":"IN_USE");
            data.put("recipientUserId",recipient.getUserId());
            if (!line.category().equals("LICENSE")) { data.put("assignedTo",recipient.getUserId()); data.put("locationId",location.getLocationId()); }
            data.put("actorUserId",actor.getUserId()); data.put("actorName",actor.getFullName());
            if (line.category().equals("LICENSE")) {
                data.put("availableSeatsAfter",asset(line.assetId()).getLicenseDetails().getSeatCount()-allocations.usedSeats(line.assetId()));
                var after=new LinkedHashMap<>(data);
                after.put("transactionId",tx.getTransactionId()); after.put("recipientName",recipient.getFullName());
                after.put("handoverDate",request.handoverDate().toString()); after.put("allocationStatus","ACTIVE");
                after.put("allocationIds",issued.stream().map(Allocation::allocationId).toList());
                audit.record(line.assetId(),"HANDOVER",actor,line.details(),after);
            }
            completedLines.add(new Line(line.assetId(),line.assetTag(),line.name(),line.category(),line.parentAssetId(),line.seats(),issued,data));
        }
        var result=new HandoverResponse(tx.getTransactionId(),tx.getTransactionCode(),now,preview.recipientUserId(),preview.recipientName(),preview.recipientEmail(),
                preview.destinationLocationId(),preview.destinationLocationName(),preview.handoverDate(),preview.notes(),preview.fingerprint(),completedLines);
        allocations.flush();
        snapshots.save(result);
        var log=new AuditLogEntity(); log.setEntityType("TRANSACTION"); log.setEntityId(tx.getTransactionId()); log.setAction("HANDOVER_COMPLETED"); log.setActor(actor);
        log.setNewData(Map.of("status","COMPLETED","recipientUserId",recipient.getUserId(),"handoverDate",request.handoverDate().toString(),"assetIds",preview.lines().stream().map(Line::assetId).toList())); logs.save(log);
        return result;
    }

    private record Link(Long id,Long parent,Long child,Long allocation) {}
    private List<Link> links(Collection<Long> ids) {
        var result=new TreeMap<Long,Link>();
        for(Long id:ids) jdbc.query("SELECT relationship_id,parent_asset_id,child_asset_id,allocation_id FROM asset_relationships WHERE parent_asset_id=? OR child_asset_id=? ORDER BY relationship_id",
                (rs,n)->new Link(rs.getLong(1),rs.getLong(2),rs.getLong(3),(Long)rs.getObject(4)),id,id).forEach(l->result.put(l.id(),l));
        return new ArrayList<>(result.values());
    }
    private HandoverResponse prepare(HandoverRequest req,boolean lock) {
        if (req.assetIds().isEmpty() && req.licenses().isEmpty()) fail("EMPTY_TRANSACTION");
        var selected=new TreeSet<>(req.assetIds());
        if(selected.size()!=req.assetIds().size()) fail("HANDOVER_DUPLICATE");
        var requestedLicenses=new TreeMap<Long,HandoverRequest.LicenseLine>();
        for(var l:req.licenses()) if (selected.contains(l.assetId()) || requestedLicenses.put(l.assetId(),l)!=null) fail("HANDOVER_DUPLICATE");
        var initialLinks=links(selected);
        var ids=new TreeSet<>(selected); ids.addAll(requestedLicenses.keySet());
        for(var l:initialLinks) if(selected.contains(l.parent())) ids.add(l.child());
        for(var l:req.licenses()) if(l.deviceId()!=null) ids.add(l.deviceId());
        // Discovery uses scalar IDs only. Lock in T11B order, then re-read relationships; never use stale managed assets.
        if(lock) {
            for(Long id:ids) assets.lockById(id).orElseThrow(()->new ResourceNotFoundException("RESOURCE_NOT_FOUND"));
            if(!initialLinks.equals(links(selected))) fail("HANDOVER_CHANGED");
            jdbc.queryForList("SELECT user_id FROM users WHERE user_id=? FOR SHARE",req.recipientUserId());
            jdbc.queryForList("SELECT location_id FROM locations WHERE location_id=? FOR SHARE",req.destinationLocationId());
        }
        var recipient=users.findById(req.recipientUserId()).orElseThrow(()->new ResourceNotFoundException("RESOURCE_NOT_FOUND"));
        if(recipient.getAccountStatus()!=AccountStatus.ACTIVE) fail("HANDOVER_RECIPIENT_INACTIVE");
        var location=locations.findById(req.destinationLocationId()).orElseThrow(()->new ResourceNotFoundException("RESOURCE_NOT_FOUND"));
        if(!Boolean.TRUE.equals(location.getIsActive())) fail("HANDOVER_LOCATION_INACTIVE");
        var bundle=new TreeSet<>(selected); var parents=new HashMap<Long,Long>();
        var issued=new TreeMap<Long,List<Allocation>>();
        for(Long id:selected) {
            var a=asset(id); if(category(a)==AssetCategory.LICENSE) fail("HANDOVER_LICENSE_SELECTION");
            for(var l:initialLinks) if(l.child().equals(id) && !selected.contains(l.parent())) fail("HANDOVER_PARENT_REQUIRED");
        }
        for(var l:initialLinks) if(selected.contains(l.parent())) {
            var child=asset(l.child()); bundle.add(l.child());
            if(category(child)==AssetCategory.LICENSE) {
                var a=allocations.findById(l.allocation()==null?-1L:l.allocation()).orElseThrow(()->new ResourceNotFoundException("RESOURCE_NOT_FOUND"));
                if(!LicenseCodes.OEM.equals(child.getLicenseDetails().getAssignmentType().getCode()) || a.getStatus()!=LicenseAllocationStatus.RESERVED
                        || !a.getDevice().getAssetId().equals(l.parent()) || !a.getLicense().getAssetId().equals(l.child()) || a.getSeatCount()!=1) fail("HANDOVER_LICENSE_SELECTION");
                issued.computeIfAbsent(l.child(),key->new ArrayList<>()).add(allocation(a.getAllocationId(),l.parent(),1,LicenseCodes.OEM));
            } else parents.put(l.child(),l.parent());
        }
        for(var l:req.licenses()) {
            var a=asset(l.assetId()); stock(a);
            if(a.getLicenseDetails()==null || !LicenseCodes.PER_USER.equals(a.getLicenseDetails().getAssignmentType().getCode())) fail("HANDOVER_LICENSE_SELECTION");
            if(allocations.usedSeats(l.assetId())+l.seats()>a.getLicenseDetails().getSeatCount()) fail("LICENSE_CAPACITY_EXCEEDED");
            if(l.deviceId()!=null) {
                var device=asset(l.deviceId());
                if(l.seats()!=1 || category(device)!=AssetCategory.DEVICE || (!selected.contains(l.deviceId()) &&
                        (device.getStatus().getCode()!=AssetStatus.IN_USE || device.getAssignedTo()==null || !device.getAssignedTo().getUserId().equals(req.recipientUserId())))) fail("HANDOVER_LICENSE_SELECTION");
            }
            issued.put(l.assetId(),List.of(allocation(null,l.deviceId(),l.seats(),LicenseCodes.PER_USER))); bundle.add(l.assetId());
        }
        var result=new ArrayList<Line>();
        for(Long id:bundle) {
            var a=asset(id); stock(a); var data=audit.snapshot(a);
            data.put("typeName",a.getType().getName());
            if(a.getHardwareDetails()!=null && a.getHardwareDetails().getModel()!=null) {
                var m=a.getHardwareDetails().getModel(); data.put("modelName",m.getName());
                data.put("defaultCpu",m.getDefaultCpu()); data.put("defaultRam",m.getDefaultRam()); data.put("defaultStorage",m.getDefaultStorage()); data.put("defaultGraphicsCard",m.getDefaultGraphicsCard());
            }
            if(a.getLicenseDetails()!=null) { data.put("softwareName",a.getLicenseDetails().getSoftwareCatalog().getName()); data.put("availableSeatsBefore",a.getLicenseDetails().getSeatCount()-allocations.usedSeats(id)); }
            var seats=issued.getOrDefault(id,List.of());
            result.add(new Line(id,a.getAssetTag(),a.getName(),category(a).name(),parents.get(id),seats.stream().mapToInt(Allocation::seats).sum(),seats,data));
        }
        var response=new HandoverResponse(null,null,null,recipient.getUserId(),recipient.getFullName(),recipient.getEmail(),location.getLocationId(),location.getName(),req.handoverDate(),req.notes(),null,result);
        return new HandoverResponse(null,null,null,response.recipientUserId(),response.recipientName(),response.recipientEmail(),response.destinationLocationId(),response.destinationLocationName(),response.handoverDate(),response.notes(),hash(snapshots.encode(response)),result);
    }
    private AssetEntity asset(Long id) { return assets.findById(id).orElseThrow(()->new ResourceNotFoundException("RESOURCE_NOT_FOUND")); }
    private Allocation allocation(Long id,Long deviceId,int seats,String assignmentType) {
        var device=deviceId==null?null:asset(deviceId);
        return new Allocation(id,deviceId,seats,assignmentType,device==null?null:device.getAssetTag(),device==null?null:device.getName());
    }
    private AssetCategory category(AssetEntity a) { return a.getType().getCategory().getCode(); }
    private void stock(AssetEntity a) {
        if(a.getAssetTag()==null || a.getAssetTag().isBlank()) fail("HANDOVER_TAG_REQUIRED");
        if(a.getStatus().getCode()!=AssetStatus.IN_STOCK || a.getAssignedTo()!=null) fail("ASSET_NOT_AVAILABLE");
    }
    private String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch(java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private void invalid() { throw new AppException(HttpStatus.BAD_REQUEST,"TRANSACTION_REQUEST_INVALID","TRANSACTION_REQUEST_INVALID"); }
    private void fail(String code) { throw new AppException(HttpStatus.CONFLICT,code,code); }
}
