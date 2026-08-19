package com.agentinsight.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.agentinsight.config.TraceIndexProperties;
import com.agentinsight.dto.OnlineQualityReportDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class OnlineQualityServiceImplTest {

    private ElasticsearchClient esClient;
    private OnlineQualityServiceImpl service;

    @BeforeEach
    void setUp() {
        esClient = mock(ElasticsearchClient.class);
        service = new OnlineQualityServiceImpl(esClient, new TraceIndexProperties());
    }

    @Test
    void summaryQueryUsesRootRequestsAndTerminalToolAndRagSpans() {
        String json = OnlineQualityServiceImpl.buildSummaryQueryJson(100L, 200L);

        assertThat(json)
                .contains("\"gte\":100", "\"lt\":200")
                .doesNotContain("\"lte\"")
                .contains("TRACE_END", "agent.ask")
                .contains("SPAN_END", "tool.", "retrieve")
                .contains("attributes.hitCount")
                .contains("cardinality", "traceId");
    }

    @Test
    void traceQueryIsBoundedAndOnlyReturnsRootTraceEnds() {
        String json = OnlineQualityServiceImpl.buildTraceQueryJson(100L, 200L, 3, 20);

        assertThat(json)
                .contains("\"from\":40", "\"size\":20")
                .contains("TRACE_END", "agent.ask")
                .contains("\"_source\":{\"includes\":[", "timestampMs");
    }

    @Test
    void rejectsRangesLongerThanThirtyOneDaysBeforeQueryingEs() {
        long start = 1_000L;
        long end = start + 32L * 24 * 60 * 60 * 1000;

        assertThatThrownBy(() -> service.getReport(start, end, 1, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("31天");
        verifyNoInteractions(esClient);
    }

    @Test
    void rejectsPaginationBeyondElasticsearchResultWindow() {
        assertThatThrownBy(() -> service.getReport(100L, 200L, 501, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("10000");
        verifyNoInteractions(esClient);
    }

    @Test
    void mapsRootTraceAttributesWithoutRequiringOptionalValues() {
        Map<String, Object> source = Map.of(
                "traceId", "trace-1",
                "serviceName", "mall-order-agent",
                "status", "OK",
                "timestampMs", 123L,
                "durationMs", 456L,
                "attributes", Map.of(
                        "planStrategy", "RAG_QA",
                        "interrupted", false,
                        "grounded", true));

        OnlineQualityReportDTO.RequestTrace item = OnlineQualityServiceImpl.toTraceItem(source);

        assertThat(item.getTraceId()).isEqualTo("trace-1");
        assertThat(item.getDurationMs()).isEqualTo(456L);
        assertThat(item.getPlanStrategy()).isEqualTo("RAG_QA");
        assertThat(item.getInterrupted()).isFalse();
        assertThat(item.getGrounded()).isTrue();
    }

    @Test
    void rateHandlesEmptyWindows() {
        assertThat(OnlineQualityServiceImpl.rate(0, 0)).isZero();
        assertThat(OnlineQualityServiceImpl.rate(8, 10)).isEqualTo(80.0);
    }
}
