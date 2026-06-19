package com.czltnb.zhi_xiang_backend.auth.verification;

/**
 * 验证码校验状态。
 */
public enum VerificationCodeStatus {
    SUCCESS,
    NOT_FOUND,
    EXPIRED,
    MISMATCH,
    TOO_MANY_ATTEMPTS
}
