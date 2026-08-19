package com.agentinsight.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.agentinsight.common.PageResult;
import com.agentinsight.dto.EvalCaseDTO;
import com.agentinsight.dto.EvalCaseQueryDTO;
import com.agentinsight.entity.mysql.EvalCase;
import com.agentinsight.entity.mysql.EvalCaseTag;
import com.agentinsight.repository.mapper.EvalCaseMapper;
import com.agentinsight.repository.mapper.EvalCaseTagMapper;
import com.agentinsight.service.CaseService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CaseServiceImpl implements CaseService {

    private final EvalCaseMapper caseMapper;
    private final EvalCaseTagMapper tagMapper;

    @Override
    public PageResult<EvalCaseDTO> listCases(EvalCaseQueryDTO query) {
        // 如果指定了 tag 筛选，先查出匹配的 caseId
        List<Long> tagCaseIds = null;
        if (StrUtil.isNotBlank(query.getTag())) {
            tagCaseIds = tagMapper.selectList(
                    new LambdaQueryWrapper<EvalCaseTag>()
                            .eq(EvalCaseTag::getTag, query.getTag())
            ).stream().map(EvalCaseTag::getCaseId).collect(Collectors.toList());
            if (tagCaseIds.isEmpty()) {
                return PageResult.of(0, query.getPage(), query.getPageSize(), List.of());
            }
        }

        LambdaQueryWrapper<EvalCase> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(query.getCategory())) {
            wrapper.eq(EvalCase::getCategory, query.getCategory());
        }
        if (StrUtil.isNotBlank(query.getAgentName())) {
            wrapper.like(EvalCase::getAgentName, query.getAgentName());
        }
        if (StrUtil.isNotBlank(query.getDifficulty())) {
            wrapper.eq(EvalCase::getDifficulty, query.getDifficulty());
        }
        if (query.getEnabled() != null) {
            wrapper.eq(EvalCase::getEnabled, query.getEnabled());
        }
        if (StrUtil.isNotBlank(query.getKeyword())) {
            wrapper.and(w -> w
                    .like(EvalCase::getName, query.getKeyword())
                    .or()
                    .like(EvalCase::getCaseCode, query.getKeyword()));
        }
        if (tagCaseIds != null) {
            wrapper.in(EvalCase::getId, tagCaseIds);
        }
        wrapper.orderByDesc(EvalCase::getUpdatedAt);

        Page<EvalCase> page = new Page<>(query.getPage(), query.getPageSize());
        IPage<EvalCase> result = caseMapper.selectPage(page, wrapper);

        // 批量查标签
        List<Long> caseIds = result.getRecords().stream().map(EvalCase::getId).collect(Collectors.toList());
        Map<Long, List<String>> tagMap = Map.of();
        if (!caseIds.isEmpty()) {
            List<EvalCaseTag> tags = tagMapper.selectList(
                    new LambdaQueryWrapper<EvalCaseTag>().in(EvalCaseTag::getCaseId, caseIds));
            tagMap = tags.stream().collect(Collectors.groupingBy(
                    EvalCaseTag::getCaseId,
                    Collectors.mapping(EvalCaseTag::getTag, Collectors.toList())
            ));
        }

        Map<Long, List<String>> finalTagMap = tagMap;
        List<EvalCaseDTO> records = result.getRecords().stream()
                .map(c -> toDTO(c, finalTagMap.getOrDefault(c.getId(), List.of())))
                .collect(Collectors.toList());

        return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(), records);
    }

    @Override
    public EvalCaseDTO getCaseById(Long id) {
        EvalCase evalCase = caseMapper.selectById(id);
        if (evalCase == null) {
            throw new RuntimeException("用例不存在: " + id);
        }
        List<String> tags = tagMapper.selectList(
                new LambdaQueryWrapper<EvalCaseTag>().eq(EvalCaseTag::getCaseId, id)
        ).stream().map(EvalCaseTag::getTag).collect(Collectors.toList());
        return toDTO(evalCase, tags);
    }

    @Override
    @Transactional
    public EvalCaseDTO createCase(EvalCaseDTO dto) {
        EvalCase evalCase = toEntity(dto);
        // case_code 是 NOT NULL，先插一个临时值，自增 ID 生成后再覆盖
        evalCase.setCaseCode("TEMP");
        caseMapper.insert(evalCase);

        evalCase.setCaseCode("CASE-" + String.format("%04d", evalCase.getId()));
        caseMapper.updateById(evalCase);

        saveTags(evalCase.getId(), dto.getTags());
        return toDTO(evalCase, dto.getTags());
    }

    @Override
    @Transactional
    public EvalCaseDTO updateCase(Long id, EvalCaseDTO dto) {
        EvalCase existing = caseMapper.selectById(id);
        if (existing == null) {
            throw new RuntimeException("用例不存在: " + id);
        }
        EvalCase evalCase = toEntity(dto);
        evalCase.setId(id);
        evalCase.setCaseCode(existing.getCaseCode());
        caseMapper.updateById(evalCase);

        // 更新标签：先删后增
        tagMapper.delete(new LambdaQueryWrapper<EvalCaseTag>().eq(EvalCaseTag::getCaseId, id));
        saveTags(id, dto.getTags());

        return toDTO(evalCase, dto.getTags());
    }

    @Override
    @Transactional
    public void deleteCase(Long id) {
        tagMapper.delete(new LambdaQueryWrapper<EvalCaseTag>().eq(EvalCaseTag::getCaseId, id));
        caseMapper.deleteById(id);
    }

    @Override
    @Transactional
    public void batchDeleteCases(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return;
        tagMapper.delete(new LambdaQueryWrapper<EvalCaseTag>().in(EvalCaseTag::getCaseId, ids));
        caseMapper.deleteBatchIds(ids);
    }

    @Override
    @Transactional
    public int batchImportCases(List<EvalCaseDTO> cases) {
        int count = 0;
        for (EvalCaseDTO dto : cases) {
            EvalCase evalCase = toEntity(dto);
            evalCase.setCaseCode("TEMP");
            caseMapper.insert(evalCase);
            evalCase.setCaseCode("CASE-" + String.format("%04d", evalCase.getId()));
            caseMapper.updateById(evalCase);
            saveTags(evalCase.getId(), dto.getTags());
            count++;
        }
        return count;
    }

    // ==================== 私有方法 ====================

    private void saveTags(Long caseId, List<String> tags) {
        if (tags == null || tags.isEmpty()) return;
        for (String tag : tags) {
            if (StrUtil.isNotBlank(tag)) {
                tagMapper.insert(EvalCaseTag.builder().caseId(caseId).tag(tag.trim()).build());
            }
        }
    }

    private EvalCaseDTO toDTO(EvalCase entity, List<String> tags) {
        EvalCaseDTO dto = new EvalCaseDTO();
        BeanUtil.copyProperties(entity, dto);
        dto.setTags(tags != null ? tags : List.of());
        return dto;
    }

    private EvalCase toEntity(EvalCaseDTO dto) {
        EvalCase entity = new EvalCase();
        BeanUtil.copyProperties(dto, entity, "id", "caseCode", "createdAt", "updatedAt", "tags");
        return entity;
    }
}
