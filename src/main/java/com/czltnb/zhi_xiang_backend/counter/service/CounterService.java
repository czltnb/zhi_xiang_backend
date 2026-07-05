package com.czltnb.zhi_xiang_backend.counter.service;

import java.util.List;
import java.util.Map;

public interface CounterService {

    /**
     * 点赞：仅在之前未点赞时置位并 +1。
     * @return 是否发生状态变化（true 表示这次操作生效）
     */
    boolean like(String entityType,String entityId,long userId);
    boolean unlike(String entityType, String entityId, long userId);
    boolean fav(String entityType, String entityId, long userId);
    boolean unfav(String entityType, String entityId, long userId);

    Map<String, Long> getCounts(String entityType, String entityId, List<String> metrics);
    Map<String, Map<String, Long>> getCountsBatch(String entityType, List<String> entityIds, List<String> metrics);

    boolean isLiked(String entityType, String entityId, long userId);
    boolean isFaved(String entityType, String entityId, long userId);
}
