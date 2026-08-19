package com.agentinsight.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.CountRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.json.JsonpMapper;
import com.agentinsight.common.AgentOperation;
import com.agentinsight.common.TraceQueryFilters;
import com.agentinsight.config.TraceIndexProperties;
import com.agentinsight.dto.AgentRankingDTO;
import com.agentinsight.dto.DashboardSummaryDTO;
import com.agentinsight.dto.TrendDataDTO;
import com.agentinsight.entity.mysql.EvalResult;
import com.agentinsight.repository.mapper.EvalResultMapper;
import com.agentinsight.service.DashboardService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.json.Json;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.StringReader;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Dashboard 大盘服务实现。
 * <p>
 * Query 对象通过 JSON → {@link Query} 反序列化构建，再注入到 Request。
 * 绕开 {@code co.elastic.clients} Builder API 的版本差异。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final ElasticsearchClient esClient;
    private final JsonpMapper jsonpMapper;
    private final EvalResultMapper evalResultMapper;
    private final TraceIndexProperties traceIndexProperties;

    private static final String INDEX = "agent-spans";
    private static final List<String> OPERATIONS = AgentOperation.names();
    private static final List<String> FINAL_EVAL_STATUSES = List.of("scored", "call_failed");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MM-dd");
    private static final DateTimeFormatter HOUR_FMT = DateTimeFormatter.ofPattern("HH:00");
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    // ==================== 公开接口 ====================

    @Override
    public DashboardSummaryDTO getSummary(String range) {
        long[] tr = resolveTimeRange(range);
        long ns = tr[0], ne = tr[1], ps = tr[2], pe = tr[3];
        String traceIndex = traceIndexProperties.getTraceIndex();

        long curCount = rootRequestCount(traceIndex, ns, ne);
        double curAvgLat = rootRequestAvgLatency(traceIndex, ns, ne);
        double curToolSuccess = opRate(traceIndex, ns, ne, AgentOperation.TOOL);
        double curRagHit = ragHitRate(traceIndex, ns, ne);
        double curTokens = avgTokens(traceIndex, ns, ne);
        double curEval = evalPassRate(ns, ne);

        long prevCount = rootRequestCount(traceIndex, ps, pe);
        double prevToolSuccess = opRate(traceIndex, ps, pe, AgentOperation.TOOL);
        double prevRagHit = ragHitRate(traceIndex, ps, pe);
        double prevAvgLat = rootRequestAvgLatency(traceIndex, ps, pe);
        double prevTokens = avgTokens(traceIndex, ps, pe);
        double prevEval = evalPassRate(ps, pe);

        return DashboardSummaryDTO.builder()
                .startMs(ns)
                .endMs(ne)
                .requestCount(card(curCount, "次", curCount, prevCount))
                .avgLatency(card(curAvgLat, "ms", curAvgLat, prevAvgLat))
                .toolSuccessRate(card(curToolSuccess, "%", curToolSuccess, prevToolSuccess))
                .ragHitRate(card(curRagHit, "%", curRagHit, prevRagHit))
                .avgTokens(card(curTokens, "", curTokens, prevTokens))
                .evalPassRate(card(curEval, "%", curEval, prevEval))
                .build();
    }

    @Override
    public TrendDataDTO getTrends(String range, String granularity) {
        long[] tr = resolveTimeRange(range);
        return buildTrend(tr[0], tr[1], "1h".equals(granularity));
    }

    @Override
    public List<AgentRankingDTO> getAgentRanking(String range, String sortBy, int limit) {
        long[] tr = resolveTimeRange(range);
        String json = """
            {"size":0,"query":%s,"aggs":{"by_agent":{"terms":{"field":"attributes.agentName.keyword","size":%d}}}}
            """.formatted(rqJson("timestampMs", tr[0], tr[1]), limit);
        try {
            var resp = esClient.search(SearchRequest.of(s ->
                    s.index(INDEX).withJson(new StringReader(json))), Map.class);
            return parseRanking(resp, limit);
        } catch (Exception e) {
            log.warn("Agent ranking failed: {}", e.getMessage());
            return List.of();
        }
    }

    // ==================== 时间范围 ====================

    private long[] resolveTimeRange(String range) {
        return resolveTimeRange(range, LocalDateTime.now(ZONE));
    }

    static long[] resolveTimeRange(String range, LocalDateTime now) {
        LocalDateTime today = now.toLocalDate().atStartOfDay();

        return switch (range != null ? range : "24h") {
            case "24h" -> rollingRange(now, 24);
            case "today" -> {
                long t = today.atZone(ZONE).toInstant().toEpochMilli();
                long n = now.atZone(ZONE).toInstant().toEpochMilli();
                yield new long[]{t, n, t - (n - t), t};
            }
            case "7d" -> rollingRange(now, 7L * 24);
            case "30d" -> rollingRange(now, 30L * 24);
            default -> throw new IllegalArgumentException("Invalid range: " + range);
        };
    }

    private static long[] rollingRange(LocalDateTime now, long hours) {
        long end = now.atZone(ZONE).toInstant().toEpochMilli();
        long start = now.minusHours(hours).atZone(ZONE).toInstant().toEpochMilli();
        long previousStart = now.minusHours(hours * 2).atZone(ZONE).toInstant().toEpochMilli();
        return new long[]{start, end, previousStart, start};
    }

    // ==================== JSON 片段 ====================

    /** range 单句 */
    private static String rng(long s, long e) { return rng("timestampMs", s, e); }

    private static String rng(String f, long s, long e) {
        return TraceQueryFilters.rangeFilter(f, s, e);
    }

    /** range bool filter query 的 JSON 字符串 */
    private static String rqJson(long s, long e) { return rqJson("timestampMs", s, e); }

    private static String rqJson(String f, long s, long e) {
        return "{\"bool\":{\"filter\":[" + rng(f, s, e) + "]}}";
    }

    // ==================== Query 构建（JSON → Query 反序列化） ====================

    /** 时间范围 Query */
    private Query rq(long s, long e) {
        return parseQuery(rqJson(s, e));
    }

    private Query rq(String f, long s, long e) {
        return parseQuery(rqJson(f, s, e));
    }

    /** 时间范围 + operation Query */
    private Query opQ(long s, long e, String op) {
        return parseQuery(TraceQueryFilters.operationSpanFilter(s, e, op));
    }

    /** 时间范围 + operation + status=OK Query */
    private Query opOkQ(long s, long e, String op) {
        String json = "{\"bool\":{\"filter\":["
                + rng(s, e) + ","
                + TraceQueryFilters.operationFilter(op) + ","
                + TraceQueryFilters.terminalSpanFilter() + ","
                + "{\"term\":{\"status\":\"OK\"}}]}}";
        return parseQuery(json);
    }

    private Query parseQuery(String json) {
        try {
            return Query._DESERIALIZER.deserialize(Json.createParser(new StringReader(json)), jsonpMapper);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse query: " + json, e);
        }
    }

    // ==================== ES 操作 ====================

    static String buildRootRequestCountQueryJson(long start, long end) {
        return "{\"size\":0,\"query\":"
                + TraceQueryFilters.rootRequestFilter(start, end)
                + ",\"aggs\":{\"v\":{\"cardinality\":{\"field\":\"traceId\","
                + "\"precision_threshold\":40000}}}}";
    }

    private long rootRequestCount(String index, long start, long end) {
        try {
            var request = SearchRequest.of(s -> s.index(index)
                    .withJson(new StringReader(buildRootRequestCountQueryJson(start, end))));
            var response = esClient.search(request, Map.class);
            var aggregate = response.aggregations().get("v");
            return aggregate != null && aggregate.isCardinality()
                    ? aggregate.cardinality().value() : 0L;
        } catch (Exception e) {
            log.error("rootRequestCount({}, {}, {}) failed", index, start, end, e);
            return 0L;
        }
    }

    private double rootRequestAvgLatency(String index, long start, long end) {
        try {
            Query rootRequest = parseQuery(TraceQueryFilters.rootRequestFilter(start, end));
            var req = SearchRequest.of(s -> s.index(index).size(0).query(rootRequest)
                    .aggregations("v", a -> a.avg(av -> av.field("durationMs"))));
            var resp = esClient.search(req, Map.class);
            var aggs = resp.aggregations();
            if (aggs != null && aggs.get("v") != null && aggs.get("v").avg() != null) {
                return aggs.get("v").avg().value();
            }
        } catch (Exception e) {
            log.error("rootRequestAvgLatency({}, {}, {}) failed", index, start, end, e);
        }
        return 0.0;
    }

    static String buildRagHitQueryJson(long start, long end) {
        return "{\"bool\":{\"filter\":["
                + TraceQueryFilters.rangeFilter(start, end) + ","
                + TraceQueryFilters.operationFilter(AgentOperation.RETRIEVE) + ","
                + TraceQueryFilters.terminalSpanFilter() + ","
                + "{\"term\":{\"status\":\"OK\"}},"
                + "{\"range\":{\"attributes.hitCount\":{\"gt\":0}}}]}}";
    }

    private double ragHitRate(String index, long start, long end) {
        try {
            long total = esClient.count(CountRequest.of(c ->
                    c.index(index).query(opQ(start, end, AgentOperation.RETRIEVE)))).count();
            long hit = esClient.count(CountRequest.of(c ->
                    c.index(index).query(parseQuery(buildRagHitQueryJson(start, end))))).count();
            return total > 0 ? round(hit * 100.0 / total) : 0.0;
        } catch (Exception e) {
            log.error("ragHitRate({}, {}, {}) failed", index, start, end, e);
            return 0.0;
        }
    }

    double opRate(String index, long start, long end, String operation) {
        try {
            long total = esClient.count(CountRequest.of(c ->
                    c.index(index).query(opQ(start, end, operation)))).count();
            long ok = esClient.count(CountRequest.of(c ->
                    c.index(index).query(opOkQ(start, end, operation)))).count();
            return total > 0 ? round(ok * 100.0 / total) : 0.0;
        } catch (Exception e) {
            log.error("opRate({}, {}, {}, {}) failed", index, start, end, operation, e);
            return 0.0;
        }
    }

    private double avgTokens(String index, long start, long end) {
        try {
            Query q = parseQuery("{\"bool\":{\"filter\":["
                    + rng(start, end) + ","
                    + "{\"exists\":{\"field\":\"inputToken\"}}]}}");
            var req = SearchRequest.of(s -> s.index(index).size(0).query(q)
                    .aggregations("ai", a -> a.avg(av -> av.field("inputToken")))
                    .aggregations("ao", a -> a.avg(av -> av.field("outputToken"))));
            var resp = esClient.search(req, Map.class);
            var aggs = resp.aggregations();
            double ai = aggs != null && aggs.get("ai") != null ? aggs.get("ai").avg().value() : 0;
            double ao = aggs != null && aggs.get("ao") != null ? aggs.get("ao").avg().value() : 0;
            return round(ai + ao);
        } catch (Exception e) {
            log.error("avgTokens({}, {}, {}) failed", index, start, end, e);
            return 0.0;
        }
    }

    double evalPassRate(long start, long end) {
        try {
            LocalDateTime startTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(start), ZONE);
            LocalDateTime endTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(end), ZONE);

            long total = evalResultMapper.selectCount(Wrappers.<EvalResult>lambdaQuery()
                    .ge(EvalResult::getEvalTime, startTime)
                    .lt(EvalResult::getEvalTime, endTime)
                    .in(EvalResult::getCollectionStatus, FINAL_EVAL_STATUSES));
            if (total == 0) {
                return 0.0;
            }

            long passed = evalResultMapper.selectCount(Wrappers.<EvalResult>lambdaQuery()
                    .ge(EvalResult::getEvalTime, startTime)
                    .lt(EvalResult::getEvalTime, endTime)
                    .in(EvalResult::getCollectionStatus, FINAL_EVAL_STATUSES)
                    .eq(EvalResult::getPassed, 1));
            return round(passed * 100.0 / total);
        } catch (Exception e) {
            log.error("evalPassRate({}, {}) failed", start, end, e);
            return 0.0;
        }
    }

    // ==================== 聚合解析 ====================

    @SuppressWarnings("unchecked")
    private List<AgentRankingDTO> parseRanking(
            co.elastic.clients.elasticsearch.core.SearchResponse<Map> resp, int limit) {
        List<AgentRankingDTO> list = new ArrayList<>();
        try {
            var aggs = resp.aggregations();
            if (aggs == null) return list;
            var byAgent = aggs.get("by_agent");
            if (byAgent == null || !byAgent.isSterms()) return list;
            var buckets = byAgent.sterms().buckets().array();
            int rank = 1;
            for (var bucket : buckets) {
                list.add(AgentRankingDTO.builder()
                        .rank(rank++)
                        .agentName(bucket.key().stringValue())
                        .requestCount(bucket.docCount())
                        .successRate(0.0).avgLatency(0.0).avgScore(0.0).build());
                if (list.size() >= limit) break;
            }
        } catch (Exception e) {
            log.warn("parseRanking failed: {}", e.getMessage());
        }
        return list;
    }

    // ==================== 辅助 ====================

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private DashboardSummaryDTO.MetricCard card(double val, String unit, double cur, double prev) {
        Double pct = null;
        String trend = null;
        if (prev != 0) {
            pct = round((cur - prev) / prev * 100.0);
            trend = pct >= 0 ? "up" : "down";
        }
        return DashboardSummaryDTO.MetricCard.builder()
                .value(round(val)).unit(unit).changePercent(pct).trend(trend).build();
    }

    private TrendDataDTO buildTrend(long start, long end, boolean hourly) {
        List<String> labels = new ArrayList<>();
        List<Long> vols = new ArrayList<>();
        Map<String, List<Double>> opRates = new LinkedHashMap<>();
        List<Double> lats = new ArrayList<>();
        long step = hourly ? 3600_000L : 86_400_000L;
        DateTimeFormatter fmt = hourly ? HOUR_FMT : DATE_FMT;

        for (String op : OPERATIONS) opRates.put(op, new ArrayList<>());

        for (long bs = start; bs < end; bs += step) {
            long be = Math.min(bs + step, end);
            labels.add(hourly
                    ? LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(bs), ZONE).format(fmt)
                    : LocalDate.ofInstant(java.time.Instant.ofEpochMilli(bs), ZONE).format(fmt));
            String traceIndex = traceIndexProperties.getTraceIndex();
            vols.add(rootRequestCount(traceIndex, bs, be));
            for (String op : OPERATIONS) {
                opRates.get(op).add(opRate(traceIndex, bs, be, op));
            }
            lats.add(round(rootRequestAvgLatency(traceIndex, bs, be)));
        }
        return TrendDataDTO.builder()
                .labels(labels).requestVolumes(vols)
                .operationSuccessRates(opRates).operationMeta(buildOpMeta())
                .avgLatencies(lats).build();
    }

    private Map<String, TrendDataDTO.OpMeta> buildOpMeta() {
        Map<String, TrendDataDTO.OpMeta> meta = new LinkedHashMap<>();
        for (AgentOperation op : AgentOperation.ALL) {
            meta.put(op.name(), TrendDataDTO.OpMeta.builder()
                    .label(op.label() + "成功率").color(op.color()).build());
        }
        return meta;
    }
}
