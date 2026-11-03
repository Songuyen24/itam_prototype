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
import com.company.itam.workflow.core.dto.WorkflowProgressModels;
import com.company.itam.workflow.core.dto.WorkflowProgressModels.Group;
import com.company.itam.workflow.core.service.WorkflowProgressService;
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
    private final com.company.itam.publication.service.TransactionPublicationService publications;
    private final JdbcTemplate jdbc;
    private final com.company.itam.workflow.core.service.WorkflowAccessService access;
    private final com.fasterxml.jackson.databind.ObjectMapper json;
    private final WorkflowProgressService progress;

    public HandoverService(AssetRepository assets, LicenseAllocationRepository allocations, LicenseAllocationService licensing,
            AssetAuditService audit, AuditLogRepository logs, UserRepository users, LocationRepository locations,
            AssetStatusRepository statuses, TransactionRepository transactions, TransactionAssetRepository lines,
            TransactionHandoverDetailRepository details, HandoverSnapshotRepository snapshots, JdbcTemplate jdbc,
            com.company.itam.workflow.core.service.WorkflowAccessService access, com.fasterxml.jackson.databind.ObjectMapper json, com.company.itam.publication.service.TransactionPublicationService publications, WorkflowProgressService progress) {
        this.assets=assets; this.allocations=allocations; this.licensing=licensing; this.audit=audit; this.logs=logs;
        this.users=users; this.locations=locations; this.statuses=statuses; this.transactions=transactions;
        this.lines=lines; this.details=details; this.snapshots=snapshots; this.jdbc=jdbc; this.access=access; this.json=json; this.publications=publications; this.progress=progress;
    }

    public record Candidate(Long assetId,String assetTag,String name,String category,String assignmentType,Long availableSeats) {}
    public PageResponse<Candidate> candidates(String keyword,int page,int size) {
        if (page<0 || size<1 || size>100 || keyword.length()>100) invalid();
        String search=keyword.trim().toLowerCase(Locale.ROOT).replace("\\","\\\\").replace("%","\\%").replace("_","\\_");
        var reserved=jdbc.queryForList("SELECT asset_id FROM handover_asset_reservations",Long.class);
        var result=assets.findAll((root,query,b)-> {
            var rel=query.subquery(Long.class); var r=rel.from(AssetRelationshipEntity.class);
            rel.select(r.get("relationshipId")).where(b.equal(r.get("childAsset"),root));
            var category=root.get("type").get("category").get("code");
            var ld=root.join("licenseDetails",jakarta.persistence.criteria.JoinType.LEFT);
            var assignment=ld.join("assignmentType",jakarta.persistence.criteria.JoinType.LEFT);
            return b.and(reserved.isEmpty()?b.conjunction():b.not(root.get("assetId").in(reserved)),b.equal(root.get("status").get("code"),AssetStatus.IN_STOCK),b.isNull(root.get("assignedTo")),
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
        var tx=transaction(id,false);
        var result=jdbc.queryForList("SELECT result::text FROM handover_workflows WHERE transaction_id=? AND result IS NOT NULL",String.class,id);
        var snapshot=snapshots.find(id).orElseGet(()->result.isEmpty()?snapshots.decode(jdbc.queryForObject(
                "SELECT preview::text FROM handover_workflows WHERE transaction_id=?",String.class,id)):snapshots.decode(result.getFirst()));
        if(tx.getStatus()==TransactionStatus.PARTIAL) {
            var planned=snapshots.decode(jdbc.queryForObject("SELECT preview::text FROM handover_workflows WHERE transaction_id=?",String.class,id));
            var byId=new LinkedHashMap<Long,Line>();for(var line:planned.lines())byId.put(line.assetId(),line);
            for(var line:snapshot.lines())byId.put(line.assetId(),line);
            snapshot=new HandoverResponse(snapshot.transactionId(),snapshot.transactionCode(),snapshot.completedAt(),snapshot.recipientUserId(),snapshot.recipientName(),snapshot.recipientEmail(),snapshot.destinationLocationId(),snapshot.destinationLocationName(),snapshot.handoverDate(),snapshot.notes(),snapshot.fingerprint(),new ArrayList<>(byId.values()));
        }
        return decorate(tx,snapshot,loadRequest(id));
    }
    private HandoverRequest loadRequest(Long id) {
        var rows=jdbc.queryForList("SELECT request::text FROM handover_workflows WHERE transaction_id=?",String.class,id);
        if(rows.isEmpty()) return null;
        try { return json.readValue(rows.get(0),HandoverRequest.class); }
        catch(com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException(e); }
    }
    private HandoverResponse decorate(TransactionEntity tx,HandoverResponse p,HandoverRequest req) {
        var proposals=jdbc.queryForList("SELECT preview::text FROM handover_workflows WHERE transaction_id=?",String.class,tx.getTransactionId());
        var proposalLines=proposals.isEmpty()?p.lines():snapshots.decode(proposals.getFirst()).lines();
        return new HandoverResponse(tx.getTransactionId(),tx.getTransactionCode(),tx.getCompletedAt(),p.recipientUserId(),p.recipientName(),p.recipientEmail(),
            p.destinationLocationId(),p.destinationLocationName(),p.handoverDate(),p.notes(),p.fingerprint(),p.lines(),
            tx.getStatus().name(),tx.getRequester().getUserId(),tx.getRequester().getFullName(),req,p.recipientEmployeeCode(),tx.getContentVersion(),readGroups(tx),proposalLines);
    }
    private List<Group> readGroups(TransactionEntity tx) {
        var existing=progress.read(tx.getTransactionId());
        if(!existing.isEmpty()||tx.getStatus()!=TransactionStatus.PENDING)return existing;
        var preview=snapshots.decode(jdbc.queryForObject("SELECT preview::text FROM handover_workflows WHERE transaction_id=?",String.class,tx.getTransactionId()));
        var physical=new LinkedHashMap<Long,LinkedHashSet<Long>>();
        for(var line:preview.lines())if(!"LICENSE".equals(line.category()))physical.computeIfAbsent(line.parentAssetId()==null?line.assetId():line.parentAssetId(),k->new LinkedHashSet<>()).add(line.assetId());
        for(var line:preview.lines())for(var allocation:line.allocations())if(LicenseCodes.OEM.equals(allocation.assignmentType()))physical.computeIfAbsent(allocation.deviceId(),k->new LinkedHashSet<>()).add(line.assetId());
        var groups=new ArrayList<Group>();
        for(var entry:physical.entrySet()) {
            var oem=preview.lines().stream().flatMap(l->l.allocations().stream()).filter(a->LicenseCodes.OEM.equals(a.assignmentType())&&entry.getKey().equals(a.deviceId())).map(Allocation::allocationId).toList();
            groups.add(new Group("asset:"+entry.getKey(),"PENDING",List.copyOf(entry.getValue()),oem,null,null,null));
        }
        for(var row:jdbc.queryForList("SELECT allocation_id,license_asset_id,seat_count FROM license_allocations WHERE handover_transaction_id=? AND status='RESERVED' ORDER BY allocation_id",tx.getTransactionId())) {
            Long allocationId=((Number)row.get("allocation_id")).longValue(),assetId=((Number)row.get("license_asset_id")).longValue();
            int seats=((Number)row.get("seat_count")).intValue();
            for(int seat=0;seat<seats;seat++)groups.add(new Group("allocation:"+allocationId+(seats==1?"":":"+seat),"PENDING",List.of(assetId),List.of(allocationId),null,null,null));
        }
        return groups;
    }
    private void consumeReservedSeat(Long transactionId,Long allocationId) {
        var rows=jdbc.queryForList("SELECT seat_count FROM license_allocations WHERE allocation_id=? AND handover_transaction_id=? AND status='RESERVED' FOR UPDATE",Integer.class,allocationId,transactionId);
        if(rows.isEmpty())fail("HANDOVER_CHANGED");
        if(rows.getFirst()>1)jdbc.update("UPDATE license_allocations SET seat_count=seat_count-1 WHERE allocation_id=?",allocationId);
        else jdbc.update("UPDATE license_allocations SET status='RELEASED',released_at=now() WHERE allocation_id=?",allocationId);
    }
    private com.company.itam.user.entity.UserEntity actor() {
        return users.findByPrincipal(SecurityContextHolder.getContext().getAuthentication().getName()).orElseThrow();
    }
    private TransactionEntity transaction(Long id,boolean lock) {
        var tx=(lock?transactions.findForUpdate(id):transactions.findById(id)).orElseThrow(()->new ResourceNotFoundException("HANDOVER_NOT_FOUND"));
        if(tx.getType()!=TransactionType.HANDOVER) throw new ResourceNotFoundException("HANDOVER_NOT_FOUND");
        return tx;
    }
    private void record(TransactionEntity tx,String action) {
        var log=new AuditLogEntity(); log.setEntityType("TRANSACTION"); log.setEntityId(tx.getTransactionId()); log.setAction(action); log.setActor(actor());
        log.setNewData(Map.of("status",tx.getStatus().name())); logs.save(log);
    }
    @Transactional
    public HandoverResponse create(HandoverRequest req) {
        var preview=prepare(req,false);
        if(!Objects.equals(req.expectedFingerprint(),preview.fingerprint())) fail("HANDOVER_CHANGED");
        var tx=new TransactionEntity(); tx.setTransactionCode("HO-"+UUID.randomUUID());tx.setType(TransactionType.HANDOVER);
        tx.setStatus(TransactionStatus.DRAFT);tx.setRequester(actor());tx.setNotes(req.notes());transactions.saveAndFlush(tx);
        store(tx,req,preview);record(tx,"HANDOVER_CREATED");return get(tx.getTransactionId());
    }
    private void store(TransactionEntity tx,HandoverRequest req,HandoverResponse preview) {
        var d=details.findById(tx.getTransactionId()).orElseGet(TransactionHandoverDetailEntity::new);
        d.setTransaction(tx);d.setRecipient(users.findById(req.recipientUserId()).orElseThrow());
        d.setDestinationLocation(locations.findById(req.destinationLocationId()).orElseThrow());d.setHandoverDate(req.handoverDate());details.saveAndFlush(d);
        tx.setNotes(req.notes());
        jdbc.update("DELETE FROM transaction_assets WHERE transaction_id=?",tx.getTransactionId());
        int number=0;
        for(var l:preview.lines()) jdbc.update("INSERT INTO transaction_assets(transaction_id,asset_id,line_number) VALUES (?,?,?)",tx.getTransactionId(),l.assetId(),++number);
        jdbc.update("INSERT INTO handover_workflows(transaction_id,request,preview) VALUES (?,?::jsonb,?::jsonb) ON CONFLICT(transaction_id) DO UPDATE SET request=EXCLUDED.request,preview=EXCLUDED.preview",
             tx.getTransactionId(),snapshots.encode(req),snapshots.encode(preview));
        progress.save(tx.getTransactionId(),List.of());
    }
    @Transactional
    public HandoverResponse update(Long id,HandoverRequest req) {
        var tx=transaction(id,true);access.requireCreator(tx,false);
        if(tx.getStatus()!=TransactionStatus.DRAFT) fail("DOCUMENT_LOCKED");
        var preview=prepare(req,false);
        if(!Objects.equals(req.expectedFingerprint(),preview.fingerprint())) fail("HANDOVER_CHANGED");
        store(tx,req,preview);progress.advance(tx);transactions.flush();record(tx,"HANDOVER_UPDATED");return get(id);
    }
    @Transactional
    public HandoverResponse update(Long id,HandoverRequest req,Long version) {progress.requireVersion(transaction(id,true),version);return update(id,req);}
    @Transactional
    public HandoverResponse submit(Long id) {
        var tx=transaction(id,true);access.requireCreator(tx,false);
        if(tx.getStatus()==TransactionStatus.PENDING) return get(id);
        if(tx.getStatus()!=TransactionStatus.DRAFT) fail("DOCUMENT_LOCKED");
        var req=loadRequest(id);var preview=prepare(req,true);
        var reviewed=snapshots.decode(jdbc.queryForObject("SELECT preview::text FROM handover_workflows WHERE transaction_id=?",String.class,id));
        if(!Objects.equals(reviewed.fingerprint(),preview.fingerprint())) fail("HANDOVER_CHANGED");
        var reserved=new TreeSet<Long>();
        for(var l:preview.lines()) if(!"LICENSE".equals(l.category())) reserved.add(l.assetId());
        for(var l:req.licenses()) if(l.deviceId()!=null) reserved.add(l.deviceId());
        for(var assetId:reserved) {
            progress.requireAssetAvailable(assetId,id);
            if(assets.hasPendingHandover(assetId)||assets.hasPendingRecovery(assetId)) fail("ASSET_NOT_AVAILABLE");
            jdbc.update("INSERT INTO handover_asset_reservations(asset_id,transaction_id) VALUES (?,?)",assetId,id);
        }
        var groups=new ArrayList<Group>();
        var physical=new LinkedHashMap<Long,LinkedHashSet<Long>>();
        for(var line:preview.lines()) if(!"LICENSE".equals(line.category())) {
            Long root=line.parentAssetId()==null?line.assetId():line.parentAssetId();
            physical.computeIfAbsent(root,k->new LinkedHashSet<>()).add(line.assetId());
        }
        for(var line:preview.lines()) for(var allocation:line.allocations()) if(LicenseCodes.OEM.equals(allocation.assignmentType()))
            physical.computeIfAbsent(allocation.deviceId(),k->new LinkedHashSet<>()).add(line.assetId());
        for(var entry:physical.entrySet()) {
            var oem=preview.lines().stream().flatMap(l->l.allocations().stream()).filter(a->LicenseCodes.OEM.equals(a.assignmentType())&&entry.getKey().equals(a.deviceId())).map(Allocation::allocationId).toList();
            groups.add(new Group("asset:"+entry.getKey(),"PENDING",List.copyOf(entry.getValue()),oem,null,null,null));
        }
        for(var l:req.licenses()) for(int seat=0;seat<l.seats();seat++) {
            var a=new LicenseAllocationEntity();a.setLicense(asset(l.assetId()));a.setUser(users.findById(req.recipientUserId()).orElseThrow());
            a.setSeatCount(1);a.setStatus(LicenseAllocationStatus.RESERVED);a.setHandoverTransactionId(id);a.setCreatedBy(actor());allocations.saveAndFlush(a);
            groups.add(new Group("allocation:"+a.getAllocationId(),"PENDING",List.of(l.assetId()),List.of(a.getAllocationId()),null,null,null));
        }
        store(tx,req,preview);progress.save(id,groups);tx.setStatus(TransactionStatus.PENDING);progress.advance(tx);transactions.flush();record(tx,"HANDOVER_SUBMITTED");return get(id);
    }
    @Transactional
    public HandoverResponse submit(Long id,Long version) {progress.requireVersion(transaction(id,true),version);return submit(id);}
    private void release(Long id) {
        jdbc.update("DELETE FROM handover_asset_reservations WHERE transaction_id=?",id);
        jdbc.update("UPDATE license_allocations SET status='RELEASED',released_at=now() WHERE handover_transaction_id=? AND status='RESERVED'",id);
    }
    private void releaseUnusedAssets(Long id,List<Group> groups,HandoverRequest req) {
        var needed=new HashSet<Long>();
        for(var group:groups) if("PENDING".equals(group.status())) {
            for(var assetId:group.assetIds()) if(category(asset(assetId))!=AssetCategory.LICENSE)needed.add(assetId);
            if(group.groupId().startsWith("allocation:"))for(var item:req.licenses())
                if(group.assetIds().contains(item.assetId())&&item.deviceId()!=null)needed.add(item.deviceId());
        }
        for(var assetId:jdbc.queryForList("SELECT asset_id FROM handover_asset_reservations WHERE transaction_id=?",Long.class,id))
            if(!needed.contains(assetId))jdbc.update("DELETE FROM handover_asset_reservations WHERE asset_id=? AND transaction_id=?",assetId,id);
    }
    private void lockRequest(HandoverRequest req) {
        var ids=new TreeSet<Long>(req.assetIds());for(var l:links(req.assetIds())) {ids.add(l.parent());ids.add(l.child());}
        for(var l:req.licenses()) {ids.add(l.assetId());if(l.deviceId()!=null)ids.add(l.deviceId());}
        for(var id:ids) assets.lockById(id).orElseThrow();
    }
    @Transactional
    public HandoverResponse change(Long id,String action) {
        var tx=transaction(id,true);access.requireCreator(tx,"cancel".equals(action)&&tx.getStatus()==TransactionStatus.PENDING);
        if("withdraw".equals(action)&&tx.getStatus()==TransactionStatus.PENDING) {
            lockRequest(loadRequest(id));release(id);tx.setStatus(TransactionStatus.DRAFT);progress.advance(tx);
        } else if("cancel".equals(action)&&(tx.getStatus()==TransactionStatus.DRAFT||tx.getStatus()==TransactionStatus.PENDING)) {
            lockRequest(loadRequest(id));release(id);tx.setStatus(TransactionStatus.CANCELLED);progress.save(id,progress.read(id).stream().map(g->new Group(g.groupId(),"CANCELLED",g.assetIds(),g.allocationIds(),null,Instant.now(),null)).toList());progress.advance(tx);
        } else fail("DOCUMENT_LOCKED");
        transactions.flush();record(tx,"HANDOVER_"+action.toUpperCase(Locale.ROOT));return get(id);
    }
    @Transactional
    public HandoverResponse change(Long id,String action,Long version) {progress.requireVersion(transaction(id,true),version);return change(id,action);}
    @Transactional
    public HandoverResponse confirm(Long id) {
        var tx=transaction(id,true);
        if(tx.getStatus()==TransactionStatus.COMPLETED) return get(id);
        if(tx.getStatus()!=TransactionStatus.PENDING) fail("DOCUMENT_LOCKED");
        return confirm(id,new WorkflowProgressModels.Action(tx.getContentVersion(),readGroups(tx).stream().map(Group::groupId).toList(),null));
    }
    @Transactional
    public HandoverResponse confirm(Long id,Long version) {progress.requireVersion(transaction(id,true),version);return confirm(id);}
    @Transactional
    public HandoverResponse confirm(Long id,WorkflowProgressModels.Action action) {
        var tx=transaction(id,true);progress.requireVersion(tx,action.expectedVersion());
        if(tx.getStatus()!=TransactionStatus.PENDING&&tx.getStatus()!=TransactionStatus.PARTIAL)fail("DOCUMENT_LOCKED");
        var all=readGroups(tx);var selected=progress.requireSelection(all,action.groupIds(),"PENDING");
        var req=loadRequest(id);var preview=snapshots.decode(jdbc.queryForObject("SELECT preview::text FROM handover_workflows WHERE transaction_id=?",String.class,id));
        var selectedIds=selected.stream().flatMap(g->g.assetIds().stream()).collect(java.util.stream.Collectors.toSet());
        var lockIds=new TreeSet<>(selectedIds);
        for(var line:preview.lines()) if(selectedIds.contains(line.assetId())) for(var allocation:line.allocations()) if(allocation.deviceId()!=null)lockIds.add(allocation.deviceId());
        for(var idToLock:lockIds)assets.lockById(idToLock).orElseThrow();
        for(var group:selected) for(var assetId:group.assetIds()) if(category(asset(assetId))!=AssetCategory.LICENSE) {
            var owners=jdbc.queryForList("SELECT transaction_id FROM handover_asset_reservations WHERE asset_id=?",Long.class,assetId);
            if(owners.size()!=1||!owners.getFirst().equals(id))fail("HANDOVER_CHANGED");
            jdbc.update("DELETE FROM handover_asset_reservations WHERE asset_id=? AND transaction_id=?",assetId,id);
        }
        for(var group:selected) if(group.groupId().startsWith("allocation:")) {
            consumeReservedSeat(id,group.allocationIds().getFirst());
        }
        var selectedLines=selectLines(preview,selected);
        for(var line:selectedLines) if(!"LICENSE".equals(line.category()))stock(asset(line.assetId()));
        for(var line:selectedLines) for(var allocation:line.allocations()) if(LicenseCodes.PER_USER.equals(allocation.assignmentType())&&allocation.deviceId()!=null
                &&!selectedIds.contains(allocation.deviceId())) {
            var device=asset(allocation.deviceId());
            if(device.getStatus().getCode()!=AssetStatus.IN_USE||device.getAssignedTo()==null||!device.getAssignedTo().getUserId().equals(req.recipientUserId()))fail("HANDOVER_CHANGED");
        }
        var now=Instant.now();tx.setStatus(TransactionStatus.COMPLETED);tx.setProcessedBy(actor());tx.setProcessedAt(now);tx.setCompletedAt(now);transactions.flush();
        var batch=finish(req,tx,new HandoverResponse(null,null,null,preview.recipientUserId(),preview.recipientName(),preview.recipientEmail(),preview.destinationLocationId(),preview.destinationLocationName(),preview.handoverDate(),preview.notes(),preview.fingerprint(),selectedLines));
        var rows=jdbc.queryForList("SELECT result::text FROM handover_workflows WHERE transaction_id=? AND result IS NOT NULL",String.class,id);
        var accumulated=mergeLines(rows.isEmpty()?List.of():snapshots.decode(rows.getFirst()).lines(),batch.lines());
        var changed=all.stream().map(g->selected.contains(g)?new Group(g.groupId(),"COMPLETED",g.assetIds(),g.allocationIds(),null,now,null):g).toList();
        progress.save(id,changed);releaseUnusedAssets(id,changed,req);progress.advance(tx);tx.setProcessedBy(actor());tx.setProcessedAt(now);
        boolean pending=changed.stream().anyMatch(g->"PENDING".equals(g.status()));
        tx.setStatus(pending?TransactionStatus.PARTIAL:TransactionStatus.COMPLETED);
        if(pending)tx.setCompletedAt(null);else release(id);
        transactions.saveAndFlush(tx);
        var combined=new HandoverResponse(id,tx.getTransactionCode(),tx.getCompletedAt(),preview.recipientUserId(),preview.recipientName(),preview.recipientEmail(),preview.destinationLocationId(),preview.destinationLocationName(),preview.handoverDate(),preview.notes(),preview.fingerprint(),accumulated);
        jdbc.update("UPDATE handover_workflows SET result=?::jsonb WHERE transaction_id=?",snapshots.encode(combined),id);
        if(!pending){var result=get(id);snapshots.save(result);publications.afterCommit(id,()->publications.onCompleted(id,result));return result;}
        return get(id);
    }
    private List<Line> selectLines(HandoverResponse preview,List<Group> selected) {
        var result=new LinkedHashMap<Long,Line>();
        for(var group:selected) for(var assetId:group.assetIds()) {
            var source=preview.lines().stream().filter(l->l.assetId().equals(assetId)).findFirst().orElseThrow();
            if(!"LICENSE".equals(source.category())) {result.putIfAbsent(assetId,source);continue;}
            var allocations=source.allocations().stream().filter(a->group.groupId().startsWith("allocation:")
                    ?LicenseCodes.PER_USER.equals(a.assignmentType()):group.allocationIds().contains(a.allocationId())).toList();
            if(group.groupId().startsWith("allocation:"))allocations=allocations.stream().map(a->new Allocation(null,a.deviceId(),1,a.assignmentType(),a.deviceTag(),a.deviceName())).toList();
            var old=result.get(assetId);var combined=new ArrayList<Allocation>(old==null?List.of():old.allocations());combined.addAll(allocations);
            result.put(assetId,new Line(assetId,source.assetTag(),source.name(),source.category(),source.parentAssetId(),combined.stream().mapToInt(Allocation::seats).sum(),combined,source.details()));
        }
        return new ArrayList<>(result.values());
    }
    private List<Line> mergeLines(List<Line> old,List<Line> added) {
        var result=new LinkedHashMap<Long,Line>();for(var line:old)result.put(line.assetId(),line);
        for(var line:added) {
            var previous=result.get(line.assetId());
            if(previous!=null&&"LICENSE".equals(line.category())) {
                var merged=new ArrayList<>(previous.allocations());merged.addAll(line.allocations());
                line=new Line(line.assetId(),line.assetTag(),line.name(),line.category(),line.parentAssetId(),previous.seats()+line.seats(),merged,line.details());
            }
            result.put(line.assetId(),line);
        }
        return new ArrayList<>(result.values());
    }
    @Transactional
    public HandoverResponse cancelLines(Long id,WorkflowProgressModels.Action action) {
        var tx=transaction(id,true);progress.requireVersion(tx,action.expectedVersion());
        if(tx.getStatus()!=TransactionStatus.PENDING&&tx.getStatus()!=TransactionStatus.PARTIAL)fail("DOCUMENT_LOCKED");
        access.requireCreator(tx,true);
        if(action.reason()==null||action.reason().isBlank())throw new AppException(HttpStatus.BAD_REQUEST,"VALIDATION_REQUIRED","VALIDATION_REQUIRED");
        var all=readGroups(tx);var selected=progress.requireSelection(all,action.groupIds(),"PENDING");var now=Instant.now();
        for(var group:selected) {
            for(var assetId:group.assetIds()) if(category(asset(assetId))!=AssetCategory.LICENSE)
                jdbc.update("DELETE FROM handover_asset_reservations WHERE asset_id=? AND transaction_id=?",assetId,id);
            if(group.groupId().startsWith("allocation:"))for(var allocationId:group.allocationIds())consumeReservedSeat(id,allocationId);
        }
        var changed=all.stream().map(g->selected.contains(g)?new Group(g.groupId(),"CANCELLED",g.assetIds(),g.allocationIds(),action.reason(),now,null):g).toList();
        progress.save(id,changed);releaseUnusedAssets(id,changed,loadRequest(id));progress.advance(tx);
        if(changed.stream().noneMatch(g->"PENDING".equals(g.status()))) {
            release(id);boolean completed=changed.stream().anyMatch(g->"COMPLETED".equals(g.status()));
            tx.setStatus(completed?TransactionStatus.COMPLETED:TransactionStatus.CANCELLED);
            if(completed)tx.setCompletedAt(now);
        }
        transactions.saveAndFlush(tx);
        var log=new AuditLogEntity();log.setEntityType("TRANSACTION");log.setEntityId(id);log.setTransaction(tx);log.setAction("HANDOVER_REMAINDER_CANCELLED");log.setActor(actor());log.setNewData(Map.of("groupIds",action.groupIds(),"reason",action.reason(),"at",now.toString()));logs.save(log);
        if(tx.getStatus()==TransactionStatus.COMPLETED) {
            var rows=jdbc.queryForList("SELECT result::text FROM handover_workflows WHERE transaction_id=? AND result IS NOT NULL",String.class,id);
            if(!rows.isEmpty()) {
                var prior=snapshots.decode(rows.getFirst());
                var completed=new HandoverResponse(id,tx.getTransactionCode(),tx.getCompletedAt(),prior.recipientUserId(),prior.recipientName(),prior.recipientEmail(),prior.destinationLocationId(),prior.destinationLocationName(),prior.handoverDate(),prior.notes(),prior.fingerprint(),prior.lines());
                jdbc.update("UPDATE handover_workflows SET result=?::jsonb WHERE transaction_id=?",snapshots.encode(completed),id);
            }
            var result=get(id);snapshots.save(result);publications.afterCommit(id,()->publications.onCompleted(id,result));return result;
        }
        return get(id);
    }
    // Compatibility for internal workflow fixtures; HTTP creation always creates a draft.
    @Transactional
    public HandoverResponse complete(HandoverRequest request) {
        var preview=prepare(request,true);
        if(!Objects.equals(request.expectedFingerprint(),preview.fingerprint())) fail("HANDOVER_CHANGED");
        var draft=create(request);submit(draft.transactionId());return confirm(draft.transactionId());
    }
    private HandoverResponse finish(HandoverRequest request,TransactionEntity tx,HandoverResponse preview) {
        var actor=actor();var recipient=users.findById(request.recipientUserId()).orElseThrow();
        var location=locations.findById(request.destinationLocationId()).orElseThrow();var now=Instant.now();
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
                preview.destinationLocationId(),preview.destinationLocationName(),preview.handoverDate(),preview.notes(),preview.fingerprint(),completedLines,
                "COMPLETED",null,null,null,preview.recipientEmployeeCode(),0,List.of(),List.of());
        allocations.flush();
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
            progress.requireCapacity(l.assetId(),l.seats());
            if(l.deviceId()!=null) {
                var device=asset(l.deviceId());if(assets.hasPendingRecovery(l.deviceId()))fail("RECOVERY_ASSET_RESERVED");
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
        // Display metadata must not change the fingerprint format of existing drafts.
        var fingerprintSource=(com.fasterxml.jackson.databind.node.ObjectNode)json.valueToTree(response);
        fingerprintSource.remove(List.of("contentVersion","groups","proposalLines"));
        return new HandoverResponse(null,null,null,response.recipientUserId(),response.recipientName(),response.recipientEmail(),response.destinationLocationId(),response.destinationLocationName(),response.handoverDate(),response.notes(),hash(fingerprintSource.toString()),result,
                null,null,null,null,recipient.getEmployeeCode(),0,List.of(),List.of());
    }
    private AssetEntity asset(Long id) { return assets.findById(id).orElseThrow(()->new ResourceNotFoundException("RESOURCE_NOT_FOUND")); }
    private Allocation allocation(Long id,Long deviceId,int seats,String assignmentType) {
        var device=deviceId==null?null:asset(deviceId);
        return new Allocation(id,deviceId,seats,assignmentType,device==null?null:device.getAssetTag(),device==null?null:device.getName());
    }
    private AssetCategory category(AssetEntity a) { return a.getType().getCategory().getCode(); }
    private void stock(AssetEntity a) {
        if(assets.hasPendingDisposal(a.getAssetId())) fail("ASSET_NOT_AVAILABLE");
        if(category(a)!=AssetCategory.LICENSE && Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM handover_asset_reservations WHERE asset_id=?)",Boolean.class,a.getAssetId()))) fail("ASSET_NOT_AVAILABLE");
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
