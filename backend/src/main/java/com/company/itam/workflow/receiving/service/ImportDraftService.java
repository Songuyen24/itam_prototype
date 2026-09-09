package com.company.itam.workflow.receiving.service;

import com.company.itam.asset.dto.request.CreateHardwareAssetRequest;
import com.company.itam.asset.service.AssetService;
import com.company.itam.common.exception.AppException;
import com.company.itam.document.service.DocumentAccessService;
import com.company.itam.user.entity.UserEntity;
import com.company.itam.user.repository.UserRepository;
import com.company.itam.workflow.core.dto.TransactionSummaryResponse;
import com.company.itam.workflow.core.entity.TransactionEntity;
import com.company.itam.workflow.core.enums.*;
import com.company.itam.workflow.core.repository.TransactionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

/** Draft/revision foundation shared by document writes and the receiving workflow. */
@Service
@Transactional
public class ImportDraftService {
    private final TransactionRepository transactions;
    private final DocumentAccessService access;
    private final UserRepository users;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final AssetService assets;
    private final ReceivingLineService lines;
    private final com.company.itam.document.repository.DocumentRepository documentRepository;
    private final com.company.itam.document.service.LocalDocumentStorage storage;
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    public ImportDraftService(TransactionRepository transactions, DocumentAccessService access,
            UserRepository users, JdbcTemplate jdbc, ObjectMapper mapper, AssetService assets, ReceivingLineService lines, com.company.itam.document.repository.DocumentRepository documentRepository, com.company.itam.document.service.LocalDocumentStorage storage) {
        this.documentRepository=documentRepository; this.storage=storage;
        this.lines=lines;
        this.transactions = transactions; this.access = access; this.users = users;
        this.jdbc = jdbc; this.mapper = mapper; this.assets = assets;
    }

    public UserEntity actor() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) throw error("UNAUTHORIZED");
        return users.findByEmail(authentication.getName()).orElseThrow(() -> error("UNAUTHORIZED"));
    }

    public TransactionEntity lockDraft(Long id, Long version) {
        TransactionEntity t = transactions.findForUpdate(id).orElseThrow(() -> error("TRANSACTION_NOT_FOUND"));
        // A document permission lookup may already have loaded this transaction.
        // Refresh AFTER acquiring the row lock: first-level cache must not validate stale state.
        entityManager.refresh(t, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        access.requireImportEditor(t);
        if (t.getStatus() != TransactionStatus.DRAFT) throw error("DOCUMENT_LOCKED");
        if (version == null || version != t.getContentVersion()) throw error("TRANSACTION_VERSION_CONFLICT");
        return t;
    }

    private TransactionEntity lock(Long id, Long version) {
        TransactionEntity t = transactions.findForUpdate(id).orElseThrow(() -> error("TRANSACTION_NOT_FOUND"));
        entityManager.refresh(t, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        requireImportRead(t);
        if (version == null || version != t.getContentVersion()) throw error("TRANSACTION_VERSION_CONFLICT");
        return t;
    }

    public void changed(TransactionEntity t, String action) {
        t.setContentVersion(t.getContentVersion() + 1);
        t.setUpdatedAt(Instant.now());
        transactions.saveAndFlush(t);
        String previousStatus=switch(action) {case "SUBMIT"->"DRAFT";case "WITHDRAW","APPROVE","REJECT"->"PENDING";default->"DRAFT";};
        jdbc.update("INSERT INTO audit_logs(entity_type,entity_id,transaction_id,actor_user_id,action,details,old_data,new_data) VALUES ('TRANSACTION',?,?,?,?,?,jsonb_build_object('status',?::text),jsonb_build_object('status',?::text))",
                t.getTransactionId(), t.getTransactionId(), actor().getUserId(), action,
                "contentVersion=" + t.getContentVersion() + "; submittedRevision=" + t.getSubmittedRevision() + "; status=" + t.getStatus(),previousStatus,t.getStatus().name());
    }

    public TransactionSummaryResponse create(String notes, Long sourceId) {
        TransactionEntity t = new TransactionEntity();
        t.setType(TransactionType.IMPORT);
        access.requireImportEditor(t);
        t.setTransactionCode("IMP-" + UUID.randomUUID());
        t.setStatus(TransactionStatus.DRAFT);
        t.setRequester(actor());
        t.setNotes(notes);
        transactions.saveAndFlush(t);
        if (sourceId != null) {
            TransactionEntity source = transactions.findById(sourceId).orElseThrow(() -> error("TRANSACTION_NOT_FOUND"));
            access.requireRead(source);
            if (source.getType() != TransactionType.IMPORT || source.getStatus() != TransactionStatus.REJECTED)
                throw error("TRANSACTION_REQUEST_INVALID");
            if (notes == null) t.setNotes(source.getNotes());
            for(Long assetId:jdbc.queryForList("SELECT asset_id FROM transaction_assets WHERE transaction_id=?",Long.class,sourceId)) {
                requireAvailable(assetId,null);
            }
            jdbc.update("UPDATE transactions SET source_transaction_id=? WHERE transaction_id=?", sourceId, t.getTransactionId());
            jdbc.update("INSERT INTO transaction_document_links SELECT ?, document_id FROM transaction_document_links WHERE transaction_id=?", t.getTransactionId(), sourceId);
            jdbc.update("INSERT INTO transaction_assets(transaction_id,asset_id,line_number,draft_data,notes) SELECT ?,asset_id,line_number,draft_data,notes FROM transaction_assets WHERE transaction_id=?", t.getTransactionId(), sourceId);
        }
        if(sourceId!=null) {
            for(Long assetId:jdbc.queryForList("SELECT asset_id FROM transaction_assets WHERE transaction_id=? AND draft_data IS NULL",Long.class,t.getTransactionId())) lines.save(t.getTransactionId(),assetId,lines.current(assetId));
        }
        changed(t, "CREATE_DRAFT");
        return TransactionSummaryResponse.fromEntity(t);
    }

    public TransactionSummaryResponse updateNotes(Long id, Long version, String notes) {
        var t = lockDraft(id, version); t.setNotes(notes); changed(t, "EDIT_DRAFT");
        return TransactionSummaryResponse.fromEntity(t);
    }

    public List<Map<String,Object>> options() {
        var probe = new TransactionEntity(); probe.setType(TransactionType.IMPORT); access.requireImportEditor(probe);
        return jdbc.queryForList("SELECT t.type_id AS id,t.name,t.code,c.code AS category FROM asset_types t JOIN asset_categories c USING(category_id) WHERE t.is_active AND c.is_active ORDER BY t.name");
    }

    public TransactionSummaryResponse addHardware(Long id, Long version, CreateHardwareAssetRequest request) {
        var t = lockDraft(id, version);
        request.setAssignedToUserId(null);
        request.setStatusId(jdbc.queryForObject("SELECT status_id FROM asset_statuses WHERE code='PENDING_IMPORT'", Long.class));
        lines.describe(null,mapper.valueToTree(request),false);
        var asset = assets.createHardwareDraft(request);
        entityManager.flush();
        Integer nextLine = jdbc.queryForObject("SELECT coalesce(max(line_number),0)+1 FROM transaction_assets WHERE transaction_id=?", Integer.class, id);
        jdbc.update("INSERT INTO transaction_assets(transaction_id,asset_id,line_number) VALUES (?,?,?)",id,asset.getAssetId(),nextLine);
        lines.save(id,asset.getAssetId(),lines.current(asset.getAssetId()));
        changed(t,"ADD_DRAFT_ASSET");
        return TransactionSummaryResponse.fromEntity(t);
    }

    @Transactional(readOnly = true)
    public JsonNode content(Long id) {
        var t = transactions.findById(id).orElseThrow(() -> error("TRANSACTION_NOT_FOUND")); requireImportRead(t);
        if (t.getStatus()!=TransactionStatus.DRAFT && t.getSubmittedRevision()>0) {
            return parse(jdbc.queryForObject("SELECT snapshot::text FROM transaction_revisions WHERE transaction_id=? AND revision=?",String.class,id,t.getSubmittedRevision()));
        }
        var result=(com.fasterxml.jackson.databind.node.ObjectNode)snapshot(id);
        var working=mapper.createArrayNode();
        result.path("assets").forEach(line -> working.add(line.has("input")?line:lines.current(line.path("assetId").asLong())));
        result.set("assets",working);
        return result;
    }

    public TransactionSummaryResponse submit(Long id, Long version) {
        var t = lockDraft(id, version);
        List<Long> ids = jdbc.queryForList("SELECT asset_id FROM transaction_assets WHERE transaction_id=? ORDER BY asset_id",Long.class,id);
        if (ids.isEmpty()) throw error("IMPORT_EMPTY");
        requireDocuments(id);
        for (Long assetId : ids) {
            String status = jdbc.queryForObject("SELECT s.code FROM assets a JOIN asset_statuses s USING(status_id) WHERE a.asset_id=? FOR UPDATE OF a",String.class,assetId);
            if (!"PENDING_IMPORT".equals(status) || Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM transaction_assets a JOIN transactions t USING(transaction_id) WHERE a.asset_id=? AND t.type='IMPORT' AND t.status='COMPLETED')", Boolean.class,assetId)))
                throw error("IMPORT_ASSET_UNAVAILABLE");
            if (jdbc.update("INSERT INTO import_asset_reservations(asset_id,transaction_id) VALUES (?,?) ON CONFLICT DO NOTHING",assetId,id) != 1)
                throw error("IMPORT_ASSET_UNAVAILABLE");
        }
        for(Long assetId:ids) {
            if(!Objects.equals(lines.load(id,assetId).path("assetTag").asText(),jdbc.queryForObject("SELECT asset_tag FROM assets WHERE asset_id=?",String.class,assetId))) throw error("IMPORT_ASSET_UNAVAILABLE");
            lines.apply(id,assetId);
        }
        entityManager.flush();
        int revision = t.getSubmittedRevision()+1;
        // Metadata/files remain immutable; the working links may change after withdrawal.
        jdbc.update("UPDATE documents SET is_locked=true WHERE NOT is_locked AND document_id IN (SELECT document_id FROM transaction_document_links WHERE transaction_id=?)",id);
        jdbc.update("INSERT INTO transaction_revisions(transaction_id,revision,content_version,submitted_by,snapshot) VALUES (?,?,?,?,CAST(? AS jsonb))",
                id,revision,t.getContentVersion(),actor().getUserId(),snapshot(id).toString());
        t.setSubmittedRevision(revision); t.setStatus(TransactionStatus.PENDING); changed(t,"SUBMIT");
        event(t,"SUBMITTED",null);
        return TransactionSummaryResponse.fromEntity(t);
    }

    public TransactionSummaryResponse withdraw(Long id, Long version) {
        var t = lock(id,version);
        access.requireImportEditor(t);
        if (t.getStatus()!=TransactionStatus.PENDING) throw error("DOCUMENT_LOCKED");
        t.setStatus(TransactionStatus.DRAFT);
        jdbc.update("DELETE FROM import_asset_reservations WHERE transaction_id=?",id);
        changed(t,"WITHDRAW"); event(t,"WITHDRAWN",null); return TransactionSummaryResponse.fromEntity(t);
    }

    @Transactional(readOnly = true)
    public List<Map<String,Object>> revisions(Long id) {
        var t = transactions.findById(id).orElseThrow(() -> error("TRANSACTION_NOT_FOUND")); requireImportRead(t);
        return jdbc.query("SELECT revision,content_version,submitted_at,snapshot::text,u.full_name FROM transaction_revisions r JOIN users u ON u.user_id=r.submitted_by WHERE transaction_id=? ORDER BY revision DESC",
                (r,n)->Map.of("revision",r.getInt(1),"contentVersion",r.getLong(2),"submittedAt",r.getTimestamp(3).toInstant(),"snapshot",parse(r.getString(4)),"submittedBy",r.getString(5)),id);
    }

    private JsonNode snapshot(Long id) {
        // Explicitly omit license keys and identities outside the purchasing scope.
        return parse(jdbc.queryForObject("""
            SELECT jsonb_build_object('transactionCode',t.transaction_code,'notes',t.notes,'sourceReceivingId',t.source_transaction_id,
              'lastEditedBy',(SELECT u.full_name FROM audit_logs al JOIN users u ON u.user_id=al.actor_user_id WHERE al.transaction_id=t.transaction_id ORDER BY al.audit_log_id DESC LIMIT 1),
              'assets',coalesce((SELECT jsonb_agg(coalesce(ta.draft_data,jsonb_build_object('assetId',a.asset_id,'assetTag',a.asset_tag,
                'name',a.name,'typeId',a.type_id,'assetData',to_jsonb(a),'model',to_jsonb(m),
                'type',to_jsonb(ty),'purchaseCost',a.purchase_cost,'purchaseDate',a.purchase_date,
                'hardware',to_jsonb(h),'license',to_jsonb(l)-'license_key')) ORDER BY ta.line_number)
                FROM transaction_assets ta JOIN assets a USING(asset_id)
                LEFT JOIN asset_hardware_details h USING(asset_id) LEFT JOIN asset_license_details l USING(asset_id)
                LEFT JOIN models m ON m.model_id=h.model_id LEFT JOIN asset_types ty ON ty.type_id=a.type_id
                WHERE ta.transaction_id=t.transaction_id),'[]'::jsonb),
              'documents',coalesce((SELECT jsonb_agg(jsonb_build_object('documentId',d.document_id,
                'transactionId',d.transaction_id,'originalFileName',d.original_file_name,'documentType',d.document_type,
                'mimeType',d.mime_type,'fileSize',d.file_size,'checksum',d.checksum,'uploadedByName',u.full_name,
                'createdAt',d.created_at,'locked',true) ORDER BY d.document_id)
                FROM transaction_document_links dl JOIN documents d USING(document_id) JOIN users u ON u.user_id=d.uploaded_by
                WHERE dl.transaction_id=t.transaction_id),'[]'::jsonb))::text
            FROM transactions t WHERE transaction_id=?
            """,String.class,id));
    }

    private void requireImportRead(TransactionEntity t) {
        access.requireRead(t);
        if(t.getType()!=TransactionType.IMPORT) throw error("TRANSACTION_REQUEST_INVALID");
    }

    private void requireDocuments(Long id) {
        var ids=jdbc.queryForList("SELECT document_id FROM transaction_document_links WHERE transaction_id=?",Long.class,id);
        if(ids.isEmpty()) throw error("IMPORT_DOCUMENT_REQUIRED");
        for(var document:documentRepository.findAllById(ids)) storage.read(document);
    }

    private void requireAvailable(Long assetId, Long owner) {
        var status=jdbc.queryForList("SELECT s.code FROM assets a JOIN asset_statuses s USING(status_id) WHERE a.asset_id=? FOR UPDATE OF a",String.class,assetId);
        if(status.isEmpty() || !"PENDING_IMPORT".equals(status.getFirst()) || Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM transaction_assets a JOIN transactions t USING(transaction_id) WHERE a.asset_id=? AND t.type='IMPORT' AND (t.status='COMPLETED' OR (t.status='PENDING' AND (?::bigint IS NULL OR t.transaction_id<>?))))",Boolean.class,assetId,owner,owner)))
            throw error("IMPORT_ASSET_UNAVAILABLE");
    }

    public TransactionSummaryResponse editLine(Long id, Long version, Long assetId, CreateHardwareAssetRequest request) {
        var t=lockDraft(id,version);
        var old=lines.load(id,assetId);
        // Blank/missing tag preserves the official identity; never allocates another tag on update.
        if(request.getAssetTag()==null || request.getAssetTag().isBlank()) request.setAssetTag(old.path("assetTag").asText());
        var line=lines.describe(assetId,mapper.valueToTree(request),false);
        String tag=jdbc.queryForObject("SELECT asset_tag FROM assets WHERE asset_id=?",String.class,assetId);
        if(!Objects.equals(tag,request.getAssetTag())) {
            requireAvailable(assetId,null);
            jdbc.queryForList("SELECT pg_advisory_xact_lock(74101301)");
            jdbc.update("UPDATE assets SET asset_tag=?,updated_by=?,updated_at=now() WHERE asset_id=?",request.getAssetTag(),actor().getUserId(),assetId);
            jdbc.update("INSERT INTO audit_logs(entity_type,entity_id,transaction_id,actor_user_id,action,old_data,new_data) VALUES ('ASSET',?,?,?,'RENAME_TAG',jsonb_build_object('assetTag',?::text),jsonb_build_object('assetTag',?::text))",assetId,id,actor().getUserId(),tag,request.getAssetTag());
        }
        lines.save(id,assetId,line); changed(t,"EDIT_DRAFT_ASSET");
        return TransactionSummaryResponse.fromEntity(t);
    }

    public TransactionSummaryResponse removeLine(Long id,Long version,Long assetId) {
        var t=lockDraft(id,version);
        if(jdbc.update("DELETE FROM transaction_assets WHERE transaction_id=? AND asset_id=?",id,assetId)!=1) throw error("TRANSACTION_REQUEST_INVALID");
        changed(t,"REMOVE_DRAFT_ASSET"); return TransactionSummaryResponse.fromEntity(t);
    }

    public TransactionSummaryResponse reuse(Long id,Long version,Long assetId) {
        var t=lockDraft(id,version); requireAvailable(assetId,null);
        int next=jdbc.queryForObject("SELECT coalesce(max(line_number),0)+1 FROM transaction_assets WHERE transaction_id=?",Integer.class,id);
        jdbc.update("INSERT INTO transaction_assets(transaction_id,asset_id,line_number) VALUES (?,?,?)",id,assetId,next);
        lines.save(id,assetId,lines.current(assetId)); changed(t,"REUSE_DRAFT_ASSET");
        return TransactionSummaryResponse.fromEntity(t);
    }

    @Transactional(readOnly=true)
    public List<Map<String,Object>> candidates(String keyword,int page) {
        var probe=new TransactionEntity(); probe.setType(TransactionType.IMPORT); access.requireImportEditor(probe);
        return jdbc.queryForList("""
            SELECT a.asset_id AS "assetId",a.asset_tag AS "assetTag",a.name,h.serial_number AS "serialNumber"
            FROM assets a JOIN asset_statuses s USING(status_id) LEFT JOIN asset_hardware_details h USING(asset_id)
            WHERE s.code='PENDING_IMPORT' AND (a.asset_tag ILIKE ? OR a.name ILIKE ? OR h.serial_number ILIKE ?)
            AND NOT EXISTS(SELECT 1 FROM transaction_assets ta JOIN transactions t USING(transaction_id)
              WHERE ta.asset_id=a.asset_id AND t.type='IMPORT' AND t.status IN ('PENDING','COMPLETED'))
            ORDER BY a.asset_id LIMIT 20 OFFSET ?
            ""","%"+keyword+"%","%"+keyword+"%","%"+keyword+"%",Math.max(0,page)*20);
    }

    public TransactionSummaryResponse withdraw(Long id,Long version,Integer revision) {
        var t=lock(id,version); checkRevision(t,revision); return withdraw(id,version);
    }

    public TransactionSummaryResponse process(Long id,Long version,Integer revision,boolean approve,String reason) {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if(auth==null || auth.getAuthorities().stream().noneMatch(a->Set.of("ADMIN","IT_STAFF").contains(a.getAuthority())))
            throw new org.springframework.security.access.AccessDeniedException("Access denied");
        var t=lock(id,version); checkRevision(t,revision);
        if(t.getStatus()!=TransactionStatus.PENDING) throw error("DOCUMENT_LOCKED");
        if(!approve && (reason==null || reason.isBlank())) throw error("REJECTION_REASON_REQUIRED");
        List<Long> ids=jdbc.queryForList("SELECT asset_id FROM transaction_assets WHERE transaction_id=? ORDER BY asset_id",Long.class,id);
        if(approve) {
            if(ids.isEmpty()) throw error("IMPORT_EMPTY"); requireDocuments(id);
            JsonNode frozen=content(id);
            Set<Long> frozenIds=new HashSet<>();
            for(JsonNode line:frozen.path("assets")) {
                frozenIds.add(line.path("assetId").asLong());
                if(line.path("assetTag").asText().isBlank() || line.path("name").asText().isBlank()) throw error("VALIDATION_ERROR");
                JsonNode input=line.path("input");
                if(input.isMissingNode()) throw error("TRANSACTION_VERSION_CONFLICT");
                if("LICENSE".equals(line.path("category").asText())) {
                    if(input.path("license").path("seatCount").asInt()<1) throw error("LICENSE_DETAILS_REQUIRED");
                } else if(!input.path("conditionId").canConvertToLong()) throw error("IMPORT_CONDITION_REQUIRED");
            }
            if(!frozenIds.equals(new HashSet<>(ids))) throw error("TRANSACTION_VERSION_CONFLICT");
            for(Long assetId:ids) {
                requireAvailable(assetId,id);
                Long reservation=jdbc.queryForObject("SELECT transaction_id FROM import_asset_reservations WHERE asset_id=?",Long.class,assetId);
                if(!id.equals(reservation)) throw error("IMPORT_ASSET_UNAVAILABLE");
                jdbc.update("UPDATE assets SET status_id=(SELECT status_id FROM asset_statuses WHERE code='IN_STOCK'),assigned_to=NULL,updated_by=?,updated_at=now() WHERE asset_id=?",actor().getUserId(),assetId);
                jdbc.update("INSERT INTO audit_logs(entity_type,entity_id,transaction_id,actor_user_id,action,old_data,new_data,details) VALUES ('ASSET',?,?,?,'IMPORT',?::jsonb,?::jsonb,?)",assetId,id,actor().getUserId(),"{\"status\":\"PENDING_IMPORT\"}","{\"status\":\"IN_STOCK\"}","revision="+revision);
            }
        }
        t.setStatus(approve?TransactionStatus.COMPLETED:TransactionStatus.REJECTED);
        t.setProcessedBy(actor()); t.setProcessedAt(Instant.now());
        t.setCompletedAt(approve?t.getProcessedAt():null); t.setRejectionReason(approve?null:reason.trim());
        changed(t,approve?"APPROVE":"REJECT"); event(t,approve?"COMPLETED":"REJECTED",t.getRejectionReason());
        return TransactionSummaryResponse.fromEntity(t);
    }

    private void checkRevision(TransactionEntity t,Integer revision) {
        if(revision==null || revision!=t.getSubmittedRevision() || revision<1) throw error("TRANSACTION_VERSION_CONFLICT");
    }
    private void event(TransactionEntity t,String event,String reason) {
        jdbc.update("INSERT INTO receiving_events(transaction_id,revision,event_type,actor_id,reason) VALUES (?,?,?,?,?)",t.getTransactionId(),t.getSubmittedRevision(),event,actor().getUserId(),reason);
    }
    @Transactional(readOnly=true)
    public List<Map<String,Object>> events(Long id) {
        requireImportRead(transactions.findById(id).orElseThrow(()->error("TRANSACTION_NOT_FOUND")));
        return jdbc.queryForList("SELECT e.revision,e.event_type AS action,e.occurred_at AS time,e.reason,u.full_name AS actor FROM receiving_events e JOIN users u ON u.user_id=e.actor_id WHERE e.transaction_id=? ORDER BY e.event_id",id);
    }
    @Transactional(readOnly=true)
    public Map<String,Object> referenceData() {
        var probe=new TransactionEntity();probe.setType(TransactionType.IMPORT);access.requireRead(probe);
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("types",jdbc.queryForList("SELECT t.type_id AS id,t.name,t.code,c.code AS category FROM asset_types t JOIN asset_categories c USING(category_id) WHERE t.is_active AND c.is_active ORDER BY t.name"));
        for(var entry:Map.of("conditions","asset_conditions:condition_id","locations","locations:location_id","departments","departments:department_id","suppliers","suppliers:supplier_id","software","software_catalog:software_catalog_id","assignments","license_assignment_types:license_assignment_type_id","terms","license_term_types:license_term_type_id").entrySet()) {
            var parts=entry.getValue().split(":");
            result.put(entry.getKey(),jdbc.queryForList("SELECT "+parts[1]+" AS id,name,"+(parts[0].equals("software_catalog")?"NULL AS code":"code")+" FROM "+parts[0]+" WHERE is_active ORDER BY name"));
        }
        result.put("models",jdbc.queryForList("SELECT model_id AS id,name,type_id AS \"typeId\",default_cpu,default_ram,default_storage,default_graphics_card FROM models WHERE is_active ORDER BY name"));
        return result;
    }

    private JsonNode parse(String value) {
        try { return mapper.readTree(value); } catch (Exception ex) { throw new IllegalStateException(ex); }
    }
    private AppException error(String code) { return new AppException("TRANSACTION_NOT_FOUND".equals(code)?HttpStatus.NOT_FOUND:HttpStatus.CONFLICT,code,code); }
}
