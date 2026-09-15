package com.company.itam.dashboard;

import com.company.itam.common.response.ApiResponse;
import com.company.itam.common.util.MessageHelper;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/v1")
public class DashboardController {
    private final DashboardService service;
    private final MessageHelper messages;
    public DashboardController(DashboardService service, MessageHelper messages) { this.service = service; this.messages = messages; }

    @GetMapping("/dashboard/summary")
    public ApiResponse<Map<String, Object>> summary() { return ApiResponse.success(messages.getMessage("DASHBOARD_READ"), service.summary()); }

    @GetMapping(value = "/reports/assets/export", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<byte[]> export() {
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=itam-assets.xlsx")
                .cacheControl(CacheControl.noStore()).body(service.exportAssets());
    }
}
