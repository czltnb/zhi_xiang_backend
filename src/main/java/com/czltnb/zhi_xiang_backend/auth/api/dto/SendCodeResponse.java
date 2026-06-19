package com.czltnb.zhi_xiang_backend.auth.api.dto;

import com.czltnb.zhi_xiang_backend.auth.verification.VerificationScene;

public record SendCodeResponse(
        String identifier,
        VerificationScene scene,
        int expireSeconds
) {
}
