package com.czltnb.zhi_xiang_backend.auth.verification;

/**
 * 验证码发送器接口。
 */
public interface CodeSender {
    void sendCode(VerificationScene scene, String identifier, String code, int expireMinutes);
}
