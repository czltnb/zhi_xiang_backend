package com.czltnb.zhi_xiang_backend.auth.token;

import java.time.Duration;

/**
 * 刷新令牌白名单存储接口。
 */
public interface RefreshTokenStore {

    void storeToken(long userId, String tokenId, Duration ttl);

    boolean isTokenValid(long userId, String tokenId);

    void revokeToken(long userId, String tokenId);

    void revokeAll(long userId);
}
