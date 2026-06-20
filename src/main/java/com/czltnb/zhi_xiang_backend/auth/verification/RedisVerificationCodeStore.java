package com.czltnb.zhi_xiang_backend.auth.verification;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

@Component
public class RedisVerificationCodeStore implements VerificationCodeStore{

    private static final String FIELD_CODE = "code";
    private static final String FIELD_MAX_ATTEMPTS = "maxAttempts";
    private static final String FIELD_ATTEMPTS = "attempts";

    private final StringRedisTemplate redisTemplate;

    public RedisVerificationCodeStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 保存验证码到 Redis Hash，并设置 TTL
     * @param scene 场景名称
     * @param identifier 标识（手机号或者邮箱）
     * @param code
     * @param ttl
     * @param maxAttempts
     */
    @Override
    public void saveCode(String scene, String identifier, String code, Duration ttl,int maxAttempts) {
        String key = buildKey(scene,identifier);
        HashOperations<String,String,String> ops = redisTemplate.opsForHash();
        try {
            ops.put(key,FIELD_CODE,code);
            ops.put(key,FIELD_MAX_ATTEMPTS,String.valueOf(maxAttempts));
            ops.put(key,FIELD_ATTEMPTS,"0");
            redisTemplate.expire(key,ttl);
        } catch (DataAccessException ex) {
            throw new RedisSystemException("Failed to save verification code", ex);
        }
    }

    @Override
    public VerificationCheckResult verify(String scene,String identifier,String code) {
        String key = buildKey(scene, identifier);
        HashOperations<String, String, String> ops = redisTemplate.opsForHash();
        Map<String, String> data = ops.entries(key);

        if (data.isEmpty()) {
            return new VerificationCheckResult(VerificationCodeStatus.NOT_FOUND, 0, 0);
        }

        /**
         * 1.检验验证码code是否达到最大重试次数
         */
        String storedCode = data.get(FIELD_CODE);
        int maxAttempts = parseInt(data.get(FIELD_MAX_ATTEMPTS), 5);
        int attempts = parseInt(data.get(FIELD_ATTEMPTS), 0);

        if (attempts >= maxAttempts) {
            return new VerificationCheckResult(VerificationCodeStatus.TOO_MANY_ATTEMPTS, attempts, maxAttempts);
        }

        /**
         * 2.检验验证码是否和预期存储的相同
         */
        if (Objects.equals(storedCode, code)) {
            redisTemplate.delete(key);  // 成功后删除，防止重用
            return new VerificationCheckResult(VerificationCodeStatus.SUCCESS, attempts, maxAttempts);
        }

        /**
         * 3.验证码输入错误，重试次数+1,并检验当前是否达到最大重试次数
         */
        int updatedAttempts = attempts + 1;
        ops.put(key, FIELD_ATTEMPTS, String.valueOf(updatedAttempts));
        if (updatedAttempts >= maxAttempts) {
            redisTemplate.expire(key, Duration.ofMinutes(30));
            return new VerificationCheckResult(VerificationCodeStatus.TOO_MANY_ATTEMPTS, updatedAttempts, maxAttempts);
        }
        return new VerificationCheckResult(VerificationCodeStatus.MISMATCH, updatedAttempts, maxAttempts);

    }

    private static String buildKey(String scene, String identifier) {
        return "auth:code:%s:%s".formatted(scene, identifier);
    }

    @Override
    public void invalidate(String scene, String identifier) {
        redisTemplate.delete(buildKey(scene, identifier));
    }

    private static int parseInt(String value, int defaultValue) {
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

}
