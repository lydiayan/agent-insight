package com.agentinsight.dto;

import com.agentinsight.common.PageResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OnlineQualityReportDTO {

    private Long startMs;
    private Long endMs;
    private OperationMetric requests;
    private OperationMetric toolCalls;
    private OperationMetric ragCalls;
    private Long ragHitCount;
    private Double ragHitRate;
    private Double avgRequestLatencyMs;
    private Double p95RequestLatencyMs;
    private Long errorCount;
    private Long timeoutCount;
    private PageResult<RequestTrace> traces;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OperationMetric {
        private Long total;
        private Long success;
        private Long failed;
        private Double successRate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RequestTrace {
        private String traceId;
        private String serviceName;
        private String status;
        private Long timestampMs;
        private Long durationMs;
        private String planStrategy;
        private Boolean interrupted;
        private Boolean grounded;
    }
}
