package com.mahjong.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service for collecting and exposing system metrics with performance monitoring and alerting
 */
@Service
public class SystemMetricsService {
    
    private static final Logger logger = LoggerFactory.getLogger(SystemMetricsService.class);
    
    private final MeterRegistry meterRegistry;
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    @Autowired
    private ConnectionPoolMonitoringService connectionPoolMonitoringService;
    
    // Alert thresholds
    private static final double MEMORY_USAGE_THRESHOLD = 0.85;
    private static final long RESPONSE_TIME_THRESHOLD = 1000;
    private static final double ERROR_RATE_THRESHOLD = 0.05; // 5%
    
    // Counters
    private final Counter gameStartedCounter;
    private final Counter gameCompletedCounter;
    private final Counter userLoginCounter;
    private final Counter userBannedCounter;
    private final Counter roomCreatedCounter;
    private final Counter roomDissolvedCounter;
    private final Counter suspiciousActivityCounter;
    
    // Gauges
    private final AtomicLong activeRoomsGauge = new AtomicLong(0);
    private final AtomicLong activePlayersGauge = new AtomicLong(0);
    private final AtomicLong onlineUsersGauge = new AtomicLong(0);
    
    // Timers
    private final Timer gameActionTimer;
    private final Timer databaseQueryTimer;
    private final Timer redisOperationTimer;
    
    @Autowired
    public SystemMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        
        // Initialize counters
        this.gameStartedCounter = Counter.builder("mahjong.games.started")
                .description("Number of games started")
                .register(meterRegistry);
        
        this.gameCompletedCounter = Counter.builder("mahjong.games.completed")
                .description("Number of games completed")
                .register(meterRegistry);
        
        this.userLoginCounter = Counter.builder("mahjong.users.login")
                .description("Number of user logins")
                .register(meterRegistry);
        
        this.userBannedCounter = Counter.builder("mahjong.users.banned")
                .description("Number of users banned")
                .register(meterRegistry);
        
        this.roomCreatedCounter = Counter.builder("mahjong.rooms.created")
                .description("Number of rooms created")
                .register(meterRegistry);
        
        this.roomDissolvedCounter = Counter.builder("mahjong.rooms.dissolved")
                .description("Number of rooms dissolved")
                .register(meterRegistry);
        
        this.suspiciousActivityCounter = Counter.builder("mahjong.security.suspicious_activity")
                .description("Number of suspicious activities detected")
                .register(meterRegistry);
        
        // Initialize gauges
        Gauge.builder("mahjong.rooms.active", activeRoomsGauge, AtomicLong::get)
                .description("Number of active rooms")
                .register(meterRegistry);
        
        Gauge.builder("mahjong.players.active", activePlayersGauge, AtomicLong::get)
                .description("Number of active players")
                .register(meterRegistry);
        
        Gauge.builder("mahjong.users.online", onlineUsersGauge, AtomicLong::get)
                .description("Number of online users")
                .register(meterRegistry);
        
        // Initialize timers
        this.gameActionTimer = Timer.builder("mahjong.game.action.duration")
                .description("Time taken to process game actions")
                .register(meterRegistry);
        
        this.databaseQueryTimer = Timer.builder("mahjong.database.query.duration")
                .description("Time taken for database queries")
                .register(meterRegistry);
        
        this.redisOperationTimer = Timer.builder("mahjong.redis.operation.duration")
                .description("Time taken for Redis operations")
                .register(meterRegistry);
    }
    
    // Counter methods
    public void incrementGameStarted() {
        gameStartedCounter.increment();
    }
    
    public void incrementGameCompleted() {
        gameCompletedCounter.increment();
    }
    
    public void incrementUserLogin() {
        userLoginCounter.increment();
    }
    
    public void incrementUserBanned() {
        userBannedCounter.increment();
    }
    
    public void incrementRoomCreated() {
        roomCreatedCounter.increment();
    }
    
    public void incrementRoomDissolved() {
        roomDissolvedCounter.increment();
    }
    
    public void incrementSuspiciousActivity() {
        suspiciousActivityCounter.increment();
    }
    
    // Gauge methods
    public void setActiveRooms(long count) {
        activeRoomsGauge.set(count);
    }
    
    public void setActivePlayers(long count) {
        activePlayersGauge.set(count);
    }
    
    public void setOnlineUsers(long count) {
        onlineUsersGauge.set(count);
    }
    
    // Timer methods
    public Timer.Sample startGameActionTimer() {
        return Timer.start(meterRegistry);
    }
    
    public void recordGameActionTime(Timer.Sample sample) {
        sample.stop(gameActionTimer);
    }
    
    public Timer.Sample startDatabaseQueryTimer() {
        return Timer.start(meterRegistry);
    }
    
    public void recordDatabaseQueryTime(Timer.Sample sample) {
        sample.stop(databaseQueryTimer);
    }
    
    public Timer.Sample startRedisOperationTimer() {
        return Timer.start(meterRegistry);
    }
    
    public void recordRedisOperationTime(Timer.Sample sample) {
        sample.stop(redisOperationTimer);
    }
    
    // Custom metrics
    public void recordCustomMetric(String name, String description, double value) {
        Gauge.builder(name, () -> value)
                .description(description)
                .register(meterRegistry);
    }
    
    public void incrementCustomCounter(String name, String description) {
        Counter.builder(name)
                .description(description)
                .register(meterRegistry)
                .increment();
    }
    
    // Performance monitoring and alerting
    
    /**
     * Monitor system performance every minute
     */
    @Scheduled(fixedRate = 60000) // Every minute
    public void monitorPerformance() {
        try {
            // Check memory usage
            double memoryUsageRatio = getMemoryUsageRatio();
            if (memoryUsageRatio > MEMORY_USAGE_THRESHOLD) {
                recordAlert("HIGH_MEMORY_USAGE", 
                    String.format("Memory usage is %.2f%% (threshold: %.2f%%)", 
                        memoryUsageRatio * 100, MEMORY_USAGE_THRESHOLD * 100));
            }
            
            // Check database pool utilization
            double dbUtilization = connectionPoolMonitoringService.getDatabasePoolUtilization();
            if (dbUtilization > 0.8) {
                recordAlert("HIGH_DATABASE_POOL_UTILIZATION", 
                    String.format("Database pool utilization is %.2f%%", dbUtilization * 100));
            }
            
            // Check error rates
            checkErrorRates();
            
            logger.debug("Performance monitoring completed - Memory: {:.2f}%, DB Pool: {:.2f}%", 
                memoryUsageRatio * 100, dbUtilization * 100);
                
        } catch (Exception e) {
            logger.error("Error during performance monitoring", e);
            recordAlert("MONITORING_ERROR", "Performance monitoring failed: " + e.getMessage());
        }
    }
    
    /**
     * Get comprehensive system health metrics
     */
    public Map<String, Object> getSystemHealthMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        
        // JVM metrics
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        Map<String, Object> jvmMetrics = new HashMap<>();
        jvmMetrics.put("memoryUsed", memoryBean.getHeapMemoryUsage().getUsed());
        jvmMetrics.put("memoryMax", memoryBean.getHeapMemoryUsage().getMax());
        jvmMetrics.put("memoryUsageRatio", getMemoryUsageRatio());
        metrics.put("jvm", jvmMetrics);
        
        // Application metrics
        Map<String, Object> appMetrics = new HashMap<>();
        appMetrics.put("activeRooms", activeRoomsGauge.get());
        appMetrics.put("activePlayers", activePlayersGauge.get());
        appMetrics.put("onlineUsers", onlineUsersGauge.get());
        metrics.put("application", appMetrics);
        
        // Database metrics
        Map<String, Object> dbMetrics = new HashMap<>();
        dbMetrics.put("activeConnections", connectionPoolMonitoringService.getActiveDatabaseConnections());
        dbMetrics.put("poolUtilization", connectionPoolMonitoringService.getDatabasePoolUtilization());
        dbMetrics.put("threadsWaiting", connectionPoolMonitoringService.getThreadsAwaitingConnection());
        metrics.put("database", dbMetrics);
        
        // Performance counters
        Map<String, Object> counters = new HashMap<>();
        counters.put("gamesStarted", gameStartedCounter.count());
        counters.put("gamesCompleted", gameCompletedCounter.count());
        counters.put("userLogins", userLoginCounter.count());
        counters.put("roomsCreated", roomCreatedCounter.count());
        counters.put("suspiciousActivities", suspiciousActivityCounter.count());
        metrics.put("counters", counters);
        
        // Timing metrics
        Map<String, Object> timings = new HashMap<>();
        timings.put("gameActionMean", gameActionTimer.mean(java.util.concurrent.TimeUnit.MILLISECONDS));
        timings.put("databaseQueryMean", databaseQueryTimer.mean(java.util.concurrent.TimeUnit.MILLISECONDS));
        timings.put("redisOperationMean", redisOperationTimer.mean(java.util.concurrent.TimeUnit.MILLISECONDS));
        metrics.put("timings", timings);
        
        metrics.put("timestamp", System.currentTimeMillis());
        
        return metrics;
    }
    
    /**
     * Record performance alert
     */
    public void recordAlert(String alertType, String message) {
        String alertKey = "alerts:" + alertType + ":" + System.currentTimeMillis();
        Map<String, Object> alert = new HashMap<>();
        alert.put("type", alertType);
        alert.put("message", message);
        alert.put("timestamp", System.currentTimeMillis());
        alert.put("severity", getSeverity(alertType));
        
        redisTemplate.opsForValue().set(alertKey, alert, Duration.ofHours(24));
        redisTemplate.opsForList().leftPush("alerts:recent", alert);
        redisTemplate.expire("alerts:recent", Duration.ofHours(24));
        
        logger.warn("Performance alert: {} - {}", alertType, message);
    }
    
    /**
     * Get recent alerts
     */
    public java.util.List<Object> getRecentAlerts(int limit) {
        return redisTemplate.opsForList().range("alerts:recent", 0, limit - 1);
    }
    
    /**
     * Record slow operation for monitoring
     */
    public void recordSlowOperation(String operationType, long durationMs) {
        if (durationMs > RESPONSE_TIME_THRESHOLD) {
            recordAlert("SLOW_OPERATION", 
                String.format("%s operation took %dms (threshold: %dms)", 
                    operationType, durationMs, RESPONSE_TIME_THRESHOLD));
        }
        
        // Store in Redis for trend analysis
        String key = "slow-operations:" + operationType;
        redisTemplate.opsForList().leftPush(key, durationMs);
        redisTemplate.expire(key, Duration.ofHours(24));
    }
    
    // Private helper methods
    
    private double getMemoryUsageRatio() {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        long used = memoryBean.getHeapMemoryUsage().getUsed();
        long max = memoryBean.getHeapMemoryUsage().getMax();
        return max > 0 ? (double) used / max : 0.0;
    }
    
    private void checkErrorRates() {
        // Calculate error rate over the last minute
        long totalOperations = gameStartedCounter.count() + userLoginCounter.count() + roomCreatedCounter.count();
        long suspiciousActivities = suspiciousActivityCounter.count();
        
        if (totalOperations > 0) {
            double errorRate = (double) suspiciousActivities / totalOperations;
            if (errorRate > ERROR_RATE_THRESHOLD) {
                recordAlert("HIGH_ERROR_RATE", 
                    String.format("Error rate is %.2f%% (threshold: %.2f%%)", 
                        errorRate * 100, ERROR_RATE_THRESHOLD * 100));
            }
        }
    }
    
    private String getSeverity(String alertType) {
        switch (alertType) {
            case "HIGH_MEMORY_USAGE":
            case "MONITORING_ERROR":
                return "HIGH";
            case "HIGH_DATABASE_POOL_UTILIZATION":
            case "HIGH_ERROR_RATE":
                return "MEDIUM";
            case "SLOW_OPERATION":
                return "LOW";
            default:
                return "MEDIUM";
        }
    }
}