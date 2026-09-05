package com.linggong.utils;

import com.linggong.entity.Job;
import com.linggong.mapper.JobMapper;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 岗位布隆过滤器：缓存穿透的第一道防线。
 *
 * <p>应用启动时预载所有岗位 id；查询前先用 {@link #mightContain} 判断，
 * 一定不存在就直接返回，避免恶意请求用不存在的 id 反复打库。
 *
 * <p>布隆过滤器可能「误判」（把不存在判成存在），但不会「漏判」（存在的一定判存在），
 * 所以误判场景兜底交给缓存空对象（CacheClient）。
 */
@Slf4j
@Component
public class JobBloomFilter implements ApplicationRunner {

    /** 布隆过滤器在 Redis 中的 key */
    private static final String BLOOM_KEY = "job:bloom";
    /** 预计元素数量 */
    private static final long EXPECTED_INSERTIONS = 10000L;
    /** 误判率 */
    private static final double FALSE_PROBABILITY = 0.03;

    private final RedissonClient redissonClient;
    private final JobMapper jobMapper;

    /** 布隆过滤器实例，初始化失败时为 null（降级为不使用布隆过滤） */
    private RBloomFilter<String> bloomFilter;

    public JobBloomFilter(RedissonClient redissonClient, JobMapper jobMapper) {
        this.redissonClient = redissonClient;
        this.jobMapper = jobMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            RBloomFilter<String> bf = redissonClient.getBloomFilter(BLOOM_KEY);
            // tryInit：第一次真正初始化，后续启动拿到的是已存在的布隆过滤器
            bf.tryInit(EXPECTED_INSERTIONS, FALSE_PROBABILITY);
            List<Job> jobs = jobMapper.selectList(null);
            for (Job job : jobs) {
                bf.add(String.valueOf(job.getId()));
            }
            this.bloomFilter = bf;
            log.info("岗位布隆过滤器初始化完成，预载 {} 个岗位 id", jobs.size());
        } catch (Exception e) {
            // 初始化失败降级：不阻塞应用启动，查询时放行由空对象兜底
            log.error("岗位布隆过滤器初始化失败，降级为不使用布隆过滤", e);
            this.bloomFilter = null;
        }
    }

    /**
     * 判断 id 是否可能存在（可能误判「不存在」为「存在」，但不会漏判）。
     * 布隆过滤器未初始化（降级）时直接放行。
     */
    public boolean mightContain(Long id) {
        return bloomFilter == null || bloomFilter.contains(String.valueOf(id));
    }

    /**
     * 新增岗位后把 id 加入布隆过滤器。
     */
    public void add(Long id) {
        if (bloomFilter != null) {
            bloomFilter.add(String.valueOf(id));
        }
    }
}
