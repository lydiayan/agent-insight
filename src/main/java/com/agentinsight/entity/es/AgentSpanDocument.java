package com.agentinsight.entity.es;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.util.Map;

/**
 * Agent Span 文档 — 对应已有 OTel 风格的 ES 索引。
 * <p>
 * 当前索引 mapping 字段：
 * traceId, spanId, parentSpanId, eventType, operation, serviceName,
 * status, timestampMs, durationMs, errorMessage, spanKind,
 * model, finishReason, inputToken, outputToken,
 * contextChunks, answerLength, outputLength,
 * promptVersion, promptLength, chunkCount, historyCount, memoryCount,
 * systemPrompt, userQuery, input, output, attributes
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "agent-spans")
public class AgentSpanDocument {

    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private String traceId;

    @Field(type = FieldType.Keyword)
    private String spanId;

    @Field(type = FieldType.Keyword)
    private String parentSpanId;

    /** LLM_CALL | TOOL_EXECUTION | RAG_RETRIEVAL | CHAIN 等 */
    @Field(type = FieldType.Keyword)
    private String eventType;

    /** chat | embedding | retrieve | tool_execute 等 */
    @Field(type = FieldType.Keyword)
    private String operation;

    @Field(type = FieldType.Keyword)
    private String serviceName;

    /** success | error | timeout */
    @Field(type = FieldType.Keyword)
    private String status;

    @Field(type = FieldType.Long)
    private Long timestampMs;

    @Field(type = FieldType.Long)
    private Long durationMs;

    @Field(type = FieldType.Text)
    private String errorMessage;

    /** CLIENT | SERVER | INTERNAL */
    @Field(type = FieldType.Keyword)
    private String spanKind;

    @Field(type = FieldType.Keyword)
    private String model;

    @Field(type = FieldType.Keyword)
    private String finishReason;

    @Field(type = FieldType.Long)
    private Long inputToken;

    @Field(type = FieldType.Long)
    private Long outputToken;

    /** RAG 召回片段数量 */
    @Field(type = FieldType.Long)
    private Long contextChunks;

    @Field(type = FieldType.Long)
    private Long answerLength;

    @Field(type = FieldType.Long)
    private Long outputLength;

    @Field(type = FieldType.Keyword)
    private String promptVersion;

    @Field(type = FieldType.Long)
    private Long promptLength;

    @Field(type = FieldType.Long)
    private Long chunkCount;

    @Field(type = FieldType.Long)
    private Long historyCount;

    @Field(type = FieldType.Long)
    private Long memoryCount;

    @Field(type = FieldType.Text, index = false)
    private String systemPrompt;

    @Field(type = FieldType.Text, index = true)
    private String userQuery;

    @Field(type = FieldType.Text, index = false)
    private String input;

    @Field(type = FieldType.Text, index = false)
    private String output;

    /** 扩展属性（object 类型，存放 agentName 等自定义字段） */
    @Field(type = FieldType.Object, enabled = true)
    private Map<String, Object> attributes;
}
