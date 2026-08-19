#!/bin/bash
# 测试用例 CRUD API 完整流程
BASE="http://localhost:8080/api/case"

echo "=== 1. 新增用例 ==="
CREATE_RES=$(curl -s -X POST "$BASE" \
  -H "Content-Type: application/json" \
  -d '{
    "name":"测试-股价查询",
    "category":"金融",
    "agentName":"金融理财助手-%",
    "inputQuery":"帮我查一下 {symbol} 当前股价",
    "expectedToolName":"stock_price",
    "expectedRequiredParams":"[\"symbol\"]",
    "expectedParamRules":"{\"symbol\":{\"type\":\"string\",\"regex\":\"^[0-9]{6}$\"}}",
    "expectedAnswer":"贵州茅台当前股价为 1,850.00 元",
    "scoreTool":30,
    "scoreRag":20,
    "scoreAnswer":50,
    "passThreshold":60,
    "difficulty":"simple",
    "enabled":1,
    "tags":["金融","查询","A股"]
  }')
echo "$CREATE_RES" | python3 -m json.tool 2>/dev/null || echo "$CREATE_RES"

# 提取 ID
CASE_ID=$(echo "$CREATE_RES" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])" 2>/dev/null)
echo "新用例 ID: $CASE_ID"

echo ""
echo "=== 2. 查询列表（第一页，10条） ==="
curl -s "$BASE/list?page=1&pageSize=10" | python3 -m json.tool 2>/dev/null || curl -s "$BASE/list?page=1&pageSize=10"

if [ -n "$CASE_ID" ]; then
  echo ""
  echo "=== 3. 查询单条详情 ==="
  curl -s "$BASE/$CASE_ID" | python3 -m json.tool 2>/dev/null || curl -s "$BASE/$CASE_ID"

  echo ""
  echo "=== 4. 编辑用例 ==="
  curl -s -X PUT "$BASE/$CASE_ID" \
    -H "Content-Type: application/json" \
    -d '{
      "name":"测试-股价查询（已修改）",
      "category":"金融",
      "agentName":"金融理财助手-%",
      "inputQuery":"帮我查一下 {symbol} 当前股价",
      "expectedToolName":"stock_price",
      "expectedRequiredParams":"[\"symbol\"]",
      "scoreTool":30,
      "scoreRag":20,
      "scoreAnswer":50,
      "passThreshold":60,
      "difficulty":"medium",
      "enabled":1,
      "tags":["金融","查询","A股","已修改"]
    }' | python3 -m json.tool 2>/dev/null

  echo ""
  echo "=== 5. 再次查询列表验证修改结果 ==="
  curl -s "$BASE/list?page=1&pageSize=10" | python3 -m json.tool 2>/dev/null

  echo ""
  echo "=== 6. 删除用例 ==="
  curl -s -X DELETE "$BASE/$CASE_ID"
  echo ""

  echo ""
  echo "=== 7. 删除后确认列表数量减少 ==="
  curl -s "$BASE/list?page=1&pageSize=10" | python3 -c "import sys,json; d=json.load(sys.stdin); print(f'总记录数: {d[\"data\"][\"total\"]}')"
fi

echo ""
echo "=== 全部测试完成 ==="
