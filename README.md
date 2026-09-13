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
| AI | langchain4j 1.0.1 + DashScope 兼容模式（deepseek-v3）、平台规则全量注入、Agent 函数调用、SSE 流式对话、问答 trace |
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
│       ├── ai/       # AI 问答（rule 规则手册 / trace 追踪）
│       └── tools/    # Agent 函数调用工具
├── frontend/         # Vue 3 移动端 H5
├── deploy/           # 生产 nginx 配置（见 deploy/README.md）
├── docs/             # 产品与技术文档
└── tools/            # 测试与数据工具（见下）
```

---

## AI 问答为什么不用 RAG

规则库只有 19 条、约 1.9K 字（≈1.3K token），远低于模型上下文窗口，**全量注入比检索更准**：

- 检索会**漏召**。中文口语 query 与规则词表大量不重合（如用户说「押金」，规则库里只有「担保金」），
  BM25 这类词袋模型此时返回零条 —— 而全量注入的召回率恒为 1，不存在这个问题。
- 检索会**帮倒忙**。无分数阈值时，任何字面重叠都会把不相关规则塞进上下文，诱导模型硬答超纲问题。

代价是每次问答都要带全部规则，但这部分前缀稳定、可命中服务端上下文缓存，且规则库改一次要重启一次，
不存在「知识频繁更新、要秒级生效」的场景。

**这个结论是实测出来的**，不是拍脑袋：项目早期实现过完整的 BM25 检索链路并用 500 条标注集做过
B0~B4 五方案对比，数据显示该规模下检索是负收益，于是把生产链路换成全量注入
（决策记录见 [PROGRESS.md](PROGRESS.md)）。

---

## 测试工具

`tools/` 下的脚本都**不是**构建产物，是长期资产。分两类：造数据、离线测 AI 链路。

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

### 二、离线测 AI 链路

| 文件 | 作用 | 用法 |
| --- | --- | --- |
| `fake_llm_sse.py` | **本地假 LLM**（OpenAI 兼容流式协议）。不消耗真实 token 就能测外层链路 | 见下 |

#### `fake_llm_sse.py` —— 不花 token 测 AI 外层链路

AI 链路的正确性分两层，测法完全不同：

1. **内层**（trace 拼装、状态流转、截断）是纯逻辑，JUnit 就够；
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

### 跑测试

```bash
cd backend
mvn -o test -Dfile.encoding=UTF-8
```

> ⚠️ **`-Dfile.encoding=UTF-8` 不要省。** JVM 在 Windows 上默认按平台编码（GBK）写 stdout，
> 中文日志会变成乱码，排查失败用例时非常难受。

---

## 相关文档

| 文档 | 内容 |
| --- | --- |
| [PROGRESS.md](PROGRESS.md) | 进度清单，唯一事实来源 |
| [CLAUDE.md](CLAUDE.md) | 稳定信息与工作规范 |
| [docs/产品与技术文档.md](docs/产品与技术文档.md) | 产品与技术设计 |
| [deploy/README.md](deploy/README.md) | 生产部署（nginx） |
