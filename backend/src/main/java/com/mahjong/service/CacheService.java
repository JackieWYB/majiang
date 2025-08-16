package com.mahjong.service;

import com.mahjong.model.entity.Room;
import com.mahjong.model.entity.User;
import com.mahjong.model.dto.UserStatisticsResponse;
import com.mahjong.repository.RoomRepository;
import com.mahjong.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Cache Service for managing frequently accessed data
 * 
 * Provides caching layer for user profiles, room data, and statistics
 * to improve system performance and reduce database load.
 */
@Service
public class CacheService {

    private static final Logger logger = LoggerFactory.getLogger(CacheService.class);

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoomRepository roomRepository;

    /**
     * Get user by ID with caching
     */
    @Cacheable(value = "users", key = "#userId")
    public Optional<User> getUserById(Long userId) {
        logger.debug("Fetching user from database: {}", userId);
        return userRepository.findById(userId);
    }

    /**
     * Get user by OpenID with caching
     */
    @Cacheable(value = "users", key = "'openid:' + #openId")
    public Optional<User> getUserByOpenId(String openId) {
        logger.debug("Fetching user by openId from database: {}", openId);
        return userRepository.findByOpenId(openId);
    }

    /**
     * Get active rooms with caching
     */
    @Cacheable(value = "active-rooms", key = "'all'")
    public List<Room> getActiveRooms() {
        logger.debug("Fetching active rooms from database");
        return roomRepository.findByStatusIn(List.of(
            com.mahjong.model.enums.RoomStatus.WAITING,
            com.mahjong.model.enums.RoomStatus.PLAYING
        ));
    }

    /**
     * Get room by ID with caching
     */
    @Cacheable(value = "active-rooms", key = "#roomId")
    public Optional<Room> getRoomById(String roomId) {
        logger.debug("Fetching room from database: {}", roomId);
        return roomRepository.findById(roomId);
    }

    /**
     * Cache user statistics
     */
    @Cacheable(value = "game-stats", key = "'user:' + #userId")
    public UserStatisticsResponse getUserStatistics(Long userId) {
        logger.debug("Calculating user statistics for: {}", userId);
        // This would typically involve complex queries
        // For now, return a placeholder implementation
        return new UserStatisticsResponse();
    }

    /**
     * Cache system metrics
     */
    @Cacheable(value = "system-metrics", key = "'current'")
    public SystemMetrics getSystemMetrics() {
        logger.debug("Calculating system metrics");
        long activeUsers = redisTemplate.keys("session:*").size();
        long activeRooms = getActiveRooms().size();
        
        return SystemMetrics.builder()
                .activeUsers(activeUsers)
                .activeRooms(activeRooms)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * Evict user cache when user data is updated
     */
    @Caching(evict = {
        @CacheEvict(value = "users", key = "#userId"),
        @CacheEvict(value = "users", key = "'openid:' + #user.openId"),
        @CacheEvict(value = "game-stats", key = "'user:' + #userId")
    })
    public void evictUserCache(Long userId, User user) {
        logger.debug("Evicting user cache for: {}", userId);
    }

    /**
     * Evict room cache when room data is updated
     */
    @Caching(evict = {
        @CacheEvict(value = "active-rooms", key = "#roomId"),
        @CacheEvict(value = "active-rooms", key = "'all'")
    })
    public void evictRoomCache(String roomId) {
        logger.debug("Evicting room cache for: {}", roomId);
    }

    /**
     * Evict system metrics cache
     */
    @CacheEvict(value = "system-metrics", key = "'current'")
    public void evictSystemMetricsCache() {
        logger.debug("Evicting system metrics cache");
    }

    /**
     * Cache frequently accessed game configuration
     */
    public void cacheGameConfig(String key, Object config, Duration ttl) {
        redisTemplate.opsForValue().set("config:" + key, config, ttl);
    }

    /**
     * Get cached game configuration
     */
    public Object getCachedGameConfig(String key) {
        return redisTemplate.opsForValue().get("config:" + key);
    }

    /**
     * Cache leaderboard data
     */
    public void cacheLeaderboard(String type, Object data) {
        redisTemplate.opsForValue().set("leaderboard:" + type, data, Duration.ofMinutes(10));
    }

    /**
     * Get cached leaderboard data
     */
    public Object getCachedLeaderboard(String type) {
        return redisTemplate.opsForValue().get("leaderboard:" + type);
    }

    /**
     * System metrics data class
     */
    public static class SystemMetrics {
        private long activeUsers;
        private long activeRooms;
        private long timestamp;

        public static SystemMetricsBuilder builder() {
            return new SystemMetricsBuilder();
        }

        // Getters and setters
        public long getActiveUsers() { return activeUsers; }
        public void setActiveUsers(long activeUsers) { this.activeUsers = activeUsers; }
        
        public long getActiveRooms() { return activeRooms; }
        public void setActiveRooms(long activeRooms) { this.activeRooms = activeRooms; }
        
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

        public static class SystemMetricsBuilder {
            private long activeUsers;
            private long activeRooms;
            private long timestamp;

            public SystemMetricsBuilder activeUsers(long activeUsers) {
                this.activeUsers = activeUsers;
                return this;
            }

            public SystemMetricsBuilder activeRooms(long activeRooms) {
                this.activeRooms = activeRooms;
                return this;
            }

            public SystemMetricsBuilder timestamp(long timestamp) {
                this.timestamp = timestamp;
                return this;
            }

            public SystemMetrics build() {
                SystemMetrics metrics = new SystemMetrics();
                metrics.setActiveUsers(this.activeUsers);
                metrics.setActiveRooms(this.activeRooms);
                metrics.setTimestamp(this.timestamp);
                return metrics;
            }
        }
    }
}