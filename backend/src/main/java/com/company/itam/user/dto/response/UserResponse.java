package com.company.itam.user.dto.response;

import com.company.itam.department.entity.DepartmentEntity;
import com.company.itam.role.entity.RoleEntity;
import com.company.itam.user.entity.UserEntity;

public class UserResponse {

    private Long id;
    private String email;
    private String fullName;
    private String roleCode;
    private String roleName;
    private String departmentName;
    private String accountStatus;

    public UserResponse() {}

    public static UserResponse fromEntity(UserEntity user) {
        UserResponse dto = new UserResponse();
        dto.id = user.getUserId();
        dto.email = user.getEmail();
        dto.fullName = user.getFullName();
        RoleEntity role = user.getRole();
        if (role != null) {
            dto.roleCode = role.getCode() != null ? role.getCode().name() : null;
            dto.roleName = role.getName();
        }
        DepartmentEntity dept = user.getDepartment();
        if (dept != null) {
            dto.departmentName = dept.getName();
        }
        dto.accountStatus = user.getAccountStatus() != null
                ? user.getAccountStatus().name()
                : null;
        return dto;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getRoleCode() { return roleCode; }
    public void setRoleCode(String roleCode) { this.roleCode = roleCode; }

    public String getRoleName() { return roleName; }
    public void setRoleName(String roleName) { this.roleName = roleName; }

    public String getDepartmentName() { return departmentName; }
    public void setDepartmentName(String departmentName) { this.departmentName = departmentName; }

    public String getAccountStatus() { return accountStatus; }
    public void setAccountStatus(String accountStatus) { this.accountStatus = accountStatus; }
}
