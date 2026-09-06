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
| 前端 | 移动端 H5（Vue3 + Vite + Vant4，自己写不复用黑马点评） |
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
└── frontend/            # 前端（移动端 H5，Vue3 + Vite + Vant4）
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
| **Phase 5 互评+上传+收尾** | 互评、文件上传、Knife4j 文档 | 评价、上传 | ✅ 完成 |
| **Phase 6 前端** | 移动端 H5，连后端 | Vue3 + Vite + Vant4 | ✅ 完成 |

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

- Phase 5 第 4 步：启动验证通过 —— 端到端 19 项全通过：登录/设角色 → 发布岗位 → 报名（MQ 异步落单）→ 雇主审核 → 互评（工人评雇主 + 雇主评工人 + 评价列表 2 条 + 评自己/重复评价/未报名第三方/评分越界 4 个边界全拦截）→ 文件上传（成功 + 非法类型/空文件拦截 + 匿名访问图片）→ 文档（/doc.html /v3/api-docs /swagger-config 全 200）。修复 1 处缺陷：`LoginInterceptor` 未放行 `/uploads/**` 导致上传图片匿名访问 401，已补放行。**Phase 5 完成。**

- diff 对齐（补缺）：对照本地黑马点评参考后端逐文件 diff，补两处缺口 —— ① 登录用户存储 JSON→Redis Hash（`UserServiceImpl` login/refreshUserCache 用 `opsForHash().putAll` 写、`RefreshTokenInterceptor` 用 `entries`+`fillBeanWithMap` 读、token TTL 加随机值）；② 补 `ILock`+`SimpleRedisLock` 教学版分布式锁（SET NX EX 加锁 + 判断线程标识释放，注释说明演化线到 Redisson）。编译 + 端到端登录验证通过（token 存成 hash、/user/me 正确转回 id/role）。**diff 对齐完成。**

- diff 对齐（三方比对，SimpleRedisLock 补原子释放）：拿到真·原版 `F:\BaiduNetdiskDownload\hm-dianping` 后三方比对，确认 CacheClient / UserHolder / 登录 Hash 本就贴合原版；唯一偏离是 SimpleRedisLock 照抄了增强版「非原子 check-then-delete」的写法，已改回原版 `lua/unlock.lua` 原子释放（DefaultRedisScript 执行 get+compare+del 一步原子）。编译通过，`unlock.lua` 逻辑验证通过（标识一致删除、不一致不删）。**三方对齐完成。**

- Phase 6 第 1 步：前端初始化 —— 手写 Vite + Vue3 脚手架（`frontend/`，不用 `npm create vite` 的示例模板）：`package.json` 锁定 vue@3.5 / vue-router@4.6 / axios@1.20 / vant@4.10 / vite@6.4；`vite.config.js` 配 `/api` 代理到后端 8080（`rewrite` 去前缀，开发期免 CORS）；`main.js` 全量引入 Vant + router；`App.vue` 全局基础样式（移动端 viewport + reset）；`router/index.js` 建 home/login 占位路由 + 标题联动；`utils/request.js` 封装 axios（authorization 头放裸 token 无 Bearer 前缀、响应拦截统一处理 Result、401 清 token 跳登录）；`views/Home.vue`/`Login.vue` 占位页。`npm run build` 通过（302 模块，5.3s）。

- Phase 6 第 2 步：登录 + 鉴权 —— 新增 `stores/user.js`（reactive 单例 + localStorage 存 token/info，不引 Pinia，状态复杂再上）、`api/user.js`（sendCode/login/getMe/logout，对齐后端 UserController）；重写 `Login.vue`（手机号+验证码登录：60s 倒计时防重发、van-form 校验、登录成功先存 token 再拉 `/user/me` 存用户信息、跳回 `redirect` 来源页）；`router` 加 `beforeEach` 鉴权守卫（`requiresAuth` 无 token 跳登录并带 redirect）；`Home.vue` 展示登录态（昵称+角色 tag）；新增受保护占位页 `Profile.vue`（用户卡片 + 退出登录）。经 Vite 代理实测端到端：发码 → 登录拿 token → `/user/me` 返回 `{id:1,nickName:"老李",icon:"",role:1}` 全通，无 token 返回 401。

- Phase 6 第 3 步：首页（岗位列表 + 分类 + 附近搜索）—— 新增 `api/category.js`（getCategories）、`api/job.js`（getJobsByCategory/getNearbyJobs）、`utils/format.js`（距离/薪资/日期格式化）、`views/JobDetail.vue`（占位，Step 4 补）；重写 `Home.vue`（分类 Tab + 「只看附近」开关 + van-list 无限滚动岗位卡片）：分类 Tab 用 `v-model:active` 绑分类 id、附近模式用浏览器 `navigator.geolocation` 先定位成功再切换、筛选变化用 `:key` 重挂载 van-list 触发重新加载、`requestSeq` 序号丢弃在途旧请求防串数据、空状态 van-empty；`router` 加 `/job/:id` 占位路由。经代理实测：分类列表 6 个、按分类分页 total=4、附近搜索（上海坐标）返回 2 个岗位 `distance:0.19m`、偏移坐标超 5km 半径返回空（GEO 半径生效）。修复 1 处缺陷：附近搜索后端不返回 total，分页判断改为「不满一页 或 有 total 且已累计到 total」双条件，避免首页就误判到底。

- Phase 6 第 4 步：岗位详情 + 报名 —— 新增 `api/application.js`（applyJob）、`api/job.js` 补 getJobById、`api/user.js` 补 getUserById；重写 `JobDetail.vue`（岗位信息卡 + 地址/时间/名额 + 发布者卡 + 岗位描述 + 底部固定报名按钮）：发布者信息需登录（`/user/{id}` 不在 LoginInterceptor 放行列表），匿名跳过不影响浏览；报名按钮按「已下架/自己发布/已报名/立即报名」动态禁用与文案；点击报名未登录先跳登录带 redirect，登录后 POST `/job-application/{id}`；成功 toast + 按钮变已报名，失败由 request.js Toast（名额满/重复报名/报自己岗位）。经代理实测：详情返回完整 JobDTO（distance=null）、新工人报名成功返回雪花单号、重复报名拦截、匿名报名 401。**注**：后端最终实现无「普通岗/限量岗」分流，所有报名统一走 Lua 秒杀 + MQ 落单，前端无需区分。

- Phase 6 第 5 步：发布岗位 + 我的报名 —— 新增 `api/job.js` publishJob、`api/application.js` getMyApplications、`utils/format.js` formatApplyStatus/applyStatusType/formatDateTime；新增 `PublishJob.vue`（雇主表单：分类选择器/名称/地址/浏览器定位取经纬度 x/y/薪资/名额 stepper/起止时间 datetime picker 输出 ISO-8601/描述，非雇主 van-empty 拦截）、`MyApplications.vue`（我的报名分页列表，状态机 0待确认/1录用/2完成/3取消 + 岗位/薪资/地址/报名时间）；入口：首页雇主悬浮「发布」按钮 + 个人中心菜单（发布岗位/我的报名）；router 加 /publish、/my-applications。经后端 E2E 实测：雇主发布返回岗位 id 7、工人发布被拒「只有雇主才能发布岗位」、报名后我的报名返回完整 DTO（jobName/status=0待确认/total=1）。**补完雇主审核**：后端补 `GET /job-application/employer`（雇主查我发布岗位下的报名，含报名人昵称头像）+ 新 `EmployerApplicationDTO`；前端补 `EmployerApplications.vue`（通过/拒绝按钮，仅待确认显示操作，审核后本地更新状态）+ api approve/reject + 路由 + 个人中心「审核报名」入口。**重要修复**：雪花单号 64 位超 JS 安全整数 2^53，数字传输精度丢失导致审核拿错 id（实测 `...639` 变 `...600`）——已给 `JobApplicationDTO`/`EmployerApplicationDTO` 的 id 加 `@JsonSerialize(ToStringSerializer)` 序列化为字符串、apply 返回单号也转字符串。全项目雪花 ID 仅此两处（Blog/User/Job 均为自增），修复闭环。经后端 E2E：列表返回 string id + workerName、拒绝 0→3、通过 0→1、重复审核拦截「该报名已处理」、apply 返回 string 单号，全通过。

- Phase 6 第 6 步：关注 + Feed + 点赞 + 签到 —— 后端 `BlogDTO` 补 `isFollow`（与 `isLike` 对称：取关后旧动态仍留在收件箱，关注状态必须由后端返回，不能前端推断），`BlogServiceImpl.queryBlogOfFollow` 批量填充 `isFollow`（读 Redis follows 集合）；前端新增 `api/blog.js`（queryBlogOfFollow 滚动分页 + likeBlog）、`api/follow.js`（follow 关注/取关）、`api/user.js` 补 sign/signCount、`utils/format.js` 加 formatRelativeTime；新增 `Feed.vue`（关注流 van-list 无限滚动 lastId+offset 游标 + 点赞/关注/取关本地即时更新 + 相对时间 + 图片九宫格 + 空状态），`Profile.vue` 加「每日签到」卡片（用 signCount>0 推导今天已签，无需额外接口）+ 动态入口，`Home.vue` 导航加动态入口，router 加 /feed。经后端 E2E 10 项全通过：发布动态 3 条 → 关注两人 → 关注流时间倒序含 isFollow=true 与作者真实昵称 → 滚动分页游标 minTime/offset 正确、第二页为空 → isFollow 校验 true/false → 点赞 liked 0→1→0 与 isLike 切换 → 取关 isFollow→false → 签到幂等 signCount=1 → 未签到用户 signCount=0。**注**：晒单「发布 UI」留到 Step 7（需文件上传），本步 Feed 用接口发布种子数据联调。

- Phase 6 第 7 步：互评 + 个人中心/上传 —— 后端 `LoginInterceptor` 放行 GET `/evaluation`（匿名浏览岗位时也能看评价，与匿名逛岗位一致，原实现会 401 跳登录）；前端 `vite.config.js` 补 `/uploads` 代理（上传后的图片 `/uploads/{文件名}` 在 dev 下可直接显示）；新增 `api/upload.js`（uploadImage multipart POST /upload/image）、`api/evaluation.js`（getEvaluationsByJob + publishEvaluation）、`api/blog.js` 补 publishBlog/getMyBlogs、`api/user.js` 补 updateProfile/getUserInfo；新增 `PublishBlog.vue`（标题+内容+van-uploader 九宫格多图，选图即传回填 item.url，提交前拦截「上传中」防丢图，images 逗号拼接）、`EditProfile.vue`（头像单图上传 + 昵称/性别/年龄/简介，昵称头像从登录态取、简介年龄性别从资料表取，保存后 getMe 刷新本地登录态）、`MyBlogs.vue`（我的动态 van-list 分页）；`JobDetail.vue` 加互评区（评价列表 + 雇主「评价工人」弹选人 action-sheet / 已报名工人「评价雇主」弹 van-rate + 文本，evalAction 按身份推导，匿名不显示）、`Profile.vue` 加发布动态/我的动态/编辑资料入口 + Step 8 占位、`Feed.vue` 加「发布」悬浮按钮，router 加 /publish-blog、/my-blogs、/edit-profile。经后端 E2E 12 项全通过：匿名 GET /evaluation 200 → 上传图片返回 /uploads/xxx 且匿名可访问 → 发布带图动态成功 → 改资料（昵称张三+头像）/user/me 与 /user/info 同步更新 → 工人评雇主成功 → 重复评价拦截「您已评价过该岗位」→ 雇主评工人成功（选工人数据源 /job-application/employer 按 jobId 过滤正确）→ 评自己拦截「不能评价自己」→ 评价列表计数正确。

- Phase 6 第 8 步：全链路联调验证 —— 逐接口核对前后端字段契约（27 个前端 API 调用 vs 8 个 Controller + 全部 DTO 字段，含雪花 ID 字符串化、datetime ISO、ScrollResult 游标、total 分页、UserDTO/JobDTO/BlogDTO/EvaluationDTO/报名 DTO 字段名逐一比对），发现并修复 1 个真实缺陷：`LoginInterceptor` 用 `startsWith("/job")` 前缀过宽，误放行 `/job-application/*`（我的报名/雇主审核），匿名访问在 `UserHolder.getUser().getId()` 处 NPE 返回 500 而非 401；已收紧为 `startsWith("/job/")`（带斜杠边界）+ 显式放行 `/job-category`。重建后端后三阶段全链路 E2E 全通过：① 匿名浏览（分类/岗位详情/评价 200，报名查询正确 401）② 打工人 11 项（报名雪花单号字符串、我的报名字段、上传、发布带图动态、我的动态、关注、关注流游标、点赞、签到、改资料、评雇主）③ 雇主 4 项（看报名 workerName 来自 DB、通过、重复审核拦截、评工人 + 评价列表 2 条）。**Phase 6 前端联调验证完成。**

- Phase 6 第 9 步：生产构建 + nginx 部署 —— `npm run build` 产出 `frontend/dist`（hash 指纹 + gzip，约 3.9s；dist 由 frontend/.gitignore 忽略不入库）；新增 `deploy/`（`nginx.conf` 生产配置 + `README.md` 部署说明）；安装真实 nginx：从 nginx.org 下载官方 Windows 便携版 1.26.2，解压到 `tools/nginx-1.26.2/`（无需管理员/无需装服务，`tools/` 已加入 .gitignore 不入库），配置监听 8088（本机 80 被系统进程 PID4 占用），`nginx -t` 校验通过并启动（master/worker 双进程）。真实 nginx 部署 8 项验证全通过：根路径 `/`、深链 `/job/8` `/feed` 均回退 index.html、静态资源 JS、反代 GET `/api`、POST `/api/user/code`（方法+query 透传）、`/uploads` 图片 image/png、分类列表经反代返回真实数据。**Phase 6 前端全部完成，项目收尾。**
- Phase 6 收尾：`feat/frontend`（Phase 6 全部 19 个提交）以 `--no-ff` 合回 `main`（合并提交 `merge: Phase 6 前端（Vue3+Vite+Vant4）合回 main`），`main` 已推送远端，本地 + 远端 `feat/frontend` 分支均已删除。**项目整体完成。**
- 技术缺口补齐（Redisson RLock）：审查发现 `RedissonClient` 只被布隆过滤器使用、`RLock` 从未接入。补齐两处 —— ① `CacheClient.queryWithMutex` 缓存击穿互斥锁由自研 setnx 换成 `RLock`（可重入 + watchdog 自动续期，修复「unlock 直接 delete 可能误删」缺陷）；② `audit` 审核加业务锁 `RLock`（对标黑马「一人一单」，防并发重复审核，锁粒度=单条报名记录）。`queryWithLogicalExpire` 因「主线程抢锁+异步释放」保留自研锁（RLock 要求同线程）。编译 + 启动 + 岗位详情 + audit 锁路径验证通过。
- 技术缺口补齐（RabbitMQ 发布确认）：审查发现报名 `convertAndSend` 是 fire-and-forget、无 confirm/return，消息投递失败会「静默丢消息」（名额泄漏 + 用户卡死）。补齐 —— ① `application.yml` 启用 `publisher-confirm-type: correlated` + `publisher-returns`；② `RabbitConfig` 定义 `RabbitTemplate` bean（setMandatory + confirm 回调记 orderId + return 回调 NO_ROUTE 告警）；③ `apply()` 携带 `CorrelationData(orderId)`。异常演练验证：错误路由 key 触发 return 回调 `replyCode=312 NO_ROUTE` + 完整消息体。

- 全面功能验证 + 分类数据修复：对 8 大模块（登录/岗位/报名/关注/Feed/签到/互评/上传 + 接口文档/前端 nginx）端到端验证约 87 项全部通过，核心链路（登录→发岗→Lua 秒杀→MQ 落单→审核→互评）闭环正常。发现并修复 1 个数据 bug：`tb_job_category` 种子数据历史被错误字符集导入导致乱码（双重编码，非代码 bug），已用 pymysql 按 db.sql 种子数据改正 6 条分类名，`/job-category/list` 及 nginx 反代均返回正确中文。
- 前端体验修复（导航 TabBar）：用户反馈「个人页退不回主页」。根因：前端无底部 TabBar，`Profile` 被当一级页设计却无回首页入口（二级页其实都已带返回箭头）。修复 —— ① `App.vue` 加 `van-tabbar`（首页/动态/我的三 tab，route 模式按当前路径高亮，`showTabbar` 仅 `/home` `/feed` `/profile` 显示，二级页仍用顶栏返回箭头）；② `Feed.vue` 顶栏去掉返回箭头（与首页/我的对齐为 tab 一级页）；③ `Profile.vue` 加 `padding-bottom: 80px` 让出 TabBar 空间；④ `Home.vue`/`Feed.vue` 发布悬浮按钮 `bottom` 40px→70px（避免被 TabBar 压住）。`npm run build` 通过（nginx root 直指 dist，即时生效）。
- 界面美化（松蓝 · 清爽专业）：用户选定「松蓝」配色方向，做全局视觉升级 —— ① 新建 `src/styles/theme.css` 定义品牌色 token（主色 #2563eb）+ 覆盖 Vant CSS 变量（主色/文字/背景/边框/功能色），`main.js` 在 vant css 后引入；② `App.vue` body 用 token 色；③ `Home.vue` 加首页 hero 品牌区（松蓝渐变）+ 卡片圆角 12px + 轻阴影；④ `Login.vue` logo 松蓝渐变、`Profile.vue` 用户卡改松蓝渐变头部（角色 tag 白半透明）；⑤ 统一 `Home/Feed/Profile/JobDetail/MyBlogs/MyApplications/EmployerApplications` 卡片（圆角 12px + 阴影 + 文字 token 色）；⑥ 评分星色改主题色、薪资红用 token 危险色。`npm run build` 通过。
- 造种子数据（可复用脚本）：新增 `tools/seed_data.py`（确定性可复现，`python tools/seed_data.py` 一键重建），清空测试残留并造真实零工场景演示数据 —— ① 数据规模：用户 50（雇主 14 + 打工人 36，含本人账号 13581043338 昵称「阿禾」）、岗位 40（6 分类各 5~6 上架 + 每分类 1 个历史下架）、报名 80（雪花 id，状态 0待确认/1已录用/2已完成/3已取消 混合）、互评 48、关注 26、动态 30；② 同步预热 Redis（FLUSHDB 后重写）：岗位 GEO（geo:job:{分类}）、报名名额（apply:stock:{岗位} = headcount - 已占）、关注集合（follows:{用户}）、Feed 收件箱（feed:{用户}，按关注滚入最近 3 条）；③ 数据完整性校验全过（无非法外键引用、无重复报名、雪花 id 唯一为正、无自关注）；④ 关键坑修复：岗位详情走布隆过滤器（启动预载），故造完数据须**重启后端**让布隆重新预载，否则详情报「岗位不存在」——已重启并验证详情/附近/Feed 全通；⑤ 自审发现「6 个下架岗位全落在发传单分类」的分布 bug，改为每分类下架 1 个。端到端验证：分类 6 个、岗位列表 34 上架（6/6/6/6/5/5）、详情 200、附近搜索按距离排序 6 个、关注流 5 条、本人账号 1 条报名，全部正常。
- 修复两个交互缺陷：① 「只看附近」点开没岗位 —— 根因：种子数据 40 个岗位坐标全在北京，而「附近」用浏览器真实定位 + 默认 5km 半径，用户不在北京导致空列表。按用户选择改「固定演示坐标」：`Home.vue` 去掉 `navigator.geolocation`，改用固定北京市中心 (116.4074, 39.9042) 为圆心、半径 20km，任何位置都能看到附近岗位并按距离排序（真实项目改回 geolocation，代码留注释）。② 发布动态看不到 —— 根因：「动态」页是关注流（只读 feed:{自己} 收件箱），发布时只推粉丝、不推自己。修复：`BlogServiceImpl.publish` 额外把新动态推进作者自己收件箱，`Feed.vue` 对自己的动态隐藏「关注」按钮。`mvn compile` + `npm run build` 通过，附近搜索 curl 实测返回按距离排序岗位（6~13km）。**后端改动需重启、前端需硬刷新**。

### 🔄 进行中
- 无（Phase 0–6 全部完成）。

### ⏭ 下一步
- 无（项目功能 + 前端 + 种子数据全部完成，可自由体验；如继续可考虑：数据统计看板、前端骨架屏/懒加载、Docker 一键化后端部署等）。

---

## 7. 恢复约定

- **对话中断后**：先读本文件「当前进度」和「下一步」，从断点继续，不重做已完成部分。
- **每完成一步**：立即更新第 6 节，并 `git commit`（写清做了什么）。
- **环境信息**：MySQL/Redis/RabbitMQ 实际连接信息放 `application.yml`（占位符，运行时填），不写进本文档。
