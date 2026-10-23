package com.company.itam.report;
import com.company.itam.common.response.ApiResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/v1/reports")
public class ReportController {
    private final ReportService service;
    private final com.company.itam.audit.service.DownloadAuditService downloads;
    public ReportController(ReportService service, com.company.itam.audit.service.DownloadAuditService downloads) {this.service=service;this.downloads=downloads;}
    private ReportAccessService.Group group(String value) {
        try {return ReportAccessService.Group.valueOf(value.toUpperCase(Locale.ROOT));}
        catch(IllegalArgumentException e) {throw new com.company.itam.common.exception.AppException(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR","Invalid report group");}
    }
    @GetMapping("/{group}/options")
    public ApiResponse<Map<String,Object>> options(@PathVariable String group) {
        return ApiResponse.success("OK",service.options(group(group)));
    }
    @GetMapping("/{group}")
    public ApiResponse<Map<String,Object>> list(@PathVariable String group,
        @RequestParam(required=false) String type,@RequestParam(required=false) String status,
        @RequestParam(required=false) Long departmentId,@RequestParam(required=false) Long locationId,
        @RequestParam(required=false) Long userId,@RequestParam(required=false) Long actorId,
        @RequestParam(defaultValue="created") String dateField,@RequestParam(required=false) LocalDate from,
        @RequestParam(required=false) LocalDate to,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return ApiResponse.success("OK",service.list(group(group),new ReportService.Filter(type,status,departmentId,locationId,userId,actorId,dateField,from,to,page,size)));
    }
    @GetMapping("/{group}/export")
    public ResponseEntity<byte[]> export(@PathVariable String group,
        @RequestParam(required=false) String type,@RequestParam(required=false) String status,
        @RequestParam(required=false) Long departmentId,@RequestParam(required=false) Long locationId,
        @RequestParam(required=false) Long userId,@RequestParam(required=false) Long actorId,
        @RequestParam(defaultValue="created") String dateField,@RequestParam(required=false) LocalDate from,
        @RequestParam(required=false) LocalDate to) {
        var selected=group(group);
        byte[] content = service.export(selected,new ReportService.Filter(type,status,departmentId,locationId,userId,actorId,dateField,from,to,0,20));
        downloads.record("MASTER_DATA", 0L, "REPORT_EXPORTED_" + selected.name());
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=itam-"+selected.name().toLowerCase(Locale.ROOT)+".xlsx")
            .cacheControl(CacheControl.noStore()).body(content);
    }
}
