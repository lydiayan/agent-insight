-- 两阶段评测：Agent 调用完成后先保存 traceId，随后按需从 ES 采集 Trace 并评分。
ALTER TABLE eval_results
    ADD COLUMN agent_trace_id VARCHAR(64) DEFAULT NULL COMMENT 'Agent 返回的真实 traceId' AFTER trace_id,
    ADD COLUMN collection_status VARCHAR(32) NOT NULL DEFAULT 'scored'
        COMMENT 'trace_pending|trace_incomplete|scored|call_failed' AFTER agent_trace_id,
    ADD COLUMN invoked_at DATETIME DEFAULT NULL COMMENT 'Agent 调用完成时间' AFTER collection_status,
    ADD COLUMN collected_at DATETIME DEFAULT NULL COMMENT 'Trace 采集完成时间' AFTER invoked_at,
    ADD COLUMN trace_snapshot JSON DEFAULT NULL COMMENT '评分时使用的 ES Trace 快照' AFTER collected_at,
    ADD INDEX idx_agent_trace_id (agent_trace_id),
    ADD INDEX idx_collection_status (collection_status);

UPDATE eval_results
SET agent_trace_id = JSON_UNQUOTE(JSON_EXTRACT(eval_detail, '$.agentTraceId'))
WHERE JSON_EXTRACT(eval_detail, '$.agentTraceId') IS NOT NULL;
