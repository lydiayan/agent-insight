package com.agentinsight.entity.mysql;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("eval_results")
public class EvalResult {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 任务内稳定结果键：eval-task-{taskId}-case-{caseId} */
    private String traceId;

    /** Agent 返回的真实 traceId */
    private String agentTraceId;

    /** trace_pending | trace_incomplete | scored | call_failed */
    private String collectionStatus;

    /** Agent 调用完成时间 */
    private LocalDateTime invokedAt;

    /** Trace 采集完成时间 */
    private LocalDateTime collectedAt;

    /** 本次评分使用的 ES Trace 快照 JSON */
    private String traceSnapshot;

    /** 关联 eval_cases.id */
    private Long caseId;

    /** Tool 维度得分 */
    private Integer scoreTool;

    /** RAG 维度得分 */
    private Integer scoreRag;

    /** Answer 维度得分 */
    private Integer scoreAnswer;

    /** 总分 */
    private Integer scoreTotal;

    /** 满分 */
    private Integer scoreMax;

    /** 是否通过 */
    private Integer passed;

    /** 评测详情 JSON */
    private String evalDetail;

    /** 评测时间 */
    private LocalDateTime evalTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
