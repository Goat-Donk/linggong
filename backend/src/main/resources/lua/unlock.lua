-- 分布式锁原子释放脚本（对齐黑马点评原版）。
--
-- 作用：只有「当前线程标识」与锁中存的值一致时，才删除锁；
-- 用 Lua 保证「比较 + 删除」两步的原子性，消除非原子 check-then-delete 的竞态
--（A 线程比较后、删除前锁过期被 B 线程拿到，A 会误删 B 的锁）。
--
-- KEYS[1] = 锁 key（lock:{name}）
-- ARGV[1] = 当前线程标识（JVM标识-线程id）
-- 返回：1 = 删除成功；0 = 标识不一致（锁不是自己的，不删）

if (redis.call('get', KEYS[1]) == ARGV[1]) then
    return redis.call('del', KEYS[1])
end
return 0
