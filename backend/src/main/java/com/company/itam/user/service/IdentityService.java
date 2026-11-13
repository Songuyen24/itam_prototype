package com.company.itam.user.service;

import com.company.itam.audit.entity.AuditLogEntity;
import com.company.itam.audit.repository.AuditLogRepository;
import com.company.itam.common.enums.*;
import com.company.itam.common.exception.AppException;
import com.company.itam.department.repository.DepartmentRepository;
import com.company.itam.role.repository.RoleRepository;
import com.company.itam.user.entity.UserEntity;
import com.company.itam.user.repository.UserRepository;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@Transactional
public class IdentityService {
    private final UserRepository users;
    private final DepartmentRepository departments;
    private final RoleRepository roles;
    private final AuditLogRepository audit;
    private final PasswordEncoder passwords;
    private final JdbcTemplate jdbc;
    public IdentityService(UserRepository users, DepartmentRepository departments, RoleRepository roles,
            AuditLogRepository audit, PasswordEncoder passwords, JdbcTemplate jdbc) {
        this.users=users; this.departments=departments; this.roles=roles; this.audit=audit; this.passwords=passwords; this.jdbc=jdbc;
    }
    public record Profile(@NotBlank @Size(max=255) String fullName, @Size(max=100) String employeeCode,
                          @Email @Size(max=255) String email, Long departmentId, @Size(max=1000) String employeeCodeCorrectionReason) {}
    public record CreateUser(@NotBlank @Size(max=255) String fullName, @NotBlank @Size(max=100) String employeeCode,
                             @Email @Size(max=255) String email, Long departmentId,
                             String password, Boolean accessGranted) {}
    public record CreateAccount(@NotNull Long userId, @NotBlank @Size(min=8,max=128) String password,
                                @NotNull Role roleCode, @NotNull Boolean accessGranted) {}
    public record UpdateAccount(@NotNull Role roleCode, @NotNull Boolean accessGranted, @NotNull Boolean loginLocked) {}
    public record PasswordChange(@NotBlank String currentPassword, @NotBlank @Size(min=8,max=128) String newPassword,
                                 @NotBlank String confirmPassword) {}
    public record PasswordReset(@NotBlank @Size(min=8,max=128) String newPassword, @NotBlank String confirmPassword) {}

    // Serialize identifier and permission changes, including rechecking the acting admin after waiting.
    private UserEntity actor(boolean admin) {
        jdbc.execute("SELECT pg_advisory_xact_lock(7240319)");
        var auth=SecurityContextHolder.getContext().getAuthentication();
        var actor=users.findByPrincipal(auth==null?null:auth.getName()).orElseThrow(()->new AccessDeniedException("Access denied"));
        if (!actor.canLogin() || admin && actor.getRole().getCode()!=Role.ADMIN)
            throw new AccessDeniedException("Access denied");
        return actor;
    }
    private UserEntity target(Long id) {
        var user=users.findForAccessUpdate(id).orElseThrow(()->error(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND"));
        user.getRole().getName();
        if(user.getDepartment()!=null) user.getDepartment().getName();
        return user;
    }
    private static AppException error(HttpStatus status,String code) { return new AppException(status,code,code); }
    private static String optional(String value) { return value==null || value.isBlank()?null:value.trim(); }
    private void unique(String column,String value,Long except) {
        if(value==null) return;
        if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM users WHERE lower("+column+")=lower(?) AND user_id<>?)",Boolean.class,value,except==null?-1:except)))
            throw error(HttpStatus.CONFLICT,"IDENTITY_DUPLICATE");
    }
    private void identifier(String value,Long except) {
        unique("email",value,except); unique("employee_code",value,except);
    }
    private void log(UserEntity actor,UserEntity target,String action,Map<String,Object> before,Map<String,Object> after) {
        var entry=new AuditLogEntity(); entry.setActor(actor); entry.setEntityType("USER"); entry.setEntityId(target.getUserId());
        entry.setAction(action); entry.setOldData(before); entry.setNewData(after); audit.save(entry);
    }
    private Map<String,Object> profileData(UserEntity u) {
        var data=new LinkedHashMap<String,Object>(); data.put("fullName",u.getFullName()); data.put("employeeCode",u.getEmployeeCode());
        data.put("email",u.getEmail()); data.put("departmentId",u.getDepartment()==null?null:u.getDepartment().getDepartmentId());
        data.put("status",u.getAccountStatus()); return data;
    }
    private Map<String,Object> accountData(UserEntity u) {
        var data=new LinkedHashMap<String,Object>(); data.put("role",u.getRole().getCode());
        data.put("accessGranted",u.isAccessGranted()); data.put("loginLocked",u.isLoginLocked()); return data;
    }
    private void role(UserEntity user,Role role) {
        user.setRole(roles.findAll().stream().filter(r->r.getCode()==role).findFirst().orElseThrow(()->error(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR")));
    }
    public UserEntity createUser(CreateUser request) {
        var actor=actor(false);
        if(actor.getRole().getCode()!=Role.ADMIN && actor.getRole().getCode()!=Role.IT_STAFF) throw new AccessDeniedException("Access denied");
        if(actor.getRole().getCode()!=Role.ADMIN && Boolean.TRUE.equals(request.accessGranted())) throw new AccessDeniedException("Access denied");
        String password=request.password()==null || request.password().isBlank()?"Pass@1234":request.password();
        if(password.length()<8 || password.length()>128) throw error(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR");
        var user=saveProfile(null,new Profile(request.fullName(),request.employeeCode(),request.email(),request.departmentId(),null));
        setPassword(user,password);
        user.setAccessGranted(Boolean.TRUE.equals(request.accessGranted()));
        log(actor,user,"ACCOUNT_CREATED",Map.of(),accountData(user));
        return user;
    }
    public UserEntity importUser(CreateUser request) {
        var user=createUser(new CreateUser(request.fullName(),request.employeeCode(),request.email(),request.departmentId(),request.password(),false));
        var actor=actor(false);
        var before=accountData(user);
        user.setLoginLocked(true);
        log(actor,user,"IMPORT_LOGIN_LOCKED",before,accountData(user));
        return user;
    }
    public UserEntity saveProfile(Long id,Profile request) {
        var actor=actor(false);
        if(actor.getRole().getCode()!=Role.ADMIN && actor.getRole().getCode()!=Role.IT_STAFF) throw new AccessDeniedException("Access denied");
        var user=id==null?new UserEntity():target(id); var before=id==null?Map.<String,Object>of():profileData(user);
        String code=optional(request.employeeCode()), email=optional(request.email());
        if(code==null && (id==null || user.getEmployeeCode()!=null)) throw error(HttpStatus.BAD_REQUEST,"EMPLOYEE_CODE_REQUIRED");
        if(email!=null) email=email.toLowerCase(Locale.ROOT);
        identifier(code,id); identifier(email,id);
        if(email==null && code==null && user.getPasswordHash()!=null)
            throw error(HttpStatus.CONFLICT,"ACCOUNT_IDENTIFIER_REQUIRED");
        if(user.getEmployeeCode()!=null && !user.getEmployeeCode().equals(code) && optional(request.employeeCodeCorrectionReason())==null)
            throw error(HttpStatus.BAD_REQUEST,"EMPLOYEE_CODE_CORRECTION_REQUIRED");
        if(id==null) role(user,Role.USER);
        if((!Objects.equals(user.getEmail(),email) || !Objects.equals(user.getEmployeeCode(),code)) && user.getPasswordHash()!=null) user.setAccessVersion(user.getAccessVersion()+1);
        user.setFullName(request.fullName().trim()); user.setEmployeeCode(code);
        user.setEmail(email);
        user.setDepartment(request.departmentId()==null?null:departments.findById(request.departmentId()).orElseThrow(()->error(HttpStatus.BAD_REQUEST,"RESOURCE_NOT_FOUND")));
        if(user.getDepartment()!=null) user.getDepartment().getName();
        users.saveAndFlush(user); var after=profileData(user);
        if(optional(request.employeeCodeCorrectionReason())!=null) after.put("correctionReason",request.employeeCodeCorrectionReason().trim());
        log(actor,user,id==null?"PROFILE_CREATED":"PROFILE_UPDATED",before,after); return user;
    }
    public UserEntity profileStatus(Long id,AccountStatus status) {
        var actor=actor(false);
        if(actor.getRole().getCode()!=Role.ADMIN && actor.getRole().getCode()!=Role.IT_STAFF) throw new AccessDeniedException("Access denied");
        var user=target(id); var before=profileData(user); user.setAccountStatus(status);
        log(actor,user,"PROFILE_STATUS_CHANGED",before,profileData(user)); return user;
    }
    public UserEntity createAccount(CreateAccount request) {
        var actor=actor(true); var user=target(request.userId());
        if(user.getPasswordHash()!=null) throw error(HttpStatus.CONFLICT,"ACCOUNT_ALREADY_EXISTS");
        if(optional(user.getEmployeeCode())==null && optional(user.getEmail())==null) throw error(HttpStatus.BAD_REQUEST,"ACCOUNT_IDENTIFIER_REQUIRED");
        role(user,request.roleCode()); user.setAccessGranted(request.accessGranted());
        user.setLoginLocked(false); setPassword(user,request.password());
        log(actor,user,"ACCOUNT_CREATED",Map.of(),accountData(user)); return user;
    }
    public UserEntity updateAccount(Long id,UpdateAccount request) {
        var actor=actor(true); var user=target(id); requireOther(actor,user);
        if(user.getPasswordHash()==null) throw error(HttpStatus.CONFLICT,"ACCOUNT_REQUIRED");
        var before=accountData(user); role(user,request.roleCode()); user.setAccessGranted(request.accessGranted());
        user.setLoginLocked(request.loginLocked()); user.setAccessVersion(user.getAccessVersion()+1);
        log(actor,user,"ACCOUNT_UPDATED",before,accountData(user)); return user;
    }
    private void requireOther(UserEntity actor,UserEntity user) {
        if(actor.getUserId().equals(user.getUserId())) throw error(HttpStatus.CONFLICT,"ACCOUNT_SELF_PERMISSION_DENIED");
    }
    private void setPassword(UserEntity user,String value) {
        if(value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72) throw error(HttpStatus.BAD_REQUEST,"PASSWORD_TOO_LONG");
        user.setPasswordHash(passwords.encode(value)); user.setAccessVersion(user.getAccessVersion()+1);
    }
    private void confirm(String password,String confirmation) {
        if(!password.equals(confirmation)) throw error(HttpStatus.BAD_REQUEST,"PASSWORD_CONFIRM_MISMATCH");
    }
    public void changePassword(PasswordChange request) {
        var actor=actor(false); var user=target(actor.getUserId());
        if(!passwords.matches(request.currentPassword(),user.getPasswordHash())) throw error(HttpStatus.BAD_REQUEST,"PASSWORD_CURRENT_INVALID");
        confirm(request.newPassword(),request.confirmPassword()); setPassword(user,request.newPassword());
        log(actor,user,"PASSWORD_CHANGED",Map.of(),Map.of());
    }
    public void resetPassword(Long id,PasswordReset request) {
        var actor=actor(true); var user=target(id); requireOther(actor,user);
        if(user.getPasswordHash()==null) throw error(HttpStatus.CONFLICT,"ACCOUNT_REQUIRED");
        confirm(request.newPassword(),request.confirmPassword()); setPassword(user,request.newPassword());
        log(actor,user,"PASSWORD_RESET",Map.of(),Map.of());
    }
    public UserEntity legacyAccess(Long id,boolean granted,AccountStatus status,String password) {
        var actor=actor(true); var user=target(id); requireOther(actor,user);
        if(user.getRole().getCode()!=Role.USER) throw error(HttpStatus.BAD_REQUEST,"ACCESS_USER_ONLY");
        var before=accountData(user);
        if(user.getPasswordHash()==null && (password!=null || granted)) {
            if(password==null) throw error(HttpStatus.BAD_REQUEST,"ACCESS_PASSWORD_REQUIRED");
            if(optional(user.getEmployeeCode())==null && optional(user.getEmail())==null) throw error(HttpStatus.BAD_REQUEST,"ACCOUNT_IDENTIFIER_REQUIRED");
        }
        if(password!=null) setPassword(user,password);
        user.setAccessGranted(granted); user.setLoginLocked(status==AccountStatus.LOCKED);
        user.setAccessVersion(user.getAccessVersion()+1); log(actor,user,"ACCESS_CHANGED",before,accountData(user));
        return user;
    }
}
