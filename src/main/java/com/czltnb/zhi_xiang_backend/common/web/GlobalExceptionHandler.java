package com.czltnb.zhi_xiang_backend.common.web;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import com.czltnb.zhi_xiang_backend.common.exception.BusinessException;
import com.czltnb.zhi_xiang_backend.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务异常统一返回：HTTP 400。
     *
     * @param ex 业务异常，包含错误码与消息。
     * @return 响应体：code/message。
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<R<?>> handleBusiness(BusinessException ex) {
        R<?> body = R.error(ex.getErrorCode(), ex.getMessage());
        return ResponseEntity.badRequest().body(body);
    }

    /**
     * 参数校验失败（@Valid）统一返回：HTTP 400。
     * 仅取首个字段错误的信息作为提示。
     *
     * @param ex Spring 的方法参数校验异常。
     * @return 响应体：code/message。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<R<?>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse(ErrorCode.BAD_REQUEST.getDefaultMessage());
        R<?> body = R.error(ErrorCode.BAD_REQUEST, message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * 约束校验失败（如 @Validated 参数）统一返回：HTTP 400。
     *
     * @param ex 参数约束异常。
     * @return 响应体：code/message。
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<R<?>> handleConstraintViolation(ConstraintViolationException ex) {
        R<?> body = R.error(ErrorCode.BAD_REQUEST, ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * 未处理异常统一返回：HTTP 500。
     * 记录错误日志并返回通用提示。
     *
     * @param ex 未捕获的异常。
     * @return 响应体：code/message。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<R<?>> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        R<?> body = R.error(ErrorCode.INTERNAL_ERROR);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
