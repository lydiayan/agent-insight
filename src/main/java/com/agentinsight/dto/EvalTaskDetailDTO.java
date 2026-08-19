package com.agentinsight.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 评测任务详情：逐条用例得分明细
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvalTaskDetailDTO {

    private Long taskId;
    private String taskName;
    private String status;

    /** 总用例数 / 完成数 / 通过数 / 失败数 / 平均分 */
    private Integer caseCount;
    private Integer completed;
    private Integer passedCount;
    private Integer failedCount;
    private Double avgScore;

    private java.util.List<CaseResultItem> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CaseResultItem {
        private Long caseId;
        private String caseCode;
        private String caseName;
        private Boolean expectedToolCalled;
        private String expectedToolName;
        private Boolean expectedRagCalled;
        private Boolean expectedHumanConfirmation;
        private Boolean actualToolCalled;
        private Boolean actualRagCalled;
        private Integer scoreTool;
        private Integer scoreRag;
        private Integer scoreAnswer;
        private Integer scoreTotal;
        private Integer scoreMax;
        private Boolean passed;
        private String failureReasons;  // JSON 数组字符串
        private String agentOutput;
        private String agentTraceId;
        private String planStrategy;
        private String conversationId;
        private Boolean interrupted;
        private Integer httpStatus;
        private String rawResponse;
        private String collectionStatus;
        private String collectionMessage;
        private LocalDateTime invokedAt;
        private LocalDateTime collectedAt;
        private LocalDateTime evalTime;
    }
}
