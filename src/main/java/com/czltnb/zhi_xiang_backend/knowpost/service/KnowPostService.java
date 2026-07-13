package com.czltnb.zhi_xiang_backend.knowpost.service;

import com.czltnb.zhi_xiang_backend.knowpost.api.dto.KnowPostDetailResponse;

import java.util.List;

public interface KnowPostService {

    long createDraft(long creatorId);

    void confirmContent(long creatorId,long id,String objectKey,String etag,Long size,String sha256);

    void updateMetadata(long creatorId,long id,String title,Long tagId,List<String> tags,
                        List<String> imgUrls,String visible,Boolean isTop,String description);

    void publish(long creatorId,long id);
}
