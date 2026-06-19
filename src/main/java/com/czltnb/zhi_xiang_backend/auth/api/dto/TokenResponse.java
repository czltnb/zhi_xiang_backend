package com.czltnb.zhi_xiang_backend.auth.api.dto;

import java.time.Instant;

public record TokenResponse(
        String accessToken,
        Instant accessTokenExpiresAt,
        String refreshToken,
        Instant refreshTokenExpiresAt
) {
}
