package com.czltnb.zhi_xiang_backend.auth.token;

import java.util.Optional;

/**
 * Refresh Token 存储抽象。
 */
public interface RefreshTokenStore {
    /** 保存 refresh token 关联的用户 ID，指定过期时间（秒） */
    void save(String tokenHash,Long userId,long ttlSeconds);

    /** 查询 token 关联的用户 ID，不存在/过期返回 empty */
    Optional<Long> findByTokenHash(String tokenHash);

    /** 吊销某用户的所有 refresh token */
    void revokeByUserId(Long userId);
}
