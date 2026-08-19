package com.agentinsight.config;

import com.agentinsight.entity.mysql.EvaluationTask;
import com.agentinsight.repository.mapper.EvaluationTaskMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class EvaluationTaskRecovery implements ApplicationRunner {

    private final EvaluationTaskMapper taskMapper;

    @Override
    public void run(ApplicationArguments args) {
        List<EvaluationTask> interruptedTasks = taskMapper.selectList(
                new LambdaQueryWrapper<EvaluationTask>()
                        .in(EvaluationTask::getStatus, "pending", "running"));
        for (EvaluationTask task : interruptedTasks) {
            task.setStatus("failed");
            task.setErrorMsg("服务重启导致评测中断，请重新提交任务");
            taskMapper.updateById(task);
        }
        if (!interruptedTasks.isEmpty()) {
            log.warn("已将 {} 个中断的评测任务标记为失败", interruptedTasks.size());
        }
    }
}
