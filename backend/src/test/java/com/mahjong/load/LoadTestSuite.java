package com.mahjong.load;

import com.mahjong.model.dto.GameMessage;
import com.mahjong.model.dto.PlayAction;
import com.mahjong.model.entity.User;
import com.mahjong.service.UserService;
import com.mahjong.service.RoomService;
import com.mahjong.service.GameService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.*;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Load testing suite for server scalability validation
 * Tests system behavior under high concurrent load
 * 
 * Note: These tests are disabled by default as they require significant resources
 * Enable with @Disabled annotation removal for load testing
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("performance")
@Disabled("Load tests disabled by default - enable for performance testing")
class LoadTestSuite {

    @Autowired
    private UserService userService;

    @Autowired
    private RoomService roomService;

    @Autowired
    private GameService gameService;

    private ExecutorService executorService;
    private final int MAX_CONCURRENT_USERS = 1000;
    private final int MAX_CONCURRENT_ROOMS = 300;
    private final int MESSAGES_PER_SECOND = 5000;

    @BeforeEach
    void setUp() {
        executorService = Executors.newFixedThreadPool(100);
    }

    @Test
    void testConcurrentUserLoad() throws InterruptedException {
        int userCount = MAX_CONCURRENT_USERS;
        CountDownLatch latch = new CountDownLatch(userCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        AtomicLong totalResponseTime = new AtomicLong(0);

        Instant startTime = Instant.now();

        for (int i = 0; i < userCount; i++) {
            final int userId = i;
            executorService.submit(() -> {
                try {
                    Instant operationStart = Instant.now();
                    
                    // Create user
                    User user = new User();
                    user.setOpenId("load_test_user_" + userId);
                    user.setNickname("LoadTestUser" + userId);
                    user.setAvatar("avatar_" + userId);
                    
                    User savedUser = userService.createUser(user);
                    assertNotNull(savedUser);
                    
                    // Perform user operations
                    User retrievedUser = userService.getUserByOpenId("load_test_user_" + userId);
                    assertNotNull(retrievedUser);
                    
                    // Update user profile
                    retrievedUser.setNickname("UpdatedUser" + userId);
                    userService.updateUser(retrievedUser);
                    
                    long responseTime = Duration.between(operationStart, Instant.now()).toMillis();
                    totalResponseTime.addAndGet(responseTime);
                    successCount.incrementAndGet();
                    
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                    System.err.println("Error in user load test " + userId + ": " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(120, TimeUnit.SECONDS), "User load test should complete within 2 minutes");

        Duration totalDuration = Duration.between(startTime, Instant.now());
        double averageResponseTime = totalResponseTime.get() / (double) successCount.get();
        double throughput = successCount.get() / (totalDuration.toMillis() / 1000.0);
        double errorRate = errorCount.get() / (double) userCount;

        System.out.printf("Concurrent User Load Test Results:%n");
        System.out.printf("  Total users: %d%n", userCount);
        System.out.printf("  Successful operations: %d%n", successCount.get());
        System.out.printf("  Failed operations: %d%n", errorCount.get());
        System.out.printf("  Error rate: %.2f%%%n", errorRate * 100);
        System.out.printf("  Total time: %d ms%n", totalDuration.toMillis());
        System.out.printf("  Average response time: %.2f ms%n", averageResponseTime);
        System.out.printf("  Throughput: %.2f operations/second%n", throughput);

        // Performance assertions
        assertTrue(errorRate < 0.05, "Error rate should be less than 5%");
        assertTrue(averageResponseTime < 1000, "Average response time should be less than 1000ms");
        assertTrue(throughput > 50, "Throughput should be at least 50 operations/second");
    }

    @Test
    void testConcurrentRoomLoad() throws InterruptedException {
        // Create users first
        List<User> users = createTestUsers(MAX_CONCURRENT_ROOMS * 3);
        
        int roomCount = MAX_CONCURRENT_ROOMS;
        CountDownLatch latch = new CountDownLatch(roomCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        AtomicLong totalResponseTime = new AtomicLong(0);

        Instant startTime = Instant.now();

        for (int i = 0; i < roomCount; i++) {
            final int roomIndex = i;
            executorService.submit(() -> {
                try {
                    Instant operationStart = Instant.now();
                    
                    User owner = users.get(roomIndex * 3);
                    User player2 = users.get(roomIndex * 3 + 1);
                    User player3 = users.get(roomIndex * 3 + 2);
                    
                    // Create room
                    String roomId = roomService.createRoom(owner.getId().toString(), null).getId();
                    
                    // Join players
                    roomService.joinRoom(roomId, player2.getId().toString());
                    roomService.joinRoom(roomId, player3.getId().toString());
                    
                    // Start game
                    gameService.startGame(roomId);
                    
                    // Simulate some game actions
                    simulateGameActions(roomId, owner.getId().toString(), 5);
                    
                    long responseTime = Duration.between(operationStart, Instant.now()).toMillis();
                    totalResponseTime.addAndGet(responseTime);
                    successCount.incrementAndGet();
                    
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                    System.err.println("Error in room load test " + roomIndex + ": " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(300, TimeUnit.SECONDS), "Room load test should complete within 5 minutes");

        Duration totalDuration = Duration.between(startTime, Instant.now());
        double averageResponseTime = totalResponseTime.get() / (double) successCount.get();
        double throughput = successCount.get() / (totalDuration.toMillis() / 1000.0);
        double errorRate = errorCount.get() / (double) roomCount;

        System.out.printf("Concurrent Room Load Test Results:%n");
        System.out.printf("  Total rooms: %d%n", roomCount);
        System.out.printf("  Successful operations: %d%n", successCount.get());
        System.out.printf("  Failed operations: %d%n", errorCount.get());
        System.out.printf("  Error rate: %.2f%%%n", errorRate * 100);
        System.out.printf("  Total time: %d ms%n", totalDuration.toMillis());
        System.out.printf("  Average response time: %.2f ms%n", averageResponseTime);
        System.out.printf("  Throughput: %.2f rooms/second%n", throughput);

        // Performance assertions
        assertTrue(errorRate < 0.10, "Error rate should be less than 10%");
        assertTrue(averageResponseTime < 3000, "Average response time should be less than 3000ms");
        assertTrue(throughput > 10, "Throughput should be at least 10 rooms/second");
    }

    @Test
    void testWebSocketConnectionLoad() throws InterruptedException {
        int connectionCount = 500;
        CountDownLatch connectionLatch = new CountDownLatch(connectionCount);
        CountDownLatch messageLatch = new CountDownLatch(connectionCount * 10); // 10 messages per connection
        AtomicInteger successfulConnections = new AtomicInteger(0);
        AtomicInteger failedConnections = new AtomicInteger(0);
        AtomicInteger messagesReceived = new AtomicInteger(0);

        List<WebSocketSession> sessions = new ArrayList<>();
        
        for (int i = 0; i < connectionCount; i++) {
            final int connectionId = i;
            executorService.submit(() -> {
                try {
                    WebSocketHandler handler = new WebSocketHandler() {
                        @Override
                        public void afterConnectionEstablished(WebSocketSession session) {
                            successfulConnections.incrementAndGet();
                            connectionLatch.countDown();
                            
                            // Send test messages
                            for (int j = 0; j < 10; j++) {
                                try {
                                    GameMessage message = new GameMessage();
                                    message.setType("REQ");
                                    message.setCmd("HEARTBEAT");
                                    message.setTimestamp(System.currentTimeMillis());
                                    
                                    session.sendMessage(new TextMessage(message.toString()));
                                } catch (Exception e) {
                                    System.err.println("Error sending message: " + e.getMessage());
                                }
                            }
                        }

                        @Override
                        public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) {
                            messagesReceived.incrementAndGet();
                            messageLatch.countDown();
                        }

                        @Override
                        public void handleTransportError(WebSocketSession session, Throwable exception) {
                            failedConnections.incrementAndGet();
                            connectionLatch.countDown();
                        }

                        @Override
                        public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) {}

                        @Override
                        public boolean supportsPartialMessages() {
                            return false;
                        }
                    };

                    StandardWebSocketClient client = new StandardWebSocketClient();
                    URI uri = URI.create("ws://localhost:8080/game?token=test_token_" + connectionId);
                    
                    WebSocketSession session = client.doHandshake(handler, null, uri).get(10, TimeUnit.SECONDS);
                    synchronized (sessions) {
                        sessions.add(session);
                    }
                    
                } catch (Exception e) {
                    failedConnections.incrementAndGet();
                    connectionLatch.countDown();
                    System.err.println("Error establishing WebSocket connection " + connectionId + ": " + e.getMessage());
                }
            });
        }

        assertTrue(connectionLatch.await(60, TimeUnit.SECONDS), "WebSocket connections should establish within 1 minute");
        assertTrue(messageLatch.await(120, TimeUnit.SECONDS), "Messages should be processed within 2 minutes");

        double connectionSuccessRate = successfulConnections.get() / (double) connectionCount;
        double messageSuccessRate = messagesReceived.get() / (double) (connectionCount * 10);

        System.out.printf("WebSocket Connection Load Test Results:%n");
        System.out.printf("  Total connections attempted: %d%n", connectionCount);
        System.out.printf("  Successful connections: %d%n", successfulConnections.get());
        System.out.printf("  Failed connections: %d%n", failedConnections.get());
        System.out.printf("  Connection success rate: %.2f%%%n", connectionSuccessRate * 100);
        System.out.printf("  Messages sent: %d%n", connectionCount * 10);
        System.out.printf("  Messages received: %d%n", messagesReceived.get());
        System.out.printf("  Message success rate: %.2f%%%n", messageSuccessRate * 100);

        // Cleanup connections
        for (WebSocketSession session : sessions) {
            try {
                if (session.isOpen()) {
                    session.close();
                }
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }

        // Performance assertions
        assertTrue(connectionSuccessRate > 0.95, "Connection success rate should be above 95%");
        assertTrue(messageSuccessRate > 0.90, "Message success rate should be above 90%");
    }

    @Test
    void testMessageThroughputLoad() throws InterruptedException {
        int messagesPerSecond = MESSAGES_PER_SECOND;
        int testDurationSeconds = 30;
        int totalMessages = messagesPerSecond * testDurationSeconds;
        
        CountDownLatch latch = new CountDownLatch(totalMessages);
        AtomicInteger processedMessages = new AtomicInteger(0);
        AtomicLong totalProcessingTime = new AtomicLong(0);

        // Create test users and rooms
        List<User> users = createTestUsers(100);
        List<String> roomIds = new ArrayList<>();
        
        for (int i = 0; i < 30; i++) {
            String roomId = roomService.createRoom(users.get(i * 3).getId().toString(), null).getId();
            roomService.joinRoom(roomId, users.get(i * 3 + 1).getId().toString());
            roomService.joinRoom(roomId, users.get(i * 3 + 2).getId().toString());
            gameService.startGame(roomId);
            roomIds.add(roomId);
        }

        Instant startTime = Instant.now();

        // Send messages at specified rate
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);
        scheduler.scheduleAtFixedRate(() -> {
            for (int i = 0; i < messagesPerSecond / 10; i++) { // Batch messages
                executorService.submit(() -> {
                    try {
                        Instant messageStart = Instant.now();
                        
                        String roomId = roomIds.get(ThreadLocalRandom.current().nextInt(roomIds.size()));
                        String userId = users.get(ThreadLocalRandom.current().nextInt(users.size())).getId().toString();
                        
                        PlayAction action = new PlayAction("1W");
                        gameService.handlePlayerAction(roomId, userId, action);
                        
                        long processingTime = Duration.between(messageStart, Instant.now()).toMillis();
                        totalProcessingTime.addAndGet(processingTime);
                        processedMessages.incrementAndGet();
                        
                    } catch (Exception e) {
                        // Expected - many actions will be invalid
                    } finally {
                        latch.countDown();
                    }
                });
            }
        }, 0, 100, TimeUnit.MILLISECONDS);

        assertTrue(latch.await(testDurationSeconds + 30, TimeUnit.SECONDS), 
            "Message throughput test should complete within time limit");

        scheduler.shutdown();

        Duration totalDuration = Duration.between(startTime, Instant.now());
        double actualThroughput = processedMessages.get() / (totalDuration.toMillis() / 1000.0);
        double averageProcessingTime = totalProcessingTime.get() / (double) processedMessages.get();

        System.out.printf("Message Throughput Load Test Results:%n");
        System.out.printf("  Target messages/second: %d%n", messagesPerSecond);
        System.out.printf("  Test duration: %d seconds%n", testDurationSeconds);
        System.out.printf("  Total messages sent: %d%n", totalMessages);
        System.out.printf("  Messages processed: %d%n", processedMessages.get());
        System.out.printf("  Actual throughput: %.2f messages/second%n", actualThroughput);
        System.out.printf("  Average processing time: %.2f ms%n", averageProcessingTime);

        // Performance assertions
        assertTrue(actualThroughput > messagesPerSecond * 0.8, 
            "Actual throughput should be at least 80% of target");
        assertTrue(averageProcessingTime < 10, 
            "Average message processing time should be less than 10ms");
    }

    @Test
    void testMemoryUsageUnderLoad() throws InterruptedException {
        Runtime runtime = Runtime.getRuntime();
        long initialMemory = runtime.totalMemory() - runtime.freeMemory();
        
        // Create sustained load
        List<User> users = createTestUsers(500);
        List<String> roomIds = new ArrayList<>();
        
        // Create many rooms and games
        for (int i = 0; i < 100; i++) {
            String roomId = roomService.createRoom(users.get(i * 3).getId().toString(), null).getId();
            roomService.joinRoom(roomId, users.get(i * 3 + 1).getId().toString());
            roomService.joinRoom(roomId, users.get(i * 3 + 2).getId().toString());
            gameService.startGame(roomId);
            roomIds.add(roomId);
        }

        // Simulate continuous gameplay
        CountDownLatch latch = new CountDownLatch(10000);
        for (int i = 0; i < 10000; i++) {
            final int actionIndex = i;
            executorService.submit(() -> {
                try {
                    String roomId = roomIds.get(actionIndex % roomIds.size());
                    String userId = users.get(actionIndex % users.size()).getId().toString();
                    
                    PlayAction action = new PlayAction("1W");
                    gameService.handlePlayerAction(roomId, userId, action);
                    
                } catch (Exception e) {
                    // Expected - many actions will be invalid
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(120, TimeUnit.SECONDS), "Memory load test should complete within 2 minutes");

        // Force garbage collection
        System.gc();
        Thread.sleep(5000);

        long finalMemory = runtime.totalMemory() - runtime.freeMemory();
        long memoryIncrease = finalMemory - initialMemory;
        long maxMemory = runtime.maxMemory();
        double memoryUsagePercentage = (finalMemory / (double) maxMemory) * 100;

        System.out.printf("Memory Usage Load Test Results:%n");
        System.out.printf("  Initial memory: %d MB%n", initialMemory / (1024 * 1024));
        System.out.printf("  Final memory: %d MB%n", finalMemory / (1024 * 1024));
        System.out.printf("  Memory increase: %d MB%n", memoryIncrease / (1024 * 1024));
        System.out.printf("  Max memory: %d MB%n", maxMemory / (1024 * 1024));
        System.out.printf("  Memory usage: %.2f%%%n", memoryUsagePercentage);

        // Memory usage assertions
        assertTrue(memoryUsagePercentage < 80, "Memory usage should be less than 80% of max heap");
        assertTrue(memoryIncrease < maxMemory * 0.5, "Memory increase should be reasonable");
    }

    @Test
    void testDatabaseConnectionPoolLoad() throws InterruptedException {
        int concurrentQueries = 200;
        CountDownLatch latch = new CountDownLatch(concurrentQueries);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        AtomicLong totalQueryTime = new AtomicLong(0);

        for (int i = 0; i < concurrentQueries; i++) {
            final int queryIndex = i;
            executorService.submit(() -> {
                try {
                    Instant queryStart = Instant.now();
                    
                    // Perform database operations
                    User user = new User();
                    user.setOpenId("db_load_test_" + queryIndex);
                    user.setNickname("DBLoadTest" + queryIndex);
                    user.setAvatar("avatar.jpg");
                    
                    User savedUser = userService.createUser(user);
                    User retrievedUser = userService.getUserById(savedUser.getId());
                    userService.updateUser(retrievedUser);
                    
                    long queryTime = Duration.between(queryStart, Instant.now()).toMillis();
                    totalQueryTime.addAndGet(queryTime);
                    successCount.incrementAndGet();
                    
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                    System.err.println("Database query error " + queryIndex + ": " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(60, TimeUnit.SECONDS), "Database load test should complete within 1 minute");

        double averageQueryTime = totalQueryTime.get() / (double) successCount.get();
        double errorRate = errorCount.get() / (double) concurrentQueries;

        System.out.printf("Database Connection Pool Load Test Results:%n");
        System.out.printf("  Concurrent queries: %d%n", concurrentQueries);
        System.out.printf("  Successful queries: %d%n", successCount.get());
        System.out.printf("  Failed queries: %d%n", errorCount.get());
        System.out.printf("  Error rate: %.2f%%%n", errorRate * 100);
        System.out.printf("  Average query time: %.2f ms%n", averageQueryTime);

        // Performance assertions
        assertTrue(errorRate < 0.05, "Database error rate should be less than 5%");
        assertTrue(averageQueryTime < 500, "Average database query time should be less than 500ms");
    }

    // Helper methods

    private List<User> createTestUsers(int count) {
        List<User> users = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            User user = new User();
            user.setOpenId("load_test_user_" + i + "_" + System.currentTimeMillis());
            user.setNickname("LoadTestUser" + i);
            user.setAvatar("avatar_" + i);
            user.setCoins(1000);
            user.setRoomCards(10);
            
            User savedUser = userService.createUser(user);
            users.add(savedUser);
        }
        return users;
    }

    private void simulateGameActions(String roomId, String userId, int actionCount) {
        for (int i = 0; i < actionCount; i++) {
            try {
                PlayAction action = new PlayAction("1W");
                gameService.handlePlayerAction(roomId, userId, action);
                Thread.sleep(100); // Small delay between actions
            } catch (Exception e) {
                // Expected - many actions will be invalid
                break;
            }
        }
    }
}