package com.czltnb.zhi_xiang_backend.profile.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

@Configuration
public class CorsConfig {

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();

        //后端/服务端，允许前端/客户端的任意请求来源
        config.setAllowedOriginPatterns(List.of("*"));

        //允许常见的跨域方法，包括简单请求和预检请求
        config.setAllowedMethods(List.of("PATCH", "POST", "GET", "OPTIONS"));

        //允许所有请求头
        config.setAllowedHeaders(List.of("*"));

        //不使用跨域凭证（若前端需要携带 Cookie，请改为 true 并限定具体来源）
        config.setAllowCredentials(false);

        //预检请求缓存时间（预检请求的缓存优化）
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // 仅对 Profile 相关接口开启跨域
        source.registerCorsConfiguration("/api/v1/profile/**", config);

        return new CorsFilter(source);
    }
}
