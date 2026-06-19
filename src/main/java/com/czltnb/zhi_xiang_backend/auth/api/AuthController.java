package com.czltnb.zhi_xiang_backend.auth.api;

import com.czltnb.zhi_xiang_backend.auth.api.dto.AuthResponse;
import com.czltnb.zhi_xiang_backend.auth.api.dto.AuthUserResponse;
import com.czltnb.zhi_xiang_backend.auth.api.dto.LoginRequest;
import com.czltnb.zhi_xiang_backend.auth.api.dto.LogoutRequest;
import com.czltnb.zhi_xiang_backend.auth.api.dto.PasswordResetRequest;
import com.czltnb.zhi_xiang_backend.auth.api.dto.RegisterRequest;
import com.czltnb.zhi_xiang_backend.auth.api.dto.SendCodeRequest;
import com.czltnb.zhi_xiang_backend.auth.api.dto.SendCodeResponse;
import com.czltnb.zhi_xiang_backend.auth.api.dto.TokenRefreshRequest;
import com.czltnb.zhi_xiang_backend.auth.api.dto.TokenResponse;
import com.czltnb.zhi_xiang_backend.auth.model.ClientInfo;
import com.czltnb.zhi_xiang_backend.auth.service.AuthService;
import com.czltnb.zhi_xiang_backend.auth.token.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证 API 控制器。
 * <p>
 * 暴露 REST 接口：发送验证码、注册、登录、刷新令牌、登出、重置密码、查询当前用户信息。
 * <p>
 * 集成 Spring Security 资源服务器能力：
 * {@code /me} 通过 {@link AuthenticationPrincipal @AuthenticationPrincipal Jwt} 提取当前用户。
 * 客户端信息（IP/UA）从请求头解析，用于审计登录日志。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Validated
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;

    /**
     * 发送短信/邮箱验证码。
     */
    @PostMapping("/send-code")
    public SendCodeResponse sendCode(@Valid @RequestBody SendCodeRequest request) {
        return authService.sendCode(request);
    }

    /**
     * 注册新用户并自动登录。
     */
    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request,
                                 HttpServletRequest httpRequest) {
        return authService.register(request, resolveClient(httpRequest));
    }

    /**
     * 登录并获取令牌对。
     */
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request,
                              HttpServletRequest httpRequest) {
        return authService.login(request, resolveClient(httpRequest));
    }

    /**
     * 使用 Refresh Token 刷新令牌。
     */
    @PostMapping("/token/refresh")
    public TokenResponse refresh(@Valid @RequestBody TokenRefreshRequest request) {
        return authService.refresh(request);
    }

    /**
     * 登出并撤销刷新令牌。
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    /**
     * 使用验证码重置密码。
     */
    @PostMapping("/password/reset")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody PasswordResetRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.noContent().build();
    }

    /**
     * 查询当前登录用户信息。
     * <p>
     * 基于 Spring Security 注入的 {@link Jwt} 令牌自动提取用户 ID。
     */
    @GetMapping("/me")
    public AuthUserResponse me(@AuthenticationPrincipal Jwt jwt) {
        long userId = jwtService.extractUserId(jwt);
        return authService.me(userId);
    }

    // ==================== 私有辅助 ====================

    /**
     * 从请求中解析客户端信息（IP + User-Agent）。
     */
    private ClientInfo resolveClient(HttpServletRequest request) {
        String ip = extractClientIp(request);
        String ua = request.getHeader("User-Agent");
        return new ClientInfo(ip, ua);
    }

    /**
     * 提取客户端真实 IP。
     * <p>
     * 优先使用代理头：X-Forwarded-For（取第一个）、X-Real-IP；
     * 否则回退到 {@code request.getRemoteAddr()}。
     */
    private String extractClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
