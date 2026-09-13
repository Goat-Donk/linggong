# 本地零工平台（linggong）

本地零工撮合平台：雇主发布岗位，打工人找活、报名、上岗、互评。

架构骨架对齐「黑马点评」，业务换成自己的。**纯练技术，不追求 QPS / 压测。**

> 动态进度（做到哪了、下一步做什么）看 [PROGRESS.md](PROGRESS.md) —— 那是唯一事实来源。
> 稳定信息与工作规范看 [CLAUDE.md](CLAUDE.md)。

---

## 技术栈

| 层 | 选型 |
| --- | --- |
| 后端 | JDK 17、Spring Boot 3.5.x、MyBatis-Plus 3.5.x |
| 存储 | MySQL 8.0、Redis 7（缓存三问题 / GEO 附近搜索 / 签到 bitmap / 分布式 ID / Redisson 锁） |
| 消息 | RabbitMQ 3.x（替代黑马点评的 Redis Stream，做异步落单 + 死信） |
| AI | langchain4j 1.0.1 + DashScope 兼容模式（deepseek-v3）、BM25 检索增强 RAG、SSE 流式对话 |
| 前端 | Vue 3 + Vite + Vant 4（移动端 H5） |
| 其它 | Lombok、Hutool、Validation、Knife4j |

## 快速开始

```bash
# 1. 中间件（MySQL / Redis / RabbitMQ）
docker compose up -d

# 2. 后端（AI 功能需要 local profile 才拿得到 API Key）
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=local

# 3. 前端
cd frontend
npm install && npm run dev
```

端口：MySQL **3307**（本机原生 MySQL 占了 3306，Docker 映射到 3307）、Redis `6379`、
RabbitMQ `5672`（管理台 `15672`）、后端 `8080`。

## 目录结构

```
linggong/
├── backend/          # Spring Boot 后端
│   └── src/main/java/com/linggong/
│       ├── controller/  service/(impl)  mapper/  entity/  dto/
│       ├── config/  interceptor/  utils/
│       ├── ai/       # AI 问答（rule 检索 / trace 追踪）
│       └── tools/    # Agent 函数调用工具
├── frontend/         # Vue 3 移动端 H5
├── deploy/           # 生产 nginx 配置（见 deploy/README.md）
├── docs/             # 评测报告、产品与技术文档
└── tools/            # 测试与数据工具（见下）
```

---

## 测试工具

`tools/` 下的脚本都**不是**构建产物，是长期资产。分三类：造数据、评 RAG、离线测 AI 链路。

> 除 `fake_llm_sse.py` 外均需先起中间件；Python 脚本依赖 `pip install pymysql redis`。

### 一、造数据

| 文件 | 作用 | 用法 |
| --- | --- | --- |
| `seed_data.py` | 清空并重建一套贴近真实的演示数据：50 用户 / 40 岗位 / 60 报名 / 40 互评 / 30 关注 / 30 动态，**并同步预热 Redis**（GEO、报名名额、关注集合、Feed 收件箱） | `python tools/seed_data.py` |
| `seed_demo_data.sql` | SQL 版演示数据（幂等，先清后插） | `mysql < tools/seed_demo_data.sql` |
| `chat_demo_data.sql` | 聊天功能演示数据（工人 101~103 + 雇主 51） | `mysql < tools/chat_demo_data.sql` |

`seed_data.py` 的关键约定必须与后端代码一致，改任一边都要同步：
报名 id 用雪花算法、GEO key `geo:job:{categoryId}`、名额 key `apply:stock:{jobId}`、
关注 `follows:{userId}`、Feed `feed:{userId}`。

### 二、RAG 评测

这一组围绕 500 条标注集与 `docs/rag-eval-baseline.md`，跑一次就知道检索改动是涨是跌。

| 文件 | 作用 | 用法 |
| --- | --- | --- |
| `gen_eval_set.py` | 生成 500 条评测集 → `backend/src/test/resources/ai/eval-set.json`。**自带 11 条自动校验**（id/query 唯一、tags 在词表内、超纲题 relevant 必空、任一标签样本数 <5 报错等）；改完必须走脚本，别手改 JSON | `python tools/gen_eval_set.py`<br>`python tools/gen_eval_set.py --category cross --sample 12` |
| `check_semantic_gap.py` | **先行指标**：算 query 的 token 有多少落在语料词表之外（OOV）。BM25 是词袋模型，token 全 OOV 必然零召回 —— 不用等指标引擎就能先量化「口语化会不会打穿检索」 | `python tools/check_semantic_gap.py <rules.tsv>` |
| `snapshot_rules.py` | 把 `tb_ai_rule` 快照成测试资源并打**指纹**。有了它评测**不连 DB 也能复现**，报告里可以声明「本次基线对应语料指纹 xxxx」 | `python tools/snapshot_rules.py` |
| `propose_aliases.py` | 从标注集里**反推**该给哪条规则补哪些别名（按 OOV token 归集到规则并按频次排序），是别名扩展的候选生成器 | `python tools/propose_aliases.py` |
| `gen_aliases.py` | 生成 B4 别名扩展表（口语说法 + 同音形近错别字）。**词驱动而非 query 驱动** —— 错别字是「词」的属性，与它出现在哪条 query 无关，否则就是对评测集过拟合 | `python tools/gen_aliases.py` |
| `check_embeddings.py` | **C 阶段前置冒烟**：验证 DashScope 兼容模式的 `/embeddings` 能不能用、该用哪个模型、维度与耗时。C1 的「零新增依赖」是**假设不是事实**，不先验证就改 `AiModelConfig`、写 RRF，接口一旦不通整条 C1 返工 | `python tools/check_embeddings.py` |

`check_embeddings.py` 会按错误类型分流，**明确区分「假设被证伪」与「根本没能测」**：
账号欠费（`Arrearage`）是后者 —— 此时假设依然悬着，不该开始动 C1 的代码，充值后重跑即可。
Key 读取顺序与环境变量/`application-local.yml` 一致，且只打印前 6 位。

跑评测本身（指标引擎与基线阶梯都是 JUnit）：

```bash
cd backend
mvn -o test -Dfile.encoding=UTF-8          # 全量，65 个用例
mvn -o test -Dtest='BaselineLadderTest'    # 只重跑基线阶梯并重写 docs/rag-eval-baseline.md
```

> ⚠️ **`-Dfile.encoding=UTF-8` 不能省。** JVM 在 Windows 上默认按平台编码（GBK）写 stdout，
> 产出的 Markdown 报告会乱码。这是交付物品质的底线。
>
> ⚠️ 报告里的数字**全部由代码生成**，不要手抄进正文。§2 核心表与 §6.5 耗时表都从同一次实测注入
> （占位符 `{{...}}` + `renderAnalysis()`），并有断言挡住没被替换的占位符 ——
> 一边生成一边手写必然漂移。

### 三、离线测 AI 链路

| 文件 | 作用 | 用法 |
| --- | --- | --- |
| `fake_llm_sse.py` | **本地假 LLM**（OpenAI 兼容流式协议）。不消耗真实 token 就能测外层链路 | 见下 |

#### `fake_llm_sse.py` —— 不花 token 测 AI 外层链路

RAG 链路的正确性分两层，测法完全不同：

1. **内层**（检索、指标、trace 拼装）是纯逻辑，JUnit 就够；
2. **外层**（SSE 流式输出、回答分片**跨线程累积**、首字延迟、客户端断开）**必须在真实 HTTP
   客户端上跑** —— 单测里 `Flux.just("好")` 合成的流不算数，它绕过了 langchain4j 的 SSE 解析、
   真实派发线程与分片时序。

第 2 层如果只能靠真实模型测，就有两个后果：**每次回归都烧 token**，以及**账号欠费/断网时整条
链路没法验证**。后一条不是假设 —— 2026-09-13 真发生过：DashScope 返回
`{"type":"Arrearage"}`，所有问答静默降级成兜底话术，`tb_ai_trace` 里一排 `status=ERROR`。
有了这个桩，那种情况下链路照样可测。

```bash
# 终端 A：起桩（仅标准库，无需 pip 安装）
python tools/fake_llm_sse.py

# 终端 B：后端把 base-url 指过来（只影响本次启动，不改任何配置文件）
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=local \
    "-Dspring-boot.run.arguments=--linggong.ai.base-url=http://127.0.0.1:18999/v1"
```

之后正常调 `/ai/assistant/chat`，回答就是桩里写死的 `CHUNKS`（故意中文多分片，顺带覆盖
UTF-8 与跨线程累积）。三种 `tb_ai_trace.status` 都能造出来：

| 状态 | 怎么触发 |
| --- | --- |
| `OK` | 正常请求，答完 |
| `INCOMPLETE` | `curl --max-time 0.9` 中途掐断（`CHUNK_DELAY_SECONDS` 就是为这个留的） |
| `ERROR` | 不启桩，或把 base-url 指向错误端口 |

**换端口前先查 Windows 保留端口段**，否则 `bind` 会报 `WinError 10013`（报错信息像权限问题，
其实是端口被系统预留，很误导）：

```bash
netsh int ipv4 show excludedportrange protocol=tcp
```

9099 就落在保留段 9093-9192 里，所以默认改用了 18999。

---

## 相关文档

| 文档 | 内容 |
| --- | --- |
| [PROGRESS.md](PROGRESS.md) | 进度清单，唯一事实来源 |
| [CLAUDE.md](CLAUDE.md) | 稳定信息与工作规范 |
| [docs/rag-eval-baseline.md](docs/rag-eval-baseline.md) | RAG 评测报告：B0~B4 五方案对比、k 敏感度、分 category、留出验证、Bad Case 归因 |
| [docs/产品与技术文档.md](docs/产品与技术文档.md) | 产品与技术设计 |
| [deploy/README.md](deploy/README.md) | 生产部署（nginx） |
