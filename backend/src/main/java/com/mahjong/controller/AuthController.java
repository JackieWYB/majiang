package com.mahjong.controller;

import com.mahjong.model.dto.ApiResponse;
import com.mahjong.model.entity.User;
import com.mahjong.service.JwtTokenService;
import com.mahjong.service.UserService;
import com.mahjong.service.WeChatService;
import com.mahjong.service.InputValidationService;
import com.mahjong.service.SuspiciousActivityService;
import com.mahjong.service.GameAuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/**
 * Authentication controller for WeChat login and user profile management
 */
@RestController
@RequestMapping("/auth")
public class AuthController {
    
    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);
    
    private final WeChatService weChatService;
    private final UserService userService;
    private final JwtTokenService jwtTokenService;
    private final InputValidationService inputValidationService;
    private final SuspiciousActivityService suspiciousActivityService;
    private final GameAuditService gameAuditService;
    
    public AuthController(
            WeChatService weChatService,
            UserService userService,
            JwtTokenService jwtTokenService,
            InputValidationService inputValidationService,
            SuspiciousActivityService suspiciousActivityService,
            GameAuditService gameAuditService) {
        this.weChatService = weChatService;
        this.userService = userService;
        this.jwtTokenService = jwtTokenService;
        this.inputValidationService = inputValidationService;
        this.suspiciousActivityService = suspiciousActivityService;
        this.gameAuditService = gameAuditService;
    }
    
    /**
     * WeChat Mini Program login
     * 
     * @param request Login request containing WeChat code and user info
     * @return Login response with JWT tokens and user profile
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        String clientIp = getClientIpAddress(httpRequest);
        
        try {
            logger.info("Processing WeChat login for code: {}", request.getCode());
            
            // Validate and sanitize input
            String sanitizedNickname = null;
            if (request.getNickname() != null) {
                sanitizedNickname = inputValidationService.validateNickname(request.getNickname());
            }
            
            // Exchange WeChat code for session
            WeChatService.WeChatSession session = weChatService.code2Session(request.getCode());
            
            // Validate OpenID
            String validatedOpenId = inputValidationService.validateOpenId(session.getOpenId());
            
            // Create or update user
            User user = userService.createOrUpdateUser(
                    validatedOpenId,
                    session.getUnionId(),
                    sanitizedNickname,
                    request.getAvatar()
            );
            
            // Check if user is banned
            if (!userService.isUserActive(user.getId())) {
                logger.warn("Banned user attempted login: {}", user.getId());
                gameAuditService.logSecurityEvent(user.getId().toString(), "BANNED_USER_LOGIN_ATTEMPT", 
                        "Banned user attempted to login", clientIp, "HIGH");
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("USER_BANNED", "User account is banned"));
            }
            
            // Check for multiple accounts from same IP
            suspiciousActivityService.checkMultipleAccountsFromIP(clientIp, user.getId().toString());
            
            // Generate JWT tokens with role
            JwtTokenService.TokenPair tokenPair = jwtTokenService.generateTokenPair(
                    user.getId(),
                    user.getOpenId(),
                    user.getRole().name()
            );
            
            LoginResponse response = new LoginResponse(
                    tokenPair.getAccessToken(),
                    tokenPair.getRefreshToken(),
                    new UserProfile(user)
            );
            
            // Log successful login
            gameAuditService.logSecurityEvent(user.getId().toString(), "SUCCESSFUL_LOGIN", 
                    "User logged in successfully", clientIp, "LOW");
            
            logger.info("Successful login for user: {}", user.getId());
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid input during login: {}", e.getMessage());
            suspiciousActivityService.logSuspiciousActivity(null, clientIp, "INVALID_LOGIN_INPUT", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("INVALID_INPUT", "Invalid input provided"));
        } catch (WeChatService.WeChatAuthException e) {
            logger.error("WeChat authentication failed", e);
            gameAuditService.logSecurityEvent(null, "WECHAT_AUTH_FAILED", e.getMessage(), clientIp, "MEDIUM");
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("WECHAT_AUTH_FAILED", e.getMessage()));
        } catch (Exception e) {
            logger.error("Login failed", e);
            gameAuditService.logSecurityEvent(null, "LOGIN_ERROR", e.getMessage(), clientIp, "MEDIUM");
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("LOGIN_FAILED", "Login failed"));
        }
    }
    
    /**
     * Refresh JWT tokens
     * 
     * @param request Refresh request containing refresh token
     * @return New token pair
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(@Valid @RequestBody RefreshRequest request) {
        try {
            logger.debug("Processing token refresh");
            
            JwtTokenService.TokenPair tokenPair = jwtTokenService.refreshTokens(request.getRefreshToken());
            
            TokenResponse response = new TokenResponse(
                    tokenPair.getAccessToken(),
                    tokenPair.getRefreshToken()
            );
            
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (JwtTokenService.JwtTokenException e) {
            logger.error("Token refresh failed", e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("TOKEN_REFRESH_FAILED", e.getMessage()));
        } catch (Exception e) {
            logger.error("Token refresh failed", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("TOKEN_REFRESH_FAILED", "Token refresh failed"));
        }
    }
    
    /**
     * Get current user profile
     * 
     * @return User profile
     */
    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<UserProfile>> getProfile() {
        try {
            Long userId = getCurrentUserId();
            
            User user = userService.findById(userId)
                    .orElseThrow(() -> new UserService.UserNotFoundException("User not found: " + userId));
            
            UserProfile profile = new UserProfile(user);
            return ResponseEntity.ok(ApiResponse.success(profile));
            
        } catch (UserService.UserNotFoundException e) {
            logger.error("User not found", e);
            return ResponseEntity.notFound()
                    .build();
        } catch (Exception e) {
            logger.error("Failed to get user profile", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("PROFILE_FETCH_FAILED", "Failed to get user profile"));
        }
    }
    
    /**
     * Update user profile
     * 
     * @param request Profile update request
     * @return Updated user profile
     */
    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<UserProfile>> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        try {
            Long userId = getCurrentUserId();
            
            User user = userService.updateProfile(userId, request.getNickname(), request.getAvatar());
            
            UserProfile profile = new UserProfile(user);
            return ResponseEntity.ok(ApiResponse.success(profile));
            
        } catch (UserService.UserNotFoundException e) {
            logger.error("User not found", e);
            return ResponseEntity.notFound()
                    .build();
        } catch (Exception e) {
            logger.error("Failed to update user profile", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("PROFILE_UPDATE_FAILED", "Failed to update user profile"));
        }
    }
    
    /**
     * Logout (invalidate token on client side)
     * 
     * @return Success response
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout() {
        // In a stateless JWT system, logout is handled client-side by discarding the token
        // For enhanced security, you could maintain a token blacklist in Redis
        logger.info("User logout: {}", getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success(null));
    }
    
    /**
     * Get current authenticated user ID
     */
    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return Long.parseLong(authentication.getName());
    }
    
    /**
     * Get client IP address from request
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
    
    // Request/Response DTOs
    
    public static class LoginRequest {
        @NotBlank(message = "WeChat code is required")
        private String code;
        
        private String nickname;
        private String avatar;
        
        public String getCode() {
            return code;
        }
        
        public void setCode(String code) {
            this.code = code;
        }
        
        public String getNickname() {
            return nickname;
        }
        
        public void setNickname(String nickname) {
            this.nickname = nickname;
        }
        
        public String getAvatar() {
            return avatar;
        }
        
        public void setAvatar(String avatar) {
            this.avatar = avatar;
        }
    }
    
    public static class RefreshRequest {
        @NotBlank(message = "Refresh token is required")
        private String refreshToken;
        
        public String getRefreshToken() {
            return refreshToken;
        }
        
        public void setRefreshToken(String refreshToken) {
            this.refreshToken = refreshToken;
        }
    }
    
    public static class UpdateProfileRequest {
        private String nickname;
        private String avatar;
        
        public String getNickname() {
            return nickname;
        }
        
        public void setNickname(String nickname) {
            this.nickname = nickname;
        }
        
        public String getAvatar() {
            return avatar;
        }
        
        public void setAvatar(String avatar) {
            this.avatar = avatar;
        }
    }
    
    public static class LoginResponse {
        private final String accessToken;
        private final String refreshToken;
        private final UserProfile user;
        
        public LoginResponse(String accessToken, String refreshToken, UserProfile user) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.user = user;
        }
        
        public String getAccessToken() {
            return accessToken;
        }
        
        public String getRefreshToken() {
            return refreshToken;
        }
        
        public UserProfile getUser() {
            return user;
        }
    }
    
    public static class TokenResponse {
        private final String accessToken;
        private final String refreshToken;
        
        public TokenResponse(String accessToken, String refreshToken) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
        }
        
        public String getAccessToken() {
            return accessToken;
        }
        
        public String getRefreshToken() {
            return refreshToken;
        }
    }
    
    public static class UserProfile {
        private final Long id;
        private final String openId;
        private final String unionId;
        private final String nickname;
        private final String avatar;
        private final Integer coins;
        private final Integer roomCards;
        private final String status;
        
        public UserProfile(User user) {
            this.id = user.getId();
            this.openId = user.getOpenId();
            this.unionId = user.getUnionId();
            this.nickname = user.getNickname();
            this.avatar = user.getAvatar();
            this.coins = user.getCoins();
            this.roomCards = user.getRoomCards();
            this.status = user.getStatus().name();
        }
        
        public Long getId() {
            return id;
        }
        
        public String getOpenId() {
            return openId;
        }
        
        public String getUnionId() {
            return unionId;
        }
        
        public String getNickname() {
            return nickname;
        }
        
        public String getAvatar() {
            return avatar;
        }
        
        public Integer getCoins() {
            return coins;
        }
        
        public Integer getRoomCards() {
            return roomCards;
        }
        
        public String getStatus() {
            return status;
        }
    }
}