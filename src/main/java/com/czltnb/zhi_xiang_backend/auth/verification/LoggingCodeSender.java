package com.czltnb.zhi_xiang_backend.auth.verification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 开发用验证码发送器 — 不实际发送，仅打印日志。
 */
@Slf4j
@Component
public class LoggingCodeSender implements CodeSender {

    @Override
    public void sendCode(VerificationScene scene, String identifier, String code, int expireMinutes) {
        log.info("Send verification code scene={} identifier={} code={} expireMinutes={}",
                scene, identifier, code, expireMinutes);
    }
}
