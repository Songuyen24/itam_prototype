package com.company.itam.dashboard;

import com.company.itam.common.response.ApiResponse;
import com.company.itam.common.util.MessageHelper;
import org.springframework.web.bind.annotation.*;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/v1")
public class DashboardController {
    private final DashboardService service;
    private final MessageHelper messages;
    public DashboardController(DashboardService service, MessageHelper messages) { this.service = service; this.messages = messages; }

    @GetMapping("/dashboard/summary")
    public ApiResponse<Map<String, Object>> summary(
        @RequestParam(defaultValue="thisMonth") String period,
        @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.success(messages.getMessage("DASHBOARD_READ"), service.summary(period,from,to));
    }
}
