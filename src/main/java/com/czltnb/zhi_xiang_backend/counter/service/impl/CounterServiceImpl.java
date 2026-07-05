package com.czltnb.zhi_xiang_backend.counter.service.impl;

import com.czltnb.zhi_xiang_backend.counter.event.CounterEvent;
import com.czltnb.zhi_xiang_backend.counter.event.CounterEventProducer;
import com.czltnb.zhi_xiang_backend.counter.schema.BitmapShard;
import com.czltnb.zhi_xiang_backend.counter.schema.CounterKeys;
import com.czltnb.zhi_xiang_backend.counter.schema.CounterSchema;
import com.czltnb.zhi_xiang_backend.counter.service.CounterService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class CounterServiceImpl implements CounterService {

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<Long> toggleScript;
    private final CounterEventProducer eventProducer;
    private final ApplicationEventPublisher eventPublisher; //用于给Spring容器内的@EventListener方法推送事件
    private final RedissonClient redisson;

    @Value("${counter.rebuild.rate.permits:3}")
    private int ratePermits;
    @Value("${counter.rebuild.rate.window-seconds:10}")
    private int rateWindowSeconds;
    @Value("${counter.rebuild.backoff.base-ms:500}")
    private long backoffBaseMs;
    @Value("${counter.rebuild.backoff.max-ms:30000}")
    private long backoffMaxMs;

    private static final String TOGGLE_LUA = """
            local bmKey = KEYS[1]
            local offset = tonumber(ARGV[1])
            local op = ARGV[2]
            local prev = redis.call('GETBIT',bmKey,offset)
            if op == 'add' then 
                if prev == 1 then return 0 end
                redis.call('SETBIT',bmKey,offset,1)
                return 1
            elseif op == 'remove' then
                if prev == 0 then return 0 end
                redis.call('SETBIT',bmKey,offset,0)
                return 1
            end
            return -1
            """;

    public CounterServiceImpl(StringRedisTemplate redis,CounterEventProducer eventProducer,
                              ApplicationEventPublisher eventPublisher, RedissonClient redisson) {
        this.redis = redis;
        this.eventProducer = eventProducer;
        this.eventPublisher = eventPublisher;
        this.redisson = redisson;
        this.toggleScript = new DefaultRedisScript<>();
        this.toggleScript.setResultType(Long.class);   // 告诉 Redis 客户端：这个脚本执行完返回的是 Long 类型
        this.toggleScript.setScriptText(TOGGLE_LUA);    // 把 Lua 脚本内容塞进去
    }

    @Override
    public boolean like(String entityType,String entityId,long userId) {
        return toggle(entityType,entityId,userId,"like", CounterSchema.IDX_LIKE,true);
    }
    @Override public boolean unlike(String entityType, String entityId, long userId) {
        return toggle(entityType, entityId, userId, "like", CounterSchema.IDX_LIKE, false);
    }
    @Override public boolean fav(String entityType, String entityId, long userId) {
        return toggle(entityType, entityId, userId, "fav", CounterSchema.IDX_FAV, true);
    }
    @Override public boolean unfav(String entityType, String entityId, long userId) {
        return toggle(entityType, entityId, userId, "fav", CounterSchema.IDX_FAV, false);
    }

    private boolean toggle(String etype,String eid,long uid,String metric,int idx,boolean add) {
        long chunk = BitmapShard.chunkOf(uid);
        long bit = BitmapShard.bitOf(uid);
        String bmKey = CounterKeys.bitmapKey(metric,etype,eid,chunk);
        Long changed = redis.execute(toggleScript, List.of(bmKey),
                String.valueOf(bit),add ? "add" : "remove");
        boolean ok = changed == 1L;
        if (ok) {
            int delta = add ? 1 : -1;
            eventProducer.publish(CounterEvent.of(etype,eid,metric,idx,uid,delta));
            eventPublisher.publishEvent(CounterEvent.of(etype,eid,metric,idx,uid,delta));
        }
        return ok;
    }

    /**
     * 获取实体计数汇总 SDS
     * 若缺失/结构异常，1.会触发基于位图的「事实重建」，2.并「清理」对应聚合字段
     * rebuild 重建流程大致是：
     * 1. 判断是否处于指数退避期
     * 2. 通过限流器控制单位时间内的重建次数
     * 3. 尝试获取分布式锁
     * 4. 由持锁者扫描所有 Bitmap 分片并执行BITCOUNT
     * 5. 把统计结果回写到 SDS
     * 6. 清理对应聚合桶字段，避免重复折叠
     * 7. 成功后重置退避状态
     */
    public Map<String,Long> getCounts(String entityType,String entityId,List<String> metrics) {
        String sdsKey = CounterKeys.sdsKey(entityType,entityId);
        int expectedLen = CounterSchema.SCHEMA_LEN * CounterSchema.FIELD_SIZE;
        byte[] raw = getRaw(sdsKey);
        boolean needRebuild = (raw == null || raw.length != expectedLen);
        Map<String,Long> result = new LinkedHashMap<>();

        if (needRebuild) {
            log.info("计数汇总不存在，需要重建");
            // 限流与指数退避：避免在热点实体上触发重建风暴
            if (inBackoff(entityType,entityId)) { //处于退避期，存全0值
                for(String m : metrics) {
                    result.put(m,0L); //降级返回，先让接口正常返回、不报错、不阻塞等锁
                }
                return result;
            }
            
            if(!allowedByRateLimiter(entityType,entityId)) {
                escalateBackoff(entityType, entityId);
                for (String m : metrics) {
                    result.put(m, 0L);
                }
                return result;
            }

            String lockKey = String.format("lock:sds-rebuild:%s:%s", entityType, entityId);

            RLock lock = redisson.getLock(lockKey);
            boolean locked = false;

            try {
                // 使用 Redisson 看门狗机制：不指定租期，自动续约（由 Redisson 的 lockWatchdogTimeout 控制）
                locked = lock.tryLock(0L, TimeUnit.MILLISECONDS);
                if (!locked) {
                    escalateBackoff(entityType, entityId);
                    for (String m : metrics) {
                        result.put(m, 0L);
                    }
                    return result;
                }
                // 依据位图分片统计真实计数（仅由持锁者执行重建）
                byte[] newSds = new byte[expectedLen];
                //rebuildFields:一个空列表,后面用来记录"这次重建到底动了哪些字段的下标"(为下一步"清理聚合桶"做准备)
                List<String> rebuildFields = new ArrayList<>();
                for (String m : metrics){
                    Integer idx = CounterSchema.NAME_TO_IDX.get(m);
                    if (idx == null) {
                        continue;
                    }
                    long sum = bitCountShardsPipelined(m, entityType, entityId);
                    //把这个数字按正确的偏移量写进新数组
                    writeInt32BE(newSds, idx * CounterSchema.FIELD_SIZE, sum);
                    result.put(m, sum);
                    //记录"动过哪些字段"
                    rebuildFields.add(String.valueOf(idx));
                }
                // 回写SDS并清理聚合桶，避免重复加算
                setRaw(sdsKey,newSds);
                if (!rebuildFields.isEmpty()) {
                    String aggKey = CounterKeys.aggKey(entityType,entityId);
                    //这次重建覆盖了哪些字段,就把聚合桶里对应字段的残留增量清掉哪些",避免下次定时任务折叠时重复计算。
                    redis.opsForHash().delete(aggKey,rebuildFields.toArray());
                }
                resetBackoff(entityType, entityId);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                escalateBackoff(entityType,entityId);
                for (String m : metrics) {
                    result.put(m, 0L);
                }
                return result;
            } finally {
                if (locked) {
                    try {
                        lock.unlock();
                    } catch (Exception ignore) {
                    }
                }
            }
        } else{
            for (String m : metrics) {
                Integer idx = CounterSchema.NAME_TO_IDX.get(m);
                if (idx == null) {
                    continue;
                }

                int off = idx * CounterSchema.FIELD_SIZE;
                long val = readInt32BE(raw, off); // 大端读取单段 32 位值
                result.put(m, val);
            }
        }
        return result;
    }

    private byte[] getRaw(String key) {
        return redis.execute((RedisCallback<byte[]>) connection ->
                connection.stringCommands().get(key.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * 是否处于指数退避期：期间跳过重建并返回降级结果。
     */
    private boolean inBackoff(String entityType,String entityId) {
        String bKey = String.format("backoff:sds-rebuild:until:%s:%s",entityType,entityId);
        RBucket<Long> bucket = redisson.getBucket(bKey);
        Long until = bucket.get();

        //System.currentTimeMillis() < until：拿"当前时间"和"允许重建的截止时间"比较，
        //如果现在还没到那个时间点，说明还在冷静期内，返回 true（表示"你现在处于退避期，别去重建了"）。
        return until != null && System.currentTimeMillis() < until;
    }

    /**
     * 限流判断：单位窗口内可重建次数，防止抖动与风暴。
     */
    private boolean allowedByRateLimiter(String entityType,String entityId) {
        String rlKey = String.format("rl:sds-rebuild:%s:%s",entityType,entityId);
        RRateLimiter limiter = redisson.getRateLimiter(rlKey);

        limiter.trySetRate(RateType.OVERALL,ratePermits, Duration.ofSeconds(rateWindowSeconds));

        return limiter.tryAcquire();
    }

    /**
     * 增加退避级别并设置下次允许尝试的时间（指数递增，封顶）。
     */
    private void escalateBackoff(String entityType,String entityId) {
        String eKey = String.format("backoff:sds-rebuild:exp:%s:%s",entityType,entityId);
        String uKey = String.format("backoff:sds-rebuild:until:%s:%s",entityType,entityId);

        RBucket<Integer> expB = redisson.getBucket(eKey);
        RBucket<Long> untilB = redisson.getBucket(uKey);
        Integer exp = expB.get();

        int nextExp = Math.min(exp == null ? 0 : exp + 1,10);
        long delay = Math.min(backoffBaseMs * (1L << nextExp),backoffMaxMs); //指数级增加时延
        long until = System.currentTimeMillis() + delay;

        // 设置过期时间，避免长时间残留
        expB.set(nextExp);
        untilB.set(until,Duration.ofMillis(delay + 1000));
    }

    private long bitCountShardsPipelined(String metric,String etype,String eid) {
        String pattern = String.format("bm:%s:%s:%s:*",metric,etype,eid);
        Set<String> keys =  redis.keys(pattern);//通配符 * 匹配"所有分片编号"
        if (keys.isEmpty()) return 0L;

        //使用管道减少网络往返
        List<Object> res = redis.executePipelined((RedisCallback<Object>) connection -> {
            for (String k : keys) {
                connection.stringCommands().bitCount(k.getBytes(StandardCharsets.UTF_8));
            }
            return null;
        });

        long sum = 0L;
        for (Object o : res) {
            if (o instanceof Number n) {
                sum += n.longValue();
            }
        }
        return sum;
    }

    /**
     * 以大端序写入 32 位无符号整型（截断到 0~2^32-1）。
     * "大端序(Big Endian)":一个32位整数由4个字节组成,"大端序"规定——高位字节存在数组的前面(小地址),低位字节存在后面(大地址)。这是最符合人类直觉的写法,就跟我们平时写十进制数字"从左到右,高位在前"是一个道理。
     *
     * 假设 val = 300(比如收藏数是300),off = 8。
     * 300 的二进制是 100101100,补齐到32位:
     * 00000000 00000000 00000001 00101100
     * buf[8]  = (300 >>> 24) & 0xFF = 0
     * buf[9]  = (300 >>> 16) & 0xFF = 0
     * buf[10] = (300 >>> 8)  & 0xFF = 1     ← 因为 300/256=1余44,所以第3字节是1
     * buf[11] = 300 & 0xFF          = 44    ← 300 - 1*256 = 44
     * 结果:buf[8..11] = [0, 0, 1, 44]
     * 验证一下:大端序还原回去就是 0*256³ + 0*256² + 1*256¹ + 44*256⁰ = 256 + 44 = 300,正确。
     */
    private static void writeInt32BE(byte[] buf,int off,long val) {
        long n = Math.max(0,Math.min(val,0xFFFF_FFFFL));
        buf[off] = (byte) ((n >>> 24) & 0xFF);
        buf[off + 1] = (byte) ((n >>> 16) & 0xFF);
        buf[off + 2] = (byte) ((n >>> 8) & 0xFF);
        buf[off + 3] = (byte) (n & 0xFF);
    }
    private static long readInt32BE(byte[] buf,int off) {
        long n = 0;
        for (int i=0;i<4;i++) {
            n = (n<<8) | (buf[off + i] & 0xFFL);//0xFF转化为二进制是8位bit
        }
        return n;
    }
    /**
     * 写入 SDS 原始字节（覆盖式写）。
     */
    private void setRaw(String key, byte[] val) {
        redis.execute((RedisCallback<Void>) connection -> {
            connection.stringCommands().set(key.getBytes(StandardCharsets.UTF_8), val);
            return null;
        });
    }
    /**
     * 重置退避状态（成功重建后）
     */
    private void resetBackoff(String entityType,String entityId) {
        String eKey = String.format("backoff:sds-rebuild:exp:%s:%s",entityType,entityId);
        String uKey = String.format("backoff:sds-rebuild:until:%s:%s", entityType, entityId);

        try {
            redisson.getBucket(eKey).delete();
        } catch (Exception ignore) {}

        try {
            redisson.getBucket(uKey).delete();
        } catch (Exception ignore) {}
    }
}
