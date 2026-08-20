package com.agentinsight.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import com.agentinsight.config.TraceIndexProperties;
import com.agentinsight.dto.EvalRunRequestDTO;
import com.agentinsight.dto.EvalTaskDTO;
import com.agentinsight.dto.EvalTaskDetailDTO;
import com.agentinsight.entity.mysql.EvalCase;
import com.agentinsight.entity.mysql.EvalCaseTag;
import com.agentinsight.entity.mysql.EvalResult;
import com.agentinsight.entity.mysql.EvaluationTask;
import com.agentinsight.repository.mapper.EvalCaseMapper;
import com.agentinsight.repository.mapper.EvalCaseTagMapper;
import com.agentinsight.repository.mapper.EvalResultMapper;
import com.agentinsight.repository.mapper.EvaluationTaskMapper;
import com.agentinsight.service.EvaluationService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * 评测引擎——真实调用 Agent API 版。
 * <p>
 * 两阶段流程：先调用 Agent 并保存 traceId；再由用户按需触发 ES Trace 采集与评分。
 */
@Slf4j
@Service
public class EvaluationServiceImpl implements EvaluationService {

    private static final String DEFAULT_ECOMMERCE_AGENT_ENDPOINT =
            "http://127.0.0.1:8087/internal/evaluation/ask";
    private static final String DEFAULT_DEMO_ACTOR_USER_ID = "USER1001";
    private static final String TRACE_PENDING = "trace_pending";
    private static final String TRACE_INCOMPLETE = "trace_incomplete";
    private static final String SCORED = "scored";
    private static final String CALL_FAILED = "call_failed";

    private final EvaluationTaskMapper taskMapper;
    private final EvalCaseMapper caseMapper;
    private final EvalCaseTagMapper caseTagMapper;
    private final EvalResultMapper resultMapper;
    private final ElasticsearchClient esClient;
    private final TraceIndexProperties traceIndexProperties;
    private final TaskExecutor evaluationTaskExecutor;

    @Value("${agent.evaluation-token:}")
    private String evaluationToken;

    @Value("${agent.evaluation-endpoint:}")
    private String configuredEvaluationEndpoint;

    private static final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    public EvaluationServiceImpl(EvaluationTaskMapper taskMapper,
                                 EvalCaseMapper caseMapper,
                                 EvalCaseTagMapper caseTagMapper,
                                 EvalResultMapper resultMapper,
                                 ElasticsearchClient esClient,
                                 TraceIndexProperties traceIndexProperties,
                                 @Qualifier("evaluationTaskExecutor") TaskExecutor evaluationTaskExecutor) {
        this.taskMapper = taskMapper;
        this.caseMapper = caseMapper;
        this.caseTagMapper = caseTagMapper;
        this.resultMapper = resultMapper;
        this.esClient = esClient;
        this.traceIndexProperties = traceIndexProperties;
        this.evaluationTaskExecutor = evaluationTaskExecutor;
    }

    @Override
    public EvalTaskDTO runEvaluation(EvalRunRequestDTO request) {
        List<Long> caseIds = List.copyOf(request.getCaseIds());
        List<EvalCase> cases = caseMapper.selectBatchIds(caseIds);
        if (cases.isEmpty()) throw new IllegalArgumentException("没有找到有效的评测用例");

        String taskName = request.getTaskName() != null
                ? request.getTaskName()
                : "评测任务 " + LocalDateTime.now().toString().substring(0, 16);

        EvaluationTask task = EvaluationTask.builder()
                .taskName(taskName).caseCount(cases.size())
                .completed(0).passedCount(0).failedCount(0).status("pending").build();
        taskMapper.insert(task);

        try {
            evaluationTaskExecutor.execute(
                    () -> executeEvaluationTask(task.getId(), caseIds, request.getAgentEndpoint()));
        } catch (TaskRejectedException e) {
            log.error("评测任务队列已满，taskId={}", task.getId(), e);
            task.setStatus("failed");
            task.setErrorMsg("评测任务队列已满，请稍后重试");
            taskMapper.updateById(task);
            throw new IllegalStateException(task.getErrorMsg(), e);
        }

        return toTaskDTO(task);
    }

    private void executeEvaluationTask(Long taskId, List<Long> caseIds, String agentEndpoint) {
        EvaluationTask task = taskMapper.selectById(taskId);
        if (task == null) {
            log.warn("后台评测任务不存在，taskId={}", taskId);
            return;
        }

        task.setStatus("running");
        task.setErrorMsg(null);
        taskMapper.updateById(task);

        try {
            List<EvalCase> cases = caseMapper.selectBatchIds(caseIds);
            if (cases.isEmpty()) {
                throw new IllegalStateException("没有找到有效的评测用例");
            }
            invokeCases(task, cases, agentEndpoint);
        } catch (Exception e) {
            log.error("评测任务 {} 执行失败", taskId, e);
            task.setStatus("failed");
            task.setErrorMsg(e.getMessage());
            taskMapper.updateById(task);
        }
    }

    // ==================== 第一阶段：调用 Agent ====================

    private void invokeCases(EvaluationTask task, List<EvalCase> cases, String agentEndpoint) {
        int pending = 0;
        int callFailed = 0;

        int processed = 0;
        for (EvalCase c : cases) {
            ScoringResult invocation = invokeAgent(task.getId(), c, agentEndpoint);
            insertResult(task.getId(), c.getId(), invocation);
            if (TRACE_PENDING.equals(invocation.collectionStatus)) {
                pending++;
            } else {
                callFailed++;
            }
            task.setCompleted(++processed);
            task.setFailedCount(callFailed);
            taskMapper.updateById(task);
        }

        task.setCompleted(callFailed);
        task.setPassedCount(0);
        task.setFailedCount(callFailed);
        task.setAvgScore(null);
        task.setStatus(pending > 0 ? TRACE_PENDING : "failed");
        if (pending == 0) {
            task.setErrorMsg("所有用例均未获得有效 traceId");
        }
        taskMapper.updateById(task);
    }

    private ScoringResult invokeAgent(Long taskId, EvalCase c, String requestedEndpoint) {
        ScoringResult r = new ScoringResult();
        r.maxTotal = nvl(c.getScoreTool(), 30) + nvl(c.getScoreRag(), 20) + nvl(c.getScoreAnswer(), 50);
        r.total = 0;
        r.collectionStatus = CALL_FAILED;

        String endpoint = firstNonBlank(
                requestedEndpoint,
                configuredEvaluationEndpoint,
                c.getAgentEndpoint(),
                DEFAULT_ECOMMERCE_AGENT_ENDPOINT);

        try {
            if (evaluationToken == null || evaluationToken.isBlank()) {
                throw new IllegalStateException("未配置 AGENT_EVALUATION_TOKEN");
            }
            String body = buildRequestBody(taskId, c);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + evaluationToken.trim())
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            captureAgentResponse(resp, r);
            r.agentTraceId = extractTraceId(resp);
            r.invokedAt = LocalDateTime.now();
            log.info("用例 {} → Agent {} → traceId={}", c.getCaseCode(), endpoint, r.agentTraceId);
        } catch (Exception e) {
            r.invokedAt = LocalDateTime.now();
            log.error("调用 Agent 失败: endpoint={}, case={}", endpoint, c.getCaseCode(), e);
            r.failureReasons.add("Agent 调用失败: " + e.getMessage());
            return r;
        }

        if (r.httpStatus != 200) {
            r.failureReasons.add("Agent 返回 HTTP " + r.httpStatus);
            return r;
        }
        if (r.agentTraceId == null || r.agentTraceId.isBlank()) {
            r.failureReasons.add("未从响应中提取到 traceId");
            return r;
        }

        r.collectionStatus = TRACE_PENDING;
        return r;
    }

    // ==================== 第二阶段：采集 Trace 并评分 ====================

    @Override
    public EvalTaskDetailDTO collectTraceAndScore(Long taskId) {
        EvaluationTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("任务不存在: " + taskId);
        }

        List<EvalResult> results = selectTaskResults(taskId);
        Map<Long, EvalCase> caseMap = loadCaseMap(results);

        for (EvalResult result : results) {
            String status = result.getCollectionStatus();
            if (!TRACE_PENDING.equals(status) && !TRACE_INCOMPLETE.equals(status)) {
                continue;
            }

            EvalCase evalCase = caseMap.get(result.getCaseId());
            if (evalCase == null) {
                markTraceIncomplete(result, null, "评测用例不存在", null);
                continue;
            }

            ScoringResult score = restoreInvocation(result);
            if (score.agentTraceId == null || score.agentTraceId.isBlank()) {
                markTraceIncomplete(result, score, "评测结果中缺少 Agent traceId", null);
                continue;
            }

            Map<String, Object> spanData;
            try {
                spanData = fetchSpanData(score.agentTraceId);
            } catch (Exception e) {
                log.error("拉取 span 失败: traceId={}", score.agentTraceId, e);
                markTraceIncomplete(result, score, "读取 ES Trace 失败: " + e.getMessage(), null);
                continue;
            }

            String snapshot = JSONUtil.toJsonStr(spanData);
            List<String> missing = findMissingTraceData(evalCase, score, spanData);
            if (!missing.isEmpty()) {
                markTraceIncomplete(result, score,
                        "Trace 数据尚未完整: " + String.join("、", missing), snapshot);
                continue;
            }

            scoreTool(score, evalCase, spanData);
            scoreRag(score, evalCase, spanData);
            scoreAnswer(score, spanData);
            score.total = score.toolScore + score.ragScore + score.answerScore;
            score.passed = score.total >= nvl(evalCase.getPassThreshold(), 60);
            score.collectionStatus = SCORED;
            score.collectedAt = LocalDateTime.now();
            updateResult(result, score, snapshot, null);
        }

        updateTaskSummary(task, results);
        return getTaskDetail(taskId);
    }

    // ==================== 打分逻辑 ====================

    /** Tool 维度：根据 Agent 根 span 的 planStrategy 校验真实规划动作。 */
    @SuppressWarnings("unchecked")
    private void scoreTool(ScoringResult r, EvalCase c, Map<String, Object> spanData) {
        if (c.getExpectedToolName() == null || c.getExpectedToolName().isBlank()) {
            r.toolScore = nvl(c.getScoreTool(), 0); r.toolMatch = true; return;
        }
        List<Map<String, Object>> agentSpans =
                (List<Map<String, Object>>) spanData.getOrDefault("agent.ask", List.of());
        String expected = c.getExpectedToolName();
        boolean found = agentSpans.stream().anyMatch(s -> {
            if (!"OK".equals(s.get("status"))) return false;
            Object attributes = s.get("attributes");
            if (!(attributes instanceof Map<?, ?> attrs)) return false;
            Object strategy = attrs.get("planStrategy");
            if ("ORDER_QUERY".equals(expected)) {
                return "ORDER_QUERY".equals(strategy) || "DANGEROUS_ORDER_OP".equals(strategy);
            }
            return expected.equals(strategy);
        });
        if (found) {
            r.toolScore = nvl(c.getScoreTool(), 30); r.toolMatch = true;
        } else {
            r.toolScore = 0; r.toolMatch = false;
            r.failureReasons.add("工具规划不匹配：期望 " + expected);
        }
    }

    /** RAG 维度 (20分)：检查 milvus/retrieve/rerank span 的 chunkCount */
    @SuppressWarnings("unchecked")
    private void scoreRag(ScoringResult r, EvalCase c, Map<String, Object> spanData) {
        if (c.getExpectedChunk() == null || c.getExpectedChunk().isBlank()) {
            r.ragScore = nvl(c.getScoreRag(), 0); r.ragMatch = true; return;
        }
        long totalChunks = 0;
        for (String op : List.of("milvus", "retrieve", "rerank")) {
            List<Map<String, Object>> spans = (List<Map<String, Object>>) spanData.getOrDefault(op, List.of());
            for (var s : spans) {
                Object cc = s.get("contextChunks");
                if (cc instanceof Number n) totalChunks += n.longValue();
                Object attributes = s.get("attributes");
                if (attributes instanceof Map<?, ?> attrs) {
                    for (String field : List.of("hitCount", "recallCount", "resultCount")) {
                        Object count = attrs.get(field);
                        if (count instanceof Number n) totalChunks += n.longValue();
                    }
                }
            }
        }
        if (totalChunks > 0) {
            r.ragScore = nvl(c.getScoreRag(), 20); r.ragMatch = true;
        } else {
            r.ragScore = 0; r.ragMatch = false;
            r.failureReasons.add("RAG 未召回任何片段");
        }
    }

    /** Answer 维度 (50分)：简单检查 llm operation span 是否有非空 output */
    @SuppressWarnings("unchecked")
    private void scoreAnswer(ScoringResult r, Map<String, Object> spanData) {
        List<Map<String, Object>> llmSpans = (List<Map<String, Object>>) spanData.getOrDefault("llm", List.of());
        boolean hasOutput = llmSpans.stream().anyMatch(s -> {
            Object o = s.get("outputToken");
            return o instanceof Number n && n.longValue() > 0;
        });
        if (hasOutput) {
            r.answerScore = 50; r.answerMatch = true;
        } else {
            r.answerScore = 0; r.answerMatch = false;
            r.failureReasons.add("LLM 未产出有效输出 (outputToken=0)");
        }
    }

    // ==================== ES 查询 ====================

    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchSpanData(String traceId) throws Exception {
        Map<String, Object> result = new HashMap<>();
        var resp = esClient.search(SearchRequest.of(s -> s
                .index(traceIndexProperties.getTraceIndex()).size(500)
                .query(Query.of(q -> q.term(t -> t.field("traceId").value(traceId))))
                .source(src -> src.filter(filter -> filter.includes(
                        "traceId", "spanId", "parentSpanId", "eventType", "timestampMs",
                        "operation", "status", "contextChunks",
                        "outputToken", "inputToken", "output", "durationMs",
                        "errorMessage", "attributes")))), Map.class);

        for (var hit : resp.hits().hits()) {
            Map<String, Object> src = (Map<String, Object>) hit.source();
            String op = (String) src.getOrDefault("operation", "unknown");
            ((List<Map<String, Object>>) result.computeIfAbsent(op, k -> new ArrayList<>())).add(src);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<String> findMissingTraceData(EvalCase evalCase, ScoringResult score,
                                              Map<String, Object> spanData) {
        List<String> missing = new ArrayList<>();
        if (!hasTraceEvent(spanData, "agent.ask", "TRACE_END")) {
            missing.add("TRACE_END");
        }

        if (nvl(evalCase.getScoreTool(), 0) > 0
                && evalCase.getExpectedToolName() != null
                && !evalCase.getExpectedToolName().isBlank()) {
            String operation = expectedToolOperation(evalCase.getExpectedToolName());
            if (!hasTraceEvent(spanData, operation, "SPAN_END")) {
                missing.add(operation);
            }
        }

        if (nvl(evalCase.getScoreRag(), 0) > 0
                && evalCase.getExpectedChunk() != null
                && !evalCase.getExpectedChunk().isBlank()
                && !hasTraceEvent(spanData, "retrieve", "SPAN_END")) {
            missing.add("retrieve");
        }

        boolean deterministicAnswer = score.interrupted
                || "DANGEROUS_ORDER_OP".equals(score.planStrategy)
                || "DANGEROUS_OP".equals(score.planStrategy);
        if (nvl(evalCase.getScoreAnswer(), 0) > 0
                && !deterministicAnswer
                && !hasTraceEvent(spanData, "llm", "SPAN_END")) {
            missing.add("llm");
        }
        return missing;
    }

    @SuppressWarnings("unchecked")
    private boolean hasTraceEvent(Map<String, Object> spanData, String operation, String eventType) {
        List<Map<String, Object>> spans =
                (List<Map<String, Object>>) spanData.getOrDefault(operation, List.of());
        return spans.stream().anyMatch(span -> eventType.equals(span.get("eventType")));
    }

    // ==================== HTTP 调用工具方法 ====================

    private String buildRequestBody(Long taskId, EvalCase c) {
        return JSONUtil.toJsonStr(buildRequestPayload(taskId, c));
    }

    static Map<String, Object> buildRequestPayload(Long taskId, EvalCase c) {
        Map<String, Object> body = new LinkedHashMap<>();
        String actorUserId = Optional.ofNullable(c.getActorUserId())
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .orElse(DEFAULT_DEMO_ACTOR_USER_ID);
        body.put("query", c.getInputQuery());
        body.put("actorUserId", actorUserId);
        body.put("conversationId", "eval-" + taskId + "-case-" + c.getId());
        body.put("topK", 5);
        return body;
    }

    static String expectedToolOperation(String expectedToolName) {
        return switch (expectedToolName) {
            case "ORDER_QUERY" -> "tool.order_query";
            case "ORDER_POLICY_QUERY" -> "tool.refund_eligibility";
            default -> "tool." + expectedToolName.toLowerCase(Locale.ROOT);
        };
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        throw new IllegalStateException("未配置 Agent 接口");
    }

    /**
     * 从 Agent 响应中提取 traceId。
     * 默认假设 traceId 在 Body 的 data.traceId 或 headers.X-Trace-Id 中。
     * 如果你的 Agent 返回格式不同，改这里即可。
     */
    private String extractTraceId(HttpResponse<String> resp) {
        if (resp.statusCode() != 200) {
            log.warn("Agent 返回非200: status={}, body={}", resp.statusCode(), resp.body().substring(0, Math.min(200, resp.body().length())));
            return null;
        }
        try {
            JSONObject json = JSONUtil.parseObj(resp.body());
            // 优先从 data.traceId 取
            Object tid = json.getByPath("data.traceId");
            if (tid != null) return tid.toString();
            // 其次从根级 traceId 取
            tid = json.get("traceId");
            if (tid != null) return tid.toString();
        } catch (Exception e) {
            log.warn("解析 Agent 响应 JSON 失败: {}", e.getMessage());
        }
        // 最后尝试 headers
        return resp.headers().firstValue("X-Trace-Id").orElse(null);
    }

    private void captureAgentResponse(HttpResponse<String> resp, ScoringResult score) {
        score.httpStatus = resp.statusCode();
        score.rawResponse = resp.body();
        try {
            JSONObject json = JSONUtil.parseObj(resp.body());
            score.agentOutput = valueAsString(json.getByPath("data.answer"));
            score.planStrategy = valueAsString(json.getByPath("data.planStrategy"));
            score.conversationId = valueAsString(json.getByPath("data.conversationId"));
            score.interrupted = Boolean.TRUE.equals(json.getByPath("data.interrupted"));
        } catch (Exception e) {
            log.warn("保存 Agent 输出时解析响应失败: {}", e.getMessage());
        }
    }

    private String valueAsString(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    private void insertResult(Long taskId, Long caseId, ScoringResult score) {
        Map<String, Object> detail = buildDetail(score, null);

        resultMapper.insert(EvalResult.builder()
                .traceId("eval-task-" + taskId + "-case-" + caseId)
                .agentTraceId(score.agentTraceId)
                .collectionStatus(score.collectionStatus)
                .invokedAt(score.invokedAt)
                .caseId(caseId)
                .scoreTool(score.toolScore).scoreRag(score.ragScore).scoreAnswer(score.answerScore)
                .scoreTotal(score.total).scoreMax(score.maxTotal)
                .passed(score.passed ? 1 : 0)
                .evalDetail(JSONUtil.toJsonStr(detail))
                .evalTime(score.invokedAt)
                .build());
    }

    private Map<String, Object> buildDetail(ScoringResult score, String collectionMessage) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("toolMatch", score.toolMatch);
        detail.put("ragMatch", score.ragMatch);
        detail.put("answerMatch", score.answerMatch);
        detail.put("failureReasons", score.failureReasons);
        detail.put("agentOutput", score.agentOutput);
        detail.put("agentTraceId", score.agentTraceId);
        detail.put("planStrategy", score.planStrategy);
        detail.put("conversationId", score.conversationId);
        detail.put("interrupted", score.interrupted);
        detail.put("httpStatus", score.httpStatus);
        detail.put("rawResponse", score.rawResponse);
        if (collectionMessage != null && !collectionMessage.isBlank()) {
            detail.put("collectionMessage", collectionMessage);
        }
        return detail;
    }

    @SuppressWarnings("unchecked")
    private ScoringResult restoreInvocation(EvalResult result) {
        ScoringResult score = new ScoringResult();
        score.maxTotal = nvl(result.getScoreMax(), 100);
        score.agentTraceId = result.getAgentTraceId();
        score.collectionStatus = result.getCollectionStatus();
        score.invokedAt = result.getInvokedAt();
        try {
            Map<String, Object> detail = JSONUtil.toBean(result.getEvalDetail(), Map.class);
            if (score.agentTraceId == null) {
                score.agentTraceId = valueAsString(detail.get("agentTraceId"));
            }
            score.agentOutput = valueAsString(detail.get("agentOutput"));
            score.planStrategy = valueAsString(detail.get("planStrategy"));
            score.conversationId = valueAsString(detail.get("conversationId"));
            score.rawResponse = valueAsString(detail.get("rawResponse"));
            score.interrupted = Boolean.TRUE.equals(detail.get("interrupted"));
            score.httpStatus = detail.get("httpStatus") instanceof Number n ? n.intValue() : 0;
            Object reasons = detail.get("failureReasons");
            if (reasons instanceof List<?> list) {
                list.forEach(reason -> score.failureReasons.add(String.valueOf(reason)));
            }
        } catch (Exception e) {
            log.warn("恢复 Agent 调用结果失败: resultId={}, error={}", result.getId(), e.getMessage());
        }
        return score;
    }

    private void updateResult(EvalResult result, ScoringResult score,
                              String traceSnapshot, String collectionMessage) {
        result.setAgentTraceId(score.agentTraceId);
        result.setCollectionStatus(score.collectionStatus);
        result.setCollectedAt(score.collectedAt);
        result.setTraceSnapshot(traceSnapshot);
        result.setScoreTool(score.toolScore);
        result.setScoreRag(score.ragScore);
        result.setScoreAnswer(score.answerScore);
        result.setScoreTotal(score.total);
        result.setScoreMax(score.maxTotal);
        result.setPassed(score.passed ? 1 : 0);
        result.setEvalDetail(JSONUtil.toJsonStr(buildDetail(score, collectionMessage)));
        if (score.collectedAt != null) {
            result.setEvalTime(score.collectedAt);
        }
        resultMapper.updateById(result);
    }

    private void markTraceIncomplete(EvalResult result, ScoringResult score,
                                     String message, String traceSnapshot) {
        ScoringResult restored = score != null ? score : restoreInvocation(result);
        restored.collectionStatus = TRACE_INCOMPLETE;
        result.setAgentTraceId(restored.agentTraceId);
        result.setCollectionStatus(TRACE_INCOMPLETE);
        result.setTraceSnapshot(traceSnapshot);
        result.setEvalDetail(JSONUtil.toJsonStr(buildDetail(restored, message)));
        resultMapper.updateById(result);
    }

    private List<EvalResult> selectTaskResults(Long taskId) {
        return resultMapper.selectList(new LambdaQueryWrapper<EvalResult>()
                .likeRight(EvalResult::getTraceId, "eval-task-" + taskId + "-")
                .orderByAsc(EvalResult::getId));
    }

    private Map<Long, EvalCase> loadCaseMap(List<EvalResult> results) {
        Map<Long, EvalCase> caseMap = new HashMap<>();
        if (!results.isEmpty()) {
            caseMapper.selectBatchIds(results.stream().map(EvalResult::getCaseId).distinct().toList())
                    .forEach(evalCase -> caseMap.put(evalCase.getId(), evalCase));
        }
        return caseMap;
    }

    private Map<Long, Set<String>> loadCaseTagMap(List<EvalResult> results) {
        List<Long> caseIds = results.stream().map(EvalResult::getCaseId).distinct().toList();
        if (caseIds.isEmpty()) return Map.of();
        return caseTagMapper.selectList(
                        new LambdaQueryWrapper<EvalCaseTag>().in(EvalCaseTag::getCaseId, caseIds))
                .stream()
                .collect(Collectors.groupingBy(
                        EvalCaseTag::getCaseId,
                        Collectors.mapping(EvalCaseTag::getTag, Collectors.toSet())));
    }

    private void updateTaskSummary(EvaluationTask task, List<EvalResult> results) {
        int scored = 0;
        int callFailed = 0;
        int passed = 0;
        int failed = 0;
        int totalScore = 0;
        boolean hasPending = false;

        for (EvalResult result : results) {
            if (SCORED.equals(result.getCollectionStatus())) {
                scored++;
                totalScore += nvl(result.getScoreTotal(), 0);
                if (Integer.valueOf(1).equals(result.getPassed())) {
                    passed++;
                } else {
                    failed++;
                }
            } else if (CALL_FAILED.equals(result.getCollectionStatus())) {
                callFailed++;
                failed++;
            } else {
                hasPending = true;
            }
        }

        int completed = scored + callFailed;
        task.setCompleted(completed);
        task.setPassedCount(passed);
        task.setFailedCount(failed);
        task.setAvgScore(!hasPending && completed > 0
                ? BigDecimal.valueOf((double) totalScore / completed).setScale(1, RoundingMode.HALF_UP)
                : null);
        task.setStatus(hasPending ? TRACE_PENDING : (scored > 0 ? "completed" : "failed"));
        task.setErrorMsg(!hasPending && scored == 0 ? "所有用例均调用失败" : null);
        taskMapper.updateById(task);
    }

    // ==================== 兜底 ====================

    /** 未配置 endpoint 时降级到模拟打分 */
    private ScoringResult fallbackScore(EvalCase c) {
        ScoringResult r = new ScoringResult();
        r.toolScore = c.getExpectedToolName() != null && !c.getExpectedToolName().isBlank() ? nvl(c.getScoreTool(), 30) : 30;
        r.ragScore = c.getExpectedChunk() != null && !c.getExpectedChunk().isBlank() ? nvl(c.getScoreRag(), 20) : 20;
        r.answerScore = nvl(c.getScoreAnswer(), 50);
        r.toolMatch = true; r.ragMatch = true; r.answerMatch = true;
        r.maxTotal = nvl(c.getScoreTool(), 30) + nvl(c.getScoreRag(), 20) + nvl(c.getScoreAnswer(), 50);
        r.total = r.toolScore + r.ragScore + r.answerScore;
        r.passed = r.total >= nvl(c.getPassThreshold(), 60);
        r.failureReasons.add("未配置 agentEndpoint，使用兜底评分");
        return r;
    }

    // ==================== 查询 ====================

    @Override
    public List<EvalTaskDTO> listTasks() {
        return taskMapper.selectList(
                new LambdaQueryWrapper<EvaluationTask>().orderByDesc(EvaluationTask::getCreatedAt))
                .stream().map(this::toTaskDTO).toList();
    }

    private EvalTaskDTO toTaskDTO(EvaluationTask task) {
        EvalTaskDTO dto = new EvalTaskDTO();
        BeanUtil.copyProperties(task, dto);
        return dto;
    }

    @Override
    public EvalTaskDetailDTO getTaskDetail(Long taskId) {
        EvaluationTask task = taskMapper.selectById(taskId);
        if (task == null) throw new RuntimeException("任务不存在: " + taskId);

        List<EvalResult> results = selectTaskResults(taskId);
        Map<Long, EvalCase> caseMap = loadCaseMap(results);
        Map<Long, Set<String>> caseTagMap = loadCaseTagMap(results);

        List<EvalTaskDetailDTO.CaseResultItem> items = results.stream().map(r -> {
            EvalCase c = caseMap.get(r.getCaseId());
            List<String> reasons = new ArrayList<>();
            String agentOutput = null;
            String agentTraceId = r.getAgentTraceId();
            String planStrategy = null;
            String conversationId = null;
            Boolean interrupted = null;
            Integer httpStatus = null;
            String rawResponse = null;
            String collectionMessage = null;
            try {
                Map<String, Object> detail = JSONUtil.toBean(r.getEvalDetail(), Map.class);
                Object fr = detail.get("failureReasons");
                if (fr instanceof List<?> list) list.forEach(o -> reasons.add(String.valueOf(o)));
                agentOutput = valueAsString(detail.get("agentOutput"));
                String detailTraceId = valueAsString(detail.get("agentTraceId"));
                if (detailTraceId != null) agentTraceId = detailTraceId;
                planStrategy = valueAsString(detail.get("planStrategy"));
                conversationId = valueAsString(detail.get("conversationId"));
                interrupted = detail.get("interrupted") instanceof Boolean b ? b : null;
                httpStatus = detail.get("httpStatus") instanceof Number n ? n.intValue() : null;
                rawResponse = valueAsString(detail.get("rawResponse"));
                collectionMessage = valueAsString(detail.get("collectionMessage"));
            } catch (Exception ignored) {}
            String collectionStatus = r.getCollectionStatus() != null
                    ? r.getCollectionStatus() : SCORED;
            boolean traceComplete = SCORED.equals(collectionStatus);
            return EvalTaskDetailDTO.CaseResultItem.builder()
                    .caseId(r.getCaseId())
                    .caseCode(c != null ? c.getCaseCode() : "UNKNOWN")
                    .caseName(c != null ? c.getName() : "未知")
                    .expectedToolCalled(c != null && hasText(c.getExpectedToolName()))
                    .expectedToolName(c != null ? c.getExpectedToolName() : null)
                    .expectedRagCalled(c != null && hasText(c.getExpectedChunk()))
                    .expectedHumanConfirmation(caseTagMap
                            .getOrDefault(r.getCaseId(), Set.of()).contains("人工确认"))
                    .actualToolCalled(detectToolCall(r.getTraceSnapshot(), traceComplete))
                    .actualRagCalled(detectRagCall(r.getTraceSnapshot(), traceComplete))
                    .scoreTool(r.getScoreTool()).scoreRag(r.getScoreRag()).scoreAnswer(r.getScoreAnswer())
                    .scoreTotal(r.getScoreTotal()).scoreMax(r.getScoreMax())
                    .passed(r.getPassed() == 1)
                    .failureReasons(String.join("; ", reasons))
                    .agentOutput(agentOutput).agentTraceId(agentTraceId)
                    .planStrategy(planStrategy).conversationId(conversationId)
                    .interrupted(CALL_FAILED.equals(collectionStatus) ? null : interrupted).httpStatus(httpStatus)
                    .rawResponse(rawResponse)
                    .collectionStatus(collectionStatus)
                    .collectionMessage(collectionMessage)
                    .invokedAt(r.getInvokedAt()).collectedAt(r.getCollectedAt())
                    .evalTime(r.getEvalTime()).build();
        }).toList();

        return EvalTaskDetailDTO.builder()
                .taskId(task.getId()).taskName(task.getTaskName()).status(task.getStatus())
                .caseCount(task.getCaseCount()).completed(task.getCompleted())
                .passedCount(task.getPassedCount()).failedCount(task.getFailedCount())
                .avgScore(task.getAvgScore() != null ? task.getAvgScore().doubleValue() : null)
                .items(items).build();
    }

    static Boolean detectToolCall(String traceSnapshot, boolean traceComplete) {
        return detectTraceCall(traceSnapshot, traceComplete, operation -> operation.startsWith("tool."));
    }

    static Boolean detectRagCall(String traceSnapshot, boolean traceComplete) {
        return detectTraceCall(traceSnapshot, traceComplete,
                operation -> Set.of("retrieve", "milvus", "rerank").contains(operation));
    }

    @SuppressWarnings("unchecked")
    private static Boolean detectTraceCall(String traceSnapshot, boolean traceComplete,
                                           Predicate<String> operationMatcher) {
        if (!hasText(traceSnapshot)) return null;
        try {
            Map<String, Object> snapshot = JSONUtil.toBean(traceSnapshot, Map.class);
            boolean found = snapshot.entrySet().stream()
                    .filter(entry -> operationMatcher.test(entry.getKey()))
                    .map(Map.Entry::getValue)
                    .filter(List.class::isInstance)
                    .map(List.class::cast)
                    .flatMap(Collection::stream)
                    .anyMatch(value -> value instanceof Map<?, ?> span
                            && "SPAN_END".equals(span.get("eventType")));
            return found ? Boolean.TRUE : (traceComplete ? Boolean.FALSE : null);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static int nvl(Integer v, int def) { return v != null ? v : def; }

    private static class ScoringResult {
        int toolScore, ragScore, answerScore, total, maxTotal = 100;
        boolean toolMatch, ragMatch, answerMatch, passed;
        String agentOutput, agentTraceId, planStrategy, conversationId, rawResponse;
        String collectionStatus;
        boolean interrupted;
        int httpStatus;
        LocalDateTime invokedAt, collectedAt;
        List<String> failureReasons = new ArrayList<>();
    }
}
