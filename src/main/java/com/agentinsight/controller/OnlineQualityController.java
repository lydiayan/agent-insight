package com.agentinsight.controller;

import com.agentinsight.common.Result;
import com.agentinsight.dto.OnlineQualityReportDTO;
import com.agentinsight.service.OnlineQualityService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/online-quality")
@RequiredArgsConstructor
public class OnlineQualityController {

    private final OnlineQualityService onlineQualityService;

    @GetMapping("/report")
    public Result<OnlineQualityReportDTO> getReport(
            @RequestParam long startMs,
            @RequestParam long endMs,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(onlineQualityService.getReport(startMs, endMs, page, pageSize));
    }
}
