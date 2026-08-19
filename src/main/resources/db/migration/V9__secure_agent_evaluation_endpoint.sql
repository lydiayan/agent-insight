UPDATE eval_cases
SET agent_endpoint = 'http://127.0.0.1:8087/internal/evaluation/ask'
WHERE agent_endpoint = 'http://127.0.0.1:8087/agent/order/ask';
