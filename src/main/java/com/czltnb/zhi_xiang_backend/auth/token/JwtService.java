package com.czltnb.zhi_xiang_backend.auth.token;

import com.czltnb.zhi_xiang_backend.auth.config.AuthProperties;
import com.czltnb.zhi_xiang_backend.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * JWT 令牌服务
 * 功能：签发 Access / Refresh Token（RS256），解码 JWT，提取用户 ID，令牌类型与令牌 ID
 * 声明：
 * token_type：标识 access 或 refresh
 * uid：用户ID/userId
 * jti：；令牌ID（用作refreshToken的白名单键,或者用作accessToken的黑名单键，两种token都有tokenId！！！)
 * 过期时间：来自AuthProperties.Jwt类中的配置
 */

@Service
@RequiredArgsConstructor
public class JwtService {

    private static final String CLAIM_TOKEN_TYPE = "token_type";
    private static final String CLAIM_USER_ID = "uid";

    //来自AuthConfiguration类的依赖注入！！！！
    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;
    private final AuthProperties authProperties;
    private final Clock clock = Clock.systemUTC(); //Clock.systemUTC()：返回 UTC 零时区时钟（格林威治标准时间，不带时区偏移）



    /**
     * 为指定用户签发一对 Access/Refresh Token。
     */
    public TokenPair issueTokenPair(User user) {
        //refreshTokenId是随机生成的UUID！！！
        String refreshTokenId = UUID.randomUUID().toString();
        Instant issuedAt = Instant.now(clock);
        Instant accessExpiresAt = issuedAt.plus(authProperties.getJwt().getAccessTokenTtl());
        Instant refreshExpiresAt = issuedAt.plus(authProperties.getJwt().getRefreshTokenTtl());

        //accessTokenId也是随机生成的！！！
        String accessTokenId = UUID.randomUUID().toString();
        String accessToken = encodeToken(user,issuedAt,accessExpiresAt,"access",accessTokenId);

        String refreshToken = encodeRefreshToken(user,issuedAt,refreshExpiresAt,refreshTokenId);

        return new TokenPair(accessToken,accessExpiresAt,refreshToken,refreshExpiresAt,refreshTokenId);
    }

    /**
     * 解码 JWT 字符串为 Jwt 对象
     */
    public Jwt decode(String token) {
        return jwtDecoder.decode(token);
    }

    /**
     * 从 JWT 中提取用户 ID。
     */
    public long extractUserId(Jwt jwt) {
        Object claim = jwt.getClaims().get(CLAIM_USER_ID);
        if (claim instanceof Number number) return number.longValue();
        if (claim instanceof String text) return Long.parseLong(text);
        throw new IllegalArgumentException("Invalid user id in token");
    }

    /**
     * 提取令牌类型声明
     */
    public String extractTokenType(Jwt jwt) {
        Object claim = jwt.getClaims().get(CLAIM_TOKEN_TYPE);
        return claim != null ? claim.toString() : "";
    }

    /**
     * 提取令牌 ID（jti）。(只有refreshToken才有tokenId)
     */
    public String extractTokenId(Jwt jwt) {
        return jwt.getId();
    }

    /**
     * 编码Jwt令牌对象，返回Jwt对象对应的token字符串
     */
    private String encodeToken(User user,Instant issuedAt,Instant expiresAt,String tokenType,String tokenId) {
        //填充JWT令牌的声明(和载荷是一个东西)
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(authProperties.getJwt().getIssuer())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .id(tokenId)
                /**
                 * .claim("nickname", xxx) 等价于 map.put("nickname", xxx)
                 * 最终打包进 JWT 的 Payload（载荷）里，前端 / 服务端解析后可以直接取出。
                 */
                .claim(CLAIM_TOKEN_TYPE, tokenType) //自定义声明/载荷
                .claim(CLAIM_USER_ID, user.getId()) //自定义声明/载荷
                .claim("nickname", user.getNickname()) //自定义声明/载荷
                .build();

                return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    /**
     * 编码 RefreshToken
     */

    private String encodeRefreshToken(User user, Instant issuedAt, Instant expiresAt,
                                      String tokenId) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(authProperties.getJwt().getIssuer())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .subject(String.valueOf(user.getId()))
                .id(tokenId)
                .claim(CLAIM_TOKEN_TYPE, "refresh")
                .claim(CLAIM_USER_ID, user.getId())
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }
}
