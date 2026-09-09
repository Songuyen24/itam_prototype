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
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    public ImportDraftService(TransactionRepository transactions, DocumentAccessService access,
            UserRepository users, JdbcTemplate jdbc, ObjectMapper mapper, AssetService assets) {
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
        access.requireImportEditor(t);
        if (version == null || version != t.getContentVersion()) throw error("TRANSACTION_VERSION_CONFLICT");
        return t;
    }

    public void changed(TransactionEntity t, String action) {
        t.setContentVersion(t.getContentVersion() + 1);
        t.setUpdatedAt(Instant.now());
        transactions.saveAndFlush(t);
        jdbc.update("INSERT INTO audit_logs(entity_type,entity_id,transaction_id,actor_user_id,action,details) VALUES ('TRANSACTION',?,?,?,?,?)",
                t.getTransactionId(), t.getTransactionId(), actor().getUserId(), action,
                "contentVersion=" + t.getContentVersion() + "; submittedRevision=" + t.getSubmittedRevision());
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
            jdbc.update("UPDATE transactions SET source_transaction_id=? WHERE transaction_id=?", sourceId, t.getTransactionId());
            jdbc.update("INSERT INTO transaction_document_links SELECT ?, document_id FROM transaction_document_links WHERE transaction_id=?", t.getTransactionId(), sourceId);
            jdbc.update("INSERT INTO transaction_assets(transaction_id,asset_id,line_number,draft_data,notes) SELECT ?,asset_id,line_number,draft_data,notes FROM transaction_assets WHERE transaction_id=?", t.getTransactionId(), sourceId);
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
        return jdbc.queryForList("SELECT t.type_id AS id,t.name,t.code,c.code AS category FROM asset_types t JOIN asset_categories c USING(category_id) WHERE t.is_active AND c.code = 'DEVICE' ORDER BY t.name");
    }

    public TransactionSummaryResponse addHardware(Long id, Long version, CreateHardwareAssetRequest request) {
        var t = lockDraft(id, version);
        request.setAssignedToUserId(null);
        request.setStatusId(jdbc.queryForObject("SELECT status_id FROM asset_statuses WHERE code='PENDING_IMPORT'", Long.class));
        var asset = assets.createHardwareDraft(request);
        Integer nextLine = jdbc.queryForObject("SELECT coalesce(max(line_number),0)+1 FROM transaction_assets WHERE transaction_id=?", Integer.class, id);
        jdbc.update("INSERT INTO transaction_assets(transaction_id,asset_id,line_number) VALUES (?,?,?)",id,asset.getAssetId(),nextLine);
        changed(t,"ADD_DRAFT_ASSET");
        return TransactionSummaryResponse.fromEntity(t);
    }

    @Transactional(readOnly = true)
    public JsonNode content(Long id) {
        var t = transactions.findById(id).orElseThrow(() -> error("TRANSACTION_NOT_FOUND")); access.requireRead(t);
        if (t.getStatus()!=TransactionStatus.DRAFT && t.getSubmittedRevision()>0) {
            return parse(jdbc.queryForObject("SELECT snapshot::text FROM transaction_revisions WHERE transaction_id=? AND revision=?",String.class,id,t.getSubmittedRevision()));
        }
        return snapshot(id);
    }

    public TransactionSummaryResponse submit(Long id, Long version) {
        var t = lockDraft(id, version);
        List<Long> ids = jdbc.queryForList("SELECT asset_id FROM transaction_assets WHERE transaction_id=? ORDER BY asset_id",Long.class,id);
        if (ids.isEmpty()) throw error("IMPORT_EMPTY");
        for (Long assetId : ids) {
            String status = jdbc.queryForObject("SELECT s.code FROM assets a JOIN asset_statuses s USING(status_id) WHERE a.asset_id=? FOR UPDATE OF a",String.class,assetId);
            if (!"PENDING_IMPORT".equals(status) || Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM transaction_assets a JOIN transactions t USING(transaction_id) WHERE a.asset_id=? AND t.type='IMPORT' AND t.status='COMPLETED')", Boolean.class,assetId)))
                throw error("IMPORT_ASSET_UNAVAILABLE");
            if (jdbc.update("INSERT INTO import_asset_reservations(asset_id,transaction_id) VALUES (?,?) ON CONFLICT DO NOTHING",assetId,id) != 1)
                throw error("IMPORT_ASSET_UNAVAILABLE");
        }
        int revision = t.getSubmittedRevision()+1;
        // Metadata/files remain immutable; the working links may change after withdrawal.
        jdbc.update("UPDATE documents SET is_locked=true WHERE NOT is_locked AND document_id IN (SELECT document_id FROM transaction_document_links WHERE transaction_id=?)",id);
        jdbc.update("INSERT INTO transaction_revisions(transaction_id,revision,content_version,submitted_by,snapshot) VALUES (?,?,?,?,CAST(? AS jsonb))",
                id,revision,t.getContentVersion(),actor().getUserId(),snapshot(id).toString());
        t.setSubmittedRevision(revision); t.setStatus(TransactionStatus.PENDING); changed(t,"SUBMIT");
        return TransactionSummaryResponse.fromEntity(t);
    }

    public TransactionSummaryResponse withdraw(Long id, Long version) {
        var t = lock(id,version);
        if (t.getStatus()!=TransactionStatus.PENDING) throw error("DOCUMENT_LOCKED");
        t.setStatus(TransactionStatus.DRAFT);
        jdbc.update("DELETE FROM import_asset_reservations WHERE transaction_id=?",id);
        changed(t,"WITHDRAW"); return TransactionSummaryResponse.fromEntity(t);
    }

    @Transactional(readOnly = true)
    public List<Map<String,Object>> revisions(Long id) {
        var t = transactions.findById(id).orElseThrow(() -> error("TRANSACTION_NOT_FOUND")); access.requireRead(t);
        return jdbc.query("SELECT revision,content_version,submitted_at,snapshot::text FROM transaction_revisions WHERE transaction_id=? ORDER BY revision DESC",
                (r,n)->Map.of("revision",r.getInt(1),"contentVersion",r.getLong(2),"submittedAt",r.getTimestamp(3).toInstant(),"snapshot",parse(r.getString(4))),id);
    }

    private JsonNode snapshot(Long id) {
        // Explicitly omit license keys and identities outside the purchasing scope.
        return parse(jdbc.queryForObject("""
            SELECT jsonb_build_object('transactionCode',t.transaction_code,'notes',t.notes,
              'assets',coalesce((SELECT jsonb_agg(jsonb_build_object('assetId',a.asset_id,'assetTag',a.asset_tag,
                'name',a.name,'typeId',a.type_id,'assetData',to_jsonb(a),'model',to_jsonb(m),
                'type',to_jsonb(ty),'purchaseCost',a.purchase_cost,'purchaseDate',a.purchase_date,
                'hardware',to_jsonb(h),'license',to_jsonb(l)-'license_key') ORDER BY ta.line_number)
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

    private JsonNode parse(String value) {
        try { return mapper.readTree(value); } catch (Exception ex) { throw new IllegalStateException(ex); }
    }
    private AppException error(String code) { return new AppException(HttpStatus.CONFLICT,code,code); }
}
