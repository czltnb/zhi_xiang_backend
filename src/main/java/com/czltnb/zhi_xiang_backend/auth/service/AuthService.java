package com.czltnb.zhi_xiang_backend.auth.service;

import com.czltnb.zhi_xiang_backend.auth.api.dto.AuthResponse;
import com.czltnb.zhi_xiang_backend.auth.api.dto.AuthUserResponse;
import com.czltnb.zhi_xiang_backend.auth.api.dto.LoginRequest;
import com.czltnb.zhi_xiang_backend.auth.api.dto.PasswordResetRequest;
import com.czltnb.zhi_xiang_backend.auth.api.dto.RegisterRequest;
import com.czltnb.zhi_xiang_backend.auth.api.dto.SendCodeRequest;
import com.czltnb.zhi_xiang_backend.auth.api.dto.SendCodeResponse;
import com.czltnb.zhi_xiang_backend.auth.api.dto.TokenRefreshRequest;
import com.czltnb.zhi_xiang_backend.auth.api.dto.TokenResponse;
import com.czltnb.zhi_xiang_backend.auth.config.AuthProperties;
import com.czltnb.zhi_xiang_backend.auth.model.ClientInfo;
import com.czltnb.zhi_xiang_backend.auth.model.IdentifierType;
import com.czltnb.zhi_xiang_backend.auth.token.TokenPair;
import com.czltnb.zhi_xiang_backend.auth.token.RefreshTokenStore;
import com.czltnb.zhi_xiang_backend.auth.token.JwtService;
import com.czltnb.zhi_xiang_backend.auth.util.IdentifierValidator;
import com.czltnb.zhi_xiang_backend.auth.verification.SendCodeResult;
import com.czltnb.zhi_xiang_backend.auth.verification.VerificationCheckResult;
import com.czltnb.zhi_xiang_backend.auth.verification.VerificationCodeStatus;
import com.czltnb.zhi_xiang_backend.auth.verification.VerificationScene;
import com.czltnb.zhi_xiang_backend.auth.verification.VerificationService;
import com.czltnb.zhi_xiang_backend.common.exception.BusinessException;
import com.czltnb.zhi_xiang_backend.common.exception.ErrorCode;
import com.czltnb.zhi_xiang_backend.user.domain.User;
import com.czltnb.zhi_xiang_backend.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 认证业务服务。
 * <p>
 * 职责：发送验证码、注册、登录、刷新令牌、登出、重置密码、查询当前用户信息。
 * <p>
 * 安全策略：
 * <ul>
 *   <li>账号格式校验（手机号/邮箱）；</li>
 *   <li>验证码状态检查（过期/错误/尝试超限）；</li>
 *   <li>密码复杂度校验（长度与字符类型）；</li>
 *   <li>Refresh Token 白名单存储与轮换，登出/重置密码后失效旧令牌。</li>
 * </ul>
 * <p>
 * 依赖：UserService、VerificationService、PasswordEncoder、JwtService、
 * RefreshTokenStore、AuthProperties。
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserService userService;
    private final VerificationService verificationService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenStore refreshTokenStore;
    private final AuthProperties authProperties;

    // ==================== 发送验证码 ====================

    /**
     * 发送验证码并返回过期信息。
     * <p>
     * 注册场景要求标识不存在；登录/重置密码场景要求标识存在。
     */
    public SendCodeResponse sendCode(SendCodeRequest request) {
        validateIdentifier(request.identifierType(), request.identifier());
        String normalized = normalizeIdentifier(request.identifierType(), request.identifier());

        boolean exists = identifierExists(request.identifierType(), normalized);
        if (request.scene() == VerificationScene.REGISTER && exists) {
            throw new BusinessException(ErrorCode.IDENTIFIER_EXISTS);
        }
        if ((request.scene() == VerificationScene.LOGIN
                || request.scene() == VerificationScene.RESET_PASSWORD) && !exists) {
            throw new BusinessException(ErrorCode.IDENTIFIER_NOT_FOUND);
        }

        SendCodeResult result = verificationService.sendCode(request.scene(), normalized);
        return new SendCodeResponse(result.identifier(), result.scene(), result.expireSeconds());
    }

    // ==================== 注册 ====================

    /**
     * 注册用户并签发令牌。
     * <p>
     * 验证标识与验证码，创建用户（可选设置密码），签发令牌对并保存刷新令牌白名单。
     */
    public AuthResponse register(RegisterRequest request, ClientInfo clientInfo) {
        if (!request.agreeTerms()) {
            throw new BusinessException(ErrorCode.TERMS_NOT_ACCEPTED);
        }
        validateIdentifier(request.identifierType(), request.identifier());
        String identifier = normalizeIdentifier(request.identifierType(), request.identifier());

        if (identifierExists(request.identifierType(), identifier)) {
            throw new BusinessException(ErrorCode.IDENTIFIER_EXISTS);
        }
        ensureVerificationSuccess(
                verificationService.verify(VerificationScene.REGISTER, identifier, request.code()));

        // 构建用户
        User user = User.builder()
                .phone(request.identifierType() == IdentifierType.PHONE ? identifier : null)
                .email(request.identifierType() == IdentifierType.EMAIL ? identifier : null)
                .nickname(generateNickname())
                .avatar("https://static.zhiguang.cn/default-avatar.png")
                .bio(null)
                .tagsJson("[]")
                .build();

        if (StringUtils.hasText(request.password())) {
            validatePassword(request.password());
            user.setPasswordHash(passwordEncoder.encode(request.password().trim()));
        }

        userService.createUser(user);
        TokenPair tokenPair = jwtService.issueTokenPair(user);
        storeRefreshToken(user.getId(), tokenPair);

        return new AuthResponse(mapUser(user), mapToken(tokenPair));
    }

    // ==================== 登录 ====================

    /**
     * 登录并签发令牌。
     * <p>
     * 支持密码或验证码通道；成功签发令牌对并保存刷新令牌白名单。
     */
    public AuthResponse login(LoginRequest request, ClientInfo clientInfo) {
        validateIdentifier(request.identifierType(), request.identifier());
        String identifier = normalizeIdentifier(request.identifierType(), request.identifier());

        Optional<User> userOptional = findUserByIdentifier(request.identifierType(), identifier);
        if (userOptional.isEmpty()) {
            throw new BusinessException(ErrorCode.IDENTIFIER_NOT_FOUND);
        }
        User user = userOptional.get();

        if (StringUtils.hasText(request.password())) {
            // 密码通道
            if (!StringUtils.hasText(user.getPasswordHash())
                    || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
                throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
            }
        } else if (StringUtils.hasText(request.code())) {
            // 验证码通道
            ensureVerificationSuccess(
                    verificationService.verify(VerificationScene.LOGIN, identifier, request.code()));
        } else {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请提供验证码或密码");
        }

        TokenPair tokenPair = jwtService.issueTokenPair(user);
        storeRefreshToken(user.getId(), tokenPair);

        return new AuthResponse(mapUser(user), mapToken(tokenPair));
    }

    // ==================== 刷新令牌 ====================

    /**
     * 使用刷新令牌获取新的令牌对。
     * <p>
     * 校验刷新令牌类型与白名单有效性，轮换旧令牌。
     */
    public TokenResponse refresh(TokenRefreshRequest request) {
        Jwt jwt = decodeRefreshToken(request.refreshToken());

        if (!Objects.equals("refresh", jwtService.extractTokenType(jwt))) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        long userId = jwtService.extractUserId(jwt);
        String tokenId = jwtService.extractTokenId(jwt);

        if (!refreshTokenStore.isTokenValid(userId, tokenId)) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        User user = findUserById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.IDENTIFIER_NOT_FOUND));
        TokenPair tokenPair = jwtService.issueTokenPair(user);

        // 轮换：撤销旧的，保存新的
        refreshTokenStore.revokeToken(userId, tokenId);
        storeRefreshToken(userId, tokenPair);

        return mapToken(tokenPair);
    }

    // ==================== 登出 ====================

    /**
     * 登出：撤销指定刷新令牌。
     */
    public void logout(String refreshToken) {
        decodeRefreshTokenSafely(refreshToken).ifPresent(jwt -> {
            if (Objects.equals("refresh", jwtService.extractTokenType(jwt))) {
                long userId = jwtService.extractUserId(jwt);
                String tokenId = jwtService.extractTokenId(jwt);
                refreshTokenStore.revokeToken(userId, tokenId);
            }
        });
    }

    // ==================== 重置密码 ====================

    /**
     * 使用验证码重置密码并使所有刷新令牌失效。
     */
    public void resetPassword(PasswordResetRequest request) {
        validateIdentifier(request.identifierType(), request.identifier());
        validatePassword(request.newPassword());
        String identifier = normalizeIdentifier(request.identifierType(), request.identifier());

        User user = findUserByIdentifier(request.identifierType(), identifier)
                .orElseThrow(() -> new BusinessException(ErrorCode.IDENTIFIER_NOT_FOUND));

        ensureVerificationSuccess(
                verificationService.verify(VerificationScene.RESET_PASSWORD, identifier, request.code()));

        user.setPasswordHash(passwordEncoder.encode(request.newPassword().trim()));
        userService.updatePassword(user);
        refreshTokenStore.revokeAll(user.getId());
    }

    // ==================== 查询当前用户 ====================

    /**
     * 查询用户概要信息。
     */
    public AuthUserResponse me(long userId) {
        User user = findUserById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.IDENTIFIER_NOT_FOUND));
        return mapUser(user);
    }

    // ==================== 私有辅助方法 ====================

    private void ensureVerificationSuccess(VerificationCheckResult result) {
        if (result.isSuccess()) return;
        VerificationCodeStatus status = result.status();
        if (status == VerificationCodeStatus.NOT_FOUND || status == VerificationCodeStatus.EXPIRED) {
            throw new BusinessException(ErrorCode.VERIFICATION_NOT_FOUND);
        }
        if (status == VerificationCodeStatus.MISMATCH) {
            throw new BusinessException(ErrorCode.VERIFICATION_MISMATCH);
        }
        if (status == VerificationCodeStatus.TOO_MANY_ATTEMPTS) {
            throw new BusinessException(ErrorCode.VERIFICATION_TOO_MANY_ATTEMPTS);
        }
        throw new BusinessException(ErrorCode.BAD_REQUEST, "验证码校验失败");
    }

    private void validateIdentifier(IdentifierType type, String identifier) {
        if (type == IdentifierType.PHONE && !IdentifierValidator.isValidPhone(identifier)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "手机号格式错误");
        }
        if (type == IdentifierType.EMAIL && !IdentifierValidator.isValidEmail(identifier)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "邮箱格式错误");
        }
    }

    private void validatePassword(String password) {
        if (!StringUtils.hasText(password)) {
            throw new BusinessException(ErrorCode.PASSWORD_POLICY_VIOLATION, "密码不能为空");
        }
        String trimmed = password.trim();
        if (trimmed.length() < 8) {
            throw new BusinessException(ErrorCode.PASSWORD_POLICY_VIOLATION, "密码长度至少8位");
        }
        boolean hasLetter = trimmed.chars().anyMatch(Character::isLetter);
        boolean hasDigit = trimmed.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw new BusinessException(ErrorCode.PASSWORD_POLICY_VIOLATION, "密码需包含字母和数字");
        }
    }

    private boolean identifierExists(IdentifierType type, String identifier) {
        return switch (type) {
            case PHONE -> userService.existsByPhone(identifier);
            case EMAIL -> userService.existsByEmail(identifier);
        };
    }

    private Optional<User> findUserByIdentifier(IdentifierType type, String identifier) {
        return switch (type) {
            case PHONE -> userService.findByPhone(identifier);
            case EMAIL -> userService.findByEmail(identifier);
        };
    }

    private Optional<User> findUserById(long userId) {
        return userService.findById(userId);
    }

    private String normalizeIdentifier(IdentifierType type, String identifier) {
        return switch (type) {
            case PHONE -> identifier.trim();
            case EMAIL -> identifier.trim().toLowerCase(Locale.ROOT);
        };
    }

    private void storeRefreshToken(Long userId, TokenPair tokenPair) {
        Duration ttl = Duration.between(Instant.now(), tokenPair.refreshTokenExpiresAt());
        if (ttl.isNegative()) ttl = Duration.ZERO;
        refreshTokenStore.storeToken(userId, tokenPair.refreshTokenId(), ttl);
    }

    private AuthUserResponse mapUser(User user) {
        return new AuthUserResponse(
                user.getId(), user.getNickname(), user.getAvatar(), user.getPhone(),
                user.getZgId(), user.getBirthday(), user.getSchool(), user.getBio(),
                user.getGender(), user.getTagsJson());
    }

    private TokenResponse mapToken(TokenPair tokenPair) {
        return new TokenResponse(
                tokenPair.accessToken(), tokenPair.accessTokenExpiresAt(),
                tokenPair.refreshToken(), tokenPair.refreshTokenExpiresAt());
    }

    private String generateNickname() {
        return "知光用户" + UUID.randomUUID().toString().substring(0, 8);
    }

    private Jwt decodeRefreshToken(String refreshToken) {
        try {
            return jwtService.decode(refreshToken);
        } catch (JwtException ex) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
    }

    private Optional<Jwt> decodeRefreshTokenSafely(String refreshToken) {
        try {
            return Optional.of(jwtService.decode(refreshToken));
        } catch (JwtException ex) {
            return Optional.empty();
        }
    }
}
