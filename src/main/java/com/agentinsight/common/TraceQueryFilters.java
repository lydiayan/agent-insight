package com.agentinsight.common;

/**
 * Shared Elasticsearch filters for Agent trace metrics.
 */
public final class TraceQueryFilters {

    private TraceQueryFilters() {
    }

    public static String rangeFilter(long startMs, long endMs) {
        return rangeFilter("timestampMs", startMs, endMs);
    }

    public static String rangeFilter(String field, long startMs, long endMs) {
        return "{\"range\":{\"" + field + "\":{\"gte\":" + startMs
                + ",\"lt\":" + endMs + "}}}";
    }

    public static String rootRequestFilter(long startMs, long endMs) {
        return "{\"bool\":{\"filter\":["
                + rangeFilter(startMs, endMs) + ","
                + "{\"term\":{\"eventType\":\"TRACE_END\"}},"
                + "{\"term\":{\"operation\":\"agent.ask\"}}]}}";
    }

    public static String terminalSpanFilter() {
        return "{\"terms\":{\"eventType\":[\"SPAN\",\"SPAN_END\"]}}";
    }

    public static String operationFilter(String operation) {
        if (AgentOperation.TOOL.equals(operation)) {
            return "{\"bool\":{\"should\":["
                    + "{\"term\":{\"operation\":\"tool\"}},"
                    + "{\"prefix\":{\"operation\":\"tool.\"}}],"
                    + "\"minimum_should_match\":1}}";
        }
        return "{\"term\":{\"operation\":\"" + operation + "\"}}";
    }

    public static String operationSpanFilter(long startMs, long endMs, String operation) {
        return "{\"bool\":{\"filter\":["
                + rangeFilter(startMs, endMs) + ","
                + operationFilter(operation) + ","
                + terminalSpanFilter() + "]}}";
    }
}
