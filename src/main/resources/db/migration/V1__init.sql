-- ============================================================
-- AgentInsight V1 初始化 DDL
-- ============================================================

-- 1. 评测用例主表
CREATE TABLE IF NOT EXISTS eval_cases (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    case_code               VARCHAR(64)   NOT NULL COMMENT '用例编号，如 CASE-001',
    name                    VARCHAR(255)  NOT NULL COMMENT '用例名称',
    category                VARCHAR(128)  NOT NULL DEFAULT '' COMMENT '分类',
    agent_name              VARCHAR(255)  NOT NULL COMMENT '适用 Agent 名称，支持 % 通配',
    agent_version           VARCHAR(64)   DEFAULT NULL COMMENT '适用 Agent 版本，NULL 表示所有版本',

    -- 测试输入与预期
    input_query             TEXT          NOT NULL COMMENT '用户输入模板',
    expected_tool_name      VARCHAR(255)  DEFAULT NULL COMMENT '预期调用的工具名称',
    expected_required_params JSON         DEFAULT NULL COMMENT '必须存在的参数列表 JSON',
    expected_param_rules    JSON          DEFAULT NULL COMMENT '参数校验规则 JSON',
    expected_forbidden_params JSON        DEFAULT NULL COMMENT '不应出现的参数列表 JSON',
    expected_chunk          TEXT          DEFAULT NULL COMMENT '预期 RAG 召回的标准知识库片段',
    expected_answer         TEXT          DEFAULT NULL COMMENT '标准答案',

    -- 评分权重
    score_tool              INT NOT NULL DEFAULT 30 COMMENT 'Tool 维度满分',
    score_rag               INT NOT NULL DEFAULT 20 COMMENT 'RAG 维度满分',
    score_answer            INT NOT NULL DEFAULT 50 COMMENT 'Answer 维度满分',
    pass_threshold          INT NOT NULL DEFAULT 60 COMMENT '通过阈值',

    -- 元信息
    difficulty              VARCHAR(32)   DEFAULT 'simple' COMMENT '难度：simple|medium|hard',
    remark                  TEXT          DEFAULT NULL COMMENT '备注说明',
    enabled                 TINYINT(1)    NOT NULL DEFAULT 1 COMMENT '是否启用',
    created_at              DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_category (category),
    INDEX idx_agent_name (agent_name),
    INDEX idx_enabled (enabled),
    INDEX idx_difficulty (difficulty)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评测用例主表';

-- 2. 用例标签关联表
CREATE TABLE IF NOT EXISTS eval_case_tags (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    case_id     BIGINT       NOT NULL COMMENT '关联 eval_cases.id',
    tag         VARCHAR(64)  NOT NULL COMMENT '标签',

    UNIQUE KEY uk_case_tag (case_id, tag),
    INDEX idx_tag (tag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用例标签关联表';

-- 3. 评测结果表
CREATE TABLE IF NOT EXISTS eval_results (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    trace_id        VARCHAR(64)  NOT NULL COMMENT '关联 agent-spans.traceId',
    case_id         BIGINT       NOT NULL COMMENT '关联 eval_cases.id',

    score_tool      INT NOT NULL DEFAULT 0 COMMENT 'Tool 维度得分',
    score_rag       INT NOT NULL DEFAULT 0 COMMENT 'RAG 维度得分',
    score_answer    INT NOT NULL DEFAULT 0 COMMENT 'Answer 维度得分',
    score_total     INT NOT NULL DEFAULT 0 COMMENT '总分',
    score_max       INT NOT NULL DEFAULT 100 COMMENT '满分',
    passed          TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否通过',

    eval_detail     JSON         NOT NULL COMMENT '评测详情 JSON',
    eval_time       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '评测时间',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_trace_id (trace_id),
    INDEX idx_case_id (case_id),
    INDEX idx_eval_time (eval_time),
    INDEX idx_passed (passed)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评测结果表';
