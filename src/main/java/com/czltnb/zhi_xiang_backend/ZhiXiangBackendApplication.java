package com.czltnb.zhi_xiang_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ZhiXiangBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZhiXiangBackendApplication.class, args);
    }

}
