package com.czltnb.zhi_xiang_backend.auth.api.dto;

import com.czltnb.zhi_xiang_backend.auth.model.IdentifierType;
import com.czltnb.zhi_xiang_backend.auth.verification.VerificationScene;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SendCodeRequest(
        @NotNull(message = "场景不能为空") VerificationScene scene, //发送验证码有多个场景，注册、登录、重置密码等等
        @NotNull(message = "账号类型不能为空") IdentifierType identifierType,
        @NotBlank(message = "账号不能为空") String identifier
) {
}
