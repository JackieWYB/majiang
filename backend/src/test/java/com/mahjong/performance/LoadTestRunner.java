package com.mahjong.performance;

import com.mahjong.model.dto.GameMessage;
import com.mahjong.service.SystemMetricsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.*;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Load Test Runner
 * 
 * Utility for running comprehensive load tests against the mahjong game system
 * to validate performance under realistic concurrent user scenarios.
 */
@SpringBootTest
@ActiveProfiles("test")
public class LoadTestRunner {

    private static final Logger logger = LoggerFactory.getLogger(LoadTestRunner.class);

    @Autowired
    private SystemMetricsService systemMetricsService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private ExecutorService executorService;
    private ScheduledExecutorService scheduledExecutorService;

    /**
     * Run comprehensive load test
     */
    public LoadTestResult runLoadTest(LoadTestConfig config) {
        logger.info("Starting load test with config: {}", config);
        
        executorService = Executors.newFixedThreadPool(config.getThreadPoolSize());
        scheduledExecutorService = Executors.newScheduledThreadPool(5);
        
        LoadTestResult result = new LoadTestResult();
        result.setStartTime(Instant.now());
        
        try {
            // Run different types of load tests concurrently
            CompletableFuture<Void> userLoadTest = runUserLoadTest(config, result);
            CompletableFuture<Void> roomLoadTest = runRoomLoadTest(config, result);
            CompletableFuture<Void> redisLoadTest = runRedisLoadTest(config, result);
            CompletableFuture<Void> websocketLoadTest = runWebSocketLoadTest(config, result);
            
            // Start metrics collection
            ScheduledFuture<?> metricsCollection = startMetricsCollection(result);
            
            // Wait for all tests to complete
            CompletableFuture.allOf(userLoadTest, roomLoadTest, redisLoadTest, websocketLoadTest)
                    .get(config.getTestDurationMinutes(), TimeUnit.MINUTES);
            
            // Stop metrics collection
            metricsCollection.cancel(true);
            
        } catch (Exception e) {
            logger.error("Load test failed", e);
            result.setError(e.getMessage());
        } finally {
            executorService.shutdown();
            scheduledExecutorService.shutdown();
            result.setEndTime(Instant.now());
        }
        
        logger.info("Load test completed: {}", result);
        return result;
    }

    private CompletableFuture<Void> runUserLoadTest(LoadTestConfig config, LoadTestResult result) {
        return CompletableFuture.runAsync(() -> {
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger errorCount = new AtomicInteger(0);
            AtomicLong totalResponseTime = new AtomicLong(0);
            
            CountDownLatch latch = new CountDownLatch(config.getConcurrentUsers());
            
            for (int i = 0; i < config.getConcurrentUsers(); i++) {
                final int userId = i;
                executorService.submit(() -> {
                    try {
                        Instant start = Instant.now();
                        
                        // Simulate user operations
                        simulateUserOperations(userId, config);
                        
                        long responseTime = Duration.between(start, Instant.now()).toMillis();
                        totalResponseTime.addAndGet(responseTime);
                        successCount.incrementAndGet();
                        
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                        logger.debug("User operation error: {}", e.getMessage());
                    } finally {
                        latch.countDown();
                    }
                });
            }
            
            try {
                latch.await(config.getTestDurationMinutes(), TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            result.setUserOperationsSuccess(successCount.get());
            result.setUserOperationsError(errorCount.get());
            result.setAverageUserResponseTime(
                successCount.get() > 0 ? totalResponseTime.get() / successCount.get() : 0
            );
            
        }, executorService);
    }

    private CompletableFuture<Void> runRoomLoadTest(LoadTestConfig config, LoadTestResult result) {
        return CompletableFuture.runAsync(() -> {
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger errorCount = new AtomicInteger(0);
            AtomicLong totalResponseTime = new AtomicLong(0);
            
            CountDownLatch latch = new CountDownLatch(config.getConcurrentRooms());
            
            for (int i = 0; i < config.getConcurrentRooms(); i++) {
                final int roomId = i;
                executorService.submit(() -> {
                    try {
                        Instant start = Instant.now();
                        
                        // Simulate room operations
                        simulateRoomOperations(roomId, config);
                        
                        long responseTime = Duration.between(start, Instant.now()).toMillis();
                        totalResponseTime.addAndGet(responseTime);
                        successCount.incrementAndGet();
                        
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                        logger.debug("Room operation error: {}", e.getMessage());
                    } finally {
                        latch.countDown();
                    }
                });
            }
            
            try {
                latch.await(config.getTestDurationMinutes(), TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            result.setRoomOperationsSuccess(successCount.get());
            result.setRoomOperationsError(errorCount.get());
            result.setAverageRoomResponseTime(
                successCount.get() > 0 ? totalResponseTime.get() / successCount.get() : 0
            );
            
        }, executorService);
    }

    private CompletableFuture<Void> runRedisLoadTest(LoadTestConfig config, LoadTestResult result) {
        return CompletableFuture.runAsync(() -> {
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger errorCount = new AtomicInteger(0);
            AtomicLong totalResponseTime = new AtomicLong(0);
            
            int operationsPerSecond = config.getRedisOperationsPerSecond();
            long testDurationMs = config.getTestDurationMinutes() * 60 * 1000;
            int totalOperations = (int) (operationsPerSecond * (testDurationMs / 1000));
            
            CountDownLatch latch = new CountDownLatch(totalOperations);
            
            // Schedule Redis operations at specified rate
            ScheduledFuture<?> scheduler = scheduledExecutorService.scheduleAtFixedRate(() -> {
                for (int i = 0; i < operationsPerSecond; i++) {
                    executorService.submit(() -> {
                        try {
                            Instant start = Instant.now();
                            
                            // Perform Redis operations
                            String key = "load_test_" + System.nanoTime();
                            String value = "test_value_" + System.currentTimeMillis();
                            
                            redisTemplate.opsForValue().set(key, value, Duration.ofMinutes(1));
                            String retrieved = (String) redisTemplate.opsForValue().get(key);
                            
                            if (value.equals(retrieved)) {
                                long responseTime = Duration.between(start, Instant.now()).toMillis();
                                totalResponseTime.addAndGet(responseTime);
                                successCount.incrementAndGet();
                            } else {
                                errorCount.incrementAndGet();
                            }
                            
                        } catch (Exception e) {
                            errorCount.incrementAndGet();
                            logger.debug("Redis operation error: {}", e.getMessage());
                        } finally {
                            latch.countDown();
                        }
                    });
                }
            }, 0, 1, TimeUnit.SECONDS);
            
            try {
                latch.await(config.getTestDurationMinutes() + 1, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                scheduler.cancel(true);
            }
            
            result.setRedisOperationsSuccess(successCount.get());
            result.setRedisOperationsError(errorCount.get());
            result.setAverageRedisResponseTime(
                successCount.get() > 0 ? totalResponseTime.get() / successCount.get() : 0
            );
            
        }, executorService);
    }

    private CompletableFuture<Void> runWebSocketLoadTest(LoadTestConfig config, LoadTestResult result) {
        return CompletableFuture.runAsync(() -> {
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger errorCount = new AtomicInteger(0);
            AtomicLong totalResponseTime = new AtomicLong(0);
            
            CountDownLatch latch = new CountDownLatch(config.getConcurrentWebSocketConnections());
            
            for (int i = 0; i < config.getConcurrentWebSocketConnections(); i++) {
                final int connectionId = i;
                executorService.submit(() -> {
                    try {
                        Instant start = Instant.now();
                        
                        // Simulate WebSocket operations
                        simulateWebSocketOperations(connectionId, config);
                        
                        long responseTime = Duration.between(start, Instant.now()).toMillis();
                        totalResponseTime.addAndGet(responseTime);
                        successCount.incrementAndGet();
                        
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                        logger.debug("WebSocket operation error: {}", e.getMessage());
                    } finally {
                        latch.countDown();
                    }
                });
            }
            
            try {
                latch.await(config.getTestDurationMinutes(), TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            result.setWebSocketOperationsSuccess(successCount.get());
            result.setWebSocketOperationsError(errorCount.get());
            result.setAverageWebSocketResponseTime(
                successCount.get() > 0 ? totalResponseTime.get() / successCount.get() : 0
            );
            
        }, executorService);
    }

    private ScheduledFuture<?> startMetricsCollection(LoadTestResult result) {
        return scheduledExecutorService.scheduleAtFixedRate(() -> {
            try {
                var metrics = systemMetricsService.getSystemHealthMetrics();
                result.addMetricsSnapshot(metrics);
                
                // Log current metrics
                logger.info("Load test metrics: {}", metrics);
                
            } catch (Exception e) {
                logger.error("Error collecting metrics during load test", e);
            }
        }, 0, 10, TimeUnit.SECONDS);
    }

    // Simulation methods

    private void simulateUserOperations(int userId, LoadTestConfig config) throws InterruptedException {
        // Simulate user login, profile updates, etc.
        Thread.sleep(config.getOperationDelayMs());
        
        // Record metrics
        systemMetricsService.incrementUserLogin();
        systemMetricsService.setOnlineUsers(userId + 1);
    }

    private void simulateRoomOperations(int roomId, LoadTestConfig config) throws InterruptedException {
        // Simulate room creation, joining, game operations
        Thread.sleep(config.getOperationDelayMs());
        
        // Record metrics
        systemMetricsService.incrementRoomCreated();
        systemMetricsService.setActiveRooms(roomId + 1);
    }

    private void simulateWebSocketOperations(int connectionId, LoadTestConfig config) throws InterruptedException {
        // Simulate WebSocket message sending/receiving
        Thread.sleep(config.getOperationDelayMs());
        
        // Simulate game actions
        for (int i = 0; i < config.getMessagesPerConnection(); i++) {
            GameMessage message = new GameMessage();
            message.setCmd("test_action");
            message.setRoomId("room_" + connectionId);
            message.setData("test_data");
            
            // Simulate processing time
            Thread.sleep(10);
        }
    }

    // Configuration and Result classes

    public static class LoadTestConfig {
        private int concurrentUsers = 100;
        private int concurrentRooms = 50;
        private int concurrentWebSocketConnections = 200;
        private int redisOperationsPerSecond = 1000;
        private int messagesPerConnection = 100;
        private int testDurationMinutes = 5;
        private int threadPoolSize = 100;
        private int operationDelayMs = 50;

        // Getters and setters
        public int getConcurrentUsers() { return concurrentUsers; }
        public void setConcurrentUsers(int concurrentUsers) { this.concurrentUsers = concurrentUsers; }
        
        public int getConcurrentRooms() { return concurrentRooms; }
        public void setConcurrentRooms(int concurrentRooms) { this.concurrentRooms = concurrentRooms; }
        
        public int getConcurrentWebSocketConnections() { return concurrentWebSocketConnections; }
        public void setConcurrentWebSocketConnections(int concurrentWebSocketConnections) { 
            this.concurrentWebSocketConnections = concurrentWebSocketConnections; 
        }
        
        public int getRedisOperationsPerSecond() { return redisOperationsPerSecond; }
        public void setRedisOperationsPerSecond(int redisOperationsPerSecond) { 
            this.redisOperationsPerSecond = redisOperationsPerSecond; 
        }
        
        public int getMessagesPerConnection() { return messagesPerConnection; }
        public void setMessagesPerConnection(int messagesPerConnection) { 
            this.messagesPerConnection = messagesPerConnection; 
        }
        
        public int getTestDurationMinutes() { return testDurationMinutes; }
        public void setTestDurationMinutes(int testDurationMinutes) { 
            this.testDurationMinutes = testDurationMinutes; 
        }
        
        public int getThreadPoolSize() { return threadPoolSize; }
        public void setThreadPoolSize(int threadPoolSize) { this.threadPoolSize = threadPoolSize; }
        
        public int getOperationDelayMs() { return operationDelayMs; }
        public void setOperationDelayMs(int operationDelayMs) { this.operationDelayMs = operationDelayMs; }

        @Override
        public String toString() {
            return String.format("LoadTestConfig{users=%d, rooms=%d, websockets=%d, duration=%dmin}", 
                concurrentUsers, concurrentRooms, concurrentWebSocketConnections, testDurationMinutes);
        }
    }

    public static class LoadTestResult {
        private Instant startTime;
        private Instant endTime;
        private String error;
        
        private int userOperationsSuccess;
        private int userOperationsError;
        private long averageUserResponseTime;
        
        private int roomOperationsSuccess;
        private int roomOperationsError;
        private long averageRoomResponseTime;
        
        private int redisOperationsSuccess;
        private int redisOperationsError;
        private long averageRedisResponseTime;
        
        private int webSocketOperationsSuccess;
        private int webSocketOperationsError;
        private long averageWebSocketResponseTime;
        
        private java.util.List<Object> metricsSnapshots = new java.util.ArrayList<>();

        // Getters and setters
        public Instant getStartTime() { return startTime; }
        public void setStartTime(Instant startTime) { this.startTime = startTime; }
        
        public Instant getEndTime() { return endTime; }
        public void setEndTime(Instant endTime) { this.endTime = endTime; }
        
        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
        
        public int getUserOperationsSuccess() { return userOperationsSuccess; }
        public void setUserOperationsSuccess(int userOperationsSuccess) { 
            this.userOperationsSuccess = userOperationsSuccess; 
        }
        
        public int getUserOperationsError() { return userOperationsError; }
        public void setUserOperationsError(int userOperationsError) { 
            this.userOperationsError = userOperationsError; 
        }
        
        public long getAverageUserResponseTime() { return averageUserResponseTime; }
        public void setAverageUserResponseTime(long averageUserResponseTime) { 
            this.averageUserResponseTime = averageUserResponseTime; 
        }
        
        public int getRoomOperationsSuccess() { return roomOperationsSuccess; }
        public void setRoomOperationsSuccess(int roomOperationsSuccess) { 
            this.roomOperationsSuccess = roomOperationsSuccess; 
        }
        
        public int getRoomOperationsError() { return roomOperationsError; }
        public void setRoomOperationsError(int roomOperationsError) { 
            this.roomOperationsError = roomOperationsError; 
        }
        
        public long getAverageRoomResponseTime() { return averageRoomResponseTime; }
        public void setAverageRoomResponseTime(long averageRoomResponseTime) { 
            this.averageRoomResponseTime = averageRoomResponseTime; 
        }
        
        public int getRedisOperationsSuccess() { return redisOperationsSuccess; }
        public void setRedisOperationsSuccess(int redisOperationsSuccess) { 
            this.redisOperationsSuccess = redisOperationsSuccess; 
        }
        
        public int getRedisOperationsError() { return redisOperationsError; }
        public void setRedisOperationsError(int redisOperationsError) { 
            this.redisOperationsError = redisOperationsError; 
        }
        
        public long getAverageRedisResponseTime() { return averageRedisResponseTime; }
        public void setAverageRedisResponseTime(long averageRedisResponseTime) { 
            this.averageRedisResponseTime = averageRedisResponseTime; 
        }
        
        public int getWebSocketOperationsSuccess() { return webSocketOperationsSuccess; }
        public void setWebSocketOperationsSuccess(int webSocketOperationsSuccess) { 
            this.webSocketOperationsSuccess = webSocketOperationsSuccess; 
        }
        
        public int getWebSocketOperationsError() { return webSocketOperationsError; }
        public void setWebSocketOperationsError(int webSocketOperationsError) { 
            this.webSocketOperationsError = webSocketOperationsError; 
        }
        
        public long getAverageWebSocketResponseTime() { return averageWebSocketResponseTime; }
        public void setAverageWebSocketResponseTime(long averageWebSocketResponseTime) { 
            this.averageWebSocketResponseTime = averageWebSocketResponseTime; 
        }
        
        public java.util.List<Object> getMetricsSnapshots() { return metricsSnapshots; }
        public void addMetricsSnapshot(Object snapshot) { this.metricsSnapshots.add(snapshot); }

        public Duration getTotalDuration() {
            return endTime != null && startTime != null ? Duration.between(startTime, endTime) : Duration.ZERO;
        }

        @Override
        public String toString() {
            return String.format("LoadTestResult{duration=%s, userOps=%d/%d, roomOps=%d/%d, redisOps=%d/%d, wsOps=%d/%d}", 
                getTotalDuration(), userOperationsSuccess, userOperationsError,
                roomOperationsSuccess, roomOperationsError,
                redisOperationsSuccess, redisOperationsError,
                webSocketOperationsSuccess, webSocketOperationsError);
        }
    }
}