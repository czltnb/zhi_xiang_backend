package com.czltnb.zhi_xiang_backend.counter.event;

import lombok.Data;

@Data
public class CounterEvent {

    private String entityType;
    private String entityId;
    private String metric;
    private int    idx;
    private long   userId;
    private int    delta;

    public CounterEvent(String entityType, String entityId, String metric,
                        int idx, long userId, int delta) {
        this.entityType = entityType;
        this.entityId   = entityId;
        this.metric     = metric;
        this.idx        = idx;
        this.userId     = userId;
        this.delta      = delta;
    }

    public static CounterEvent of(String entityType, String entityId, String metric,
                                  int idx, long userId, int delta) {
        return new CounterEvent(entityType, entityId, metric, idx, userId, delta);
    }
}
