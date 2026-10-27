package com.company.itam.user.controller;

import com.company.itam.common.response.ApiResponse;
import com.company.itam.user.service.AccountImportService;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/v1/users/import")
public class AccountImportController {
    private final AccountImportService imports;
    public AccountImportController(AccountImportService imports) { this.imports=imports; }
    @GetMapping("/template")
    @PreAuthorize("hasAnyAuthority('ADMIN','IT_STAFF')")
    public ResponseEntity<byte[]> template() {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=account-import-template.xlsx").body(imports.template());
    }
    @PostMapping(value="/preview",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('ADMIN','IT_STAFF')")
    public ApiResponse<AccountImportService.Result> preview(@RequestPart("file") MultipartFile file) { return ApiResponse.success("ACCOUNT_IMPORT_PREVIEW",imports.preview(file)); }
    @PostMapping(value="/confirm",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('ADMIN','IT_STAFF')")
    public ApiResponse<AccountImportService.Result> confirm(@RequestPart("file") MultipartFile file) { return ApiResponse.success("ACCOUNT_IMPORT_CONFIRMED",imports.confirm(file)); }
}
