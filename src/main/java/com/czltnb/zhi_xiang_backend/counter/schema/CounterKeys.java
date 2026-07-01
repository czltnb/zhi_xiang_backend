package com.czltnb.zhi_xiang_backend.counter.schema;

public final class CounterKeys {

    private CounterKeys() {}

    //1.汇总快照层
    //自定义的、SDS类型的、计数总数对应的的Key
    public static String sdsKey(String entityType,String entityId) {
        return String.format("cnt:%s:%s:%s",CounterSchema.SCHEMA_ID,entityType,entityId);
    }

    //2.事实层
    public static String bitmapKey(String metric,String entityType,String entityId,long chunk) {
        return String.format("bm:%s:%s:%s:%d",metric,entityType,entityId,chunk);
    }

    //3.增量缓冲层
    public static String aggKey(String entityType,String entityId) {
        return String.format("agg:%s:%s:%s", CounterSchema.SCHEMA_ID, entityType, entityId);
    }
}
