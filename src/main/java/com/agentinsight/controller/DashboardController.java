package com.agentinsight.controller;

import com.agentinsight.common.Result;
import com.agentinsight.dto.AgentRankingDTO;
import com.agentinsight.dto.DashboardSummaryDTO;
import com.agentinsight.dto.TrendDataDTO;
import com.agentinsight.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Dashboard 大盘 API
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    /**
     * 获取概览指标卡片数据
     */
    @GetMapping("/summary")
    public Result<DashboardSummaryDTO> getSummary(
            @RequestParam(defaultValue = "24h") String range) {
        return Result.success(dashboardService.getSummary(range));
    }

    /**
     * 获取历史趋势数据
     */
    @GetMapping("/trends")
    public Result<TrendDataDTO> getTrends(
            @RequestParam(defaultValue = "7d") String range,
            @RequestParam(defaultValue = "1d") String granularity) {
        return Result.success(dashboardService.getTrends(range, granularity));
    }

    /**
     * 获取 Agent 调用排行
     */
    @GetMapping("/agent-ranking")
    public Result<List<AgentRankingDTO>> getAgentRanking(
            @RequestParam(defaultValue = "7d") String range,
            @RequestParam(defaultValue = "requestCount") String sortBy,
            @RequestParam(defaultValue = "10") int limit) {
        return Result.success(dashboardService.getAgentRanking(range, sortBy, limit));
    }
}
