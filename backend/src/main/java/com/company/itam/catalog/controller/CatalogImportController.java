package com.company.itam.catalog.controller;

import com.company.itam.catalog.service.CatalogImportService;
import com.company.itam.common.response.ApiResponse;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/v1/catalog-imports/{kind}")
@PreAuthorize("hasAnyAuthority('ADMIN','IT_STAFF')")
public class CatalogImportController {
    private final CatalogImportService imports;
    public CatalogImportController(CatalogImportService imports){this.imports=imports;}
    @GetMapping("/template")
    public ResponseEntity<byte[]> template(@PathVariable String kind){
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=catalog-"+kind+".xlsx").body(imports.template(kind));
    }
    @PostMapping(value="/preview",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<CatalogImportService.Result> preview(@PathVariable String kind,@RequestPart("file") MultipartFile file){return ApiResponse.success("CATALOG_IMPORT_PREVIEW",imports.preview(kind,file));}
    @PostMapping(value="/confirm",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<CatalogImportService.Result> confirm(@PathVariable String kind,@RequestPart("file") MultipartFile file){return ApiResponse.success("CATALOG_IMPORT_CONFIRMED",imports.confirm(kind,file));}
}
