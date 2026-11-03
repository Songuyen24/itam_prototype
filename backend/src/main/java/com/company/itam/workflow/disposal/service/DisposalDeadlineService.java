package com.company.itam.workflow.disposal.service;

import com.company.itam.common.exception.AppException;
import com.company.itam.publication.service.EmailGateway;
import com.company.itam.workflow.core.dto.WorkflowProgressModels.Group;
import com.company.itam.workflow.core.enums.TransactionType;
import com.company.itam.workflow.core.repository.TransactionRepository;
import com.company.itam.workflow.core.service.WorkflowProgressService;
import com.company.itam.user.repository.UserRepository;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service
@EnableScheduling
public class DisposalDeadlineService {
    private final TransactionRepository transactions;
    private final WorkflowProgressService progress;
    private final UserRepository users;
    private final JdbcTemplate jdbc;
    private final EmailGateway email;
    public DisposalDeadlineService(TransactionRepository transactions, WorkflowProgressService progress,
            UserRepository users, JdbcTemplate jdbc, EmailGateway email) {
        this.transactions=transactions;this.progress=progress;this.users=users;this.jdbc=jdbc;this.email=email;
    }
    public record Extension(@NotNull Long expectedVersion,@NotEmpty List<String> groupIds,
                            @NotNull LocalDate dueDate,@NotBlank @Size(max=4000) String reason) {}
    public static LocalDate addWorkingDays(LocalDate start,int days) {
        var date=start;
        while(days>0) { date=date.plusDays(1); if(date.getDayOfWeek()!=DayOfWeek.SATURDAY && date.getDayOfWeek()!=DayOfWeek.SUNDAY) days--; }
        return date;
    }
    @Transactional
    @PreAuthorize("hasAnyAuthority('ADMIN','IT_STAFF')")
    public Map<String,Object> extend(Long id,Extension request) {
        var tx=transactions.findForUpdate(id).orElseThrow(()->new AppException(HttpStatus.NOT_FOUND,"DISPOSAL_NOT_FOUND","DISPOSAL_NOT_FOUND"));
        if(tx.getType()!=TransactionType.DISPOSAL) throw invalid();
        progress.requireVersion(tx,request.expectedVersion());
        var groups=progress.read(id);var selected=progress.requireSelection(groups,request.groupIds(),"APPROVED");
        if(request.dueDate()==null || !request.dueDate().isAfter(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")))
                || selected.stream().anyMatch(g->g.dueDate()!=null && !request.dueDate().isAfter(g.dueDate()))
                || request.reason()==null || request.reason().isBlank()) throw invalid();
        var actor=users.findByPrincipal(SecurityContextHolder.getContext().getAuthentication().getName()).orElseThrow();
        var changed=groups.stream().map(g->request.groupIds().contains(g.groupId())
                ?new Group(g.groupId(),g.status(),g.assetIds(),g.allocationIds(),g.reason(),g.approvedAt(),request.dueDate()):g).toList();
        progress.save(id,changed);progress.advance(tx);tx.setUpdatedAt(Instant.now());transactions.flush();
        for(var group:selected) jdbc.update("INSERT INTO audit_logs(entity_type,entity_id,transaction_id,actor_user_id,action,details,old_data,new_data) VALUES ('TRANSACTION',?,?,?,'DISPOSAL_DEADLINE_EXTENDED',?,jsonb_build_object('dueDate',?::text),jsonb_build_object('dueDate',?::text,'reason',?::text))",
                id,id,actor.getUserId(),group.groupId(),Objects.toString(group.dueDate(),null),request.dueDate().toString(),request.reason().trim());
        return Map.of("contentVersion",tx.getContentVersion(),"groups",changed);
    }
    @Scheduled(cron="0 0 9 * * *",zone="Asia/Ho_Chi_Minh")
    @Transactional
    public void notifyOverdue() {
        var ids=jdbc.queryForList("SELECT t.transaction_id FROM transactions t JOIN workflow_progress p USING(transaction_id) WHERE t.type='DISPOSAL' AND t.status IN ('APPROVED','PARTIAL') ORDER BY t.transaction_id",Long.class);
        var today=LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        for(var id:ids) {
            var tx=transactions.findForUpdate(id).orElseThrow();
            var recipients=jdbc.queryForList("SELECT DISTINCT u.email FROM users u JOIN roles r USING(role_id) WHERE u.email IS NOT NULL AND (r.code='ADMIN' OR u.user_id=?)",String.class,tx.getRequester().getUserId());
            for(var group:progress.read(id)) if("APPROVED".equals(group.status()) && group.dueDate()!=null && today.isAfter(group.dueDate())) {
                for(var recipient:recipients) {
                    if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM disposal_deadline_notices WHERE transaction_id=? AND group_id=? AND due_date=? AND recipient=?)",Boolean.class,id,group.groupId(),group.dueDate(),recipient))) continue;
                    String subject="Quá hạn xác nhận thanh lý / Disposal confirmation overdue: "+tx.getTransactionCode();
                    var tags=jdbc.queryForList("SELECT asset_tag FROM assets WHERE asset_id IN (SELECT jsonb_array_elements_text(?::jsonb)::bigint) ORDER BY asset_tag",String.class,group.assetIds().toString());
                    String content="Phiếu / Voucher "+tx.getTransactionCode()+", tài sản / assets "+String.join(", ",tags)+", hạn / due "+group.dueDate()+". Vui lòng xác nhận thực hiện hoặc hủy phần chưa thực hiện. Thiết bị tiếp tục được giữ. / Confirm execution or cancel the unperformed group. Assets remain reserved.";
                    try {
                        email.send(recipient,subject,content);
                        jdbc.update("INSERT INTO disposal_deadline_notices(transaction_id,group_id,due_date,recipient) VALUES (?,?,?,?) ON CONFLICT DO NOTHING",id,group.groupId(),group.dueDate(),recipient);
                        logEmail(id,recipient,subject,content,"SENT",null);
                    } catch(RuntimeException ex) { logEmail(id,recipient,subject,content,"FAILED","Delivery failed; will retry on the next scheduled run"); }
                }
            }
        }
    }
    private void logEmail(Long id,String recipient,String subject,String content,String status,String error) {
        jdbc.update("INSERT INTO email_logs(transaction_id,recipient,subject,content,status,event_type,error_message,sent_at) VALUES (?,?,?,?,?,'DISPOSAL_OVERDUE',?,CASE WHEN ?='SENT' THEN now() END)",id,recipient,subject,content,status,error,status);
    }
    private AppException invalid(){return new AppException(HttpStatus.BAD_REQUEST,"TRANSACTION_REQUEST_INVALID","TRANSACTION_REQUEST_INVALID");}
}
