package com.czltnb.zhi_xiang_backend.cache.hotkey;

import com.czltnb.zhi_xiang_backend.cache.config.CacheProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * HotKeyDetector 不是缓存本身,而是挂在这条读链路旁边的一个计数器 + 决策器:每次有内容被读到,就记一笔;
 * 当某个内容足够热,它就出面把这个内容相关缓存的 TTL(过期时间)往后延。
 * 所以它的输入是"访问",输出只有一个:一个该续多久的 TTL 数字
 */
@Component
public class HotKeyDetector {

    public enum Level { NONE,LOW,MEDIUM,HIGH }

    private final CacheProperties properties;
    private final Map<String,int[]> counters = new ConcurrentHashMap<>();
    private final AtomicInteger current = new AtomicInteger();
    private final int segments; //每个窗口划分的段数

    public HotKeyDetector(CacheProperties properties) {
        this.properties = properties;
        int segSeconds = properties.getHotKey().getSegmentSeconds();
        int winSeconds = properties.getHotKey().getWindowSeconds();
        this.segments = Math.max(1, winSeconds / Math.max(1, segSeconds));
    }

    /**
     * record 是这个热度探测器的"记一笔访问"的入口
     * 每当某个内容被读到一次,就调它一下,把这次访问累加到当前时间段的计数里。
     * @param key
     */
    public void record(String key) {
        //computeIfAbsent 在 counters 这个 Map 里取出这个 key 对应的计数数组。如果这个 key 第一次出现(还没有数组),就新建一个长度为 segments(默认 6)的全零 int[] 放进去;已经有了就直接拿到。
        int[] arr = counters.computeIfAbsent(key,k -> new int[segments]);
        arr[current.get()]++;
    }

    //计算6个segment加起来的总热度
    public int heat(String key) {
        int[] arr = counters.get(key);
        if (arr == null) return 0;
        int sum = 0;
        for (int v : arr) sum += v;
        return sum;
    }

    public Level level(String key) {
        int h = heat(key);
        if (h >= properties.getHotKey().getLevelHigh()) return Level.HIGH;
        if (h >= properties.getHotKey().getLevelMedium()) return Level.MEDIUM;
        if (h >= properties.getHotKey().getLevelLow()) return Level.LOW;
        return Level.NONE;
    }

    public int ttlForPublic(int baseTtlSeconds,String key) {
        return baseTtlSeconds + extendSeconds(level(key));
    }

    public int ttlForMine(int baseTtlSeconds, String key) {
        return baseTtlSeconds + extendSeconds(level(key));
    }

    private int extendSeconds(Level l) {
        return switch (l) {
            case HIGH -> properties.getHotKey().getExtendHighSeconds();
            case MEDIUM -> properties.getHotKey().getExtendMediumSeconds();
            case LOW -> properties.getHotKey().getExtendLowSeconds();
            default -> 0;
        };
    }

    @Scheduled(fixedRateString = "${cache.hotkey.segment-seconds:10}000")
    public void rotate() {
        int next = (current.get() + 1) % segments;
        current.set(next);
        for (int[] arr : counters.values()) {
            arr[next] = 0;
        }
    }

    public void reset(String key) {
        int[] arr = counters.get(key);
        if (arr != null) Arrays.fill(arr, 0);
    }
}
