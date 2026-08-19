package com.agentinsight.service;

import com.agentinsight.dto.AgentRankingDTO;
import com.agentinsight.dto.DashboardSummaryDTO;
import com.agentinsight.dto.TrendDataDTO;

import java.util.List;

/**
 * Dashboard 大盘服务接口
 */
public interface DashboardService {

    /**
     * 获取概览指标卡片数据
     *
     * @param range 时间范围：24h | today | 7d | 30d
     */
    DashboardSummaryDTO getSummary(String range);

    /**
     * 获取历史趋势数据
     *
     * @param range       时间范围：7d | 30d
     * @param granularity 粒度：1h | 1d
     */
    TrendDataDTO getTrends(String range, String granularity);

    /**
     * 获取 Agent 调用排行
     *
     * @param range     时间范围
     * @param sortBy    排序维度：requestCount | successRate | avgLatency | avgScore
     * @param limit     返回条数
     */
    List<AgentRankingDTO> getAgentRanking(String range, String sortBy, int limit);
}
