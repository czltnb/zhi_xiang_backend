package com.czltnb.zhi_xiang_backend.counter.service;

public interface CounterService {

    /**
     * 点赞：仅在之前未点赞时置位并 +1。
     * @return 是否发生状态变化（true 表示这次操作生效）
     */
    boolean like(String entityType,String entityId,long userId);
}
