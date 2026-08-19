package com.agentinsight.service;

import com.agentinsight.dto.EvalRunRequestDTO;
import com.agentinsight.dto.EvalTaskDTO;
import com.agentinsight.dto.EvalTaskDetailDTO;

import java.util.List;

public interface EvaluationService {

    /** 发起评测任务 */
    EvalTaskDTO runEvaluation(EvalRunRequestDTO request);

    /** 查询任务列表 */
    List<EvalTaskDTO> listTasks();

    /** 查询任务详情（逐条用例得分） */
    EvalTaskDetailDTO getTaskDetail(Long taskId);

    /** 从 ES 获取已落盘的 Trace，并对数据完整的用例评分 */
    EvalTaskDetailDTO collectTraceAndScore(Long taskId);
}
