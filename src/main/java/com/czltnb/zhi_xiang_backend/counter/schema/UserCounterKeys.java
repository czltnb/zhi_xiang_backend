package com.czltnb.zhi_xiang_backend.counter.schema;

public final class UserCounterKeys {

    private UserCounterKeys() {}

    public static String sdsKey(long userId) {
        return "ucnt:" + userId;
    }
}
