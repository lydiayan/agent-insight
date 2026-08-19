package com.agentinsight.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.CountRequest;
import co.elastic.clients.elasticsearch.core.CountResponse;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import com.agentinsight.common.AgentOperation;
import com.agentinsight.config.TraceIndexProperties;
import com.agentinsight.entity.mysql.EvalResult;
import com.agentinsight.repository.mapper.EvalResultMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import java.util.List;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceImplTest {

    @Mock
    private ElasticsearchClient esClient;

    @Mock
    private EvalResultMapper evalResultMapper;

    @Mock
    private CountResponse totalResponse;

    @Mock
    private CountResponse okResponse;

    private DashboardServiceImpl service;

    @BeforeEach
    void setUp() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace(EvalResultMapper.class.getName());
        TableInfoHelper.initTableInfo(assistant, EvalResult.class);
        service = new DashboardServiceImpl(
                esClient, new JacksonJsonpMapper(), evalResultMapper, traceIndexProperties());
    }

    @Test
    void toolRateIncludesNamespacedOperationsAndOnlyTerminalEvents() throws Exception {
        when(totalResponse.count()).thenReturn(11L);
        when(okResponse.count()).thenReturn(11L);
        when(esClient.count(any(CountRequest.class))).thenReturn(totalResponse, okResponse);

        double rate = service.opRate("rag-traces", 100L, 200L, AgentOperation.TOOL);

        assertThat(rate).isEqualTo(100.0);
        ArgumentCaptor<CountRequest> requests = ArgumentCaptor.forClass(CountRequest.class);
        verify(esClient, times(2)).count(requests.capture());
        List<String> queries = requests.getAllValues().stream()
                .map(request -> request.query().toString())
                .toList();
        assertThat(queries).allSatisfy(query -> {
            assertThat(query).contains("tool.");
            assertThat(query).contains("SPAN_END");
            assertThat(query).contains("SPAN");
        });
        assertThat(queries.get(1)).contains("OK");
    }

    @Test
    void rollingTwentyFourHourRangeUsesEquivalentAdjacentWindows() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 19, 15, 30);

        long[] range = DashboardServiceImpl.resolveTimeRange("24h", now);

        assertThat(range[1] - range[0]).isEqualTo(24L * 60 * 60 * 1000);
        assertThat(range[3] - range[2]).isEqualTo(24L * 60 * 60 * 1000);
        assertThat(range[3]).isEqualTo(range[0]);
    }

    @Test
    void rootRequestCountQueryMatchesOnlineQualityDefinition() {
        String json = DashboardServiceImpl.buildRootRequestCountQueryJson(100L, 200L);

        assertThat(json)
                .contains("TRACE_END", "agent.ask", "cardinality", "traceId")
                .contains("\"gte\":100", "\"lt\":200")
                .doesNotContain("\"lte\"");
    }

    @Test
    void ragHitQueryRequiresSuccessfulPositiveRetrieval() {
        String json = DashboardServiceImpl.buildRagHitQueryJson(100L, 200L);

        assertThat(json)
                .contains("retrieve", "SPAN", "SPAN_END", "OK")
                .contains("attributes.hitCount", "\"gt\":0");
    }

    @Test
    void evalPassRateUsesOnlyFinalMysqlResults() {
        when(evalResultMapper.selectCount(any())).thenReturn(17L, 8L);

        double rate = service.evalPassRate(1_785_945_600_000L, 1_786_032_000_000L);

        assertThat(rate).isEqualTo(47.06);
        @SuppressWarnings({"rawtypes", "unchecked"})
        ArgumentCaptor<Wrapper<EvalResult>> wrappers =
                (ArgumentCaptor) ArgumentCaptor.forClass(Wrapper.class);
        verify(evalResultMapper, times(2)).selectCount(wrappers.capture());
        List<Wrapper<EvalResult>> queries = wrappers.getAllValues();
        AbstractWrapper<?, ?, ?> totalQuery = (AbstractWrapper<?, ?, ?>) queries.get(0);
        AbstractWrapper<?, ?, ?> passedQuery = (AbstractWrapper<?, ?, ?>) queries.get(1);
        assertThat(totalQuery.getSqlSegment())
                .contains("eval_time", "collection_status");
        assertThat(passedQuery.getSqlSegment())
                .contains("eval_time", "collection_status", "passed");
        assertThat(totalQuery.getParamNameValuePairs().values())
                .contains("scored", "call_failed");
        assertThat(passedQuery.getParamNameValuePairs().values())
                .contains("scored", "call_failed", 1);
    }

    @Test
    void evalPassRateSkipsPassedQueryWhenThereAreNoFinalResults() {
        when(evalResultMapper.selectCount(any())).thenReturn(0L);

        double rate = service.evalPassRate(1_785_945_600_000L, 1_786_032_000_000L);

        assertThat(rate).isZero();
        verify(evalResultMapper).selectCount(any());
    }

    @Test
    void evalPassRateDoesNotBreakDashboardWhenMysqlQueryFails() {
        when(evalResultMapper.selectCount(any())).thenThrow(new IllegalStateException("mysql unavailable"));

        double rate = service.evalPassRate(1_785_945_600_000L, 1_786_032_000_000L);

        assertThat(rate).isZero();
    }

    private static TraceIndexProperties traceIndexProperties() {
        return new TraceIndexProperties();
    }
}
