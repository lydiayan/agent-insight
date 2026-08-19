#!/bin/bash
# 批量创建 15 条测试用例
BASE="http://localhost:8080/api/case"

call() {
  curl -s -X POST "$BASE" -H "Content-Type: application/json" -d "$1" | python3 -c "import sys,json; d=json.load(sys.stdin)['data']; print(f'  {d[\"caseCode\"]}  {d[\"name\"]}')" 2>/dev/null
}

echo "=== 批量创建测试用例 ==="

# ---- 金融类 ----
call '{"name":"A股实时股价查询","category":"金融","agentName":"金融理财助手-%","inputQuery":"帮我查一下 {symbol} 当前股价","expectedToolName":"stock_price","expectedRequiredParams":"[\"symbol\"]","expectedAnswer":"贵州茅台当前股价为 1,850.00 元","scoreTool":30,"scoreRag":20,"scoreAnswer":50,"passThreshold":60,"difficulty":"simple","enabled":1,"tags":["金融","查询","A股"]}'
call '{"name":"美股股价查询","category":"金融","agentName":"金融理财助手-%","inputQuery":"What is the current price of {symbol}?","expectedToolName":"stock_price","expectedRequiredParams":"[\"symbol\"]","expectedAnswer":"AAPL 当前股价为 $232.54","scoreTool":30,"scoreRag":20,"scoreAnswer":50,"passThreshold":60,"difficulty":"simple","enabled":1,"tags":["金融","美股"]}'
call '{"name":"K线数据导出","category":"金融","agentName":"金融理财助手-%","inputQuery":"导出 {symbol} 近 {days} 天K线","expectedToolName":"stock_kline","expectedRequiredParams":"[\"symbol\",\"days\"]","expectedAnswer":"已导出近30日K线数据","scoreTool":30,"scoreRag":20,"scoreAnswer":50,"passThreshold":60,"difficulty":"medium","enabled":1,"tags":["金融","K线","导出"]}'
call '{"name":"基金净值查询","category":"金融","agentName":"金融理财助手-%","inputQuery":"{fundCode} 净值多少，近一年收益率？","expectedToolName":"fund_nav","expectedRequiredParams":"[\"fundCode\"]","expectedAnswer":"当前净值 2.3456 元，近一年收益率 +12.8%","scoreTool":30,"scoreRag":20,"scoreAnswer":50,"passThreshold":60,"difficulty":"medium","enabled":1,"tags":["金融","基金"]}'
call '{"name":"汇率换算","category":"金融","agentName":"金融理财助手-%","inputQuery":"{amount} {fromCurrency} 兑 {toCurrency}","expectedToolName":"currency_exchange","expectedRequiredParams":"[\"fromCurrency\",\"toCurrency\",\"amount\"]","expectedAnswer":"当前汇率为 7.2450","scoreTool":30,"scoreRag":20,"scoreAnswer":50,"passThreshold":60,"difficulty":"medium","enabled":1,"tags":["金融","汇率","换算"]}'

# ---- 客服类 ----
call '{"name":"快递物流查询","category":"客服","agentName":"客服智能助手-%","inputQuery":"快递 {trackingNumber} 到哪了","expectedToolName":"logistics_query","expectedRequiredParams":"[\"trackingNumber\"]","expectedAnswer":"您的快递当前在运输中，预计7月25日送达","scoreTool":30,"scoreRag":20,"scoreAnswer":50,"passThreshold":60,"difficulty":"simple","enabled":1,"tags":["客服","物流"]}'
call '{"name":"订单退款进度","category":"客服","agentName":"客服智能助手-%","inputQuery":"订单 {orderId} 退款进度","expectedToolName":"refund_status","expectedRequiredParams":"[\"orderId\"]","expectedAnswer":"退款审核通过，3-5个工作日退回","scoreTool":30,"scoreRag":20,"scoreAnswer":50,"passThreshold":60,"difficulty":"simple","enabled":1,"tags":["客服","售后","退款"]}'
call '{"name":"换货申请处理","category":"客服","agentName":"客服智能助手-%","inputQuery":"买的{product}有问题，订单{orderId}，要退货","expectedToolName":"order_return","expectedRequiredParams":"[\"orderId\"]","expectedAnswer":"已创建退货申请，48小时内审核","scoreTool":30,"scoreRag":20,"scoreAnswer":50,"passThreshold":60,"difficulty":"medium","enabled":1,"tags":["客服","售后","退货"]}'
call '{"name":"优惠券可用性检查","category":"客服","agentName":"客服智能助手-%","inputQuery":"优惠券 {couponCode} 能在 {product} 上用吗","expectedToolName":"coupon_check","expectedRequiredParams":"[\"couponCode\"]","expectedAnswer":"优惠券有效，满200元可用","scoreTool":30,"scoreRag":20,"scoreAnswer":50,"passThreshold":60,"difficulty":"medium","enabled":1,"tags":["客服","优惠券"]}'
call '{"name":"注销账户——无权限兜底","category":"客服","agentName":"客服智能助手-%","inputQuery":"帮我注销这个账号","expectedToolName":"","expectedRequiredParams":"[]","expectedAnswer":"请通过App设置-账号安全-注销账号自助操作","scoreTool":30,"scoreRag":0,"scoreAnswer":50,"passThreshold":50,"difficulty":"hard","enabled":1,"tags":["客服","权限","兜底"]}'

# ---- 知识库类 ----
call '{"name":"公司年假政策查询","category":"知识库","agentName":"知识库检索助手-%","inputQuery":"公司年假政策","expectedToolName":"","expectedRequiredParams":null,"expectedChunk":"入职满1年享5天年假，每增加1年增加1天，上限15天","expectedAnswer":"入职满1年享5天年假，每年递增1天至15天上限","scoreTool":0,"scoreRag":20,"scoreAnswer":50,"passThreshold":40,"difficulty":"simple","enabled":1,"tags":["知识库","HR","年假"]}'
call '{"name":"新员工入职材料与流程","category":"知识库","agentName":"知识库检索助手-%","inputQuery":"新员工入职需要准备哪些材料？流程是什么？","expectedToolName":"","expectedChunk":"入职材料包括身份证、学历证书、离职证明、银行卡、体检报告。入职流程为HR办理入职→IT配发设备→部门培训→试用期考核。","expectedAnswer":"需准备身份证、学历证书、离职证明、银行卡和体检报告。流程为HR办理入职→配发设备→新人培训→试用期考核。","scoreTool":0,"scoreRag":20,"scoreAnswer":50,"passThreshold":40,"difficulty":"simple","enabled":1,"tags":["知识库","HR","入职"]}'

# ---- 工具调用类 ----
call '{"name":"会议日程预约","category":"工具调用","agentName":"办公助手Agent-%","inputQuery":"预约明天下午2点项目评审会，参会人：{names}，1小时","expectedToolName":"calendar_create","expectedRequiredParams":"[\"time\",\"duration\"]","expectedAnswer":"已创建明天14:00会议，已发邀请","scoreTool":30,"scoreRag":20,"scoreAnswer":50,"passThreshold":60,"difficulty":"medium","enabled":1,"tags":["工具调用","日历"]}'
call '{"name":"SQL单表统计查询","category":"工具调用","agentName":"SQL生成助手-%","inputQuery":"查 users 表近7天注册人数","expectedToolName":"sql_generate","expectedRequiredParams":"[\"table\"]","expectedAnswer":"SELECT COUNT(*) FROM users WHERE created_at >= DATE_SUB(NOW(), INTERVAL 7 DAY)","scoreTool":30,"scoreRag":0,"scoreAnswer":50,"passThreshold":50,"difficulty":"medium","enabled":1,"tags":["SQL","工具调用"]}'

# ---- 翻译类 ----
call '{"name":"技术文档中译英","category":"翻译","agentName":"多模态翻译官-%","inputQuery":"翻译成英文：本系统采用微服务架构，支持弹性伸缩和高可用部署。","expectedToolName":"","expectedAnswer":"This system adopts a microservices architecture, supporting elastic scaling and high-availability deployment.","scoreTool":0,"scoreRag":0,"scoreAnswer":50,"passThreshold":30,"difficulty":"simple","enabled":1,"tags":["翻译","中译英"]}'

echo ""
echo "=== 验证 ==="
curl -s "$BASE/list?page=1&pageSize=5" | python3 -c "import sys,json; r=json.load(sys.stdin)['data']; print(f'总计 {r[\"total\"]} 条，当前页 {len(r[\"records\"])} 条')"
echo "完成！"
