package com.czltnb.zhi_xiang_backend.knowpost.service;

import com.czltnb.zhi_xiang_backend.knowpost.api.dto.KnowPostDetailResponse;

import java.util.List;

public interface KnowPostService {

    long createDraft(long creatorId);
}
