package com.company.itam.report;

import com.company.itam.common.response.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/v1/reports")
public class ReportPermissionController {
    private final ReportAccessService access;
    public ReportPermissionController(ReportAccessService access) { this.access = access; }
    @GetMapping("/permissions")
    public ApiResponse<Map<ReportAccessService.Group, ReportAccessService.Permission>> current() {
        return ApiResponse.success("OK", access.current());
    }
    @GetMapping("/permissions/{userId}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<Map<ReportAccessService.Group, ReportAccessService.Permission>> account(@PathVariable Long userId) {
        return ApiResponse.success("OK", access.permissions(userId));
    }
    @PutMapping("/permissions/{userId}/{group}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<Map<ReportAccessService.Group, ReportAccessService.Permission>> update(
            @PathVariable Long userId, @PathVariable ReportAccessService.Group group,
            @RequestBody ReportAccessService.Permission permission) {
        access.update(userId, group, permission);
        return ApiResponse.success("OK", access.permissions(userId));
    }
}
