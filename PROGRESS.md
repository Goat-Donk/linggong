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
| **Phase 0 地基** | 骨架、pom、配置、统一返回/异常/校验、db.sql、空跑 | 工程规范 | ✅ 完成 |
| **Phase 1 登录** | 验证码登录、token、双拦截器、ThreadLocal、用户资料 | 认证/拦截器 | ✅ 完成 |
| **Phase 2 岗位+缓存** | 岗位 CRUD、分类、附近搜索、岗位详情缓存 | 缓存三问题、GEO、布隆 | ✅ 完成 |
| **Phase 3 报名+秒杀+MQ** | 报名、限量秒杀、RabbitMQ 异步落单、审核 | Lua、雪花ID、Redisson、MQ | ✅ 完成 |
| **Phase 4 社交+Feed+签到** | 关注、晒单动态、推流、每日签到 | Set交集、Feed、Bitmap | ✅ 完成 |
| **Phase 5 互评+上传+收尾** | 互评、文件上传、Knife4j 文档 | 评价、上传 | 🔄 进行中 |
| **Phase 6 前端（后期）** | 移动端 H5，连后端 | Vue | ⬜ 未开始 |

---

## 5. Git 规范

- **远程仓库**：`https://github.com/Goat-Donk/linggong.git`
- **GitHub 用户名**：`Goat-Donk`　**邮箱**：`liufazhen1024@163.com`（仅本仓库局部配置，不动全局）
- **默认分支**：`main`（稳定线，永远可运行）
- **分支工作流**（Phase 2 起）：每个 Phase 开 `feat/*` 分支开发，验证通过后合回 `main` 并删分支。
  - 开分支：`git checkout -b feat/<phase>`（如 `feat/job-cache`、`feat/apply-mq`）
  - 合回：`git checkout main` → `git merge --no-ff feat/<phase>` → `git push` → 删本地/远程 feature 分支
  - Phase 0/1 是直接在 main 上提交的（历史已定，不再回溯重改）
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
- Phase 0 第 1 步：编写 `docker-compose.yml`（MySQL 8.0 / Redis 7 / RabbitMQ 3-management，含健康检查与数据卷）。
- Phase 0 第 2 步：环境就绪 —— 三个服务已启动并验证（MySQL `linggong` 库已建、Redis `PONG`、RabbitMQ 运行正常）；顺带清理了本机残留的 smart-raffle 容器/数据卷以释放 5672/6379 端口。
- Phase 0 第 3 步：编写 `backend/pom.xml`（依赖：Web / Validation / Redis / **AMQP(RabbitMQ)** / MyBatis-Plus / MySQL / Redisson / Hutool；去掉参考项目的 AI 依赖），`mvn validate` 校验通过。
- Phase 0 第 4 步：编写代码骨架 —— `application.yml`（MySQL/Redis/RabbitMQ 连接）、主启动类 `LinggongApplication`、统一返回体 `Result`、全局异常 `WebExceptionAdvice`（含参数校验处理），`mvn compile` 编译通过。
- 环境修复：Docker 的 WSL2 吃满内存导致 Maven 无法启动（报"页面文件太小"），已在 `C:\Users\86135\.wslconfig` 把 WSL 内存 6GB→4GB。
- Phase 0 第 5 步：编写 `db.sql`（8 张建表脚本 + 岗位分类种子数据；报名记录表 id 用雪花算法、唯一键防重复报名）。
- Phase 0 第 6 步：空跑验证通过 —— 执行 `db.sql` 建 8 张表，应用启动成功（Tomcat 8080，约 5.8s，无报错）。
- Phase 1 第 1 步：实体 + DTO + Mapper —— `User`/`UserInfo` 实体、`LoginFormDTO`(带手机号/验证码校验)/`UserDTO`(安全返回)、`UserMapper`/`UserInfoMapper`，`mvn compile` 通过。
- Phase 1 第 2 步：通用工具 —— `RedisConstants`（login:code / login:token 前缀 + TTL）、`UserHolder`（ThreadLocal）、`RegexUtils`（手机号校验），`mvn compile` 通过。登录存 token 用 `StringRedisTemplate` + hutool `JSONUtil`，自定义 `RedisTemplate` 留到 Phase 2 缓存再加。
- Phase 1 第 3 步：Service 层 —— `IUserService`/`UserServiceImpl`（发验证码 + 登录 + 退出，首次登录自动注册）、`IUserInfoService`/`UserInfoServiceImpl`（`getByUserId` 按 user_id 查、`saveOrUpdateByUserId` 保存/更新），`mvn compile` 通过。
- Phase 1 第 4 步：Controller + 双拦截器 —— `UserController`（`/user/code`、`/user/login`、`/user/me`、`/user/logout`、`/user/{id}`、`/user/info/{id}`、`/user/update`）、`RefreshTokenInterceptor`（解析 token + 刷新有效期）、`LoginInterceptor`（未登录返回 401）、`MvcConfig`（注册 + 排除登录路径）、`UserUpdateDTO`，`mvn compile` 通过。
- Phase 1 第 5 步：启动验证通过 —— curl 端到端测完整链路（发码 → 登录拿 token → /user/me → 改资料 → 看资料 → 退出 → 无 token 返回 401）全部成功。
- Phase 1 收尾修复：① 发现并修复「改资料后 /user/me 返回旧昵称」—— 新增 `UserService.refreshUserCache()`，改资料后回写 Redis token 缓存 + ThreadLocal；② 环境冲突：本机原生 MySQL（服务「MySQL」）占 3306，Docker MySQL 改映射到 **3307**（docker-compose + application.yml 已同步改）。
- 环境排查（重要）：本机 16GB 内存未满（空闲 ~7GB），OOM 根因是**页面文件被固定成 2915MB×2（非系统托管）**，导致提交内存上限仅 ~18.6GB，Docker+VSCode+应用一起跑就触顶崩溃。已把 WSL2 内存 4GB→3GB；**建议用户把虚拟内存改为「系统托管」**（需重启）。
- Phase 2 第 1 步：实体 + Mapper —— `JobCategory`/`Job` 实体、`JobCategoryMapper`/`JobMapper`（空 BaseMapper 接口），`mvn compile` 通过。
- Phase 2 第 2 步：岗位 CRUD + 分类列表 —— `IJobCategoryService`/`IJobService` 及实现、`JobController`/`JobCategoryController`、`JobDTO`/`JobFormDTO`、`MybatisConfig`（分页插件）、`UserDTO` 加 role 字段。发布仅限雇主（role=1）+ 岗位归属校验 + 起止时间校验，`mvn compile` 通过。
- Phase 2 第 3 步：岗位详情缓存（穿透 + 击穿）—— 新增 `CacheClient`（set 随机 TTL 防雪崩 / queryWithPassThrough 空对象防穿透 / queryWithMutex 互斥锁防击穿 / delete 缓存失效）、`RedisConstants` 加 cache:job: 与 lock: 常量，`queryById` 改走缓存。自审修复：改/下架岗位后删缓存（缓存一致性）+ 修正黑马点评互斥锁误删锁的瑕疵。`mvn compile` 通过。
- Phase 2 第 4 步：缓存击穿进阶（逻辑过期）+ 布隆过滤器 —— `RedisData` 逻辑过期包装、`CacheClient` 加 setWithLogicalExpire/queryWithLogicalExpire（异步重建线程池）、`RedissonConfig`（RedissonClient）、`JobBloomFilter`（启动预载岗位 id，初始化失败降级）、`queryById` 改走布隆预判 + 逻辑过期查询（未预热兜底）、publish 新岗位入布隆。`mvn compile` 通过。
- Phase 2 第 5 步：附近搜索（Redis GEO）—— `RedisConstants` 加 geo:job: 常量、`IJobService.queryNearby`、`JobController /job/nearby`、`JobServiceImpl` 注入 StringRedisTemplate：发布写 GEO / 编辑先删旧分类再加新分类 / 下架移除 GEO、`queryNearby` 用 GEOSEARCH + WITHDIST 按距离升序分页。`mvn compile` 通过。
- Phase 2 第 6 步：启动验证通过 —— 端到端 curl 测岗位全链路全部成功：① 岗位详情（未登录放行）② 附近搜索（按距离升序、distance 正确）③ 分类分页（total 正确）④ 关键词搜索 ⑤ 下架（成功）⑥ 下架后 GEO 移除（附近搜索不再返回该岗位）⑦ 下架后详情 status=1（缓存已删）⑧ role=0 用户发布被拒（"只有雇主才能发布岗位"）。修复：`LoginInterceptor` 放行 GET /job 浏览类接口（未登录也能逛岗位，写操作仍需登录）。**Phase 2 完成。**
- Phase 3 第 1 步：报名实体 + Mapper + 分布式 ID —— `JobApplication` 实体（id 雪花算法 INPUT，status 0待确认/1已录用/2已完成/3已取消）、`JobApplicationMapper`（空 BaseMapper）、`RedisIdWorker`（雪花算法简化版：1符号位+31时间戳+32序列号，序列号用 Redis INCR 按天自增）。`mvn compile` 通过。
- Phase 3 第 2 步：RabbitMQ 组件 + 秒杀 Lua —— `MqConstants`（交换机/队列/路由 key 常量）、`RabbitConfig`（交换机 job.direct + 主队列 job.application 带死信 + 死信队列 job.application.dlq + 绑定）、`RedisScriptConfig`（静态加载 seckill.lua 为 Bean）、`resources/lua/seckill.lua`（原子：查名额 → 一人一单 → 扣名额 → 记标记，返回 0/1/2）、`RedisConstants` 加 apply:stock: / apply:order: / apply id 前缀。`mvn compile` 通过。
- Phase 3 第 3 步：报名核心 Service + Controller —— `ApplyMessage`（MQ 消息体 jobId/workerId/orderId）、`IJobApplicationService`/`JobApplicationServiceImpl`（报名：岗位校验 → 名额懒加载预热 → Lua 原子扣名额 → 生成雪花单号 → 发 MQ）、`JobApplicationController`（POST /job-application/{jobId}）、`JobApplicationConsumer`（@RabbitListener 监听主队列，幂等落单 + 扣 DB 名额 + 手动 ACK，失败 basicNack 进死信）、`JobMapper.deductHeadcount`（headcount-1 且 >0 防负数）、`JobServiceImpl.publish` 发布岗位预热名额、`application.yml` 配 manual ack。`mvn compile` 通过。
- Phase 3 第 4 步：报名查询 + 雇主审核 —— `JobApplicationDTO`（报名字段 + 岗位简要信息）、`myApplications`（我的报名分页，批量查岗位避免 N+1）、`audit`（雇主审核：归属校验 + 状态机 0→1通过/0→3拒绝 + 防重复审核）、`JobApplicationController` 加 GET /my、PUT /{id}/approve、PUT /{id}/reject。`mvn compile` 通过。
- Phase 3 第 5 步：启动验证通过 —— 端到端 11 项全通过：发布岗位预热名额（apply:stock 正确）→ 工人报名 Lua 秒杀（名额扣减 + 一人一单标记）→ MQ 异步落单（tb_job_application 写入 + DB headcount 扣减）→ 我的报名（含岗位信息）→ 重复报名拦截 → 报名自己岗位拦截 → 雇主审核通过（0→1）→ 重复审核拦截 → 名额满拦截 → 拒绝审核（0→3）→ 越权审核拦截。**Phase 3 完成。**

- Phase 4 第 1 步：关注功能 —— `Follow` 实体 + `FollowMapper`、`IFollowService`/`FollowServiceImpl`（关注/取关 DB+Redis 双写、是否已关注、共同关注 SINTER 交集）、`FollowController`（PUT /follow/{id}/{isFollow}、GET /follow/or/not/{id}、GET /follow/common/{id}）、`RedisConstants` 加 follows: 前缀。`mvn compile` 通过。
- Phase 4 第 2 步：晒单动态 —— `Blog` 实体 + `BlogMapper`（incrementLike/decrementLike 防负数）、`BlogDTO`（动态字段 + isLike + 发布者头像昵称）、`BlogFormDTO`（发布校验）、`IBlogService`/`BlogServiceImpl`（发布 userId 登录态注入、我的动态用 UserHolder 填发布者避免查库、点赞 Redis Set + DB 同步幂等切换）、`BlogController`（POST /blog、GET /blog/my、PUT /blog/like/{id}）、`RedisConstants` 加 blog:liked: 前缀。`mvn compile` 通过。
- Phase 4 第 3 步：Feed 推流 + 滚动分页 —— `ScrollResult`（list/minTime/offset 游标）、发布动态推粉丝收件箱 `feed:{userId}`（ZSet，score=时间戳）、关注时滚动推送对方最近 3 条历史动态（score 用 createTime 毫秒）、关注的人动态滚动分页（lastId+offset，回查动态用 Map 重排保序 + 批量查发布者避免 N+1）、`BlogController` 加 GET /blog/of/follow、`RedisConstants` 加 feed: 前缀。`mvn compile` 通过。

- Phase 4 第 4 步：每日签到（Bitmap）—— `RedisConstants` 加 sign: 前缀、`IUserService`/`UserServiceImpl` 加 `sign()`（SETBIT 记当月第几天）+ `signCount()`（BITFIELD GET u{day} 取本月签到位，从最低位往前数连续 1）、`UserController` 加 POST /user/sign、GET /user/sign/count。`mvn compile` 通过。

- Phase 4 第 5 步：启动验证通过 —— 端到端 8 项全通过：① 签到（0→1→幂等 1，未登录 401，bitmap 位正确）② 发布动态+我的动态 ③ 关注/取关（DB+Redis 双写一致）④ 是否关注 ⑤ 共同关注 ⑥ 推流（关注滚入历史 3 条 + 发布推粉丝）⑦ 滚动分页（lastId+offset 无丢无重）⑧ 点赞（切换+DB 同步）。**Phase 4 完成。**

- Phase 5 第 1 步：互评 —— `JobEvaluation` 实体 + `JobEvaluationMapper`、`EvaluationFormDTO`（评分 1-5 校验）+ `EvaluationDTO`（含评价/被评人昵称头像）、`IJobEvaluationService`/`JobEvaluationServiceImpl`（发布：岗位存在 + 不能评自己 + 雇佣双方关系 + 工人已报名 + 防重复评价；查询：分页 + 批量查用户避免 N+1）、`EvaluationController`（POST /evaluation、GET /evaluation/job/{jobId}）。`mvn compile` 通过。

- Phase 5 第 2 步：文件上传 —— `UploadController`（POST /upload/image：空校验 + 图片类型白名单 + UUID 唯一文件名 + mkdirs + transferTo，返回 /uploads/{filename}）、`MvcConfig` 加 `addResourceHandlers` 映射 /uploads/** → 本地目录（`Paths.toUri` 规避 Windows 反斜杠）、`application.yml` 加 multipart 5MB/10MB + `linggong.upload.dir`。`mvn compile` 通过。

- Phase 5 第 3 步：Knife4j 接口文档 —— pom 加 `knife4j-openapi3-jakarta-spring-boot-starter 4.5.0`（排除自带 springdoc）+ `springdoc-openapi-starter-webmvc-ui 2.8.5`（覆盖，否则 knife4j 增强模式与 Boot 3.5 冲突报 NoSuchMethodError）；`Knife4jConfig`（OpenAPI 标题/描述 + 全局 `authorization` header APIKEY，页面右上角可 Authorize）；8 个 Controller 全加 `@Tag`/`@Operation`/`@Parameter`，Request DTO 加 `@Schema` 字段注解，实体/返回 DTO 加类级 `@Schema`；`LoginInterceptor` 放行 /doc.html /v3/api-docs /webjars /swagger-ui /error。踩坑修复：不能设 `springdoc.swagger-ui.enabled: false`（会把 knife4j 依赖的 `/v3/api-docs/swagger-config` 关掉，导致文档页拿不到分组）。验证：/doc.html、/v3/api-docs、/v3/api-docs/swagger-config 全 200，8 个 tag、30 个 path 正常生成。

### 🔄 进行中
- Phase 5 互评+上传+收尾 —— 第 3 步（Knife4j 文档）已完成，进行第 4 步（启动验证）。

### ⏭ 下一步
- Phase 5 第 4 步：启动验证（互评 + 文件上传 + Knife4j 文档全链路回归），通过后合回 main 并推远程、删本地分支、更新记忆。

---

## 7. 恢复约定

- **对话中断后**：先读本文件「当前进度」和「下一步」，从断点继续，不重做已完成部分。
- **每完成一步**：立即更新第 6 节，并 `git commit`（写清做了什么）。
- **环境信息**：MySQL/Redis/RabbitMQ 实际连接信息放 `application.yml`（占位符，运行时填），不写进本文档。
