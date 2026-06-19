package com.czltnb.zhi_xiang_backend.auth.api.dto;

import jakarta.validation.constraints.NotBlank;

public record LogoutRequest(@NotBlank(message = "刷新令牌不能为空") String refreshToken) {
}
/**
 * 登出的核心需求：让当前这套令牌彻底作废
 * 单纯删掉前端本地 Token 不安全：
 * 用户登出后，如果别人窃取到他的 refreshToken，依然可以调用刷新接口，无限换新 accessToken，冒充登录。
 * 所以登出后端必须做两件事：
 * 根据传入的 refreshToken 找到对应用户；
 * 将该 refreshToken 在服务端标记为失效 / 拉黑 / 直接删除；
 * 之后哪怕拿着这个 refreshToken 去刷新接口，直接拒绝。
 */
