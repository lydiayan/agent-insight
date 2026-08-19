package com.agentinsight.service;

import com.agentinsight.common.PageResult;
import com.agentinsight.dto.EvalCaseDTO;
import com.agentinsight.dto.EvalCaseQueryDTO;

import java.util.List;

/**
 * 评测用例管理服务接口
 */
public interface CaseService {

    PageResult<EvalCaseDTO> listCases(EvalCaseQueryDTO query);

    EvalCaseDTO getCaseById(Long id);

    EvalCaseDTO createCase(EvalCaseDTO dto);

    EvalCaseDTO updateCase(Long id, EvalCaseDTO dto);

    void deleteCase(Long id);

    void batchDeleteCases(List<Long> ids);

    int batchImportCases(List<EvalCaseDTO> cases);
}
