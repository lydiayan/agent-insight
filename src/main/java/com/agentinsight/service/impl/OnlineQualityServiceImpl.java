package com.agentinsight.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.FilterAggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Percentiles;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.agentinsight.common.AgentOperation;
import com.agentinsight.common.PageResult;
import com.agentinsight.common.TraceQueryFilters;
import com.agentinsight.config.TraceIndexProperties;
import com.agentinsight.dto.OnlineQualityReportDTO;
import com.agentinsight.service.OnlineQualityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OnlineQualityServiceImpl implements OnlineQualityService {

    private static final long MAX_RANGE_MS = 31L * 24 * 60 * 60 * 1000;
    private static final int MAX_RESULT_WINDOW = 10_000;
    private static final int MAX_PAGE_SIZE = 100;

    private final ElasticsearchClient esClient;
    private final TraceIndexProperties traceIndexProperties;

    @Override
    public OnlineQualityReportDTO getReport(long startMs, long endMs, int page, int pageSize) {
        validate(startMs, endMs, page, pageSize);
        try {
            SearchResponse<Map> summaryResponse = esClient.search(
                    SearchRequest.of(s -> s.index(traceIndexProperties.getTraceIndex())
                            .withJson(new StringReader(buildSummaryQueryJson(startMs, endMs)))),
                    Map.class);
            SearchResponse<Map> traceResponse = esClient.search(
                    SearchRequest.of(s -> s.index(traceIndexProperties.getTraceIndex())
                            .withJson(new StringReader(buildTraceQueryJson(startMs, endMs, page, pageSize)))),
                    Map.class);
            return toReport(startMs, endMs, page, pageSize, summaryResponse, traceResponse);
        } catch (Exception e) {
            log.error("Query online quality report failed: startMs={}, endMs={}", startMs, endMs, e);
            throw new IllegalStateException("获取线上质量数据失败，请稍后重试", e);
        }
    }

    private void validate(long startMs, long endMs, int page, int pageSize) {
        if (startMs <= 0 || endMs <= 0 || startMs >= endMs) {
            throw new IllegalArgumentException("开始时间必须早于结束时间");
        }
        if (endMs - startMs > MAX_RANGE_MS) {
            throw new IllegalArgumentException("单次查询时间范围不能超过31天");
        }
        if (page < 1) {
            throw new IllegalArgumentException("页码必须大于0");
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("每页条数必须在1到100之间");
        }
        long resultEnd = (long) page * pageSize;
        if (resultEnd > MAX_RESULT_WINDOW) {
            throw new IllegalArgumentException("最多查看前10000条请求记录");
        }
    }

    static String buildSummaryQueryJson(long startMs, long endMs) {
        String rootRequests = TraceQueryFilters.rootRequestFilter(startMs, endMs);
        String toolCalls = TraceQueryFilters.operationSpanFilter(
                startMs, endMs, AgentOperation.TOOL);
        String ragCalls = TraceQueryFilters.operationSpanFilter(
                startMs, endMs, AgentOperation.RETRIEVE);

        return """
                {"size":0,"aggs":{
                  "requests":{"filter":%s,"aggs":{
                    "total":{"cardinality":{"field":"traceId","precision_threshold":40000}},
                    "success":{"filter":{"term":{"status":"OK"}},"aggs":{"count":{"cardinality":{"field":"traceId","precision_threshold":40000}}}},
                    "error":{"filter":{"term":{"status":"ERROR"}},"aggs":{"count":{"cardinality":{"field":"traceId","precision_threshold":40000}}}},
                    "timeout":{"filter":{"term":{"status":"TIMEOUT"}},"aggs":{"count":{"cardinality":{"field":"traceId","precision_threshold":40000}}}},
                    "avg_latency":{"avg":{"field":"durationMs"}},
                    "p95_latency":{"percentiles":{"field":"durationMs","percents":[95],"keyed":true}}
                  }},
                  "tools":{"filter":%s,"aggs":{"success":{"filter":{"term":{"status":"OK"}}}}},
                  "rag":{"filter":%s,"aggs":{
                    "success":{"filter":{"term":{"status":"OK"}}},
                    "hit":{"filter":{"bool":{"filter":[
                      {"term":{"status":"OK"}},
                      {"range":{"attributes.hitCount":{"gt":0}}}
                    ]}}}
                  }}
                }}
                """.formatted(rootRequests, toolCalls, ragCalls);
    }

    static String buildTraceQueryJson(long startMs, long endMs, int page, int pageSize) {
        int from = (page - 1) * pageSize;
        return """
                {"from":%d,"size":%d,"track_total_hits":true,
                 "sort":[{"timestampMs":{"order":"desc"}}],
                 "_source":{"includes":["traceId","serviceName","status","timestampMs","durationMs","attributes.planStrategy","attributes.interrupted","attributes.grounded"]},
                 "query":%s}
                """.formatted(from, pageSize,
                TraceQueryFilters.rootRequestFilter(startMs, endMs));
    }

    private OnlineQualityReportDTO toReport(long startMs, long endMs, int page, int pageSize,
                                             SearchResponse<Map> summaryResponse,
                                             SearchResponse<Map> traceResponse) {
        FilterAggregate requests = filter(summaryResponse, "requests");
        FilterAggregate tools = filter(summaryResponse, "tools");
        FilterAggregate rag = filter(summaryResponse, "rag");

        long requestTotal = cardinality(requests, "total");
        long requestSuccess = nestedCardinality(requests, "success", "count");
        long errors = nestedCardinality(requests, "error", "count");
        long timeouts = nestedCardinality(requests, "timeout", "count");
        long toolTotal = docCount(tools);
        long toolSuccess = nestedDocCount(tools, "success");
        long ragTotal = docCount(rag);
        long ragSuccess = nestedDocCount(rag, "success");
        long ragHit = nestedDocCount(rag, "hit");

        List<OnlineQualityReportDTO.RequestTrace> traces = new ArrayList<>();
        traceResponse.hits().hits().forEach(hit -> {
            if (hit.source() != null) {
                traces.add(toTraceItem(hit.source()));
            }
        });

        return OnlineQualityReportDTO.builder()
                .startMs(startMs)
                .endMs(endMs)
                .requests(metric(requestTotal, requestSuccess))
                .toolCalls(metric(toolTotal, toolSuccess))
                .ragCalls(metric(ragTotal, ragSuccess))
                .ragHitCount(ragHit)
                .ragHitRate(rate(ragHit, ragTotal))
                .avgRequestLatencyMs(round(avg(requests, "avg_latency")))
                .p95RequestLatencyMs(round(percentile95(requests, "p95_latency")))
                .errorCount(errors)
                .timeoutCount(timeouts)
                .traces(PageResult.of(requestTotal, page, pageSize, traces))
                .build();
    }

    private static OnlineQualityReportDTO.OperationMetric metric(long total, long success) {
        return OnlineQualityReportDTO.OperationMetric.builder()
                .total(total)
                .success(success)
                .failed(Math.max(0, total - success))
                .successRate(rate(success, total))
                .build();
    }

    static OnlineQualityReportDTO.RequestTrace toTraceItem(Map<String, Object> source) {
        Map<?, ?> attributes = source.get("attributes") instanceof Map<?, ?> map ? map : Map.of();
        return OnlineQualityReportDTO.RequestTrace.builder()
                .traceId(stringValue(source.get("traceId")))
                .serviceName(stringValue(source.get("serviceName")))
                .status(stringValue(source.get("status")))
                .timestampMs(longValue(source.get("timestampMs")))
                .durationMs(longValue(source.get("durationMs")))
                .planStrategy(stringValue(attributes.get("planStrategy")))
                .interrupted(booleanValue(attributes.get("interrupted")))
                .grounded(booleanValue(attributes.get("grounded")))
                .build();
    }

    private static FilterAggregate filter(SearchResponse<Map> response, String name) {
        Aggregate aggregate = response.aggregations().get(name);
        return aggregate != null && aggregate.isFilter() ? aggregate.filter() : null;
    }

    private static long cardinality(FilterAggregate parent, String name) {
        if (parent == null) return 0L;
        Aggregate aggregate = parent.aggregations().get(name);
        return aggregate != null && aggregate.isCardinality() ? aggregate.cardinality().value() : 0L;
    }

    private static long nestedCardinality(FilterAggregate parent, String bucket, String name) {
        FilterAggregate nested = nestedFilter(parent, bucket);
        return cardinality(nested, name);
    }

    private static long nestedDocCount(FilterAggregate parent, String bucket) {
        return docCount(nestedFilter(parent, bucket));
    }

    private static FilterAggregate nestedFilter(FilterAggregate parent, String bucket) {
        if (parent == null) return null;
        Aggregate aggregate = parent.aggregations().get(bucket);
        return aggregate != null && aggregate.isFilter() ? aggregate.filter() : null;
    }

    private static long docCount(FilterAggregate aggregate) {
        return aggregate != null ? aggregate.docCount() : 0L;
    }

    private static double avg(FilterAggregate parent, String name) {
        if (parent == null) return 0.0;
        Aggregate aggregate = parent.aggregations().get(name);
        if (aggregate == null || !aggregate.isAvg()) return 0.0;
        return finite(aggregate.avg().value());
    }

    private static double percentile95(FilterAggregate parent, String name) {
        if (parent == null) return 0.0;
        Aggregate aggregate = parent.aggregations().get(name);
        if (aggregate == null || !aggregate.isTdigestPercentiles()) return 0.0;
        Percentiles values = aggregate.tdigestPercentiles().values();
        if (values.isKeyed()) {
            String value = values.keyed().get("95.0");
            if (value == null) value = values.keyed().get("95");
            return parseFinite(value);
        }
        if (values.isArray()) {
            return values.array().stream()
                    .filter(item -> "95.0".equals(item.key()) || "95".equals(item.key()))
                    .mapToDouble(item -> finite(item.value()))
                    .findFirst()
                    .orElse(0.0);
        }
        return 0.0;
    }

    static double rate(long success, long total) {
        return total > 0 ? round(success * 100.0 / total) : 0.0;
    }

    private static double parseFinite(String value) {
        try {
            return finite(Double.parseDouble(value));
        } catch (Exception ignored) {
            return 0.0;
        }
    }

    private static double finite(double value) {
        return Double.isFinite(value) ? value : 0.0;
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static String stringValue(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    private static Long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private static Boolean booleanValue(Object value) {
        return value instanceof Boolean bool ? bool : null;
    }
}
