package com.czltnb.zhi_xiang_backend.knowpost.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowPost {
    private Long id;
    private Long tagId;
    private String tags;           // JSON 字符串，示例：["java","编程"]
    private String title;
    private String description;
    private String contentUrl;
    private String contentObjectKey;
    private String contentEtag;
    private Long contentSize;
    private String contentSha256;
    private Long creatorId;
    private Boolean isTop;
    private String type;           // 默认 "image_text"
    private String visible;        // public/followers/school/private/unlisted
    private String imgUrls;       // JSON 字符串,示例：["https://...","https://..."]
    private String videoUrl;
    private String status;         // draft/published/deleted
    private Instant createTime;
    private Instant updateTime;
    private Instant publishTime;
}
