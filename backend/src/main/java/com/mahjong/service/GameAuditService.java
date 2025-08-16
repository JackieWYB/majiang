package com.mahjong.service;

import com.mahjong.model.entity.AuditLog;
import com.mahjong.repository.AuditLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Service for game audit and seed preservation
 */
@Service
public class GameAuditService {

    private static final Logger logger = LoggerFactory.getLogger(GameAuditService.class);

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Preserve game seed for audit purposes
     */
    public void preserveGameSeed(String gameId, String roomId, long seed, String shuffleAlgorithm) {
        try {
            Map<String, Object> seedData = new HashMap<>();
            seedData.put("gameId", gameId);
            seedData.put("roomId", roomId);
            seedData.put("seed", seed);
            seedData.put("algorithm", shuffleAlgorithm);
            seedData.put("timestamp", LocalDateTime.now().toString());

            // Store in Redis with long TTL (30 days)
            String redisKey = "game_seed:" + gameId;
            redisTemplate.opsForValue().set(redisKey, seedData, 30, TimeUnit.DAYS);

            // Also save to audit log for permanent record
            AuditLog auditLog = new AuditLog();
            auditLog.setAdminId(0L);
            auditLog.setAdminName("System");
            auditLog.setAction("GAME_SEED_PRESERVED");
            auditLog.setTargetType("GAME");
            auditLog.setTargetId(gameId);
            auditLog.setDetails(objectMapper.writeValueAsString(seedData));
            auditLog.setCreatedAt(LocalDateTime.now());
            auditLogRepository.save(auditLog);

            logger.info("Game seed preserved for audit - GameId: {}, RoomId: {}, Seed: {}", gameId, roomId, seed);

        } catch (Exception e) {
            logger.error("Failed to preserve game seed for gameId: {}", gameId, e);
        }
    }

    /**
     * Log game action for audit trail
     */
    public void logGameAction(String gameId, String userId, String action, Object actionData, String ipAddress) {
        try {
            Map<String, Object> auditData = new HashMap<>();
            auditData.put("gameId", gameId);
            auditData.put("userId", userId);
            auditData.put("action", action);
            auditData.put("actionData", actionData);
            auditData.put("timestamp", LocalDateTime.now().toString());

            AuditLog auditLog = new AuditLog();
            auditLog.setAdminId(userId != null ? Long.parseLong(userId) : 0L);
            auditLog.setAdminName(userId != null ? "User-" + userId : "System");
            auditLog.setAction("GAME_ACTION");
            auditLog.setTargetType("GAME");
            auditLog.setTargetId(gameId);
            auditLog.setDetails(objectMapper.writeValueAsString(auditData));
            auditLog.setIpAddress(ipAddress);
            auditLog.setCreatedAt(LocalDateTime.now());
            auditLogRepository.save(auditLog);

            // Also store in Redis for quick access during game
            String redisKey = "game_actions:" + gameId;
            redisTemplate.opsForList().rightPush(redisKey, auditData);
            redisTemplate.expire(redisKey, 7, TimeUnit.DAYS);

        } catch (Exception e) {
            logger.error("Failed to log game action - GameId: {}, UserId: {}, Action: {}", gameId, userId, action, e);
        }
    }

    /**
     * Log game state change for audit
     */
    public void logGameStateChange(String gameId, String changeType, Object oldState, Object newState, String triggeredBy) {
        try {
            Map<String, Object> stateChangeData = new HashMap<>();
            stateChangeData.put("gameId", gameId);
            stateChangeData.put("changeType", changeType);
            stateChangeData.put("oldState", oldState);
            stateChangeData.put("newState", newState);
            stateChangeData.put("triggeredBy", triggeredBy);
            stateChangeData.put("timestamp", LocalDateTime.now().toString());

            AuditLog auditLog = new AuditLog();
            auditLog.setAdminId(0L);
            auditLog.setAdminName("System");
            auditLog.setAction("GAME_STATE_CHANGE");
            auditLog.setTargetType("GAME");
            auditLog.setTargetId(gameId);
            auditLog.setDetails(objectMapper.writeValueAsString(stateChangeData));
            auditLog.setCreatedAt(LocalDateTime.now());
            auditLogRepository.save(auditLog);

        } catch (Exception e) {
            logger.error("Failed to log game state change for gameId: {}", gameId, e);
        }
    }

    /**
     * Log security event
     */
    public void logSecurityEvent(String userId, String eventType, String description, String ipAddress, String severity) {
        try {
            Map<String, Object> securityData = new HashMap<>();
            securityData.put("userId", userId);
            securityData.put("eventType", eventType);
            securityData.put("description", description);
            securityData.put("ipAddress", ipAddress);
            securityData.put("severity", severity);
            securityData.put("timestamp", LocalDateTime.now().toString());

            AuditLog auditLog = new AuditLog();
            auditLog.setAdminId(userId != null ? Long.parseLong(userId) : 0L);
            auditLog.setAdminName(userId != null ? "User-" + userId : "System");
            auditLog.setAction("SECURITY_EVENT");
            auditLog.setTargetType("SECURITY");
            auditLog.setTargetId(userId);
            auditLog.setDetails(objectMapper.writeValueAsString(securityData));
            auditLog.setIpAddress(ipAddress);
            auditLog.setCreatedAt(LocalDateTime.now());
            auditLogRepository.save(auditLog);

            // Log with appropriate level based on severity
            switch (severity.toUpperCase()) {
                case "HIGH":
                    logger.error("HIGH SEVERITY SECURITY EVENT - User: {}, Type: {}, Description: {}, IP: {}", 
                            userId, eventType, description, ipAddress);
                    break;
                case "MEDIUM":
                    logger.warn("MEDIUM SEVERITY SECURITY EVENT - User: {}, Type: {}, Description: {}, IP: {}", 
                            userId, eventType, description, ipAddress);
                    break;
                default:
                    logger.info("SECURITY EVENT - User: {}, Type: {}, Description: {}, IP: {}", 
                            userId, eventType, description, ipAddress);
            }

        } catch (Exception e) {
            logger.error("Failed to log security event - UserId: {}, EventType: {}", userId, eventType, e);
        }
    }

    /**
     * Retrieve game seed for verification
     */
    public Map<String, Object> retrieveGameSeed(String gameId) {
        try {
            String redisKey = "game_seed:" + gameId;
            Object seedData = redisTemplate.opsForValue().get(redisKey);
            
            if (seedData != null) {
                return (Map<String, Object>) seedData;
            }
            
            logger.warn("Game seed not found in Redis for gameId: {}", gameId);
            return null;
            
        } catch (Exception e) {
            logger.error("Failed to retrieve game seed for gameId: {}", gameId, e);
            return null;
        }
    }

    /**
     * Verify game integrity using preserved seed
     */
    public boolean verifyGameIntegrity(String gameId, long expectedSeed) {
        try {
            Map<String, Object> seedData = retrieveGameSeed(gameId);
            if (seedData == null) {
                logger.warn("Cannot verify game integrity - seed data not found for gameId: {}", gameId);
                return false;
            }

            Long preservedSeed = (Long) seedData.get("seed");
            boolean isValid = preservedSeed != null && preservedSeed.equals(expectedSeed);
            
            if (!isValid) {
                logSecurityEvent(null, "GAME_INTEGRITY_VIOLATION", 
                        String.format("Game integrity check failed - GameId: %s, Expected: %d, Found: %s", 
                                gameId, expectedSeed, preservedSeed), 
                        null, "HIGH");
            }
            
            return isValid;
            
        } catch (Exception e) {
            logger.error("Failed to verify game integrity for gameId: {}", gameId, e);
            return false;
        }
    }

    /**
     * Log admin action for audit
     */
    public void logAdminAction(String adminUserId, String action, String targetUserId, String details, String ipAddress) {
        try {
            Map<String, Object> adminData = new HashMap<>();
            adminData.put("adminUserId", adminUserId);
            adminData.put("action", action);
            adminData.put("targetUserId", targetUserId);
            adminData.put("details", details);
            adminData.put("timestamp", LocalDateTime.now().toString());

            AuditLog auditLog = new AuditLog();
            auditLog.setAdminId(Long.parseLong(adminUserId));
            auditLog.setAdminName("Admin-" + adminUserId);
            auditLog.setAction("ADMIN_ACTION");
            auditLog.setTargetType("USER");
            auditLog.setTargetId(targetUserId);
            auditLog.setDetails(objectMapper.writeValueAsString(adminData));
            auditLog.setIpAddress(ipAddress);
            auditLog.setCreatedAt(LocalDateTime.now());
            auditLogRepository.save(auditLog);

            logger.info("Admin action logged - Admin: {}, Action: {}, Target: {}, Details: {}", 
                    adminUserId, action, targetUserId, details);

        } catch (Exception e) {
            logger.error("Failed to log admin action - AdminId: {}, Action: {}", adminUserId, action, e);
        }
    }
}