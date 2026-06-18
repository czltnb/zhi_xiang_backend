//package com.czltnb.zhi_xiang_backend.auth.util;
//
//import com.czltnb.zhi_xiang_backend.auth.config.AuthProperties;
//import com.czltnb.zhi_xiang_backend.user.domain.User;
//import io.jsonwebtoken.Claims;
//import io.jsonwebtoken.Jwts;
//import org.springframework.stereotype.Component;
//
//import java.security.KeyFactory;
//import java.security.PrivateKey;
//import java.security.PublicKey;
//import java.security.spec.PKCS8EncodedKeySpec;
//import java.security.spec.X509EncodedKeySpec;
//import java.time.Instant;
//import java.util.Base64;
//import java.util.Date;
//
///**
// * JWT 工具类：负责 access_token 和 refresh_token 的生成、解析与校验。
// *
// * <p>使用 RS256 (RSA + SHA-256) 非对称签名：私钥签名，公钥验签。</p>
// *
// * <p>Token 结构 (claims)：
// * <ul>
// *   <li>{@code sub} — 用户 ID（字符串）</li>
// *   <li>{@code type} — 令牌类型（"access" / "refresh"）</li>
// *   <li>{@code iss} — 签发者</li>
// *   <li>{@code iat} — 签发时间</li>
// *   <li>{@code exp} — 过期时间</li>
// *   <li>{@code jti} — 令牌唯一 ID（用于黑名单）</li>
// * </ul>
// * </p>
// */
//@Component
//public class JwtUtil {
//
//    private final AuthProperties authProperties;
//    private final PrivateKey privateKey;
//    private final PublicKey publicKey;
//
//    /**
//     * 构造时一次性加载 RSA 密钥对到内存。
//     */
//    public JwtUtil(AuthProperties authProperties) {
//        this.authProperties = authProperties;
//        this.privateKey = loadPrivateKey(authProperties.getPrivateKeyPath());
//        this.publicKey = loadPublicKey(authProperties.getPublicKeyPath());
//    }
//
//    // ==================== Token 生成 ====================（私钥签名）
//
//    /**
//     * 生成 access_token（短期有效，用于接口鉴权）。
//     */
//    public String generateAccessToken(User user) {
//        Instant now = Instant.now();
//        return Jwts.builder()
//                .subject(String.valueOf(user.getId())) //主题:用户ID 标识"这个 Token 代表谁"
//                .claim("type","access")
//                .issuer(authProperties.getIssuer()) //签发者:"zhixiang"
//                .issuedAt(Date.from(now)) //签发时间
//                .expiration(Date.from(now.plusMillis(authProperties.getAccessTokenExpiration()))) //过期时间,15分钟
//                .signWith(privateKey) // RS256 私钥签名
//                .compact();
//
//    }
//
//    /**
//     * 生成 refresh_token（长期有效，用于刷新 access_token）。
//     */
//    public String generateRefreshToken(User user) {
//        Instant now = Instant.now();
//        return Jwts.builder()
//                .subject(String.valueOf(user.getId()))
//                .claim("type", "refresh")
//                .issuer(authProperties.getIssuer())
//                .issuedAt(Date.from(now))
//                .expiration(Date.from(now.plusMillis(authProperties.getRefreshTokenExpiration())))
//                .signWith(privateKey)
//                .compact();
//    }
//
//    // ==================== Token 解析与校验 ====================（公钥验签！！！！！）
//
//    /**
//     * 解析并校验 JWT 的签名和有效期。
//     *
//     * @param token JWT 字符串
//     * @return 解析后的 Claims
//     * @throws io.jsonwebtoken.JwtException 签名无效、过期、格式错误
//     */
//    public Claims parseToken(String token) {
//        return Jwts.parser()
//                .verifyWith(publicKey)     // 公钥验签
//                .requireIssuer(authProperties.getIssuer())  // 校验签发者
//                .build()
//                .parseSignedClaims(token)  // 解析 + 校验签名+过期
//                .getPayload();
//    }
//
//    /**
//     * 从 token 中提取用户 ID。
//     */
//    public Long getUserIdFromToken(String token) {
//        String sub = parseToken(token).getSubject();
//        return Long.valueOf(sub);
//    }
//
//    /**
//     * 判断是否是 access_token。
//     */
//    public boolean isAccessToken(String token) {
//        return "access".equals(parseToken(token).get("type", String.class));
//    }
//
//    /**
//     * 判断是否是 refresh_token。
//     */
//    public boolean isRefreshToken(String token) {
//        return "refresh".equals(parseToken(token).get("type", String.class));
//    }
//
//    /**
//     * 获取 token 的过期时间。
//     */
//    public Instant getExpiration(String token) {
//        return parseToken(token).getExpiration().toInstant();
//    }
//
//    // ==================== RSA 密钥加载 ====================
//
//    /**
//     * 一、一句话区分X509EncodedKeySpec和PKCS8EncodedKeySpec
//     * 二者都是 Java JCE 里存放密钥原始二进制字节的载体，区别只对应两种行业标准密钥编码格式：
//     * X509EncodedKeySpec：存放公钥二进制，遵循 X.509 公钥标准
//     * PKCS8EncodedKeySpec：存放私钥二进制，遵循 PKCS#8 私钥标准
//     * 二、分别详解
//     * 1. X509EncodedKeySpec（公钥专用）
//     * 对应 PEM 头
//     * plaintext
//     * -----BEGIN PUBLIC KEY-----
//     * 标准来源 X.509
//     * X.509 是数字证书、公钥的国际标准，所有对外公开的公钥统一用这套二进制编码规则存储。
//     * 作用
//     * 把 PEM 去掉头尾、base64 解码后的原始字节丢进这个类，KeyFactory.getInstance("RSA").generatePublic(spec) 就能解析出 PublicKey 对象。
//     * 2. PKCS8EncodedKeySpec（私钥专用）
//     * 对应 PEM 头
//     * plaintext
//     * -----BEGIN PRIVATE KEY-----
//     * 标准来源 PKCS#8
//     * PKCS#8 是专门用来存储非对称私钥的通用标准，支持 RSA、EC、SM2 等各种算法私钥，也是 Java 唯一原生支持读取的私钥格式。
//     * 作用
//     * 承载私钥原始二进制，交给 KeyFactory 生成 PrivateKey。
//     * 三、底层本质（共同点）
//     * 两个类都继承 EncodedKeySpec，内部只存一个 byte[] encodedKey，结构几乎一模一样：
//     * 构造方法都接收密钥二进制数组
//     * 都提供 getEncoded() 获取原始密钥字节
//     * 只是规范不同，所以分成两个类强制区分公私钥，不能混用：
//     * 公钥字节 → 不能丢进 PKCS8EncodedKeySpec，会解析报错
//     * PKCS8 私钥字节 → 不能丢进 X509EncodedKeySpec，解析失败
//     */
//
//    /**
//     * 从 classpath PEM 文件加载 RSA 私钥。
//     *
//     * PEM 格式：
//     * -----BEGIN PRIVATE KEY-----
//     * <Base64 编码的密钥体>
//     * -----END PRIVATE KEY-----
//     */
//    private PrivateKey loadPrivateKey(String path) {
//        try {
//            String pem = readPemContent(path);
//            byte[] keyBytes = Base64.getDecoder().decode(pem);
//            //用 PKCS8EncodedKeySpec 封装私钥二进制（PKCS8 是 Java 识别私钥的标准规范）；
//            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
//            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
//            //RSA 工厂解析生成 PrivateKey 私钥对象
//            return keyFactory.generatePrivate(spec);
//        } catch (Exception e) {
//            throw new RuntimeException("Failed to load RSA private key from: " + path, e);
//        }
//    }
//
//    /**
//     * 从 classpath PEM 文件加载 RSA 公钥。
//     *
//     * PEM 格式：
//     * -----BEGIN PUBLIC KEY-----
//     * <Base64 编码的密钥体>
//     * -----END PUBLIC KEY-----
//     */
//    private PublicKey loadPublicKey(String path) {
//        try {
//            String pem = readPemContent(path);
//            byte[] keyBytes = Base64.getDecoder().decode(pem);
//            //使用 X509EncodedKeySpec 封装公钥二进制（Java 公钥统一规范）；
//            //生成 PublicKey 公钥对象。
//            X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
//            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
//            return keyFactory.generatePublic(spec);
//        } catch (Exception e) {
//            throw new RuntimeException("Failed to load RSA public key from: " + path, e);
//        }
//    }
//
//
//    /**
//     * 读取 PEM 文件内容，去除头尾标记和空白字符，返回纯 Base64 体。
//     */
//    private String readPemContent(String path) {
//        try {
//            // 去掉 "classpath:" 前缀，从类路径读取
//            String classpathFile = path.replace("classpath:", "");
//            java.io.InputStream in = getClass().getClassLoader().getResourceAsStream(classpathFile);
//            if (in == null) {
//                throw new RuntimeException("PEM file not found: " + path);
//            }
//            String content = new String(in.readAllBytes());
//            // 去掉 -----BEGIN ...----- 和 -----END ...----- 头尾标记
//            content = content.replace("-----BEGIN PRIVATE KEY-----", "")
//                    .replace("-----END PRIVATE KEY-----", "")
//                    .replace("-----BEGIN PUBLIC KEY-----", "")
//                    .replace("-----END PUBLIC KEY-----", "");
//            // 去掉所有空白字符（换行、空格等）
//            return content.replaceAll("\\s", "");
//        } catch (java.io.IOException e) {
//            throw new RuntimeException("Failed to read PEM file: " + path, e);
//        }
//    }
//
//
//
//
//}
