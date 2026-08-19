package com.agentinsight.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 历史趋势数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrendDataDTO {

    /** 日期/时间标签列表 */
    private List<String> labels;

    /** 请求量序列 */
    private List<Long> requestVolumes;

    /** 各 operation 类型的成功率序列（key: rerank/milvus/retrieve/prompt_build/llm/tool） */
    private Map<String, List<Double>> operationSuccessRates;

    /** operation 元数据：name → {label, color} */
    private Map<String, OpMeta> operationMeta;

    /** 平均耗时序列 (ms) */
    private List<Double> avgLatencies;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OpMeta {
        private String label;
        private String color;
    }
}
