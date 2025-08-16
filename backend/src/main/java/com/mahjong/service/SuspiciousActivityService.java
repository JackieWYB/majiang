package com.mahjong.service;

import com.mahjong.model.entity.AuditLog;
import com.mahjong.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

/**
 * Service for detecting and logging suspicious activities
 */
@Service
public class SuspiciousActivityService {

    private static final Logger logger = LoggerFactory.getLogger(SuspiciousActivityService.class);

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Value("${admin.monitoring.suspicious-activity-threshold:5}")
    private int suspiciousActivityThreshold;

    /**
     * Log suspicious activity and check if threshold is exceeded
     */
    public void logSuspiciousActivity(String userId, String ipAddress, String activity, String details) {
        logger.warn("Suspicious activity detected - User: {}, IP: {}, Activity: {}, Details: {}", 
                userId, ipAddress, activity, details);

        // Save to audit log
        AuditLog auditLog = new AuditLog();
        auditLog.setAdminId(userId != null ? Long.parseLong(userId) : 0L);
        auditLog.setAdminName(userId != null ? "User-" + userId : "System");
        auditLog.setAction("SUSPICIOUS_ACTIVITY");
        auditLog.setTargetType("SECURITY");
        auditLog.setTargetId(userId);
        auditLog.setDetails(String.format("Activity: %s, IP: %s, Details: %s", activity, ipAddress, details));
        auditLog.setIpAddress(ipAddress);
        auditLog.setCreatedAt(LocalDateTime.now());
        auditLogRepository.save(auditLog);

        // Track in Redis for real-time monitoring
        String redisKey = "suspicious_activity:" + (userId != null ? userId : ipAddress);
        Long count = redisTemplate.opsForValue().increment(redisKey);
        redisTemplate.expire(redisKey, 1, TimeUnit.HOURS);

        // Check if threshold exceeded
        if (count != null && count >= suspiciousActivityThreshold) {
            handleSuspiciousActivityThresholdExceeded(userId, ipAddress, activity, count);
        }
    }

    /**
     * Check for rapid successive actions (potential bot behavior)
     */
    public boolean isRapidActionDetected(String userId, String action) {
        String redisKey = "rapid_action:" + userId + ":" + action;
        String lastActionTime = (String) redisTemplate.opsForValue().get(redisKey);
        
        LocalDateTime now = LocalDateTime.now();
        if (lastActionTime != null) {
            LocalDateTime lastTime = LocalDateTime.parse(lastActionTime);
            long secondsBetween = ChronoUnit.SECONDS.between(lastTime, now);
            
            // If actions are less than 1 second apart, it's suspicious
            if (secondsBetween < 1) {
                logSuspiciousActivity(userId, null, "RAPID_ACTIONS", 
                        String.format("Action: %s, Time between actions: %d seconds", action, secondsBetween));
                return true;
            }
        }
        
        // Update last action time
        redisTemplate.opsForValue().set(redisKey, now.toString(), 10, TimeUnit.SECONDS);
        return false;
    }

    /**
     * Check for unusual game patterns
     */
    public void checkUnusualGamePattern(String userId, String pattern, String details) {
        switch (pattern) {
            case "PERFECT_PLAY":
                // Player always makes optimal moves
                logSuspiciousActivity(userId, null, "PERFECT_PLAY_PATTERN", details);
                break;
            case "INSTANT_DECISIONS":
                // Player makes decisions too quickly
                logSuspiciousActivity(userId, null, "INSTANT_DECISION_PATTERN", details);
                break;
            case "UNUSUAL_WIN_RATE":
                // Player has unusually high win rate
                logSuspiciousActivity(userId, null, "UNUSUAL_WIN_RATE", details);
                break;
            case "REPEATED_ACTIONS":
                // Player repeats same action pattern
                logSuspiciousActivity(userId, null, "REPEATED_ACTION_PATTERN", details);
                break;
        }
    }

    /**
     * Check for multiple accounts from same IP
     */
    public void checkMultipleAccountsFromIP(String ipAddress, String userId) {
        String redisKey = "ip_users:" + ipAddress;
        redisTemplate.opsForSet().add(redisKey, userId);
        redisTemplate.expire(redisKey, 24, TimeUnit.HOURS);
        
        Long userCount = redisTemplate.opsForSet().size(redisKey);
        if (userCount != null && userCount > 3) {
            logSuspiciousActivity(userId, ipAddress, "MULTIPLE_ACCOUNTS_SAME_IP", 
                    String.format("IP has %d different user accounts", userCount));
        }
    }

    /**
     * Check for session anomalies
     */
    public void checkSessionAnomaly(String userId, String sessionId, String anomalyType, String details) {
        logSuspiciousActivity(userId, null, "SESSION_ANOMALY", 
                String.format("Type: %s, Session: %s, Details: %s", anomalyType, sessionId, details));
    }

    /**
     * Check for invalid game state manipulation attempts
     */
    public void checkGameStateManipulation(String userId, String roomId, String manipulationType, String details) {
        logSuspiciousActivity(userId, null, "GAME_STATE_MANIPULATION", 
                String.format("Room: %s, Type: %s, Details: %s", roomId, manipulationType, details));
    }

    /**
     * Handle when suspicious activity threshold is exceeded
     */
    private void handleSuspiciousActivityThresholdExceeded(String userId, String ipAddress, String activity, Long count) {
        logger.error("ALERT: Suspicious activity threshold exceeded - User: {}, IP: {}, Activity: {}, Count: {}", 
                userId, ipAddress, activity, count);

        // Save high-priority audit log
        AuditLog alertLog = new AuditLog();
        alertLog.setAdminId(userId != null ? Long.parseLong(userId) : 0L);
        alertLog.setAdminName(userId != null ? "User-" + userId : "System");
        alertLog.setAction("SUSPICIOUS_ACTIVITY_ALERT");
        alertLog.setTargetType("SECURITY");
        alertLog.setTargetId(userId);
        alertLog.setDetails(String.format("THRESHOLD EXCEEDED - Activity: %s, IP: %s, Count: %d", activity, ipAddress, count));
        alertLog.setIpAddress(ipAddress);
        alertLog.setCreatedAt(LocalDateTime.now());
        auditLogRepository.save(alertLog);

        // TODO: Integrate with alerting system (email, Slack, etc.)
        // TODO: Consider automatic temporary ban for severe cases
    }

    /**
     * Get suspicious activity count for user/IP in last hour
     */
    public Long getSuspiciousActivityCount(String identifier) {
        String redisKey = "suspicious_activity:" + identifier;
        String count = (String) redisTemplate.opsForValue().get(redisKey);
        return count != null ? Long.parseLong(count) : 0L;
    }

    /**
     * Clear suspicious activity tracking for user/IP
     */
    public void clearSuspiciousActivityTracking(String identifier) {
        String redisKey = "suspicious_activity:" + identifier;
        redisTemplate.delete(redisKey);
        logger.info("Cleared suspicious activity tracking for: {}", identifier);
    }
}