package com.czltnb.zhi_xiang_backend.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

import java.time.Duration;

/**
 * 认证相关配置属性，绑定前缀 {@code auth.*}。
 */
@Data
@ConfigurationProperties(prefix = "auth")
public class AuthProperties {

    private final Jwt jwt = new Jwt();
    private final Verification verification = new Verification();
    private final Password password = new Password();

    @Data
    public static class Jwt {
        /** JWT 签发者标识（iss） */
        private String issuer = "zhixiang";
        /** 访问令牌有效期 */
        private Duration accessTokenTtl = Duration.ofMinutes(15);
        /** 刷新令牌有效期 */
        private Duration refreshTokenTtl = Duration.ofDays(7);
        /** JWK 密钥标识（kid） */
        private String keyId = "zhixiang-key";
        /** RSA 私钥 PEM 资源 */
        private Resource privateKey;
        /** RSA 公钥 PEM 资源 */
        private Resource publicKey;
    }

    @Data
    public static class Verification {
        /** 验证码位数 */
        private int codeLength = 6;
        /** 验证码有效时间 */
        private Duration ttl = Duration.ofMinutes(5);
        /** 最大校验尝试次数 */
        private int maxAttempts = 5;
        /** 同标识连续发送的最小间隔 */
        private Duration sendInterval = Duration.ofSeconds(60);
        /** 同标识每日发送上限 */
        private int dailyLimit = 10;
    }

    @Data
    public static class Password {
        /** BCrypt 哈希强度 */
        private int bcryptStrength = 12;
        /** 密码最小长度 */
        private int minLength = 8;
    }
}
