-- 滑动窗口限流脚本（Redis Lua 保证「清过期记录 → 统计 → 写入」原子性）
--
-- 思路：用 ZSet 存每次请求，score = 请求时间戳(ms)，member = 唯一标识（时间戳+随机数，防同毫秒覆盖）。
-- 每次请求先把窗口外的旧记录删掉，再统计窗口内请求数：未超限则写入本次并返回 1，否则返回 0。
--
-- 参数：
--   KEYS[1] = 限流 key（如 rate:limit:com.xxx.Controller.method 或 +维度后缀）
--   ARGV[1] = window    滑动窗口大小（毫秒，由切面把注解秒数 × 1000 传入）
--   ARGV[2] = limit     窗口内允许的最大请求数
--   ARGV[3] = now       当前时间戳（毫秒）
--   ARGV[4] = member    本次请求唯一标识（时间戳-随机数，避免同毫秒写入互相覆盖）
--
-- 返回值：
--   1 = 放行（已写入本次请求）
--   0 = 超限（拒绝）

local key = KEYS[1]
local windowMs = tonumber(ARGV[1])
local limit = tonumber(ARGV[2])
local now = tonumber(ARGV[3])
local member = ARGV[4]

-- 1. 删除窗口外的旧记录（score <= now - windowMs 的都已滑出窗口）
redis.call('ZREMRANGEBYSCORE', key, 0, now - windowMs)

-- 2. 统计窗口内剩余请求数
local current = redis.call('ZCARD', key)

-- 3. 未超限：写入本次请求 + 设置过期（比窗口稍长，防 key 无限增长）；超限返回 0
if (current < limit) then
    redis.call('ZADD', key, now, member)
    redis.call('EXPIRE', key, math.floor(windowMs / 1000) + 1)
    return 1
end

return 0
