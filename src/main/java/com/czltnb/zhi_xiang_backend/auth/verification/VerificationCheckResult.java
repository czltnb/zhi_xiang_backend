package com.czltnb.zhi_xiang_backend.auth.verification;

/**
 * 验证码校验结果。
 */
public record VerificationCheckResult(
        boolean success,
        VerificationCodeStatus status
) {
    public static VerificationCheckResult ok() {
        return new VerificationCheckResult(true, VerificationCodeStatus.SUCCESS);
    }

    public static VerificationCheckResult failed(VerificationCodeStatus status) {
        return new VerificationCheckResult(false, status);
    }

    public boolean isSuccess() {
        return success;
    }
}
