package com.czltnb.zhi_xiang_backend.auth.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * JWT / 认证相关配置。
 *
 * <p>激活 {@link AuthProperties} 的配置绑定，并注册全局使用的 Bean。</p>
 */
@Configuration
@EnableConfigurationProperties(AuthProperties.class) //@EnableConfigurationProperties(AuthProperties.class) — 手动激活 @ConfigurationProperties（另一种方式是在 AuthProperties 上加 @Configuration 或 @Component）
public class JwtConfig {
    /**
     * 密码编码器：BCrypt。
     *
     * <p>注册后 Spring Security 的 {@code DaoAuthenticationProvider}
     * 和业务代码中的手动密码校验都可以注入使用。</p>
     */
    //BCryptPasswordEncoder — Spring Security 推荐的密码哈希算法，自动加盐，encode() 生成 $2a$... 格式，matches(raw, encoded) 对比
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
