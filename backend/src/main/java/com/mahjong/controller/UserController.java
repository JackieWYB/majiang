package com.mahjong.controller;

import com.mahjong.model.dto.ApiResponse;
import com.mahjong.model.dto.UserStatisticsResponse;
import com.mahjong.model.entity.User;
import com.mahjong.service.GameHistoryService;
import com.mahjong.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for user profile management operations
 */
@RestController
@RequestMapping("/api/users")
public class UserController {
    
    private static final Logger logger = LoggerFactory.getLogger(UserController.class);
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private GameHistoryService gameHistoryService;
    
    /**
     * Get current user profile
     */
    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getCurrentUserProfile(Authentication authentication) {
        try {
            User user = userService.getCurrentUser(authentication);
            UserProfileResponse response = new UserProfileResponse(user);
            
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (Exception e) {
            logger.error("Error getting current user profile", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to get user profile"));
        }
    }
    
    /**
     * Update current user profile
     */
    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateCurrentUserProfile(
            @Valid @RequestBody UpdateUserProfileRequest request,
            Authentication authentication) {
        
        try {
            User currentUser = userService.getCurrentUser(authentication);
            
            User updatedUser = userService.updateProfile(
                    currentUser.getId(),
                    request.getNickname(),
                    request.getAvatar()
            );
            
            UserProfileResponse response = new UserProfileResponse(updatedUser);
            
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (UserService.UserNotFoundException e) {
            logger.error("User not found during profile update", e);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("Error updating user profile", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to update user profile"));
        }
    }
    
    /**
     * Get user profile by ID (public info only)
     */
    @GetMapping("/{userId}/profile")
    public ResponseEntity<ApiResponse<PublicUserProfileResponse>> getUserProfile(@PathVariable Long userId) {
        try {
            User user = userService.findById(userId)
                    .orElseThrow(() -> new UserService.UserNotFoundException("User not found: " + userId));
            
            PublicUserProfileResponse response = new PublicUserProfileResponse(user);
            
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (UserService.UserNotFoundException e) {
            logger.warn("User not found: {}", userId);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("Error getting user profile for user: {}", userId, e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to get user profile"));
        }
    }
    
    /**
     * Get current user statistics
     */
    @GetMapping("/statistics")
    public ResponseEntity<ApiResponse<UserStatisticsResponse>> getCurrentUserStatistics(Authentication authentication) {
        try {
            User user = userService.getCurrentUser(authentication);
            Map<String, Object> stats = gameHistoryService.getEnhancedUserStatistics(user.getId());
            
            UserStatisticsResponse response = convertToUserStatisticsResponse(user.getId(), stats);
            
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (Exception e) {
            logger.error("Error getting current user statistics", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to get user statistics"));
        }
    }
    
    /**
     * Get user statistics by ID
     */
    @GetMapping("/{userId}/statistics")
    public ResponseEntity<ApiResponse<UserStatisticsResponse>> getUserStatistics(
            @PathVariable Long userId,
            Authentication authentication) {
        
        try {
            // Check if user can access this data (own data or admin)
            if (!canAccessUserData(userId, authentication)) {
                return ResponseEntity.status(403)
                        .body(ApiResponse.error("Access denied"));
            }
            
            Map<String, Object> stats = gameHistoryService.getEnhancedUserStatistics(userId);
            UserStatisticsResponse response = convertToUserStatisticsResponse(userId, stats);
            
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (Exception e) {
            logger.error("Error getting user statistics for user: {}", userId, e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to get user statistics"));
        }
    }
    
    /**
     * Update user coins (admin only or game system)
     */
    @PutMapping("/{userId}/coins")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateUserCoins(
            @PathVariable Long userId,
            @Valid @RequestBody UpdateCoinsRequest request,
            Authentication authentication) {
        
        try {
            User currentUser = userService.getCurrentUser(authentication);
            
            // Only allow admin or system to update coins
            if (!currentUser.isAdmin() && !currentUser.getId().equals(userId)) {
                return ResponseEntity.status(403)
                        .body(ApiResponse.error("Access denied"));
            }
            
            User updatedUser = userService.updateCoins(userId, request.getCoins());
            UserProfileResponse response = new UserProfileResponse(updatedUser);
            
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (UserService.UserNotFoundException e) {
            logger.warn("User not found: {}", userId);
            return ResponseEntity.notFound().build();
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid coins update request: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.error("Error updating user coins for user: {}", userId, e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to update user coins"));
        }
    }
    
    /**
     * Update user room cards (admin only or game system)
     */
    @PutMapping("/{userId}/room-cards")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateUserRoomCards(
            @PathVariable Long userId,
            @Valid @RequestBody UpdateRoomCardsRequest request,
            Authentication authentication) {
        
        try {
            User currentUser = userService.getCurrentUser(authentication);
            
            // Only allow admin or system to update room cards
            if (!currentUser.isAdmin() && !currentUser.getId().equals(userId)) {
                return ResponseEntity.status(403)
                        .body(ApiResponse.error("Access denied"));
            }
            
            User updatedUser = userService.updateRoomCards(userId, request.getRoomCards());
            UserProfileResponse response = new UserProfileResponse(updatedUser);
            
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (UserService.UserNotFoundException e) {
            logger.warn("User not found: {}", userId);
            return ResponseEntity.notFound().build();
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid room cards update request: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.error("Error updating user room cards for user: {}", userId, e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to update user room cards"));
        }
    }
    
    /**
     * Check if user can access data (own data or admin)
     */
    private boolean canAccessUserData(Long userId, Authentication authentication) {
        try {
            User currentUser = userService.getCurrentUser(authentication);
            
            // Users can access their own data
            if (currentUser.getId().equals(userId)) {
                return true;
            }
            
            // Admin can access any user data
            return currentUser.isAdmin();
            
        } catch (Exception e) {
            logger.error("Error checking user access", e);
            return false;
        }
    }
    
    /**
     * Convert statistics map to UserStatisticsResponse
     */
    private UserStatisticsResponse convertToUserStatisticsResponse(Long userId, Map<String, Object> stats) {
        UserStatisticsResponse response = new UserStatisticsResponse();
        response.setUserId(userId);
        response.setTotalGames(getLongValue(stats, "totalGames"));
        response.setWins(getLongValue(stats, "wins"));
        response.setLosses(getLongValue(stats, "losses"));
        response.setDraws(getLongValue(stats, "draws"));
        response.setWinRate(getDoubleValue(stats, "winRate"));
        response.setTotalScore(getLongValue(stats, "totalScore"));
        response.setAverageScore(getDoubleValue(stats, "averageScore"));
        response.setDealerGames(getLongValue(stats, "dealerGames"));
        response.setDealerWins(getLongValue(stats, "dealerWins"));
        response.setDealerWinRate(getDoubleValue(stats, "dealerWinRate"));
        response.setSelfDrawWins(getLongValue(stats, "selfDrawWins"));
        response.setLongestWinStreak(getIntegerValue(stats, "longestWinStreak"));
        response.setCurrentWinStreak(getIntegerValue(stats, "currentWinStreak"));
        
        return response;
    }
    
    /**
     * Helper method to safely get Long value from map
     */
    private Long getLongValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return 0L;
    }
    
    /**
     * Helper method to safely get Double value from map
     */
    private Double getDoubleValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return 0.0;
    }
    
    /**
     * Helper method to safely get Integer value from map
     */
    private Integer getIntegerValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return 0;
    }
    
    // Request/Response DTOs
    
    public static class UpdateUserProfileRequest {
        @Size(max = 50, message = "Nickname must not exceed 50 characters")
        private String nickname;
        
        @Size(max = 500, message = "Avatar URL must not exceed 500 characters")
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
    
    public static class UpdateCoinsRequest {
        @jakarta.validation.constraints.Min(value = 0, message = "Coins must be non-negative")
        private Integer coins;
        
        public Integer getCoins() {
            return coins;
        }
        
        public void setCoins(Integer coins) {
            this.coins = coins;
        }
    }
    
    public static class UpdateRoomCardsRequest {
        @jakarta.validation.constraints.Min(value = 0, message = "Room cards must be non-negative")
        private Integer roomCards;
        
        public Integer getRoomCards() {
            return roomCards;
        }
        
        public void setRoomCards(Integer roomCards) {
            this.roomCards = roomCards;
        }
    }
    
    public static class UserProfileResponse {
        private final Long id;
        private final String openId;
        private final String unionId;
        private final String nickname;
        private final String avatar;
        private final Integer coins;
        private final Integer roomCards;
        private final String status;
        private final String role;
        
        public UserProfileResponse(User user) {
            this.id = user.getId();
            this.openId = user.getOpenId();
            this.unionId = user.getUnionId();
            this.nickname = user.getNickname();
            this.avatar = user.getAvatar();
            this.coins = user.getCoins();
            this.roomCards = user.getRoomCards();
            this.status = user.getStatus().name();
            this.role = user.getRole().name();
        }
        
        public Long getId() { return id; }
        public String getOpenId() { return openId; }
        public String getUnionId() { return unionId; }
        public String getNickname() { return nickname; }
        public String getAvatar() { return avatar; }
        public Integer getCoins() { return coins; }
        public Integer getRoomCards() { return roomCards; }
        public String getStatus() { return status; }
        public String getRole() { return role; }
    }
    
    public static class PublicUserProfileResponse {
        private final Long id;
        private final String nickname;
        private final String avatar;
        private final String status;
        
        public PublicUserProfileResponse(User user) {
            this.id = user.getId();
            this.nickname = user.getNickname();
            this.avatar = user.getAvatar();
            this.status = user.getStatus().name();
        }
        
        public Long getId() { return id; }
        public String getNickname() { return nickname; }
        public String getAvatar() { return avatar; }
        public String getStatus() { return status; }
    }
}