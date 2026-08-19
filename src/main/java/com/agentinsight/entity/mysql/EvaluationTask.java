package com.agentinsight.entity.mysql;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("evaluation_tasks")
public class EvaluationTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String taskName;
    private Integer caseCount;
    private Integer completed;
    private Integer passedCount;
    private Integer failedCount;
    private BigDecimal avgScore;

    /** pending | running | trace_pending | completed | failed */
    private String status;

    private String errorMsg;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
