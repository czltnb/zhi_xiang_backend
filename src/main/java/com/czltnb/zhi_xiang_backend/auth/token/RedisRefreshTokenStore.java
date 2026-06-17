package com.czltnb.zhi_xiang_backend.auth.token;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Redis 的 Refresh Token 存储实现。
 *
 * <p>Key 设计：
 * <ul>
 *   <li>{@code refresh_token:{tokenHash}} → user_id（String），TTL = token 有效期</li>
 *   <li>{@code refresh_token:user:{userId}} → Set&lt;tokenHash&gt;，用于批量吊销</li>
 * </ul>
 * </p>
 */
@Component
@RequiredArgsConstructor
public class RedisRefreshTokenStore implements RefreshTokenStore {

    private final StringRedisTemplate redis;

    private static final String TOKEN_PREFIX = "refresh_token:";
    private static final String USER_PREFIX = "refresh_token:user:";

    @Override
    public void save(String tokenHash, Long userId, long ttlSeconds) {
        String tokenKey = TOKEN_PREFIX + tokenHash;
        String userKey = USER_PREFIX + userId;
        // 1. token -> userId 映射，带过期时间
        redis.opsForValue().set(tokenKey, String.valueOf(userId), ttlSeconds, TimeUnit.SECONDS);
        // 2. userId -> 该用户所有token集合（Set）
        redis.opsForSet().add(userKey, tokenHash);
        // 3. 给用户token集合也设置相同过期时间
        redis.expire(userKey, ttlSeconds, TimeUnit.SECONDS);
        /**
         * 设计目的：
         * 单点登录 / 多端登录管理：通过 userKey 这个 Set，可以快速查出某个用户名下所有登录 Token；
         * 统一过期：用户的 Token 集合和单个 Token 同步过期，避免数据库残留无效关联数据。
         */
    }

    @Override
    public Optional<Long> findByTokenHash(String tokenHash) {
        String userId = redis.opsForValue().get(tokenHash);
        return Optional.ofNullable(userId).map(Long::valueOf);
    }

    @Override
    public void revokeByUserId(Long userId) {
        String userKey = USER_PREFIX + userId;
        Set<String> hashes = redis.opsForSet().members(userKey);
        if (hashes != null && !hashes.isEmpty()) {
            hashes.forEach(hash -> redis.delete(TOKEN_PREFIX + hash));
        }
        redis.delete(userKey);
    }
}

