package com.czltnb.zhi_xiang_backend.counter.schema;

/**
 * 位图分片配置与帮助函数。
 * 采用固定分片大小，避免单键因用户ID偏移过大而膨胀。
 * 把一个巨大的位图，切成很多小的"分片(chunk)"，每片固定大小（这里是 32768 位 = 4KB）。
 *
 * 这其实就是经典的取商 + 取余做法，跟数组分页、哈希分桶思路一样。
 * 举例说明
 * 假设 CHUNK_SIZE = 32768，某用户 userId = 100000：
 * chunkOf(100000) = 100000 / 32768 = 3       // 第 3 个分片（从0开始数）
 * bitOf(100000)   = 100000 % 32768 = 1808    // 在分片3内的第1808位
 * 那么实际存到 Redis 里，key 可能拼成这样：
 * SETBIT user_signin:chunk:3 1808 1
 * 可以看到，每满 32768 个用户就自动"翻页"进入下一个分片，每个分片始终固定是 4KB 大小，不会因为 userId 无限增大而让某个 key 无限膨胀。
 */
public final class BitmapShard {
    public static final int CHUNK_SIZE = 32_768; // 32768/8bit = 4096byte = 4KB

    public static long chunkOf(long userId) {
        return userId / CHUNK_SIZE;  // 算出这个用户落在第几个分片
    }

    public static long bitOf(long userId) {
        return userId % CHUNK_SIZE;  // 算出这个用户在该分片内的具体偏移
    }
}
