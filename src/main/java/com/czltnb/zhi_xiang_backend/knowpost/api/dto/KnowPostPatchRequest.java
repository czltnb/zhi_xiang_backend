package com.czltnb.zhi_xiang_backend.knowpost.api.dto;

import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 帖子元数据更新请求（部分字段可选）。
 */
public record KnowPostPatchRequest(
        String title,
        Long tagId,
        @Size(max = 20) List<String> tags,
        @Size(max = 20) List<String> imgUrls,
        String visible,
        Boolean isTop,
        String description
) {}
