package com.agentinsight.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 评测用例查询参数
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvalCaseQueryDTO {

    /** 页码，从 1 开始 */
    private Integer page = 1;

    /** 每页条数 */
    private Integer pageSize = 20;

    /** 分类筛选 */
    private String category;

    /** Agent 名称筛选 */
    private String agentName;

    /** 标签筛选 */
    private String tag;

    /** 难度筛选 */
    private String difficulty;

    /** 是否启用 */
    private Integer enabled;

    /** 关键词搜索（名称/编号） */
    private String keyword;
}
