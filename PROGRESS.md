# 本地零工平台 —— 开发进度清单

> 本文件是项目的**唯一事实来源（source of truth）**。对话中断后，读本文件即可知道：
> 项目是干什么的、用什么技术、做到哪了、下一步做什么。
>
> **规则**：每完成一步，必须更新文末「当前进度」和「下一步」两个小节，并 `git commit`。

---

## 0. 项目基本信息

| 项 | 内容 |
|---|---|
| 项目名称 | 本地零工平台（linggong） |
| 应用场景 | 本地兼职/零工撮合：雇主发布岗位，打工人找活、报名、上岗、互评 |
| 学习目标 | 用「黑马点评」的架构骨架，换自己的业务皮，做一个**经典、全面、规范**的 Spring Boot 教学级项目 |
| 角色 | 打工人（求职者）、雇主（发布者）。一个用户可兼任两种身份 |
| 包名 | `com.linggong` |
| 后端 ArtifactId | `linggong-backend` |
| 前端 | 后期再做（移动端 H5，与黑马点评类似风格，但不复用其代码） |
| 是否追性能指标 | 否，重点是学技术、写规范，不追求 QPS/压测 |

### 目录结构

```
d:\linggong\
├── PROGRESS.md          # 本文件：进度清单（唯一事实来源）
├── .gitignore
├── backend/             # Spring Boot 后端（先做）
│   ├── pom.xml
│   └── src/main/java/com/linggong/
│       ├── controller/  service/(impl)  mapper/  entity/  dto/
│       ├── config/      interceptor/    utils/
│       └── resources/(application.yml, logback.xml, mapper/*.xml, lua/*.lua, db.sql)
└── frontend/            # 前端（后期，移动端 H5）
```

### 环境要求

- JDK 17、Maven 3.8+
- MySQL 8.0
- Redis 6+（缓存 / GEO / 签到 bitmap / 分布式 ID / Redisson 锁）
- RabbitMQ 3.x（`5672` 端口，管理台 `15672`）—— 本项目用 MQ 替代黑马点评的 Redis Stream
- Git

---

## 1. 完整功能清单（按模块）

### 1.1 用户与认证
- [ ] 手机号 + 验证码登录（模拟短信，验证码存 Redis，TTL 2 分钟）
- [ ] 获取当前登录用户 / 修改个人资料
- [ ] 查看他人用户主页
- [ ] 退出登录（删除 token）
- [ ] 每日签到 + 连续签到天数（Redis Bitmap）

### 1.2 岗位（零工）管理
- [ ] 岗位分类列表
- [ ] 雇主发布岗位（含经纬度、名额、薪资、起止时间）
- [ ] 编辑 / 下架岗位
- [ ] 岗位详情（带缓存）
- [ ] 按分类分页查询岗位
- [ ] 按关键词搜索岗位
- [ ] 附近岗位（Redis GEO，按距离排序）

### 1.3 报名（核心，含秒杀 + MQ）
- [ ] 报名岗位（普通岗直接报名；限量热门岗走秒杀逻辑）
- [ ] 我的报名记录（状态机：待确认/已录用/已完成/已取消）
- [ ] 雇主审核报名（通过 / 拒绝）
- [ ] 防重复报名（一人一单）

### 1.4 关注
- [ ] 关注 / 取关（关注雇主）
- [ ] 判断是否已关注
- [ ] 共同关注

### 1.5 动态 Feed
- [ ] 发布晒单动态（打工人晒今天打的零工）
- [ ] 点赞动态
- [ ] 我的动态
- [ ] 关注的人动态（推流）
- [ ] 滚动分页（lastId + offset）

### 1.6 互评
- [ ] 雇主评价工人 / 工人评价雇主
- [ ] 查看岗位下的评价列表

### 1.7 文件上传
- [ ] 头像 / 岗位图 / 晒单图上传（本地存储）

### 1.8 前端（后期）
- [ ] 移动端 H5（与黑马点评类似风格，自己写不复用）

---

## 2. 数据模型（表结构）

| 表名 | 说明 | 关键字段 |
|---|---|---|
| `tb_user` | 用户 | id, phone, nick_name, icon, role(0打工人/1雇主), create_time |
| `tb_user_info` | 用户资料 | user_id, introduce, age, gender, credit(信用分) |
| `tb_job_category` | 岗位分类 | id, name, sort |
| `tb_job` | 零工岗位 | id, category_id, employer_id, name, address, x, y(经纬度), salary, headcount(名额), start_time, end_time, description, status(0上架/1下架), create_time |
| `tb_job_application` | 报名记录(订单) | id, job_id, worker_id, status(0待确认/1已录用/2已完成/3已取消), create_time, update_time |
| `tb_job_evaluation` | 互评 | id, job_id, from_user_id, to_user_id, rating, content, create_time |
| `tb_follow` | 关注 | id, user_id, follow_user_id |
| `tb_blog` | 晒单动态 | id, user_id, title, content, images, liked, create_time |

> 签到用 Redis bitmap 实现（key=`sign:{userId}:{yyyyMM}`），不落库。
> 建表脚本先写进 `backend/src/main/resources/db.sql`。

---

## 3. 技术实现方案（每个技术点怎么做）

### 3.1 登录认证（对齐黑马点评）
- 验证码：`POST /user/code`，生成 6 位随机码存 Redis，key=`login:code:{phone}`，TTL 2 分钟。
- 登录：`POST /user/login`，校验验证码 → 生成 token(UUID) 存 Redis，key=`login:token:{token}` 存 UserDTO，TTL 30 分钟。
- 拦截器：`RefreshTokenInterceptor`（放行全部，刷新 token 有效期，用户存 ThreadLocal）+ `LoginInterceptor`（校验 ThreadLocal）。
- `UserHolder`：ThreadLocal 存当前用户，用完 remove 防内存泄漏。

### 3.2 缓存三问题（核心）
- **CacheClient** 统一封装：`queryWithPassThrough`（穿透：空对象）、`queryWithMutex`（击穿：互斥锁）、`queryWithLogicalExpire`（击穿：逻辑过期异步重建）。
- **布隆过滤器**：Redisson `RBloomFilter` 预载岗位 id，防穿透。
- **雪崩**：TTL 加随机值。
- 岗位详情走这套缓存。

### 3.3 GEO 附近搜索
- 岗位发布时把 `(x, y, jobId)` 写入 Redis GEO，key=`geo:job:{categoryId}`。
- 查询用 `GEOSEARCH`（经纬度 + 距离排序），拿 id 列表回 DB 取详情。

### 3.4 报名 + 秒杀 + RabbitMQ（与黑马点评最大差异）
- **普通岗**：直接报名写 `tb_job_application`。
- **限量热门岗**（名额有限）：
  1. 生成报名单号：`RedisIdWorker`（雪花算法）。
  2. 执行 **Lua**（原子）：查名额 → 查一人一单 → 扣名额 → 记标记。
  3. 成功 → publisher 发消息到 RabbitMQ（发布确认）。
  4. `@RabbitListener` 消费者：幂等校验 → 写表 → 手动 ACK。
  5. 失败重试 → 超限进**死信队列**。
- **RabbitMQ 组件**：交换机 `job.direct`、队列 `job.application` + `job.application.dlq`、路由 key `job.application`。
- **Redisson 锁**：报名幂等兜底、防重复提交。

### 3.5 分布式 ID
- `RedisIdWorker`：雪花算法，Redis `INCRBY` 生成 workerId。

### 3.6 分布式锁
- `SimpleRedisLock`（`SET NX EX` + Lua 释放，教学用）+ Redisson `RLock`（生产用）。

### 3.7 Feed 推流
- 发布动态读关注者集合推送到粉丝收件箱（Redis ZSet，score=时间戳）。
- `ScrollResult` 滚动分页（lastId + offset）。

### 3.8 Bitmap 签到
- `Redis bitfield`：key=`sign:{userId}:{yyyyMM}`，统计连续签到天数。

### 3.9 其他规范
- 统一返回体 `Result<T>`；全局异常 `WebExceptionAdvice`；参数校验 Validation；敏感配置用占位符。

---

## 4. 分阶段规划（里程碑）

| 阶段 | 内容 | 对应技术点 | 状态 |
|---|---|---|---|
| **Phase 0 地基** | 骨架、pom、配置、统一返回/异常/校验、db.sql、空跑 | 工程规范 | 🔄 进行中 |
| **Phase 1 登录** | 验证码登录、token、双拦截器、ThreadLocal、用户资料 | 认证/拦截器 | ⬜ 未开始 |
| **Phase 2 岗位+缓存** | 岗位 CRUD、分类、附近搜索、岗位详情缓存 | 缓存三问题、GEO、布隆 | ⬜ 未开始 |
| **Phase 3 报名+秒杀+MQ** | 报名、限量秒杀、RabbitMQ 异步落单、审核 | Lua、雪花ID、Redisson、MQ | ⬜ 未开始 |
| **Phase 4 社交+Feed+签到** | 关注、晒单动态、推流、每日签到 | Set交集、Feed、Bitmap | ⬜ 未开始 |
| **Phase 5 互评+上传+收尾** | 互评、文件上传、Knife4j 文档 | 评价、上传 | ⬜ 未开始 |
| **Phase 6 前端（后期）** | 移动端 H5，连后端 | Vue | ⬜ 未开始 |

---

## 5. Git 规范

- **远程仓库**：`https://github.com/Goat-Donk/linggong.git`
- **GitHub 用户名**：`Goat-Donk`　**邮箱**：`liufazhen1024@163.com`（仅本仓库局部配置，不动全局）
- **默认分支**：`main`
- **提交信息格式**（中文，讲清楚做了什么）：
  ```
  <type>(<scope>): <一句话描述>

  - 改动点1
  - 改动点2
  ```
  `type`：`feat` 新功能 / `fix` 修复 / `refactor` 重构 / `docs` 文档 / `test` 测试 / `chore` 杂项。
- **每次提交/合并必须写清楚这次做了什么**（用户明确要求）。

---

## 6. 当前进度（动态更新，每步必改）

### ✅ 已完成
- 项目目录 `d:\linggong` 建立（backend / frontend）。
- Git 本地仓库初始化（main 分支）。
- 配置本仓库 donk 身份（Goat-Donk / liufazhen1024@163.com）。
- 关联远程 `origin` = `https://github.com/Goat-Donk/linggong.git`，连通验证通过（空仓库）。
- 首次提交完成：进度清单 `PROGRESS.md` + `.gitignore`。
- 首次 push 到 GitHub 成功（main 分支已上线远程，本机凭据已就绪）。
- Phase 0 第 1 步：编写 `docker-compose.yml`（MySQL 8.0 / Redis 7 / RabbitMQ 3-management，含健康检查与数据卷），待启动验证。

### 🔄 进行中
- Phase 0：后端骨架

### ⏭ 下一步
- 启动三个服务：`docker compose up -d`，确认 3306 / 6379 / 5672 / 15672 端口正常。
- 然后写 `backend/pom.xml`。

---

## 7. 恢复约定

- **对话中断后**：先读本文件「当前进度」和「下一步」，从断点继续，不重做已完成部分。
- **每完成一步**：立即更新第 6 节，并 `git commit`（写清做了什么）。
- **环境信息**：MySQL/Redis/RabbitMQ 实际连接信息放 `application.yml`（占位符，运行时填），不写进本文档。
