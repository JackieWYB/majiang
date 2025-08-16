package com.mahjong.service;

import com.mahjong.config.RateLimitConfig;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing rate limiting across different operations
 */
@Service
public class RateLimitService {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitService.class);

    @Autowired
    private RateLimitConfig rateLimitConfig;
    
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    /**
     * Check if API request is allowed for user
     */
    public boolean isApiRequestAllowed(String userId) {
        if (userId == null) return true;
        
        String bucketKey = "api_rate_limit:" + userId;
        Bucket bucket = buckets.computeIfAbsent(bucketKey, k -> 
                Bucket.builder()
                        .addLimit(Bandwidth.simple(100, Duration.ofMinutes(1)))
                        .build());
        
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            logger.warn("API rate limit exceeded for user: {}, remaining tokens: {}, retry after: {} seconds", 
                    userId, probe.getRemainingTokens(), probe.getNanosToWaitForRefill() / 1_000_000_000);
            return false;
        }
        
        return true;
    }

    /**
     * Check if WebSocket message is allowed for user
     */
    public boolean isWebSocketMessageAllowed(String userId) {
        if (userId == null) return true;
        
        String bucketKey = "websocket_rate_limit:" + userId;
        Bucket bucket = buckets.computeIfAbsent(bucketKey, k -> 
                Bucket.builder()
                        .addLimit(Bandwidth.simple(30, Duration.ofMinutes(1)))
                        .build());
        
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            logger.warn("WebSocket rate limit exceeded for user: {}, remaining tokens: {}, retry after: {} seconds", 
                    userId, probe.getRemainingTokens(), probe.getNanosToWaitForRefill() / 1_000_000_000);
            return false;
        }
        
        return true;
    }

    /**
     * Check if login attempt is allowed for IP address
     */
    public boolean isLoginAttemptAllowed(String ipAddress) {
        if (ipAddress == null) return true;
        
        String bucketKey = "login_rate_limit:" + ipAddress;
        Bucket bucket = buckets.computeIfAbsent(bucketKey, k -> 
                Bucket.builder()
                        .addLimit(Bandwidth.simple(5, Duration.ofMinutes(15)))
                        .build());
        
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            logger.warn("Login rate limit exceeded for IP: {}, remaining tokens: {}, retry after: {} seconds", 
                    ipAddress, probe.getRemainingTokens(), probe.getNanosToWaitForRefill() / 1_000_000_000);
            return false;
        }
        
        return true;
    }

    /**
     * Check if room creation is allowed for user
     */
    public boolean isRoomCreationAllowed(String userId) {
        if (userId == null) return true;
        
        String bucketKey = "room_creation_rate_limit:" + userId;
        Bucket bucket = buckets.computeIfAbsent(bucketKey, k -> 
                Bucket.builder()
                        .addLimit(Bandwidth.simple(10, Duration.ofHours(1)))
                        .build());
        
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            logger.warn("Room creation rate limit exceeded for user: {}, remaining tokens: {}, retry after: {} seconds", 
                    userId, probe.getRemainingTokens(), probe.getNanosToWaitForRefill() / 1_000_000_000);
            return false;
        }
        
        return true;
    }

    /**
     * Get remaining tokens for API requests
     */
    public long getRemainingApiTokens(String userId) {
        if (userId == null) return 100;
        
        String bucketKey = "api_rate_limit:" + userId;
        Bucket bucket = buckets.get(bucketKey);
        if (bucket == null) {
            return 100; // Full capacity if bucket doesn't exist yet
        }
        
        return bucket.getAvailableTokens();
    }

    /**
     * Get time until next token refill
     */
    public Duration getTimeUntilRefill(String userId, String operation) {
        if (userId == null) return Duration.ZERO;
        
        String bucketKey = operation + "_rate_limit:" + userId;
        Bucket bucket = buckets.get(bucketKey);
        if (bucket == null) {
            return Duration.ZERO;
        }
        
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(0);
        return Duration.ofNanos(probe.getNanosToWaitForRefill());
    }
}