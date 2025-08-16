package com.mahjong.performance;

import com.mahjong.model.dto.GameMessage;
import com.mahjong.model.entity.Room;
import com.mahjong.model.entity.User;
import com.mahjong.service.GameService;
import com.mahjong.service.RoomService;
import com.mahjong.service.SystemMetricsService;
import com.mahjong.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Performance Test Suite
 * 
 * Comprehensive performance tests for concurrent load scenarios,
 * database operations, Redis operations, and system scalability.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.datasource.hikari.maximum-pool-size=50",
    "spring.data.redis.lettuce.pool.max-active=100"
})
public class PerformanceTestSuite {

    @Autowired
    private UserService userService;

    @Autowired
    private RoomService roomService;

    @Autowired
    private GameService gameService;

    @Autowired
    private SystemMetricsService systemMetricsService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private ExecutorService executorService;
    private final int THREAD_POOL_SIZE = 50;
    private final int CONCURRENT_USERS = 100;
    private final int CONCURRENT_ROOMS = 50;

    @BeforeEach
    void setUp() {
        executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        
        // Clear Redis cache before tests
        redisTemplate.getConnectionFactory().getConnection().flushAll();
    }

    /**
     * Test concurrent user creation and authentication
     */
    @Test
    void testConcurrentUserOperations() throws InterruptedException {
        int userCount = CONCURRENT_USERS;
        CountDownLatch latch = new CountDownLatch(userCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        AtomicLong totalTime = new AtomicLong(0);

        Instant startTime = Instant.now();

        for (int i = 0; i < userCount; i++) {
            final int userId = i;
            executorService.submit(() -> {
                try {
                    Instant operationStart = Instant.now();
                    
                    // Create user
                    User user = new User();
                    user.setOpenId("test_user_" + userId);
                    user.setNickname("TestUser" + userId);
                    user.setAvatar("avatar_" + userId);
                    
                    User savedUser = userService.createUser(user);
                    assertNotNull(savedUser);
                    assertNotNull(savedUser.getId());
                    
                    // Authenticate user
                    User authenticatedUser = userService.getUserByOpenId("test_user_" + userId);
                    assertNotNull(authenticatedUser);
                    assertEquals("TestUser" + userId, authenticatedUser.getNickname());
                    
                    long operationTime = Duration.between(operationStart, Instant.now()).toMillis();
                    totalTime.addAndGet(operationTime);
                    successCount.incrementAndGet();
                    
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                    System.err.println("Error in user operation " + userId + ": " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(30, TimeUnit.SECONDS), "User operations should complete within 30 seconds");

        Duration totalDuration = Duration.between(startTime, Instant.now());
        double averageTime = totalTime.get() / (double) successCount.get();
        double throughput = successCount.get() / (totalDuration.toMillis() / 1000.0);

        System.out.printf("User Operations Performance:%n");
        System.out.printf("  Total users: %d%n", userCount);
        System.out.printf("  Successful: %d%n", successCount.get());
        System.out.printf("  Errors: %d%n", errorCount.get());
        System.out.printf("  Total time: %d ms%n", totalDuration.toMillis());
        System.out.printf("  Average operation time: %.2f ms%n", averageTime);
        System.out.printf("  Throughput: %.2f operations/second%n", throughput);

        // Performance assertions
        assertTrue(successCount.get() >= userCount * 0.95, "At least 95% of operations should succeed");
        assertTrue(averageTime < 500, "Average operation time should be less than 500ms");
        assertTrue(throughput > 10, "Throughput should be at least 10 operations/second");
    }

    /**
     * Test concurrent room creation and management
     */
    @Test
    void testConcurrentRoomOperations() throws InterruptedException {
        // First create users for room operations
        List<User> users = createTestUsers(CONCURRENT_ROOMS * 3);
        
        int roomCount = CONCURRENT_ROOMS;
        CountDownLatch latch = new CountDownLatch(roomCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        AtomicLong totalTime = new AtomicLong(0);

        Instant startTime = Instant.now();

        for (int i = 0; i < roomCount; i++) {
            final int roomIndex = i;
            executorService.submit(() -> {
                try {
                    Instant operationStart = Instant.now();
                    
                    User owner = users.get(roomIndex * 3);
                    
                    // Create room
                    Room room = roomService.createRoom(owner.getId().toString(), null);
                    assertNotNull(room);
                    assertNotNull(room.getId());
                    
                    // Join additional players
                    User player2 = users.get(roomIndex * 3 + 1);
                    User player3 = users.get(roomIndex * 3 + 2);
                    
                    roomService.joinRoom(room.getId(), player2.getId().toString());
                    roomService.joinRoom(room.getId(), player3.getId().toString());
                    
                    // Verify room state
                    Room updatedRoom = roomService.getRoomById(room.getId());
                    assertNotNull(updatedRoom);
                    
                    long operationTime = Duration.between(operationStart, Instant.now()).toMillis();
                    totalTime.addAndGet(operationTime);
                    successCount.incrementAndGet();
                    
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                    System.err.println("Error in room operation " + roomIndex + ": " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(60, TimeUnit.SECONDS), "Room operations should complete within 60 seconds");

        Duration totalDuration = Duration.between(startTime, Instant.now());
        double averageTime = totalTime.get() / (double) successCount.get();
        double throughput = successCount.get() / (totalDuration.toMillis() / 1000.0);

        System.out.printf("Room Operations Performance:%n");
        System.out.printf("  Total rooms: %d%n", roomCount);
        System.out.printf("  Successful: %d%n", successCount.get());
        System.out.printf("  Errors: %d%n", errorCount.get());
        System.out.printf("  Total time: %d ms%n", totalDuration.toMillis());
        System.out.printf("  Average operation time: %.2f ms%n", averageTime);
        System.out.printf("  Throughput: %.2f operations/second%n", throughput);

        // Performance assertions
        assertTrue(successCount.get() >= roomCount * 0.90, "At least 90% of operations should succeed");
        assertTrue(averageTime < 1000, "Average operation time should be less than 1000ms");
        assertTrue(throughput > 5, "Throughput should be at least 5 operations/second");
    }

    /**
     * Test Redis operations performance under load
     */
    @Test
    void testRedisPerformanceUnderLoad() throws InterruptedException {
        int operationCount = 1000;
        CountDownLatch latch = new CountDownLatch(operationCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicLong totalTime = new AtomicLong(0);

        Instant startTime = Instant.now();

        for (int i = 0; i < operationCount; i++) {
            final int opIndex = i;
            executorService.submit(() -> {
                try {
                    Instant operationStart = Instant.now();
                    
                    String key = "perf_test_" + opIndex;
                    String value = "test_value_" + opIndex + "_" + System.currentTimeMillis();
                    
                    // Write operation
                    redisTemplate.opsForValue().set(key, value, Duration.ofMinutes(5));
                    
                    // Read operation
                    String retrievedValue = (String) redisTemplate.opsForValue().get(key);
                    assertEquals(value, retrievedValue);
                    
                    // Hash operations
                    String hashKey = "hash_" + opIndex;
                    redisTemplate.opsForHash().put(hashKey, "field1", "value1");
                    redisTemplate.opsForHash().put(hashKey, "field2", "value2");
                    
                    Object hashValue = redisTemplate.opsForHash().get(hashKey, "field1");
                    assertEquals("value1", hashValue);
                    
                    // List operations
                    String listKey = "list_" + opIndex;
                    redisTemplate.opsForList().leftPush(listKey, "item1");
                    redisTemplate.opsForList().leftPush(listKey, "item2");
                    
                    Long listSize = redisTemplate.opsForList().size(listKey);
                    assertEquals(2L, listSize);
                    
                    long operationTime = Duration.between(operationStart, Instant.now()).toMillis();
                    totalTime.addAndGet(operationTime);
                    successCount.incrementAndGet();
                    
                } catch (Exception e) {
                    System.err.println("Error in Redis operation " + opIndex + ": " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(30, TimeUnit.SECONDS), "Redis operations should complete within 30 seconds");

        Duration totalDuration = Duration.between(startTime, Instant.now());
        double averageTime = totalTime.get() / (double) successCount.get();
        double throughput = successCount.get() / (totalDuration.toMillis() / 1000.0);

        System.out.printf("Redis Operations Performance:%n");
        System.out.printf("  Total operations: %d%n", operationCount);
        System.out.printf("  Successful: %d%n", successCount.get());
        System.out.printf("  Total time: %d ms%n", totalDuration.toMillis());
        System.out.printf("  Average operation time: %.2f ms%n", averageTime);
        System.out.printf("  Throughput: %.2f operations/second%n", throughput);

        // Performance assertions
        assertTrue(successCount.get() >= operationCount * 0.98, "At least 98% of Redis operations should succeed");
        assertTrue(averageTime < 50, "Average Redis operation time should be less than 50ms");
        assertTrue(throughput > 100, "Redis throughput should be at least 100 operations/second");
    }

    /**
     * Test database query performance under concurrent load
     */
    @Test
    void testDatabasePerformanceUnderLoad() throws InterruptedException {
        // Create test data
        List<User> users = createTestUsers(100);
        
        int queryCount = 500;
        CountDownLatch latch = new CountDownLatch(queryCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicLong totalTime = new AtomicLong(0);

        Instant startTime = Instant.now();

        for (int i = 0; i < queryCount; i++) {
            final int queryIndex = i;
            executorService.submit(() -> {
                try {
                    Instant operationStart = Instant.now();
                    
                    // Random user query
                    User randomUser = users.get(queryIndex % users.size());
                    User queriedUser = userService.getUserById(randomUser.getId());
                    assertNotNull(queriedUser);
                    assertEquals(randomUser.getOpenId(), queriedUser.getOpenId());
                    
                    // Query by OpenID
                    User userByOpenId = userService.getUserByOpenId(randomUser.getOpenId());
                    assertNotNull(userByOpenId);
                    assertEquals(randomUser.getId(), userByOpenId.getId());
                    
                    long operationTime = Duration.between(operationStart, Instant.now()).toMillis();
                    totalTime.addAndGet(operationTime);
                    successCount.incrementAndGet();
                    
                } catch (Exception e) {
                    System.err.println("Error in database query " + queryIndex + ": " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(45, TimeUnit.SECONDS), "Database queries should complete within 45 seconds");

        Duration totalDuration = Duration.between(startTime, Instant.now());
        double averageTime = totalTime.get() / (double) successCount.get();
        double throughput = successCount.get() / (totalDuration.toMillis() / 1000.0);

        System.out.printf("Database Query Performance:%n");
        System.out.printf("  Total queries: %d%n", queryCount);
        System.out.printf("  Successful: %d%n", successCount.get());
        System.out.printf("  Total time: %d ms%n", totalDuration.toMillis());
        System.out.printf("  Average query time: %.2f ms%n", averageTime);
        System.out.printf("  Throughput: %.2f queries/second%n", throughput);

        // Performance assertions
        assertTrue(successCount.get() >= queryCount * 0.95, "At least 95% of database queries should succeed");
        assertTrue(averageTime < 200, "Average database query time should be less than 200ms");
        assertTrue(throughput > 20, "Database throughput should be at least 20 queries/second");
    }

    /**
     * Test system metrics collection performance
     */
    @Test
    void testSystemMetricsPerformance() throws InterruptedException {
        int metricsCollectionCount = 100;
        CountDownLatch latch = new CountDownLatch(metricsCollectionCount);
        AtomicLong totalTime = new AtomicLong(0);

        Instant startTime = Instant.now();

        for (int i = 0; i < metricsCollectionCount; i++) {
            executorService.submit(() -> {
                try {
                    Instant operationStart = Instant.now();
                    
                    // Collect system metrics
                    var healthMetrics = systemMetricsService.getSystemHealthMetrics();
                    assertNotNull(healthMetrics);
                    assertTrue(healthMetrics.containsKey("jvm"));
                    assertTrue(healthMetrics.containsKey("application"));
                    assertTrue(healthMetrics.containsKey("counters"));
                    
                    // Record some metrics
                    systemMetricsService.incrementGameStarted();
                    systemMetricsService.incrementUserLogin();
                    systemMetricsService.setActiveRooms(10);
                    systemMetricsService.setOnlineUsers(50);
                    
                    long operationTime = Duration.between(operationStart, Instant.now()).toMillis();
                    totalTime.addAndGet(operationTime);
                    
                } catch (Exception e) {
                    System.err.println("Error in metrics collection: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(15, TimeUnit.SECONDS), "Metrics collection should complete within 15 seconds");

        Duration totalDuration = Duration.between(startTime, Instant.now());
        double averageTime = totalTime.get() / (double) metricsCollectionCount;
        double throughput = metricsCollectionCount / (totalDuration.toMillis() / 1000.0);

        System.out.printf("System Metrics Performance:%n");
        System.out.printf("  Total collections: %d%n", metricsCollectionCount);
        System.out.printf("  Total time: %d ms%n", totalDuration.toMillis());
        System.out.printf("  Average collection time: %.2f ms%n", averageTime);
        System.out.printf("  Throughput: %.2f collections/second%n", throughput);

        // Performance assertions
        assertTrue(averageTime < 100, "Average metrics collection time should be less than 100ms");
        assertTrue(throughput > 50, "Metrics collection throughput should be at least 50/second");
    }

    /**
     * Test memory usage under load
     */
    @Test
    void testMemoryUsageUnderLoad() throws InterruptedException {
        Runtime runtime = Runtime.getRuntime();
        long initialMemory = runtime.totalMemory() - runtime.freeMemory();
        
        // Create memory load
        List<Object> memoryLoad = new ArrayList<>();
        int objectCount = 10000;
        CountDownLatch latch = new CountDownLatch(objectCount);

        for (int i = 0; i < objectCount; i++) {
            final int index = i;
            executorService.submit(() -> {
                try {
                    // Create objects that simulate game state
                    GameMessage message = new GameMessage();
                    message.setCmd("test_command_" + index);
                    message.setRoomId("room_" + index);
                    message.setData("test_data_" + index + "_" + System.currentTimeMillis());
                    
                    synchronized (memoryLoad) {
                        memoryLoad.add(message);
                    }
                    
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS), "Memory load test should complete within 10 seconds");

        long finalMemory = runtime.totalMemory() - runtime.freeMemory();
        long memoryIncrease = finalMemory - initialMemory;
        double memoryIncreasePerObject = memoryIncrease / (double) objectCount;

        System.out.printf("Memory Usage Test:%n");
        System.out.printf("  Objects created: %d%n", objectCount);
        System.out.printf("  Initial memory: %d bytes%n", initialMemory);
        System.out.printf("  Final memory: %d bytes%n", finalMemory);
        System.out.printf("  Memory increase: %d bytes%n", memoryIncrease);
        System.out.printf("  Memory per object: %.2f bytes%n", memoryIncreasePerObject);

        // Memory usage assertions
        assertTrue(memoryIncreasePerObject < 1000, "Memory usage per object should be reasonable");
        
        // Clean up
        memoryLoad.clear();
        System.gc();
    }

    // Helper methods

    private List<User> createTestUsers(int count) {
        List<User> users = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            User user = new User();
            user.setOpenId("perf_test_user_" + i);
            user.setNickname("PerfTestUser" + i);
            user.setAvatar("avatar_" + i);
            
            User savedUser = userService.createUser(user);
            users.add(savedUser);
        }
        return users;
    }
}