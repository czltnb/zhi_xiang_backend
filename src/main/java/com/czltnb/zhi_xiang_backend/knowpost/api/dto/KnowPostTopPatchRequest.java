package com.czltnb.zhi_xiang_backend.knowpost.api.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 帖子置顶请求
 * @param isTop
 */
public record KnowPostTopPatchRequest(
        @NotNull Boolean isTop
) {}
