package com.agentinsight.controller;

import com.agentinsight.common.Result;
import com.agentinsight.dto.EvalRunRequestDTO;
import com.agentinsight.dto.EvalTaskDTO;
import com.agentinsight.dto.EvalTaskDetailDTO;
import com.agentinsight.service.EvaluationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/evaluation")
@RequiredArgsConstructor
public class EvaluationController {

    private final EvaluationService evaluationService;

    /** 发起评测 */
    @PostMapping("/run")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Result<EvalTaskDTO> run(@Valid @RequestBody EvalRunRequestDTO request) {
        return Result.success(evaluationService.runEvaluation(request));
    }

    /** 任务列表 */
    @GetMapping("/task/list")
    public Result<List<EvalTaskDTO>> listTasks() {
        return Result.success(evaluationService.listTasks());
    }

    /** 任务详情（逐条用例得分） */
    @GetMapping("/task/{id}")
    public Result<EvalTaskDetailDTO> getTaskDetail(@PathVariable Long id) {
        return Result.success(evaluationService.getTaskDetail(id));
    }

    /** 获取 Trace 并评分；单次按需查询 ES，不执行后台轮询。 */
    @PostMapping("/task/{id}/collect-trace")
    public Result<EvalTaskDetailDTO> collectTraceAndScore(@PathVariable Long id) {
        return Result.success(evaluationService.collectTraceAndScore(id));
    }
}
