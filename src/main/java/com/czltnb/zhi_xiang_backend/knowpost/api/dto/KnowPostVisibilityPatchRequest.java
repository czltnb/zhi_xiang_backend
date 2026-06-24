package com.czltnb.zhi_xiang_backend.knowpost.api.dto;

import jakarta.validation.constraints.NotBlank;

public record KnowPostVisibilityPatchRequest(
        @NotBlank String visible
) {}
