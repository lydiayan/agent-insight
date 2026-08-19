package com.agentinsight.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TraceQueryFiltersTest {

    @Test
    void rootRequestFilterUsesHalfOpenRangeAndRootTraceDefinition() {
        String json = TraceQueryFilters.rootRequestFilter(100L, 200L);

        assertThat(json)
                .contains("\"gte\":100", "\"lt\":200", "TRACE_END", "agent.ask")
                .doesNotContain("\"lte\"");
    }

    @Test
    void toolOperationFilterIncludesNamespacedTerminalSpans() {
        String json = TraceQueryFilters.operationSpanFilter(100L, 200L, AgentOperation.TOOL);

        assertThat(json).contains("tool.", "SPAN", "SPAN_END");
    }
}
