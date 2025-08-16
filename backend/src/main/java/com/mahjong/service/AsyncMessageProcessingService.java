package com.mahjong.service;

import com.mahjong.model.dto.GameMessage;
import com.mahjong.model.entity.GameRecord;
import com.mahjong.model.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Asynchronous Message Processing Service
 * 
 * Handles background processing of game events, analytics, and notifications
 * to improve real-time game performance by offloading non-critical operations.
 */
@Service
public class AsyncMessageProcessingService {

    private static final Logger logger = LoggerFactory.getLogger(AsyncMessageProcessingService.class);

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private GameAuditService gameAuditService;

    @Autowired
    private CacheService cacheService;

    /**
     * Process game analytics asynchronously
     */
    @Async("analyticsTaskExecutor")
    public CompletableFuture<Void> processGameAnalytics(String roomId, GameMessage message) {
        try {
            logger.debug("Processing game analytics for room: {}", roomId);
            
            // Update game statistics
            updateGameStatistics(roomId, message);
            
            // Update player statistics
            updatePlayerStatistics(message);
            
            // Store analytics data in Redis for real-time dashboards
            storeAnalyticsData(roomId, message);
            
            logger.debug("Game analytics processed successfully for room: {}", roomId);
        } catch (Exception e) {
            logger.error("Error processing game analytics for room: {}", roomId, e);
        }
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Process notifications asynchronously
     */
    @Async("notificationTaskExecutor")
    public CompletableFuture<Void> processNotifications(List<String> userIds, String message, String type) {
        try {
            logger.debug("Processing notifications for {} users", userIds.size());
            
            for (String userId : userIds) {
                // Queue notification for user
                queueNotification(userId, message, type);
            }
            
            logger.debug("Notifications queued successfully for {} users", userIds.size());
        } catch (Exception e) {
            logger.error("Error processing notifications", e);
        }
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Process game audit logs asynchronously
     */
    @Async("gameTaskExecutor")
    public CompletableFuture<Void> processAuditLog(String roomId, String userId, String action, Object data) {
        try {
            logger.debug("Processing audit log for room: {}, user: {}, action: {}", roomId, userId, action);
            
            // Store audit log
            gameAuditService.logGameAction(userId, action, roomId, data);
            
            // Check for suspicious activity patterns
            checkSuspiciousActivity(userId, action);
            
            logger.debug("Audit log processed successfully");
        } catch (Exception e) {
            logger.error("Error processing audit log", e);
        }
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Process cache updates asynchronously
     */
    @Async("cacheTaskExecutor")
    public CompletableFuture<Void> processCacheUpdate(String cacheType, String key, Object data) {
        try {
            logger.debug("Processing cache update for type: {}, key: {}", cacheType, key);
            
            switch (cacheType) {
                case "user-stats":
                    cacheService.evictUserCache(Long.parseLong(key), (User) data);
                    break;
                case "room-data":
                    cacheService.evictRoomCache(key);
                    break;
                case "system-metrics":
                    cacheService.evictSystemMetricsCache();
                    break;
                case "leaderboard":
                    cacheService.cacheLeaderboard(key, data);
                    break;
                default:
                    logger.warn("Unknown cache type: {}", cacheType);
            }
            
            logger.debug("Cache update processed successfully");
        } catch (Exception e) {
            logger.error("Error processing cache update", e);
        }
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Process cleanup tasks asynchronously
     */
    @Async("cleanupTaskExecutor")
    public CompletableFuture<Void> processCleanupTasks() {
        try {
            logger.debug("Processing cleanup tasks");
            
            // Clean up expired game states
            cleanupExpiredGameStates();
            
            // Clean up old session data
            cleanupOldSessions();
            
            // Clean up temporary cache entries
            cleanupTemporaryCache();
            
            logger.debug("Cleanup tasks completed successfully");
        } catch (Exception e) {
            logger.error("Error processing cleanup tasks", e);
        }
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Process batch operations asynchronously
     */
    @Async("gameTaskExecutor")
    public CompletableFuture<Void> processBatchOperation(String operationType, List<Object> items) {
        try {
            logger.debug("Processing batch operation: {} with {} items", operationType, items.size());
            
            switch (operationType) {
                case "game-records":
                    processBatchGameRecords(items);
                    break;
                case "user-updates":
                    processBatchUserUpdates(items);
                    break;
                case "statistics-update":
                    processBatchStatisticsUpdate(items);
                    break;
                default:
                    logger.warn("Unknown batch operation type: {}", operationType);
            }
            
            logger.debug("Batch operation completed successfully");
        } catch (Exception e) {
            logger.error("Error processing batch operation: {}", operationType, e);
        }
        return CompletableFuture.completedFuture(null);
    }

    // Private helper methods

    private void updateGameStatistics(String roomId, GameMessage message) {
        String key = "game-stats:" + roomId;
        redisTemplate.opsForHash().increment(key, "total-actions", 1);
        redisTemplate.opsForHash().increment(key, message.getCmd(), 1);
        redisTemplate.expire(key, Duration.ofHours(24));
    }

    private void updatePlayerStatistics(GameMessage message) {
        // Extract user ID from message and update player stats
        // This is a simplified implementation
        String key = "player-stats:daily:" + LocalDateTime.now().toLocalDate();
        redisTemplate.opsForHash().increment(key, "total-actions", 1);
        redisTemplate.expire(key, Duration.ofDays(7));
    }

    private void storeAnalyticsData(String roomId, GameMessage message) {
        String analyticsKey = "analytics:" + roomId + ":" + System.currentTimeMillis();
        redisTemplate.opsForValue().set(analyticsKey, message, Duration.ofHours(1));
    }

    private void queueNotification(String userId, String message, String type) {
        String notificationKey = "notifications:" + userId;
        NotificationMessage notification = new NotificationMessage(message, type, System.currentTimeMillis());
        redisTemplate.opsForList().leftPush(notificationKey, notification);
        redisTemplate.expire(notificationKey, Duration.ofDays(7));
    }

    private void checkSuspiciousActivity(String userId, String action) {
        String key = "suspicious-activity:" + userId;
        redisTemplate.opsForList().leftPush(key, action + ":" + System.currentTimeMillis());
        redisTemplate.expire(key, Duration.ofHours(1));
        
        // Check if user has too many actions in short time
        Long actionCount = redisTemplate.opsForList().size(key);
        if (actionCount != null && actionCount > 100) {
            logger.warn("Suspicious activity detected for user: {} with {} actions", userId, actionCount);
        }
    }

    private void cleanupExpiredGameStates() {
        // Clean up game states older than 2 hours
        String pattern = "game:*";
        redisTemplate.keys(pattern).forEach(key -> {
            Long ttl = redisTemplate.getExpire(key);
            if (ttl != null && ttl < 0) {
                redisTemplate.delete(key);
            }
        });
    }

    private void cleanupOldSessions() {
        // Clean up sessions older than 24 hours
        String pattern = "session:*";
        redisTemplate.keys(pattern).forEach(key -> {
            Long ttl = redisTemplate.getExpire(key);
            if (ttl != null && ttl < 0) {
                redisTemplate.delete(key);
            }
        });
    }

    private void cleanupTemporaryCache() {
        // Clean up temporary cache entries
        String pattern = "temp:*";
        redisTemplate.keys(pattern).forEach(key -> {
            Long ttl = redisTemplate.getExpire(key);
            if (ttl != null && ttl < 3600) { // Less than 1 hour TTL
                redisTemplate.delete(key);
            }
        });
    }

    private void processBatchGameRecords(List<Object> items) {
        // Process game records in batch
        logger.debug("Processing {} game records", items.size());
        // Implementation would batch insert/update game records
    }

    private void processBatchUserUpdates(List<Object> items) {
        // Process user updates in batch
        logger.debug("Processing {} user updates", items.size());
        // Implementation would batch update user data
    }

    private void processBatchStatisticsUpdate(List<Object> items) {
        // Process statistics updates in batch
        logger.debug("Processing {} statistics updates", items.size());
        // Implementation would batch update statistics
    }

    /**
     * Notification message data class
     */
    public static class NotificationMessage {
        private String message;
        private String type;
        private long timestamp;

        public NotificationMessage(String message, String type, long timestamp) {
            this.message = message;
            this.type = type;
            this.timestamp = timestamp;
        }

        // Getters and setters
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    }
}