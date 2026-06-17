package com.czltnb.zhi_xiang_backend.common.web;

import com.czltnb.zhi_xiang_backend.common.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

/**
 * 统一响应体。
 *
 * <p>所有 Controller 的返回值都用此类包装，前端通过 {@code code} 判断成功/失败，
 * {@code data} 承载业务数据，{@code message} 承载提示信息。</p>
 *
 * <p>使用示例：
 * <pre>{@code
 *   // 成功返回（带数据）
 *   return R.ok(user);
 *
 *   // 成功返回（无数据）
 *   return R.ok();
 *
 *   // 业务异常（用错误码默认消息）
 *   return R.error(ErrorCode.IDENTIFIER_EXISTS);
 *
 *   // 业务异常（自定义消息）
 *   return R.error(ErrorCode.BAD_REQUEST, "手机号格式不正确");
 * }</pre>
 * </p>
 *
 * @param <T> 响应数据的类型
 */
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class R<T> {

    /**
     * 业务状态码。
     * 成功时为 {@code "SUCCESS"}，失败时为对应 {@link ErrorCode#getCode()}。
     */
    private final String code;

    /**
     * 提示信息。
     * 成功时可为 {@code null}，失败时携带错误描述。
     */
    private final String message;

    /**
     * 响应数据。
     * 成功时承载业务数据，失败时为 {@code null}。
     */
    private final T data;

    private R(String code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    // ==================== 成功响应 ====================

    /**
     * 成功（无数据）。
     * 常用于 204 语义或操作确认型接口。
     */
    public static <T> R<T> ok() {
        return new R<>("SUCCESS", null, null);
    }

    /**
     * 成功（带数据）。
     *
     * @param data 业务数据
     */
    public static <T> R<T> ok(T data) {
        return new R<>("SUCCESS", null, data);
    }

    /**
     * 成功（自定义消息 + 数据）。
     */
    public static <T> R<T> ok(String message, T data) {
        return new R<>("SUCCESS", message, data);
    }

    // ==================== 失败响应 ====================

    /**
     * 失败（用错误码的默认消息）。
     *
     * @param errorCode 业务错误码
     */
    public static <T> R<T> error(ErrorCode errorCode) {
        return new R<>(errorCode.getCode(), errorCode.getDefaultMessage(), null);
    }

    /**
     * 失败（用错误码 + 自定义消息）。
     *
     * @param errorCode 业务错误码
     * @param message   自定义提示
     */
    public static <T> R<T> error(ErrorCode errorCode, String message) {
        return new R<>(errorCode.getCode(), message, null);
    }

    /**
     * 失败（直接指定 code 和 message）。
     *
     * @param code    业务状态码
     * @param message 提示信息
     */
    public static <T> R<T> error(String code, String message) {
        return new R<>(code, message, null);
    }

}
