-- 岗位报名秒杀脚本（Redis Lua 保证「查名额 → 一人一单 → 扣名额 → 记标记」原子性）
--
-- 参数（ARGV，通过 StringRedisTemplate.execute 传入）：
--   ARGV[1] = jobId    岗位 id
--   ARGV[2] = workerId 打工人 id
--
-- 返回值：
--   0 = 报名成功（已扣名额 + 记录一人一单标记）
--   1 = 名额不足
--   2 = 重复报名（一人一单）
--
-- 注意：key 前缀 apply:stock: / apply:order: 需与 Java 常量 RedisConstants.APPLY_STOCK_KEY / APPLY_ORDER_KEY 保持一致。

local jobId = ARGV[1]
local workerId = ARGV[2]

-- 剩余名额 key（发布岗位时预热，value 为剩余名额数字）
local stockKey = 'apply:stock:' .. jobId
-- 已报名集合 key（Set，存已报名 workerId）
local orderKey = 'apply:order:' .. jobId

-- 1. 判断名额是否充足
if (tonumber(redis.call('get', stockKey)) <= 0) then
    return 1
end

-- 2. 判断是否已报名（一人一单）
if (redis.call('sismember', orderKey, workerId) == 1) then
    return 2
end

-- 3. 扣名额 + 记录已报名标记
redis.call('incrby', stockKey, -1)
redis.call('sadd', orderKey, workerId)
return 0
