package com.czltnb.zhi_xiang_backend.auth.verification;

import java.time.Duration;

/**
 * 验证码存储接口 — 抽象保存、校验、失效操作。
 */
public interface VerificationCodeStore {

    void saveCode(String scene, String identifier, String code, Duration ttl, int maxAttempts);

    VerificationCheckResult verify(String scene, String identifier, String code);

    void invalidate(String scene, String identifier);
}
