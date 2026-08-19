package com.agentinsight.controller;

import com.agentinsight.common.PageResult;
import com.agentinsight.common.Result;
import com.agentinsight.dto.EvalCaseDTO;
import com.agentinsight.dto.EvalCaseQueryDTO;
import com.agentinsight.service.CaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 评测用例管理 API
 */
@RestController
@RequestMapping("/api/case")
@RequiredArgsConstructor
public class CaseController {

    private final CaseService caseService;

    /**
     * 分页查询用例列表
     */
    @GetMapping("/list")
    public Result<PageResult<EvalCaseDTO>> listCases(EvalCaseQueryDTO query) {
        return Result.success(caseService.listCases(query));
    }

    /**
     * 查询单条用例详情
     */
    @GetMapping("/{id}")
    public Result<EvalCaseDTO> getCase(@PathVariable Long id) {
        return Result.success(caseService.getCaseById(id));
    }

    /**
     * 新增测试用例
     */
    @PostMapping
    public Result<EvalCaseDTO> createCase(@RequestBody EvalCaseDTO dto) {
        return Result.success(caseService.createCase(dto));
    }

    /**
     * 编辑测试用例
     */
    @PutMapping("/{id}")
    public Result<EvalCaseDTO> updateCase(@PathVariable Long id, @RequestBody EvalCaseDTO dto) {
        return Result.success(caseService.updateCase(id, dto));
    }

    /**
     * 删除单条用例
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteCase(@PathVariable Long id) {
        caseService.deleteCase(id);
        return Result.success();
    }

    /**
     * 批量删除用例
     */
    @DeleteMapping("/batch")
    public Result<Void> batchDeleteCases(@RequestBody List<Long> ids) {
        caseService.batchDeleteCases(ids);
        return Result.success();
    }

    /**
     * 批量导入用例
     */
    @PostMapping("/batchImport")
    public Result<Integer> batchImportCases(@RequestBody List<EvalCaseDTO> cases) {
        return Result.success(caseService.batchImportCases(cases));
    }
}
