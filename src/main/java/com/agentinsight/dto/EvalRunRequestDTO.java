package com.agentinsight.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvalRunRequestDTO {

    @NotEmpty
    @Size(min = 1, max = 100)
    private List<Long> caseIds;

    private String taskName;

    /** 本次评测统一调用的 Agent 接口；为空时使用用例自身配置 */
    @Size(max = 512)
    private String agentEndpoint;
}
