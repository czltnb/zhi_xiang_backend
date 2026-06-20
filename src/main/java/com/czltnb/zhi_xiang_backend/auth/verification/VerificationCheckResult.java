package com.czltnb.zhi_xiang_backend.auth.verification;

/**
 * 验证码校验结果。
 * 包含状态（成功/失败/过期/错误/尝试过多）和尝试次数统计信息，提供便捷成功判断
 */
public record VerificationCheckResult(
        VerificationCodeStatus status,
        int attempts,
        int maxAttempts
) {
    public boolean isSuccess() {
        return status == VerificationCodeStatus.SUCCESS;
    }
}
