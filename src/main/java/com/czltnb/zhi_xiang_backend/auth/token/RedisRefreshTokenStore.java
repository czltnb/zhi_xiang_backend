package com.czltnb.zhi_xiang_backend.auth.token;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;

/**
 * 基于 Redis 的刷新令牌白名单存储。
 * <p>
 * 键空间：{@code auth:rt:{userId}:{tokenId}}，值固定为 "1"，TTL 控制过期。
 * Redis的String数据类型
 *
 * 特别注意：！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！1
 * 这个类是RefreshToken 专用会话存储，采用白名单机制必须持久化全部有效 rt；
 * AccessToken 是短期令牌，仅作废时存入临时黑名单，由拦截器直接处理，不属于该类的管理范围，所以这里只有 rt 的 Redis 键逻辑。
 */
@Component
public class RedisRefreshTokenStore implements RefreshTokenStore {

    private final StringRedisTemplate redisTemplate;

    public RedisRefreshTokenStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    //存储RefreshToken
    @Override
    public void storeToken(long userId,String tokenId,Duration ttl) {
        redisTemplate.opsForValue().set(key(userId,tokenId),"1",ttl);
    }

    @Override
    public boolean isTokenValid(long userId,String tokenId) {
        return Objects.equals("1",redisTemplate.opsForValue().get(key(userId,tokenId)));
    }

    @Override
    public void revokeToken(long userId,String tokenId) {
        redisTemplate.delete(key(userId,tokenId));
    }

    /**
     * 让指定用户名下全部刷新令牌 refreshToken 一次性全部作废，常用于改密码、退出登录、账号冻结场景。
     * @param userId
     */
    @Override
    public void revokeAll(long userId) {
        String pattern = "auth:rt:%d:*".formatted(userId); //通配符匹配
        var keys = redisTemplate.keys(pattern); //redisTemplate.keys() 是 Redis 模糊查询命令 KEYS pattern，把该用户全部 refreshToken 的 key 查出来放到集合。
        if(!keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    public static String key(long userId,String tokenId) {
        return "auth:rt:%d:%s".formatted(userId, tokenId);
    }
}
