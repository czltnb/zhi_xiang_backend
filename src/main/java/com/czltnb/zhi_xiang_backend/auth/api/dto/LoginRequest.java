package com.czltnb.zhi_xiang_backend.auth.api.dto;

import com.czltnb.zhi_xiang_backend.auth.model.IdentifierType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record LoginRequest(
        @NotNull(message = "账号类型不能为空") IdentifierType identifierType,
        @NotBlank(message = "账号不能为空") String identifier,
        String code, //验证码，验证码登录需要传，密码登录不用传
        String password
) {
}
