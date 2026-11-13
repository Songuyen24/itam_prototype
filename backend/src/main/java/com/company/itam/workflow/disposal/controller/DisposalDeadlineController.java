package com.company.itam.workflow.disposal.controller;

import com.company.itam.common.response.ApiResponse;
import com.company.itam.workflow.disposal.service.DisposalDeadlineService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/v1/disposals")
public class DisposalDeadlineController {
    private final DisposalDeadlineService service;
    public DisposalDeadlineController(DisposalDeadlineService service){this.service=service;}
    @PostMapping("/{id}/extend")
    public ApiResponse<Map<String,Object>> extend(@PathVariable Long id,@Valid @RequestBody DisposalDeadlineService.Extension request){return ApiResponse.success("OK",service.extend(id,request));}
}
