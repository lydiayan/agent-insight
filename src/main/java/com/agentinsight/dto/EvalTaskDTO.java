package com.agentinsight.dto;

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
public class EvalTaskDTO {

    private Long id;
    private String taskName;
    private Integer caseCount;
    private Integer completed;
    private Integer passedCount;
    private Integer failedCount;
    private BigDecimal avgScore;
    private String status;
    private String errorMsg;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
