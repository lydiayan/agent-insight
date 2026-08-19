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
@TableName("eval_cases")
public class EvalCase {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用例编号，如 CASE-001 */
    private String caseCode;

    /** 用例名称 */
    private String name;

    /** 分类 */
    private String category;

    /** 适用 Agent 名称，支持 % 通配 */
    private String agentName;

    /** 适用 Agent 版本，NULL 表示所有版本 */
    private String agentVersion;

    /** Agent API 入口地址，如 http://agent-service:8080/api/chat */
    private String agentEndpoint;

    /** 调用 Agent 时使用的演示身份 ID，如 HR001、DEV001、SALES001、USER1001 */
    private String actorUserId;

    /** 用户输入模板，如 "帮我查一下 {symbol} 当前股价" */
    private String inputQuery;

    /** 预期调用的工具名称 */
    private String expectedToolName;

    /** 必须存在的参数列表，JSON 数组格式，如 ["symbol"] */
    private String expectedRequiredParams;

    /** 参数校验规则，JSON 对象格式，如 {"symbol":{"type":"string","regex":"^[A-Z]{1,5}$"}} */
    private String expectedParamRules;

    /** 不应出现的参数列表，JSON 数组格式 */
    private String expectedForbiddenParams;

    /** 预期 RAG 应召回的知识库片段 */
    private String expectedChunk;

    /** 标准答案 */
    private String expectedAnswer;

    /** Tool 维度满分（默认 30） */
    private Integer scoreTool;

    /** RAG 维度满分（默认 20） */
    private Integer scoreRag;

    /** Answer 维度满分（默认 50） */
    private Integer scoreAnswer;

    /** 通过阈值，默认 60 */
    private Integer passThreshold;

    /** 难度：simple | medium | hard */
    private String difficulty;

    /** 备注说明 */
    private String remark;

    /** 是否启用：1=启用，0=禁用 */
    private Integer enabled;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
