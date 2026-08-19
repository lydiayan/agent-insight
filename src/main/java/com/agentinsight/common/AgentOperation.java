package com.agentinsight.common;

import java.util.List;

/**
 * Agent 链路 operation 类型常量。
 * 趋势图和概览卡片中按这些值分项统计成功率。
 */
public record AgentOperation(String name, String label, String color) {

    public static final String RERANK = "rerank";
    public static final String MILVUS = "milvus";
    public static final String RETRIEVE = "retrieve";
    public static final String PROMPT_BUILD = "prompt_build";
    public static final String LLM = "llm";
    public static final String TOOL = "tool";

    public static final List<AgentOperation> ALL = List.of(
            new AgentOperation(RERANK, "Rerank", "#8b5cf6"),
            new AgentOperation(MILVUS, "Milvus", "#06b6d4"),
            new AgentOperation(RETRIEVE, "Retrieve", "#f59e0b"),
            new AgentOperation(PROMPT_BUILD, "Prompt", "#ec4899"),
            new AgentOperation(LLM, "LLM", "#3b82f6"),
            new AgentOperation(TOOL, "Tool", "#10b981")
    );

    public static List<String> names() {
        return ALL.stream().map(AgentOperation::name).toList();
    }

    public static String labelOf(String name) {
        return ALL.stream()
                .filter(o -> o.name.equals(name))
                .findFirst()
                .map(AgentOperation::label)
                .orElse(name);
    }

    public static String colorOf(String name) {
        return ALL.stream()
                .filter(o -> o.name.equals(name))
                .findFirst()
                .map(AgentOperation::color)
                .orElse("#9ca3af");
    }
}
