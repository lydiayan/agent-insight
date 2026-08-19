package com.agentinsight.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Dashboard 概览指标
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardSummaryDTO {

    /** 当前统计窗口，左闭右开。 */
    private Long startMs;
    private Long endMs;

    /** 当前窗口请求量 */
    private MetricCard requestCount;

    /** 平均耗时 (ms) */
    private MetricCard avgLatency;

    /** Tool 成功率 (%) */
    private MetricCard toolSuccessRate;

    /** RAG 命中率 (%) */
    private MetricCard ragHitRate;

    /** 平均 Token 消耗 */
    private MetricCard avgTokens;

    /** 评测通过率 (%) */
    private MetricCard evalPassRate;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MetricCard {
        /** 当前值 */
        private double value;
        /** 单位 */
        private String unit;
        /** 环比变化百分比（正数上升，负数下降） */
        private Double changePercent;
        /** 变化方向：up | down */
        private String trend;
    }
}
