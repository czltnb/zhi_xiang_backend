package com.czltnb.zhi_xiang_backend.auth.verification;

/**
 * 发送验证码结果。
 */
public record SendCodeResult(
        String identifier,
        VerificationScene scene,
        int expireSeconds
) {
}
