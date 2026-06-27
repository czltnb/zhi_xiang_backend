package com.czltnb.zhi_xiang_backend.cache.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "cache")
@Data
public class CacheProperties {

    private L2 l2 = new L2();
    private HotKey hotKey = new HotKey();

    @Data
    public static class L2 {
        private PublicCfg publicCfg = new PublicCfg();
        private MineCfg mineCfg = new MineCfg();
        private DetailCfg detailCfg = new DetailCfg();
    }

    @Data
    public static class PublicCfg {
        private int ttlSeconds = 15;
        private long maxSize = 1000;
    }

    @Data
    public static class MineCfg {
        private int ttlSeconds = 10;
        private long maxSize = 1000;
    }

    @Data
    public static class DetailCfg {
        private int ttlSeconds = 30;
        private long maxSize = 5000;
    }

    @Data
    public static class HotKey {
        private int windowSeconds = 60;
        private int segmentSeconds = 10;
        private int levelLow = 50;
        private int levelMedium = 200;
        private int levelHigh = 500;
        private int extendLowSeconds = 20;
        private int extendMediumSeconds = 60;
        private int extendHighSeconds = 120;
    }
}
