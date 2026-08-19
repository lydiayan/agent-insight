ALTER TABLE eval_cases
    ADD COLUMN actor_user_id VARCHAR(64) DEFAULT NULL
        COMMENT '调用 Agent 时使用的演示身份 ID' AFTER agent_endpoint,
    ADD INDEX idx_actor_user_id (actor_user_id);
