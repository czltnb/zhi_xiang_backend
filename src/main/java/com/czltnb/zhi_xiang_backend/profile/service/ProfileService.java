package com.czltnb.zhi_xiang_backend.profile.service;

import com.czltnb.zhi_xiang_backend.profile.api.dto.ProfilePatchRequest;
import com.czltnb.zhi_xiang_backend.profile.api.dto.ProfileResponse;
import com.czltnb.zhi_xiang_backend.user.domain.User;

import java.util.Optional;

/**
 * 个人资料业务接口。
 */
public interface ProfileService {

    Optional<User> getById(long userId);

    ProfileResponse updateProfile(long userId, ProfilePatchRequest req);

    ProfileResponse updateAvatar(long userId, String avatarUrl);
}
