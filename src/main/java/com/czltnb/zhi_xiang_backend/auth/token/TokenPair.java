package com.czltnb.zhi_xiang_backend.auth.token;

import java.time.Instant;

/**
 * 令牌对 — access_token + refresh_token 及其过期信息。
 */
public record TokenPair(
        String accessToken,
        Instant accessTokenExpiresAt,
        String refreshToken,
        Instant refreshTokenExpiresAt,
        String refreshTokenId   //参考RedisRefreshTokenStore类，本项目中Redis存储Token的键设计，只有refreshToken
) {
}
