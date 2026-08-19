package com.agentinsight.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 评测用例 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvalCaseDTO {

    private Long id;

    private String caseCode;

    private String name;

    private String category;

    private String agentName;

    private String agentVersion;

    private String agentEndpoint;

    private String actorUserId;

    private String inputQuery;

    private String expectedToolName;

    private String expectedRequiredParams;

    private String expectedParamRules;

    private String expectedForbiddenParams;

    private String expectedChunk;

    private String expectedAnswer;

    private Integer scoreTool;

    private Integer scoreRag;

    private Integer scoreAnswer;

    private Integer passThreshold;

    private String difficulty;

    private String remark;

    private Integer enabled;

    private List<String> tags;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
