package com.czltnb.zhi_xiang_backend.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;


@Data
@ConfigurationProperties(prefix = "auth")
public class AuthProperties {
    /** JWT 签发者 */
    private String issuer = "zhiguang";
    /** access_token 有效期（毫秒），默认 15 分钟 */
    private long accessTokenExpiration = 900_000;
    /** refresh_token 有效期（毫秒），默认 7 天 */
    private long refreshTokenExpiration = 604_800_000;
    /** RSA 公钥路径 */
    private String publicKeyPath = "classpath:keys/public_key.pem";
    /** RSA 私钥路径 */
    private String privateKeyPath = "classpath:keys/private_key.pem";
}
