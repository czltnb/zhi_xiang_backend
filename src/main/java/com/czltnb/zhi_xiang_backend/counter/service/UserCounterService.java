package com.czltnb.zhi_xiang_backend.counter.service;

public interface UserCounterService {

    void incrementFollowings(long userId, int delta);
    void incrementFollowers(long userId, int delta);
    void incrementPosts(long userId, int delta);
    void incrementLikesReceived(long userId, int delta);
    void incrementFavsReceived(long userId, int delta);
    void rebuildAllCounters(long userId);
}