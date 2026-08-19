-- ============================================================
-- V3: 评测任务表
-- ============================================================
CREATE TABLE IF NOT EXISTS evaluation_tasks (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_name     VARCHAR(255)  NOT NULL COMMENT '任务名称',
    case_count    INT           NOT NULL DEFAULT 0 COMMENT '总用例数',
    completed     INT           NOT NULL DEFAULT 0 COMMENT '已完成数',
    passed_count  INT           NOT NULL DEFAULT 0 COMMENT '通过数',
    failed_count  INT           NOT NULL DEFAULT 0 COMMENT '未通过数',
    avg_score     DECIMAL(5,1)  DEFAULT NULL COMMENT '平均分',
    status        VARCHAR(32)   NOT NULL DEFAULT 'pending' COMMENT 'pending|running|completed|failed',
    error_msg     TEXT          DEFAULT NULL COMMENT '失败原因',
    created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评测任务表';
