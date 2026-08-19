package com.agentinsight.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent 调用排行项
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentRankingDTO {

    /** 排名 */
    private Integer rank;

    /** Agent 名称 */
    private String agentName;

    /** 调用量 */
    private Long requestCount;

    /** 成功率 (%) */
    private Double successRate;

    /** 平均耗时 (ms) */
    private Double avgLatency;

    /** 评测平均分 */
    private Double avgScore;
}
