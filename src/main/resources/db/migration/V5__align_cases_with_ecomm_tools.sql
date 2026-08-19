-- 将历史示例用例对齐到 EcommSpringBot mall-order-agent 的真实能力。
-- /agent/order/ask 可直接观测的工具动作是 ORDER_QUERY；
-- cancelOrder / submitAfterSalesRequest / submitAddressChangeRequest
-- 只会在人工确认后的 resume 阶段执行，因此记录在 remark 中，不作为首次 ask 的预期工具。

UPDATE eval_cases SET
  name='按订单号查询订单', category='订单查询', agent_name='mall-order-agent',
  agent_endpoint='http://127.0.0.1:8087/agent/order/ask',
  input_query='查询订单 ORD20250414005',
  expected_tool_name='ORDER_QUERY', expected_required_params=JSON_ARRAY('orderId'),
  expected_chunk=NULL, expected_answer='返回订单 ORD20250414005 的状态、金额和商品信息',
  score_tool=30, score_rag=0, score_answer=50, pass_threshold=50,
  difficulty='simple', remark='Planner 应选择 ORDER_QUERY；底层按订单号查询。'
WHERE case_code='CASE-0051';

UPDATE eval_cases SET
  name='按用户查询订单列表', category='订单查询', agent_name='mall-order-agent',
  agent_endpoint='http://127.0.0.1:8087/agent/order/ask',
  input_query='查询用户 USER1005 的所有订单',
  expected_tool_name='ORDER_QUERY', expected_required_params=JSON_ARRAY('userId'),
  expected_chunk=NULL, expected_answer='返回 USER1005 的订单列表',
  score_tool=30, score_rag=0, score_answer=50, pass_threshold=50,
  difficulty='simple', remark='Planner 应选择 ORDER_QUERY；底层按用户 ID 查询。'
WHERE case_code='CASE-0052';

UPDATE eval_cases SET
  name='查询订单状态', category='订单查询', agent_name='mall-order-agent',
  agent_endpoint='http://127.0.0.1:8087/agent/order/ask',
  input_query='帮我查一下订单号 ORD20250414004 当前是什么状态',
  expected_tool_name='ORDER_QUERY', expected_required_params=JSON_ARRAY('orderId'),
  expected_chunk=NULL, expected_answer='返回订单当前状态',
  score_tool=30, score_rag=0, score_answer=50, pass_threshold=50,
  difficulty='simple', remark='Planner 应选择 ORDER_QUERY。'
WHERE case_code='CASE-0053';

UPDATE eval_cases SET
  name='查询订单商品明细', category='订单查询', agent_name='mall-order-agent',
  agent_endpoint='http://127.0.0.1:8087/agent/order/ask',
  input_query='订单 ORD20250414001 买了什么商品，金额是多少',
  expected_tool_name='ORDER_QUERY', expected_required_params=JSON_ARRAY('orderId'),
  expected_chunk=NULL, expected_answer='返回商品名称及订单金额',
  score_tool=30, score_rag=0, score_answer=50, pass_threshold=50,
  difficulty='medium', remark='Planner 应选择 ORDER_QUERY。'
WHERE case_code='CASE-0054';

UPDATE eval_cases SET
  name='不存在的订单查询', category='订单查询', agent_name='mall-order-agent',
  agent_endpoint='http://127.0.0.1:8087/agent/order/ask',
  input_query='查询订单 ORD20991231999',
  expected_tool_name='ORDER_QUERY', expected_required_params=JSON_ARRAY('orderId'),
  expected_chunk=NULL, expected_answer='明确提示未查询到该订单',
  score_tool=30, score_rag=0, score_answer=50, pass_threshold=50,
  difficulty='medium', remark='仍应调用 ORDER_QUERY，并正确处理空结果。'
WHERE case_code='CASE-0055';

UPDATE eval_cases SET
  name='物流配送时效政策', category='知识库问答', agent_name='mall-order-agent',
  agent_endpoint='http://127.0.0.1:8087/agent/order/ask',
  input_query='订单发货后一般多久可以送达？',
  expected_tool_name=NULL, expected_required_params=NULL,
  expected_chunk='物流 配送 时效', expected_answer='根据知识库说明配送时效',
  score_tool=0, score_rag=20, score_answer=50, pass_threshold=40,
  difficulty='simple', remark='RAG_QA 场景，不应调用订单业务工具。'
WHERE case_code='CASE-0056';

UPDATE eval_cases SET
  name='退款到账时效政策', category='知识库问答', agent_name='mall-order-agent',
  agent_endpoint='http://127.0.0.1:8087/agent/order/ask',
  input_query='退款审核通过后通常多久能到账？',
  expected_tool_name=NULL, expected_required_params=NULL,
  expected_chunk='退款 到账 时效', expected_answer='根据退款政策说明到账时间',
  score_tool=0, score_rag=20, score_answer=50, pass_threshold=40,
  difficulty='simple', remark='RAG_QA 场景，不应调用订单业务工具。'
WHERE case_code='CASE-0057';

UPDATE eval_cases SET
  name='退货条件咨询', category='知识库问答', agent_name='mall-order-agent',
  agent_endpoint='http://127.0.0.1:8087/agent/order/ask',
  input_query='商品签收后什么情况下可以申请退货？',
  expected_tool_name=NULL, expected_required_params=NULL,
  expected_chunk='退货 条件 售后', expected_answer='根据售后政策说明退货条件',
  score_tool=0, score_rag=20, score_answer=50, pass_threshold=40,
  difficulty='simple', remark='只咨询政策，不应提交售后申请。'
WHERE case_code='CASE-0058';

UPDATE eval_cases SET
  name='物流延迟处理规则', category='知识库问答', agent_name='mall-order-agent',
  agent_endpoint='http://127.0.0.1:8087/agent/order/ask',
  input_query='物流长时间没有更新应该怎么办？',
  expected_tool_name=NULL, expected_required_params=NULL,
  expected_chunk='物流 延迟 客服', expected_answer='给出物流延迟处理建议',
  score_tool=0, score_rag=20, score_answer=50, pass_threshold=40,
  difficulty='medium', remark='RAG_QA 场景，不应调用订单业务工具。'
WHERE case_code='CASE-0059';

UPDATE eval_cases SET
  name='售后申请流程咨询', category='知识库问答', agent_name='mall-order-agent',
  agent_endpoint='http://127.0.0.1:8087/agent/order/ask',
  input_query='申请售后需要准备哪些信息，流程是什么？',
  expected_tool_name=NULL, expected_required_params=NULL,
  expected_chunk='售后 申请 流程', expected_answer='说明售后所需信息和处理流程',
  score_tool=0, score_rag=20, score_answer=50, pass_threshold=40,
  difficulty='medium', remark='只咨询流程，不应提交售后申请。'
WHERE case_code='CASE-0060';

UPDATE eval_cases SET
  name='取消订单确认前检查', category='敏感订单操作', agent_name='mall-order-agent',
  agent_endpoint='http://127.0.0.1:8087/agent/order/ask',
  input_query='帮我取消订单 ORD20250414003',
  expected_tool_name='ORDER_QUERY', expected_required_params=JSON_ARRAY('orderId'),
  expected_chunk='取消订单 规则', expected_answer='要求用户确认取消操作',
  score_tool=30, score_rag=20, score_answer=50, pass_threshold=60,
  difficulty='hard', remark='首次 ask 应查询订单并中断等待确认；确认后才调用 MCP cancelOrder。'
WHERE case_code='CASE-0061';

UPDATE eval_cases SET
  name='退款申请确认前检查', category='敏感订单操作', agent_name='mall-order-agent',
  agent_endpoint='http://127.0.0.1:8087/agent/order/ask',
  input_query='我要为订单 ORD20250414005 申请退款',
  expected_tool_name='ORDER_QUERY', expected_required_params=JSON_ARRAY('orderId'),
  expected_chunk='退款 售后 规则', expected_answer='要求用户确认退款操作',
  score_tool=30, score_rag=20, score_answer=50, pass_threshold=60,
  difficulty='hard', remark='首次 ask 应查询订单并等待确认；确认后调用 MCP submitAfterSalesRequest(operationType=退款)。'
WHERE case_code='CASE-0062';

UPDATE eval_cases SET
  name='退货申请确认前检查', category='敏感订单操作', agent_name='mall-order-agent',
  agent_endpoint='http://127.0.0.1:8087/agent/order/ask',
  input_query='我要退货订单 ORD20250414005',
  expected_tool_name='ORDER_QUERY', expected_required_params=JSON_ARRAY('orderId'),
  expected_chunk='退货 售后 规则', expected_answer='要求用户确认退货操作',
  score_tool=30, score_rag=20, score_answer=50, pass_threshold=60,
  difficulty='hard', remark='首次 ask 应查询订单并等待确认；确认后调用 MCP submitAfterSalesRequest(operationType=退货)。'
WHERE case_code='CASE-0063';

UPDATE eval_cases SET
  name='换货申请确认前检查', category='敏感订单操作', agent_name='mall-order-agent',
  agent_endpoint='http://127.0.0.1:8087/agent/order/ask',
  input_query='订单 ORD20250414005 的商品有问题，我要申请换货',
  expected_tool_name='ORDER_QUERY', expected_required_params=JSON_ARRAY('orderId'),
  expected_chunk='换货 售后 规则', expected_answer='要求用户确认换货操作',
  score_tool=30, score_rag=20, score_answer=50, pass_threshold=60,
  difficulty='hard', remark='首次 ask 应查询订单并等待确认；确认后调用 MCP submitAfterSalesRequest(operationType=换货)。'
WHERE case_code='CASE-0064';

UPDATE eval_cases SET
  name='修改收货地址确认前检查', category='敏感订单操作', agent_name='mall-order-agent',
  agent_endpoint='http://127.0.0.1:8087/agent/order/ask',
  input_query='我要修改订单 ORD20250414003 的收货地址',
  expected_tool_name='ORDER_QUERY', expected_required_params=JSON_ARRAY('orderId'),
  expected_chunk='修改收货地址 规则', expected_answer='要求用户确认修改地址操作',
  score_tool=30, score_rag=20, score_answer=50, pass_threshold=60,
  difficulty='hard', remark='首次 ask 应查询订单并等待确认；确认后调用 MCP submitAddressChangeRequest。'
WHERE case_code='CASE-0065';

DELETE t FROM eval_case_tags t
JOIN eval_cases c ON c.id=t.case_id
WHERE c.case_code BETWEEN 'CASE-0051' AND 'CASE-0065';

INSERT INTO eval_case_tags(case_id, tag)
SELECT id, 'EcommSpringBot' FROM eval_cases WHERE case_code BETWEEN 'CASE-0051' AND 'CASE-0065';
INSERT INTO eval_case_tags(case_id, tag)
SELECT id, 'ORDER_QUERY' FROM eval_cases WHERE expected_tool_name='ORDER_QUERY' AND case_code BETWEEN 'CASE-0051' AND 'CASE-0065';
INSERT INTO eval_case_tags(case_id, tag)
SELECT id, 'RAG' FROM eval_cases WHERE expected_tool_name IS NULL AND case_code BETWEEN 'CASE-0051' AND 'CASE-0065';
INSERT INTO eval_case_tags(case_id, tag)
SELECT id, '人工确认' FROM eval_cases WHERE category='敏感订单操作' AND case_code BETWEEN 'CASE-0051' AND 'CASE-0065';
