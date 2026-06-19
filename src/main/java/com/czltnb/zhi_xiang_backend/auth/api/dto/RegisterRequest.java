package com.czltnb.zhi_xiang_backend.auth.api.dto;

import com.czltnb.zhi_xiang_backend.auth.model.IdentifierType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RegisterRequest(
        @NotNull(message = "账号类型不能为空") IdentifierType identifierType,
        @NotBlank(message = "账号不能为空") String identifier,
        @NotBlank(message = "验证码不能为空") String code,
        String password,
        boolean agreeTerms //是否同意用户协议条款
) {
}
