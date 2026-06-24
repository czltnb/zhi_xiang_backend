package com.czltnb.zhi_xiang_backend.knowpost.api.dto;

import jakarta.validation.constraints.NotBlank;

public record DescriptionSuggestRequest(
        @NotBlank(message = "content 不能为空") String content
) {}
