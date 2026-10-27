package com.company.itam.document.service;

import com.company.itam.common.exception.AppException;
import com.company.itam.document.entity.DocumentEntity;
import com.company.itam.user.repository.UserRepository;
import com.company.itam.workflow.core.entity.TransactionEntity;
import com.company.itam.workflow.core.enums.TransactionStatus;
import com.company.itam.workflow.core.repository.TransactionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@Transactional(readOnly=true)
public class AdjustmentService {
    private final JdbcTemplate jdbc;
    private final TransactionRepository transactions;
    private final UserRepository users;
    private final DocumentAccessService access;
    private final LocalDocumentStorage storage;
    private final ObjectMapper json;

    public AdjustmentService(JdbcTemplate jdbc, TransactionRepository transactions, UserRepository users,
            DocumentAccessService access, LocalDocumentStorage storage, ObjectMapper json) {
        this.jdbc=jdbc; this.transactions=transactions; this.users=users; this.access=access; this.storage=storage; this.json=json;
    }
    public record FileInfo(long fileId, String name, String mimeType, long size, String checksum) {}
    public record Impact(long transactionId, String transactionCode, String type, String status) {}
    public record Adjustment(long id, long transactionId, int version, String kind, String reason,
            String proposedText, Long correctiveTransactionId, long requestedBy, String requesterName,
            String createdAt, String status, Long decidedBy, String deciderName, String decidedAt,
            String decisionReason, String impactSnapshot, List<FileInfo> files) {}
    public record Overview(boolean canRequest, List<Adjustment> requests, List<Impact> subsequentTransactions, long total) {}
    public record Decision(int expectedVersion, boolean approve, String reason, List<Impact> reviewedTransactions) {}
    public record Download(FileInfo file, byte[] content) {}

    public Overview list(long transactionId, int page) {
        if(page<0 || page>1000000) throw invalid();
        var tx=read(transactionId);
        long actor=actorId();
        return new Overview(tx.getStatus()==TransactionStatus.COMPLETED && (admin() || Objects.equals(tx.getRequester().getUserId(),actor)),
                jdbc.queryForList("SELECT adjustment_id FROM transaction_adjustments WHERE transaction_id=? ORDER BY version DESC LIMIT 10 OFFSET ?",Long.class,transactionId,page*10)
                    .stream().map(this::get).toList(), impacts(transactionId).stream().filter(i -> !access.isPurchasingReader() || i.type().equals("IMPORT")).toList(), jdbc.queryForObject("SELECT count(*) FROM transaction_adjustments WHERE transaction_id=?",Long.class,transactionId));
    }

    @Transactional
    public Adjustment create(long transactionId, String kind, String reason, String proposedText,
            Long correctiveTransactionId, List<MultipartFile> files) {
        var tx=read(transactionId);
        long actor=actorId();
        if (!admin() && !Objects.equals(tx.getRequester().getUserId(),actor)) throw new AccessDeniedException("Access denied");
        if (tx.getStatus()!=TransactionStatus.COMPLETED) throw error(HttpStatus.CONFLICT,"ADJUSTMENT_COMPLETED_ONLY");
        reason=required(reason,4000); proposedText=required(proposedText,8000);
        if (kind==null || !List.of("DESCRIPTION","DOCUMENT","WORKFLOW").contains(kind)) throw invalid();
        if (files==null) files=List.of();
        if (files.size()>5 || (kind.equals("DOCUMENT") && files.isEmpty())) throw invalid();
        if (correctiveTransactionId!=null) {
            if (!kind.equals("WORKFLOW") || correctiveTransactionId==transactionId) throw invalid();
            var corrective=read(correctiveTransactionId);
            if (corrective.getCreatedAt().isBefore(tx.getCreatedAt()) || !sharesAsset(transactionId,correctiveTransactionId)) throw invalid();
        }
        // Serialize version allocation without changing the completed original.
        jdbc.queryForObject("SELECT transaction_id FROM transactions WHERE transaction_id=? FOR UPDATE",Long.class,transactionId);
        int version=jdbc.queryForObject("SELECT COALESCE(MAX(version),0)+1 FROM transaction_adjustments WHERE transaction_id=?",Integer.class,transactionId);
        Long id=jdbc.queryForObject("""
                INSERT INTO transaction_adjustments(transaction_id,version,kind,reason,proposed_text,corrective_transaction_id,requested_by)
                VALUES(?,?,?,?,?,?,?) RETURNING adjustment_id
                """,Long.class,transactionId,version,kind,reason,proposedText,correctiveTransactionId,actor);
        for (var file:files) {
            var stored=storage.store(tx.getType(),file);
            jdbc.update("""
                    INSERT INTO adjustment_files(adjustment_id,original_file_name,stored_file_name,storage_path,mime_type,file_size,checksum)
                    VALUES(?,?,?,?,?,?,?)
                    """,id,stored.originalFileName(),stored.storedFileName(),stored.storagePath(),stored.mimeType(),stored.fileSize(),stored.checksum());
        }
        var result=get(id);
        audit(result,actor,Objects.equals(tx.getRequester().getUserId(),actor)?"ADJUSTMENT_REQUESTED":"ADJUSTMENT_REQUESTED_ON_BEHALF",null,result);
        return result;
    }

    @Transactional
    public Adjustment decide(long transactionId,long id,Decision decision) {
        if (!admin()) throw new AccessDeniedException("Access denied");
        read(transactionId);
        if (decision==null || decision.expectedVersion()<1) throw invalid();
        String reason=required(decision.reason(),4000);
        var locked=jdbc.queryForList("SELECT adjustment_id FROM transaction_adjustments WHERE adjustment_id=? AND transaction_id=? FOR UPDATE",Long.class,id,transactionId);
        if (locked.isEmpty()) throw error(HttpStatus.NOT_FOUND,"ADJUSTMENT_NOT_FOUND");
        var before=get(id);
        if (!before.status().equals("PENDING") || before.version()!=decision.expectedVersion()) throw error(HttpStatus.CONFLICT,"ADJUSTMENT_STALE");
        // Existing workflow mutations take asset locks; capture the impact at this decision boundary.
        jdbc.queryForList("SELECT asset_id FROM assets WHERE asset_id IN (SELECT asset_id FROM transaction_assets WHERE transaction_id=?) ORDER BY asset_id FOR UPDATE",Long.class,transactionId);
        var impacts=impacts(transactionId);
        if (decision.reviewedTransactions()==null || !impacts.equals(decision.reviewedTransactions())) throw error(HttpStatus.CONFLICT,"ADJUSTMENT_IMPACT_CHANGED");
        long actor=actorId();
        jdbc.update("""
                UPDATE transaction_adjustments SET status=?,decided_by=?,decided_at=now(),decision_reason=?,impact_snapshot=?::jsonb
                WHERE adjustment_id=?
                """,decision.approve()?"APPROVED":"REJECTED",actor,reason,serialize(impacts),id);
        var after=get(id);
        audit(after,actor,decision.approve()?(before.requestedBy()==actor?"ADJUSTMENT_SELF_APPROVED":"ADJUSTMENT_APPROVED"):"ADJUSTMENT_REJECTED",before,after);
        return after;
    }

    public Download download(long transactionId,long requestId,long fileId) {
        var tx=read(transactionId);
        var request=get(requestId);
        if (request.transactionId()!=transactionId) throw error(HttpStatus.NOT_FOUND,"ADJUSTMENT_NOT_FOUND");
        var file=request.files().stream().filter(f->f.fileId()==fileId).findFirst().orElseThrow(()->error(HttpStatus.NOT_FOUND,"DOCUMENT_NOT_FOUND"));
        var paths=jdbc.queryForMap("SELECT storage_path,stored_file_name FROM adjustment_files WHERE file_id=?",fileId);
        var document=new DocumentEntity();
        document.setTransaction(tx); document.setStoragePath((String)paths.get("storage_path"));
        document.setStoredFileName((String)paths.get("stored_file_name")); document.setFileSize(file.size()); document.setChecksum(file.checksum());
        return new Download(file,storage.read(document));
    }

    private TransactionEntity read(long id) {
        access.requireTransactionReader();
        var tx=transactions.findById(id).orElseThrow(()->error(HttpStatus.NOT_FOUND,"TRANSACTION_NOT_FOUND"));
        access.requireRead(tx); return tx;
    }
    private Adjustment get(long id) {
        var rows=jdbc.query("""
                SELECT a.*,u.full_name requester_name,d.full_name decider_name,
                CASE WHEN ? AND c.type<>'IMPORT' THEN NULL ELSE a.corrective_transaction_id END safe_corrective,
                CASE WHEN ? AND a.impact_snapshot IS NOT NULL THEN (SELECT COALESCE(jsonb_agg(x),'[]'::jsonb) FROM jsonb_array_elements(a.impact_snapshot) x WHERE x->>'type'='IMPORT') ELSE a.impact_snapshot END safe_impact
                FROM transaction_adjustments a LEFT JOIN transactions c ON c.transaction_id=a.corrective_transaction_id
                JOIN users u ON u.user_id=a.requested_by LEFT JOIN users d ON d.user_id=a.decided_by WHERE adjustment_id=?
                """,(rs,n)->new Adjustment(rs.getLong("adjustment_id"),rs.getLong("transaction_id"),rs.getInt("version"),rs.getString("kind"),
                rs.getString("reason"),rs.getString("proposed_text"),rs.getObject("safe_corrective",Long.class),rs.getLong("requested_by"),rs.getString("requester_name"),
                rs.getTimestamp("created_at").toInstant().toString(),rs.getString("status"),rs.getObject("decided_by",Long.class),rs.getString("decider_name"),
                rs.getTimestamp("decided_at")==null?null:rs.getTimestamp("decided_at").toInstant().toString(),rs.getString("decision_reason"),rs.getString("safe_impact"),files(id)),access.isPurchasingReader(),access.isPurchasingReader(),id);
        if(rows.isEmpty()) throw error(HttpStatus.NOT_FOUND,"ADJUSTMENT_NOT_FOUND");
        return rows.get(0);
    }
    private List<FileInfo> files(long id) {
        return jdbc.query("SELECT * FROM adjustment_files WHERE adjustment_id=? ORDER BY file_id",(rs,n)->new FileInfo(rs.getLong("file_id"),rs.getString("original_file_name"),rs.getString("mime_type"),rs.getLong("file_size"),rs.getString("checksum")),id);
    }
    private List<Impact> impacts(long id) {
        // Include later pending/draft activity as well as completion; never reverse any transaction.
        return jdbc.query("""
                SELECT DISTINCT t.transaction_id,t.transaction_code,t.type,t.status FROM transactions t
                JOIN transaction_assets a ON a.transaction_id=t.transaction_id
                JOIN transaction_assets original_asset ON original_asset.asset_id=a.asset_id
                JOIN transactions original ON original.transaction_id=original_asset.transaction_id
                WHERE original.transaction_id=? AND t.transaction_id<>original.transaction_id
                AND (t.created_at>=COALESCE(original.completed_at,original.created_at) OR t.completed_at>COALESCE(original.completed_at,original.created_at))
                ORDER BY t.transaction_id
                """,(rs,n)->new Impact(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getString(4)),id);
    }
    private boolean sharesAsset(long a,long b) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM transaction_assets a JOIN transaction_assets b USING(asset_id) WHERE a.transaction_id=? AND b.transaction_id=?)",Boolean.class,a,b));
    }
    private long actorId() { return users.findByPrincipal(SecurityContextHolder.getContext().getAuthentication().getName()).orElseThrow(()->new AccessDeniedException("Access denied")).getUserId(); }
    private boolean admin() { var auth=SecurityContextHolder.getContext().getAuthentication(); return auth!=null && auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ADMIN")); }
    private String required(String value,int max) { if(value==null || value.isBlank() || value.length()>max) throw invalid(); return value.trim(); }
    private AppException invalid() { return error(HttpStatus.BAD_REQUEST,"ADJUSTMENT_INVALID"); }
    private AppException error(HttpStatus status,String code) { return new AppException(status,code,code); }
    private String serialize(Object value) { try { return json.writeValueAsString(value); } catch(JsonProcessingException e) { throw new IllegalStateException(e); } }
    private void audit(Adjustment request,long actor,String action,Object before,Object after) {
        jdbc.update("""
                INSERT INTO audit_logs(entity_type,entity_id,transaction_id,actor_user_id,action,old_data,new_data,details)
                VALUES('TRANSACTION',?,?,?,?,?::jsonb,?::jsonb,?)
                """,request.transactionId(),request.transactionId(),actor,action,serialize(before),serialize(after),request.reason());
    }
}





