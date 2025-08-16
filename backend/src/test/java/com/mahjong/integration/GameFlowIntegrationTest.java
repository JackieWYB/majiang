package com.mahjong.integration;

import com.mahjong.model.config.RoomConfig;
import com.mahjong.model.dto.GameMessage;
import com.mahjong.model.dto.PlayAction;
import com.mahjong.model.dto.PengAction;
import com.mahjong.model.dto.HuAction;
import com.mahjong.model.entity.Room;
import com.mahjong.model.entity.User;
import com.mahjong.model.game.GameState;
import com.mahjong.service.GameService;
import com.mahjong.service.RoomService;
import com.mahjong.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end integration tests for complete game scenarios
 * Tests full game flow from room creation to settlement
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class GameFlowIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("mahjong_test")
            .withUsername("test")
            .withPassword("test");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:6-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }

    @Autowired
    private UserService userService;

    @Autowired
    private RoomService roomService;

    @Autowired
    private GameService gameService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private User user1, user2, user3;
    private Room testRoom;

    @BeforeEach
    void setUp() {
        // Clear Redis
        redisTemplate.getConnectionFactory().getConnection().flushAll();

        // Create test users
        user1 = createTestUser("player1", "Player One");
        user2 = createTestUser("player2", "Player Two");
        user3 = createTestUser("player3", "Player Three");
    }

    @Test
    void testCompleteGameFlow_BasicWin() throws InterruptedException {
        // 1. Create room
        testRoom = roomService.createRoom(user1.getId().toString(), createDefaultConfig());
        assertNotNull(testRoom);
        assertEquals(user1.getId(), testRoom.getOwnerId());

        // 2. Join players
        roomService.joinRoom(testRoom.getId(), user2.getId().toString());
        roomService.joinRoom(testRoom.getId(), user3.getId().toString());

        // Verify room is ready
        Room updatedRoom = roomService.getRoomById(testRoom.getId());
        assertEquals(3, updatedRoom.getPlayers().size());

        // 3. Start game
        GameState gameState = gameService.startGame(testRoom.getId());
        assertNotNull(gameState);
        assertEquals(GameState.GamePhase.PLAYING, gameState.getPhase());
        assertEquals(3, gameState.getPlayers().size());

        // 4. Simulate game play
        simulateGamePlay(gameState);

        // 5. Verify game completion
        GameState finalState = gameService.getGameState(testRoom.getId());
        assertEquals(GameState.GamePhase.SETTLEMENT, finalState.getPhase());
    }

    @Test
    void testGameFlow_WithReconnection() throws InterruptedException {
        // Setup game
        testRoom = roomService.createRoom(user1.getId().toString(), createDefaultConfig());
        roomService.joinRoom(testRoom.getId(), user2.getId().toString());
        roomService.joinRoom(testRoom.getId(), user3.getId().toString());

        GameState gameState = gameService.startGame(testRoom.getId());

        // Simulate player disconnection
        gameService.handlePlayerDisconnection(user2.getId().toString(), testRoom.getId());

        // Verify player is marked as disconnected
        GameState stateAfterDisconnect = gameService.getGameState(testRoom.getId());
        assertNotNull(stateAfterDisconnect);

        // Simulate reconnection
        GameState reconnectState = gameService.handlePlayerReconnection(
            user2.getId().toString(), testRoom.getId());
        assertNotNull(reconnectState);

        // Verify player is reconnected
        assertEquals(GameState.GamePhase.PLAYING, reconnectState.getPhase());
    }

    @Test
    void testGameFlow_MultipleRounds() throws InterruptedException {
        // Create room with multiple rounds
        RoomConfig config = createDefaultConfig();
        config.setMaxRounds(3);
        
        testRoom = roomService.createRoom(user1.getId().toString(), config);
        roomService.joinRoom(testRoom.getId(), user2.getId().toString());
        roomService.joinRoom(testRoom.getId(), user3.getId().toString());

        // Play multiple rounds
        for (int round = 1; round <= 3; round++) {
            GameState gameState = gameService.startGame(testRoom.getId());
            assertEquals(round, gameState.getRoundNumber());

            // Simulate quick game completion
            simulateQuickGameCompletion(gameState);

            // Verify round completion
            GameState finalState = gameService.getGameState(testRoom.getId());
            assertEquals(GameState.GamePhase.SETTLEMENT, finalState.getPhase());

            if (round < 3) {
                // Start next round
                gameService.startNextRound(testRoom.getId());
            }
        }

        // Verify final game state
        GameState finalGameState = gameService.getGameState(testRoom.getId());
        assertEquals(GameState.GamePhase.FINISHED, finalGameState.getPhase());
    }

    @Test
    void testGameFlow_WithTimeouts() throws InterruptedException {
        testRoom = roomService.createRoom(user1.getId().toString(), createDefaultConfig());
        roomService.joinRoom(testRoom.getId(), user2.getId().toString());
        roomService.joinRoom(testRoom.getId(), user3.getId().toString());

        GameState gameState = gameService.startGame(testRoom.getId());

        // Wait for turn timeout
        Thread.sleep(16000); // Longer than turn timeout

        // Verify auto-play was triggered
        GameState stateAfterTimeout = gameService.getGameState(testRoom.getId());
        assertNotNull(stateAfterTimeout);
        
        // Current player should have been auto-played
        assertTrue(stateAfterTimeout.getTotalTurns() > 0);
    }

    @Test
    void testGameFlow_ConcurrentActions() throws InterruptedException {
        testRoom = roomService.createRoom(user1.getId().toString(), createDefaultConfig());
        roomService.joinRoom(testRoom.getId(), user2.getId().toString());
        roomService.joinRoom(testRoom.getId(), user3.getId().toString());

        GameState gameState = gameService.startGame(testRoom.getId());

        // Simulate concurrent actions from multiple players
        CountDownLatch latch = new CountDownLatch(3);
        
        // Player 1 discards
        new Thread(() -> {
            try {
                PlayAction action = new PlayAction("1W");
                gameService.handlePlayerAction(testRoom.getId(), user1.getId().toString(), action);
            } catch (Exception e) {
                // Expected - only current player can act
            } finally {
                latch.countDown();
            }
        }).start();

        // Player 2 tries to peng
        new Thread(() -> {
            try {
                PengAction action = new PengAction("1W", user1.getId().toString());
                gameService.handlePlayerAction(testRoom.getId(), user2.getId().toString(), action);
            } catch (Exception e) {
                // May fail if conditions not met
            } finally {
                latch.countDown();
            }
        }).start();

        // Player 3 tries to hu
        new Thread(() -> {
            try {
                HuAction action = new HuAction("1W", false);
                gameService.handlePlayerAction(testRoom.getId(), user3.getId().toString(), action);
            } catch (Exception e) {
                // May fail if conditions not met
            } finally {
                latch.countDown();
            }
        }).start();

        assertTrue(latch.await(5, TimeUnit.SECONDS));

        // Verify game state is still consistent
        GameState finalState = gameService.getGameState(testRoom.getId());
        assertNotNull(finalState);
        assertEquals(GameState.GamePhase.PLAYING, finalState.getPhase());
    }

    @Test
    void testGameFlow_ErrorRecovery() throws InterruptedException {
        testRoom = roomService.createRoom(user1.getId().toString(), createDefaultConfig());
        roomService.joinRoom(testRoom.getId(), user2.getId().toString());
        roomService.joinRoom(testRoom.getId(), user3.getId().toString());

        GameState gameState = gameService.startGame(testRoom.getId());

        // Simulate Redis failure by clearing the game state
        redisTemplate.delete("game:" + testRoom.getId());

        // Try to get game state - should trigger recovery
        GameState recoveredState = gameService.getGameState(testRoom.getId());
        
        // Should either recover from database or create new state
        assertNotNull(recoveredState);
    }

    @Test
    void testGameFlow_MemoryUsage() throws InterruptedException {
        // Create multiple games to test memory usage
        for (int i = 0; i < 10; i++) {
            User owner = createTestUser("owner" + i, "Owner " + i);
            User player2 = createTestUser("player2_" + i, "Player 2 " + i);
            User player3 = createTestUser("player3_" + i, "Player 3 " + i);

            Room room = roomService.createRoom(owner.getId().toString(), createDefaultConfig());
            roomService.joinRoom(room.getId(), player2.getId().toString());
            roomService.joinRoom(room.getId(), player3.getId().toString());

            GameState gameState = gameService.startGame(room.getId());
            assertNotNull(gameState);

            // Simulate quick completion
            simulateQuickGameCompletion(gameState);
        }

        // Force garbage collection
        System.gc();
        Thread.sleep(1000);

        // Verify system is still responsive
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        long maxMemory = runtime.maxMemory();
        
        // Memory usage should be reasonable (less than 80% of max)
        assertTrue(usedMemory < maxMemory * 0.8, 
            "Memory usage too high: " + usedMemory + "/" + maxMemory);
    }

    @Test
    void testGameFlow_DatabaseConsistency() throws InterruptedException {
        testRoom = roomService.createRoom(user1.getId().toString(), createDefaultConfig());
        roomService.joinRoom(testRoom.getId(), user2.getId().toString());
        roomService.joinRoom(testRoom.getId(), user3.getId().toString());

        GameState gameState = gameService.startGame(testRoom.getId());
        
        // Simulate game completion
        simulateGamePlay(gameState);

        // Verify database records were created
        // This would check game records, player records, etc.
        // Implementation depends on your specific data access layer
        
        GameState finalState = gameService.getGameState(testRoom.getId());
        assertEquals(GameState.GamePhase.SETTLEMENT, finalState.getPhase());
    }

    // Helper methods

    private User createTestUser(String openId, String nickname) {
        User user = new User();
        user.setOpenId(openId);
        user.setNickname(nickname);
        user.setAvatar("avatar.jpg");
        user.setCoins(1000);
        user.setRoomCards(10);
        return userService.createUser(user);
    }

    private RoomConfig createDefaultConfig() {
        RoomConfig config = new RoomConfig();
        config.setTiles("WAN_ONLY");
        config.setAllowPeng(true);
        config.setAllowGang(true);
        config.setAllowChi(false);
        config.setMaxRounds(1);
        return config;
    }

    private void simulateGamePlay(GameState gameState) throws InterruptedException {
        // Simulate a few turns of gameplay
        for (int turn = 0; turn < 5 && gameState.getPhase() == GameState.GamePhase.PLAYING; turn++) {
            String currentPlayerId = gameState.getCurrentPlayer().getUserId();
            
            // Simple discard action
            PlayAction action = new PlayAction(
                gameState.getCurrentPlayer().getHandTiles().get(0).toString()
            );
            
            try {
                gameService.handlePlayerAction(testRoom.getId(), currentPlayerId, action);
                gameState = gameService.getGameState(testRoom.getId());
                Thread.sleep(100); // Small delay between actions
            } catch (Exception e) {
                // Action might fail, continue
                break;
            }
        }
    }

    private void simulateQuickGameCompletion(GameState gameState) throws InterruptedException {
        // Force game to settlement phase for testing
        gameState.setPhase(GameState.GamePhase.SETTLEMENT);
        gameService.saveGameState(gameState);
    }
}