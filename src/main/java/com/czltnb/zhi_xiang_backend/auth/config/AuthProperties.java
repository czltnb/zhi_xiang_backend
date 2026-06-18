package com.czltnb.zhi_xiang_backend.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;


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
    private Resource publicKeyPath;
    /** RSA 私钥路径 */
    private Resource privateKeyPath;
}
