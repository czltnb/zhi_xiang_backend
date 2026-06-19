package com.czltnb.zhi_xiang_backend.auth.api.dto;

public record AuthResponse(
        AuthUserResponse user,
        TokenResponse token
) {
}

/**
 * 登录接口统一返回外层包装对象
 * 登录成功后一次性返回两部分数据：
 * user：AuthUserResponse 用户个人信息
 * token：TokenResponse 鉴权令牌
 */
