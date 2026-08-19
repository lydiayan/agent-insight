#!/bin/bash
# ============================================================
# 往配置的 Trace 索引灌入 7 天的模拟 Agent 调用日志
# 用法: AGENT_INSIGHT_TRACE_INDEX=rag-traces bash seed_es_data.sh http://localhost:9200
# ============================================================
ES_HOST=${1:-http://localhost:9200}
INDEX=${AGENT_INSIGHT_TRACE_INDEX:-rag-traces}

# 确保索引存在（如果不存在则自动创建）
echo "=== 写入模拟 Trace 数据到 $ES_HOST/$INDEX ==="

# 6 种 operation，对应 AgentOperation 中定义的值
# rerank, milvus, retrieve, prompt_build, llm, tool
OPERATIONS=("rerank" "milvus" "retrieve" "prompt_build" "llm" "tool")
STATUSES=("OK" "OK" "OK" "OK" "OK" "OK" "OK" "OK" "ERROR" "TIMEOUT")
AGENTS=("金融理财助手-v2" "客服智能助手-v2" "SQL生成引擎-v1" "知识库检索助手-v1" "办公助手Agent-v2")

# 当前时间戳（毫秒），7天前开始
NOW=$(date +%s)000
SEVEN_DAYS_AGO=$((NOW - 7 * 86400 * 1000))

BULK_BODY=""
COUNT=0
TRACE_IDX=0

for ((ts = SEVEN_DAYS_AGO; ts < NOW; ts += 600000)); do  # 每10分钟一条
  for agent in "${AGENTS[@]}"; do
    TRACE_IDX=$((TRACE_IDX + 1))
    TRACE_ID="trace-$(printf '%06d' $TRACE_IDX)"

    # 生成该 trace 的 6 个 span（每个 operation 一个）
    PARENT_SPAN=""
    for op in "${OPERATIONS[@]}"; do
      STATUS=${STATUSES[$((RANDOM % 10))]}
      DURATION=$((50 + RANDOM % 800))
      INPUT_TOKEN=$((200 + RANDOM % 2000))
      OUTPUT_TOKEN=$((100 + RANDOM % 1500))
      CHUNK_COUNT=$((RANDOM % 5))
      SPAN_ID="${TRACE_ID}-${op}"

      BULK_BODY+="${BULK_BODY:+$'\n'}{\"index\":{\"_index\":\"$INDEX\"}}"
      BULK_BODY+=$'\n'{$(cat <<JSON
"traceId":"$TRACE_ID",
"spanId":"$SPAN_ID",
"parentSpanId":"${PARENT_SPAN:-null}",
"operation":"$op",
"serviceName":"$agent",
"eventType":"SPAN",
"spanKind":"INTERNAL",
"status":"$STATUS",
"timestampMs":$ts,
"durationMs":$DURATION,
"model":"claude-opus-4.7",
"finishReason":"${STATUS}",
"inputToken":$INPUT_TOKEN,
"outputToken":$OUTPUT_TOKEN,
"contextChunks":$CHUNK_COUNT,
"chunkCount":$CHUNK_COUNT,
"promptVersion":"v2.1",
"userQuery":"用户提问示例 #$TRACE_IDX",
"attributes":{"agentName":"$agent"}
JSON
}
)
      ((COUNT++))
      PARENT_SPAN="$SPAN_ID"
    done

    # 每 100 个 trace 批量写入一次
    if ((TRACE_IDX % 100 == 0)); then
      echo "$BULK_BODY" | curl -s -X POST "$ES_HOST/_bulk" \
        -H "Content-Type: application/x-ndjson" \
        --data-binary @- > /dev/null
      echo "  已写入 $TRACE_IDX trace ($((TRACE_IDX * 6)) span)..."
      BULK_BODY=""
    fi
  done
done

# 写入剩余数据
if [ -n "$BULK_BODY" ]; then
  echo "$BULK_BODY" | curl -s -X POST "$ES_HOST/_bulk" \
    -H "Content-Type: application/x-ndjson" \
    --data-binary @- > /dev/null
fi

echo "=== 完成！共写入 $TRACE_IDX 条 trace ($COUNT 条 span) ==="
