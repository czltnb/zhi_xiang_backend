package com.czltnb.zhi_xiang_backend.auth.util;

import java.util.regex.Pattern;

/**
 * 标识校验器 — 校验手机号和邮箱格式。
 */
public final class IdentifierValidator {

    private static final Pattern PHONE_PATTERN =
            Pattern.compile("^1[3-9]\\d{9}$");

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");

    private IdentifierValidator() {}

    public static boolean isValidPhone(String phone) {
        return phone != null && PHONE_PATTERN.matcher(phone.trim()).matches();
    }

    public static boolean isValidEmail(String email) {
        return email != null && EMAIL_PATTERN.matcher(email.trim()).matches();
    }
}
