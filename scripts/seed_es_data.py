"""
往配置的 Trace 索引灌入 7 天的模拟 Agent 调用日志。
用法: AGENT_INSIGHT_TRACE_INDEX=rag-traces python3 seed_es_data.py http://localhost:9200
"""
import json, os, random, sys, time, urllib.request
from datetime import datetime, timedelta, timezone

ES = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:9200"
INDEX = os.environ.get("AGENT_INSIGHT_TRACE_INDEX", "rag-traces")
TZ = timezone(timedelta(hours=8))

OPERATIONS = {
    "rerank":    {"div": 180, "input": (100, 800),  "output": (50, 300)},
    "milvus":    {"div": 220, "input": (300, 1200), "output": (100, 500)},
    "retrieve":  {"div": 200, "input": (400, 1500), "output": (200, 800)},
    "prompt_build": {"div": 120, "input": (500, 2000), "output": (300, 1200)},
    "llm":       {"div": 90,  "input": (800, 3000), "output": (500, 2000)},
    "tool":      {"div": 150, "input": (200, 1000), "output": (100, 600)},
}

AGENTS = ["金融理财助手-v2", "客服智能助手-v2", "SQL生成引擎-v1",
          "知识库检索助手-v1", "办公助手Agent-v2"]

# 成功率：operation 不同成功率不同（模拟真实场景）
OP_OK_RATE = {"rerank": 0.97, "milvus": 0.98, "retrieve": 0.92,
              "prompt_build": 0.99, "llm": 0.96, "tool": 0.88}


def post(url, body):
    data = body.encode() if isinstance(body, str) else json.dumps(body).encode()
    req = urllib.request.Request(url, data=data,
                                 headers={"Content-Type": "application/json"})
    try:
        return urllib.request.urlopen(req, timeout=10).read()
    except Exception as e:
        return b""


# 确保索引存在
post(f"{ES}/{INDEX}", {"settings": {"number_of_shards": 1, "number_of_replicas": 0}})

now = datetime.now(TZ)
start = now - timedelta(days=7)
step = timedelta(minutes=8)

bulk = []
trace_idx = 0
t = start

while t < now:
    for agent in AGENTS:
        trace_idx += 1
        tid = f"trace-{trace_idx:07d}"
        ts = int(t.timestamp() * 1000)
        parent = None

        for op, cfg in OPERATIONS.items():
            ok = random.random() < OP_OK_RATE[op]
            dur = random.randint(40, cfg["div"])
            if not ok:
                dur = random.randint(cfg["div"], cfg["div"] + 500)

            sid = f"{tid}-{op}"
            doc = {
                "traceId": tid,
                "spanId": sid,
                "parentSpanId": parent,
                "operation": op,
                "serviceName": agent,
                "eventType": "SPAN",
                "spanKind": "INTERNAL",
                "status": "OK" if ok else random.choice(["ERROR", "TIMEOUT"]),
                "timestampMs": ts,
                "durationMs": dur,
                "model": "claude-opus-4.7",
                "finishReason": "stop",
                "inputToken": random.randint(*cfg["input"]),
                "outputToken": random.randint(*cfg["output"]),
                "contextChunks": random.randint(0, 5),
                "chunkCount": random.randint(0, 5),
                "promptVersion": "v2.1",
                "userQuery": f"用户提问示例 #{trace_idx}",
                "attributes": {"agentName": agent}
            }
            bulk.append(json.dumps({"index": {"_index": INDEX}}))
            bulk.append(json.dumps(doc))
            parent = sid

        # 每 50 个 trace 批量写入
        if trace_idx % 50 == 0:
            nd = "\n".join(bulk) + "\n"
            post(f"{ES}/_bulk", nd)
            print(f"  已写入 {trace_idx} trace ({trace_idx * 6} span)...")
            bulk = []

    t += step

# 写入剩余
if bulk:
    nd = "\n".join(bulk) + "\n"
    post(f"{ES}/_bulk", nd)

print(f"=== 完成！共写入 {trace_idx} 条 trace ({trace_idx * 6} 条 span) ===")
