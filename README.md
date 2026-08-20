# AgentInsight

[![CI](https://github.com/lydiayan/agent-insight/actions/workflows/ci.yml/badge.svg)](https://github.com/lydiayan/agent-insight/actions/workflows/ci.yml)

AgentInsight 是一个面向 AI Agent 的全链路观测与自动化评测平台。它将 Trace 查询、调用链分析、RAG 与工具质量指标、评测用例管理和在线质量看板集中到一个可独立部署的系统中。

## 关联项目

- [ecommerce-order-agent-platform](https://github.com/lydiayan/ecommerce-order-agent-platform)：订单领域 Agent 平台，提供规划、RAG、记忆、MCP 工具、人工审批和 Trace 数据，是 AgentInsight 的完整接入示例。

两个项目保持独立仓库和发布周期：订单 Agent 负责生成标准化 Trace，AgentInsight 负责存储查询、可视化分析和自动化评测。

## 核心能力

- Agent Trace 和 Span 全链路查询
- 请求量、时延、工具成功率、RAG 命中率与 Token 指标
- 评测用例、标签、任务和评分结果管理
- 调用真实 Agent API 并按 Trace 证据评分
- Vue 3 + ECharts 质量看板
- MySQL、Redis 与 Elasticsearch 持久化

## 技术栈

- 后端：Java 17、Spring Boot 3.5、MyBatis-Plus、Flyway
- 前端：Vue 3、TypeScript、Vite、Element Plus、ECharts
- 数据：MySQL、Redis、Elasticsearch

## 本地启动

### Docker Compose 一键启动（推荐）

环境要求：Docker Desktop 或 Docker Engine + Compose v2。首次启动会构建前后端镜像并初始化 MySQL 表结构。

```bash
cp .env.example .env
docker compose up --build -d
```

等待所有服务健康后访问：

- 前端：`http://localhost:13000`
- 后端健康检查：`http://localhost:8080/actuator/health`
- Elasticsearch：`http://localhost:19201`

验收服务状态与查看日志：

```bash
docker compose ps
docker compose logs -f backend frontend
```

停止服务会保留数据；需要彻底重置演示数据时再删除数据卷：

```bash
docker compose down
docker compose down -v
```

默认只向本机开放端口；前端、MySQL、Redis 与 Elasticsearch 分别使用 `13000`、`13316`、`16389`、`19201`，避免与源码开发服务及关联项目的基础设施端口冲突。
Elasticsearch 默认使用 256 MB 演示堆；可在 `.env` 中通过 `ELASTICSEARCH_JAVA_OPTS` 按机器资源提高。

### 源码方式启动

准备 MySQL、Redis 和 Elasticsearch 后启动后端：

```bash
mvn spring-boot:run
```

启动前端：

```bash
cd frontend
npm install
npm run dev
```

默认访问地址：

- 前端：`http://localhost:3000`
- 后端：`http://localhost:8080`

## 配置

生产或共享环境应通过环境变量覆盖本地默认值。

| 环境变量 | 默认值 | 用途 |
| --- | --- | --- |
| `ELASTICSEARCH_URIS` | `http://127.0.0.1:19200` | Elasticsearch 地址 |
| `AGENT_INSIGHT_TRACE_INDEX` | `rag-traces` | Agent Trace 索引 |
| `AGENT_EVALUATION_TOKEN` | 本地演示 Token | 调用 Agent 评测接口的服务凭证 |
| `AGENT_EVALUATION_ENDPOINT` | 源码模式为用例地址；Compose 为宿主机 `8087` | 订单 Agent 内部评测接口 |

Compose 的全部可配置端口和本地演示凭证见 `.env.example`。容器通过 `host.docker.internal` 调用运行在宿主机 `8087` 端口的订单 Agent；Linux 环境所需的宿主机映射已包含在 `compose.yaml` 中。

造数脚本与后端使用同一个索引环境变量：

```bash
AGENT_INSIGHT_TRACE_INDEX=rag-traces python3 scripts/seed_es_data.py http://localhost:19200
```

## 验证

```bash
mvn verify
cd frontend && npm ci && npm run build
docker compose config --quiet
```

## 安全说明

- 不要提交 `.env`、本地覆盖 YAML、日志、数据库导出或真实服务凭证。
- 仓库内默认凭证仅供本机演示，部署时必须通过环境变量覆盖。
- 评测接口应只连接到可信 Agent，并使用最小权限的服务 Token。
