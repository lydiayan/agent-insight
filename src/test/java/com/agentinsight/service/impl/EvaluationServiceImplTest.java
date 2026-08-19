package com.agentinsight.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.agentinsight.config.TraceIndexProperties;
import com.agentinsight.dto.EvalRunRequestDTO;
import com.agentinsight.dto.EvalTaskDTO;
import com.agentinsight.entity.mysql.EvalCase;
import com.agentinsight.entity.mysql.EvaluationTask;
import com.agentinsight.repository.mapper.EvalCaseMapper;
import com.agentinsight.repository.mapper.EvalCaseTagMapper;
import com.agentinsight.repository.mapper.EvalResultMapper;
import com.agentinsight.repository.mapper.EvaluationTaskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvaluationServiceImplTest {

    @Mock
    private EvaluationTaskMapper taskMapper;
    @Mock
    private EvalCaseMapper caseMapper;
    @Mock
    private EvalCaseTagMapper caseTagMapper;
    @Mock
    private EvalResultMapper resultMapper;
    @Mock
    private ElasticsearchClient elasticsearchClient;
    @Mock
    private TaskExecutor taskExecutor;

    private EvaluationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new EvaluationServiceImpl(
                taskMapper, caseMapper, caseTagMapper, resultMapper, elasticsearchClient,
                new TraceIndexProperties(), taskExecutor);
    }

    @Test
    void createsPendingTaskAndReturnsBeforeExecution() {
        when(caseMapper.selectBatchIds(List.of(1L))).thenReturn(List.of(testCase()));
        assignTaskIdOnInsert();

        EvalTaskDTO task = service.runEvaluation(EvalRunRequestDTO.builder()
                .caseIds(List.of(1L))
                .taskName("异步评测")
                .build());

        assertEquals(100L, task.getId());
        assertEquals("pending", task.getStatus());
        assertEquals(0, task.getCompleted());
        verify(taskExecutor).execute(any(Runnable.class));
        verifyNoInteractions(resultMapper);
    }

    @Test
    void marksTaskFailedWhenExecutorRejectsSubmission() {
        when(caseMapper.selectBatchIds(List.of(1L))).thenReturn(List.of(testCase()));
        assignTaskIdOnInsert();
        doThrow(new TaskRejectedException("queue full"))
                .when(taskExecutor).execute(any(Runnable.class));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.runEvaluation(EvalRunRequestDTO.builder()
                        .caseIds(List.of(1L))
                        .build()));

        assertEquals("评测任务队列已满，请稍后重试", error.getMessage());
        verify(taskMapper).updateById(any(EvaluationTask.class));
    }

    @Test
    void buildsRequestWithConfiguredDemoActor() {
        EvalCase evalCase = testCase();
        evalCase.setActorUserId(" SALES001 ");

        Map<String, Object> payload = EvaluationServiceImpl.buildRequestPayload(20L, evalCase);

        assertEquals("SALES001", payload.get("actorUserId"));
        assertFalse(payload.containsKey("userId"));
        assertEquals("eval-20-case-1", payload.get("conversationId"));
    }

    @Test
    void usesCustomerDemoActorForLegacyCases() {
        Map<String, Object> payload = EvaluationServiceImpl.buildRequestPayload(20L, testCase());

        assertEquals("USER1001", payload.get("actorUserId"));
    }

    @Test
    void mapsRefundEligibilityStrategyToRealTraceOperation() {
        assertEquals("tool.order_query",
                EvaluationServiceImpl.expectedToolOperation("ORDER_QUERY"));
        assertEquals("tool.refund_eligibility",
                EvaluationServiceImpl.expectedToolOperation("ORDER_POLICY_QUERY"));
    }

    @Test
    void detectsActualToolAndRagCallsFromCompletedTrace() {
        String snapshot = """
                {
                  "tool.order_query":[{"eventType":"SPAN_END"}],
                  "retrieve":[{"eventType":"SPAN_END"}]
                }
                """;

        assertTrue(EvaluationServiceImpl.detectToolCall(snapshot, true));
        assertTrue(EvaluationServiceImpl.detectRagCall(snapshot, true));
    }

    @Test
    void reportsAbsentCallsOnlyWhenTraceIsComplete() {
        String snapshot = "{\"agent.ask\":[{\"eventType\":\"TRACE_END\"}]}";

        assertFalse(EvaluationServiceImpl.detectToolCall(snapshot, true));
        assertFalse(EvaluationServiceImpl.detectRagCall(snapshot, true));
        assertNull(EvaluationServiceImpl.detectToolCall(snapshot, false));
        assertNull(EvaluationServiceImpl.detectRagCall(null, true));
    }

    private void assignTaskIdOnInsert() {
        doAnswer(invocation -> {
            EvaluationTask task = invocation.getArgument(0);
            task.setId(100L);
            return 1;
        }).when(taskMapper).insert(any(EvaluationTask.class));
    }

    private static EvalCase testCase() {
        return EvalCase.builder()
                .id(1L)
                .caseCode("CASE-001")
                .name("测试用例")
                .inputQuery("查询订单")
                .build();
    }
}
