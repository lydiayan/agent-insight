ALTER TABLE eval_cases ADD COLUMN agent_endpoint VARCHAR(512) DEFAULT NULL COMMENT 'Agent API 入口地址，如 http://agent-service:8080/api/chat' AFTER agent_version;
