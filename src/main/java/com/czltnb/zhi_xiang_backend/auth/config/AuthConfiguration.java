package com.czltnb.zhi_xiang_backend.auth.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

/**
 * 认证相关 Bean 配置。
 * <p>
 * - {@code PasswordEncoder}：BCrypt；
 * - {@code JwtEncoder} / {@code JwtDecoder}：基于 RSA 密钥对的 Nimbus 实现。
 */
@Configuration
@EnableConfigurationProperties(AuthProperties.class)
@RequiredArgsConstructor
public class AuthConfiguration {

    private final AuthProperties authProperties;

    /**
     * BCrypt 密码编码器。
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * JWT 编码器 — 用 RSA 私钥签名。
     * <p>
     * 通过 JWK 封装密钥对，提供给 NimbusJwtEncoder 使用。
     */
    @Bean
    public JwtEncoder jwtEncoder() {
        RSAPrivateKey privateKey = PemUtils.readPrivateKey(authProperties.getPrivateKeyPath());
        RSAPublicKey publicKey = PemUtils.readPublicKey(authProperties.getPublicKeyPath());
        RSAKey jwk = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID("zhiguang")
                .build();
        JWKSource<SecurityContext> jwkSource = new ImmutableJWKSet<>(new JWKSet(jwk));
        return new NimbusJwtEncoder(jwkSource);
    }

    /**
     * JWT 解码器 — 用 RSA 公钥验签。
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        RSAPublicKey publicKey = PemUtils.readPublicKey(authProperties.getPublicKeyPath());
        return NimbusJwtDecoder.withPublicKey(publicKey).build();
    }
}
