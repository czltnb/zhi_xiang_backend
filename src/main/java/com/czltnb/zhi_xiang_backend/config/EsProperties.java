package com.czltnb.zhi_xiang_backend.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Data
@ConfigurationProperties(prefix = "spring.elasticsearch")
public class EsProperties {

    private List<String> uris;
    private String username;
    private String password;

    @Value("${spring.ai.vectorstore.elasticsearch.index-name:}")
    private String index;

    public String getHost() {
        return (uris == null || uris.isEmpty()) ? null : uris.get(0);
    }
}
