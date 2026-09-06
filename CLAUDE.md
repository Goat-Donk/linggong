# 本地零工平台（linggong）

> 本文件存放**稳定信息与工作规范**，每次会话自动加载。
> 动态进度（做到哪了、下一步做什么）看 [[PROGRESS.md]]，**开工前必读**。

## 项目是什么

本地零工撮合平台：雇主发布岗位，打工人找活、报名、上岗、互评。

学习目标：用「黑马点评」的架构骨架，换自己的业务皮，做一个**经典、全面、规范**的 Spring Boot 教学级项目。

**纯练技术，不追求 QPS / 压测 / 性能指标。**

## 技术栈

- JDK 17、Maven 3.8+
- Spring Boot 3.5.x、MyBatis-Plus 3.5.x
- MySQL 8.0
- Redis 7（缓存穿透/击穿/雪崩、GEO 附近搜索、签到 bitmap、分布式 ID、Redisson 锁）
- RabbitMQ 3.x（替代黑马点评的 Redis Stream，做异步落单）
- Lombok、Hutool、Validation
- 前端（后期）：移动端 H5，与黑马点评类似风格，但自己写不复用

## 参考代码（改动核心类前先看）

本项目骨架对齐「黑马点评」，本地有两份参考源码，**改动前优先对照真·原版**：

| 版本 | 路径 | 说明 |
| --- | --- | --- |
| **真·原版** | `F:\BaiduNetdiskDownload\hm-dianping` | 黑马点评官方课程源码（com.hmdp），**对齐基准** |
| 增强版 | `d:\AI点评\AI-dianping-backend` | 用户自己加料的（CacheClient 合布隆+Guava 本地缓存、有 MCP/Reservation/Chat/AiSkill），**已偏离原版，仅作参考** |

关键差异（linggong 已对齐原版，改代码时别照着增强版改回去）：
- `CacheClient`：原版三方法拆分（穿透/逻辑过期/互斥）←→ 增强版合并成一个方法 + 布隆 + 本地缓存。
- `SimpleRedisLock`：原版 `unlock()` 用 `lua/unlock.lua` 原子释放 ←→ 增强版回退成非原子 check-then-delete。
- `UserHolder`：原版普通 ThreadLocal ←→ 增强版 InheritableThreadLocal。
- 登录存储：两边都是 Redis Hash（linggong 也用 Hash）。

有意为之的选型差异（不算偏离，别改）：RabbitMQ 替代 Redis Stream（seckill.lua 不 `xadd`）、验证码登录无密码（无 PasswordEncoder）。

## 目录结构

```
d:\linggong\
├── CLAUDE.md            # 本文件：稳定信息 + 工作规范
├── PROGRESS.md          # 进度清单（唯一事实来源，动态更新）
├── docker-compose.yml   # 一键启动 MySQL/Redis/RabbitMQ
├── .gitignore
├── backend/             # Spring Boot 后端（先做）
│   ├── pom.xml
│   └── src/main/java/com/linggong/
│       ├── controller/  service/(impl)  mapper/  entity/  dto/
│       ├── config/      interceptor/    utils/
│       └── resources/(application.yml, mapper/*.xml, lua/*.lua, db.sql)
└── frontend/            # 前端（后期，移动端 H5）
```

包名 `com.linggong`，后端 ArtifactId `linggong-backend`。

## 环境

- 三个中间件用 Docker Compose 一键启动：`docker compose up -d`
- 端口：MySQL `3306` / Redis `6379` / RabbitMQ `5672`（管理台 `15672`）
- 实际连接信息在 `backend/src/main/resources/application.yml`（占位符，运行时填）

## Git 规范

- 远程仓库：`https://github.com/Goat-Donk/linggong.git`
- 本仓库身份（仅局部配置，不动全局）：`Goat-Donk` / `liufazhen1024@163.com`
- 默认分支：`main`
- 提交信息格式（中文）：
  ```
  <type>(<scope>): <一句话描述>

  - 改动点1
  - 改动点2
  ```
  `type`：`feat` 新功能 / `fix` 修复 / `refactor` 重构 / `docs` 文档 / `test` 测试 / `chore` 杂项。
- **每次提交/合并必须写清楚这次做了什么。**

## 工作方式（重要）

- **每次只做一步**，不一次性生成全部，避免细节缺失、像个 toy。
- 每完成一步，立即更新 [[PROGRESS.md]] 的「当前进度」和「下一步」，并 `git commit` + `push`。
- 代码风格对齐黑马点评分层：`controller / service(impl) / mapper / entity / dto / config / interceptor / utils`。

## 恢复规则

- 开工前先读 [[PROGRESS.md]] 的「当前进度」和「下一步」，从断点继续，**不重做已完成部分**。
