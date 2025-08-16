package com.mahjong.service;

import com.mahjong.model.config.RoomConfig;
import com.mahjong.model.game.GameState;
import com.mahjong.model.game.PlayerState;
import com.mahjong.model.game.Tile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Redis-based game state management
 */
@SpringBootTest
@ActiveProfiles("test")
class RedisGameStateIntegrationTest {
    
    @Autowired
    private GameStateRedisService gameStateRedisService;
    
    @Autowired
    private GameStateConsistencyService consistencyService;
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    private String testRoomId;
    private List<String> playerIds;
    private RoomConfig testConfig;
    
    @BeforeEach
    void setUp() {
        // Clean up Redis before each test
        redisTemplate.getConnectionFactory().getConnection().flushAll();
        
        testRoomId = "123456";
        playerIds = List.of("user1", "user2", "user3");
        testConfig = createTestRoomConfig();
    }
    
    @Test
    void shouldHandleCompleteGameStateLifecycle() {
        // Create initial game state
        GameState gameState = new GameState(testRoomId, "game-123", playerIds, testConfig);
        
        // Validate initial state
        GameStateConsistencyService.ValidationResult validation = consistencyService.validateGameState(gameState);
        assertTrue(validation.isValid());
        
        // Save to Redis
        gameStateRedisService.saveGameState(gameState);
        assertTrue(gameStateRedisService.gameStateExists(testRoomId));
        
        // Load from Redis
        GameState loadedState = gameStateRedisService.loadGameState(testRoomId);
        assertNotNull(loadedState);
        assertEquals(gameState.getRoomId(), loadedState.getRoomId());
        
        // Validate loaded state
        validation = consistencyService.validateGameState(loadedState);
        assertTrue(validation.isValid());
        
        // Deal tiles and update
        loadedState.dealInitialTiles();
        gameStateRedisService.saveGameState(loadedState);
        
        // Load updated state
        GameState updatedState = gameStateRedisService.loadGameState(testRoomId);
        assertEquals(GameState.GamePhase.PLAYING, updatedState.getPhase());
        
        // Validate updated state
        validation = consistencyService.validateGameState(updatedState);
        assertTrue(validation.isValid());
        
        // Clean up
        gameStateRedisService.deleteGameState(testRoomId);
        assertFalse(gameStateRedisService.gameStateExists(testRoomId));
    }
    
    @Test
    void shouldHandleSessionManagement() {
        String sessionId1 = "session-1";
        String sessionId2 = "session-2";
        String sessionId3 = "session-3";
        
        // Save player sessions
        gameStateRedisService.savePlayerSession(playerIds.get(0), sessionId1, testRoomId);
        gameStateRedisService.savePlayerSession(playerIds.get(1), sessionId2, testRoomId);
        gameStateRedisService.savePlayerSession(playerIds.get(2), sessionId3, testRoomId);
        
        // Verify session mappings
        assertEquals(sessionId1, gameStateRedisService.getPlayerSessionId(playerIds.get(0)));
        assertEquals(sessionId2, gameStateRedisService.getPlayerSessionId(playerIds.get(1)));
        assertEquals(sessionId3, gameStateRedisService.getPlayerSessionId(playerIds.get(2)));
        
        // Verify session info
        GameStateRedisService.SessionInfo sessionInfo = gameStateRedisService.getSessionInfo(sessionId1);
        assertNotNull(sessionInfo);
        assertEquals(playerIds.get(0), sessionInfo.getUserId());
        assertEquals(testRoomId, sessionInfo.getRoomId());
        
        // Verify room players
        Set<Object> roomPlayers = gameStateRedisService.getRoomPlayers(testRoomId);
        assertEquals(3, roomPlayers.size());
        assertTrue(roomPlayers.containsAll(playerIds));
        
        // Update heartbeat
        long originalHeartbeat = sessionInfo.getLastHeartbeat();
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        gameStateRedisService.updateSessionHeartbeat(sessionId1);
        GameStateRedisService.SessionInfo updatedInfo = gameStateRedisService.getSessionInfo(sessionId1);
        assertTrue(updatedInfo.getLastHeartbeat() > originalHeartbeat);
        
        // Remove sessions
        gameStateRedisService.removePlayerSession(playerIds.get(0), sessionId1);
        assertNull(gameStateRedisService.getPlayerSessionId(playerIds.get(0)));
        
        roomPlayers = gameStateRedisService.getRoomPlayers(testRoomId);
        assertEquals(2, roomPlayers.size());
        assertFalse(roomPlayers.contains(playerIds.get(0)));
    }
    
    @Test
    void shouldHandleGameStateValidationAndRecovery() {
        // Create and save a valid game state
        GameState gameState = new GameState(testRoomId, "game-123", playerIds, testConfig);
        gameState.dealInitialTiles();
        gameStateRedisService.saveGameState(gameState);
        
        // Load and validate
        GameState loadedState = gameStateRedisService.loadGameState(testRoomId);
        GameStateConsistencyService.ValidationResult validation = consistencyService.validateGameState(loadedState);
        assertTrue(validation.isValid());
        
        // Test recovery for non-existent state
        GameState recoveredState = consistencyService.recoverGameState("nonexistent");
        assertNull(recoveredState);
        
        // Test recovery for existing valid state
        recoveredState = consistencyService.recoverGameState(testRoomId);
        assertNotNull(recoveredState);
        assertEquals(testRoomId, recoveredState.getRoomId());
    }
    
    @Test
    void shouldHandleSnapshotCreation() {
        GameState gameState = new GameState(testRoomId, "game-123", playerIds, testConfig);
        gameState.dealInitialTiles();
        
        // Create snapshot
        GameStateRedisService.GameStateSnapshot snapshot = gameStateRedisService.createSnapshot(gameState);
        
        assertNotNull(snapshot);
        assertEquals(gameState.getRoomId(), snapshot.getRoomId());
        assertEquals(gameState.getGameId(), snapshot.getGameId());
        assertEquals(gameState.getPhase(), snapshot.getPhase());
        assertEquals(gameState.getCurrentPlayerIndex(), snapshot.getCurrentPlayerIndex());
        assertEquals(gameState.getDealerUserId(), snapshot.getDealerUserId());
        assertEquals(gameState.getRemainingTiles(), snapshot.getRemainingTiles());
        assertTrue(snapshot.getSnapshotTime() > 0);
    }
    
    @Test
    void shouldHandleConcurrentAccess() throws InterruptedException {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        
        // Create initial game state
        GameState gameState = new GameState(testRoomId, "game-123", playerIds, testConfig);
        gameStateRedisService.saveGameState(gameState);
        
        // Concurrent read/write operations
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    // Load game state
                    GameState loadedState = gameStateRedisService.loadGameState(testRoomId);
                    assertNotNull(loadedState);
                    
                    // Validate state
                    GameStateConsistencyService.ValidationResult validation = 
                        consistencyService.validateGameState(loadedState);
                    assertTrue(validation.isValid());
                    
                    // Update session
                    String sessionId = "session-" + threadId;
                    String userId = "user-" + threadId;
                    gameStateRedisService.savePlayerSession(userId, sessionId, testRoomId);
                    
                    // Verify session
                    assertEquals(sessionId, gameStateRedisService.getPlayerSessionId(userId));
                    
                } finally {
                    latch.countDown();
                }
            });
        }
        
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        executor.shutdown();
    }
    
    @Test
    void shouldHandleGameStateWithComplexOperations() {
        // Create game state and deal tiles
        GameState gameState = new GameState(testRoomId, "game-123", playerIds, testConfig);
        gameState.dealInitialTiles();
        
        // Perform some game actions
        PlayerState currentPlayer = gameState.getCurrentPlayer();
        if (!currentPlayer.getHandTiles().isEmpty()) {
            Tile tileToDiscard = currentPlayer.getHandTiles().get(0);
            gameState.processDiscard(currentPlayer.getUserId(), tileToDiscard);
        }
        
        // Save complex state
        gameStateRedisService.saveGameState(gameState);
        
        // Load and validate
        GameState loadedState = gameStateRedisService.loadGameState(testRoomId);
        assertNotNull(loadedState);
        
        GameStateConsistencyService.ValidationResult validation = consistencyService.validateGameState(loadedState);
        assertTrue(validation.isValid());
        
        // Verify discard pile has the discarded tile
        assertFalse(loadedState.getDiscardPile().isEmpty());
        
        // Verify tile counts are still correct
        int totalPlayerTiles = loadedState.getPlayers().stream()
                .mapToInt(p -> p.getHandTiles().size())
                .sum();
        int tilesInWall = loadedState.getRemainingTiles();
        int tilesInDiscard = loadedState.getDiscardPile().size();
        
        assertEquals(36, totalPlayerTiles + tilesInWall + tilesInDiscard);
    }
    
    @Test
    void shouldHandleTtlAndExpiration() {
        GameState gameState = new GameState(testRoomId, "game-123", playerIds, testConfig);
        
        // Save with TTL
        gameStateRedisService.saveGameState(gameState);
        assertTrue(gameStateRedisService.gameStateExists(testRoomId));
        
        // Refresh TTL
        gameStateRedisService.refreshGameStateTtl(testRoomId);
        assertTrue(gameStateRedisService.gameStateExists(testRoomId));
        
        // Verify TTL is set (we can't easily test expiration in unit tests)
        String key = "game:state:" + testRoomId;
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        assertNotNull(ttl);
        assertTrue(ttl > 0);
    }
    
    @Test
    void shouldHandleMultipleGameStates() {
        String roomId1 = "room1";
        String roomId2 = "room2";
        String roomId3 = "room3";
        
        // Create multiple game states
        GameState gameState1 = new GameState(roomId1, "game-1", playerIds, testConfig);
        GameState gameState2 = new GameState(roomId2, "game-2", playerIds, testConfig);
        GameState gameState3 = new GameState(roomId3, "game-3", playerIds, testConfig);
        
        // Save all states
        gameStateRedisService.saveGameState(gameState1);
        gameStateRedisService.saveGameState(gameState2);
        gameStateRedisService.saveGameState(gameState3);
        
        // Verify all exist
        assertTrue(gameStateRedisService.gameStateExists(roomId1));
        assertTrue(gameStateRedisService.gameStateExists(roomId2));
        assertTrue(gameStateRedisService.gameStateExists(roomId3));
        
        // Get all active rooms
        Set<String> activeRooms = gameStateRedisService.getAllActiveGameRooms();
        assertTrue(activeRooms.size() >= 3);
        
        // Load and validate each
        GameState loaded1 = gameStateRedisService.loadGameState(roomId1);
        GameState loaded2 = gameStateRedisService.loadGameState(roomId2);
        GameState loaded3 = gameStateRedisService.loadGameState(roomId3);
        
        assertNotNull(loaded1);
        assertNotNull(loaded2);
        assertNotNull(loaded3);
        
        assertEquals(roomId1, loaded1.getRoomId());
        assertEquals(roomId2, loaded2.getRoomId());
        assertEquals(roomId3, loaded3.getRoomId());
        
        // Clean up
        gameStateRedisService.deleteGameState(roomId1);
        gameStateRedisService.deleteGameState(roomId2);
        gameStateRedisService.deleteGameState(roomId3);
    }
    
    @Test
    void shouldHandleErrorConditionsGracefully() {
        // Test with null game state
        assertDoesNotThrow(() -> gameStateRedisService.saveGameState(null));
        
        // Test loading non-existent state
        GameState nonExistent = gameStateRedisService.loadGameState("nonexistent");
        assertNull(nonExistent);
        
        // Test deleting non-existent state
        assertDoesNotThrow(() -> gameStateRedisService.deleteGameState("nonexistent"));
        
        // Test session operations with invalid data
        assertDoesNotThrow(() -> {
            gameStateRedisService.removePlayerSession("nonexistent", "nonexistent");
            gameStateRedisService.updateSessionHeartbeat("nonexistent");
        });
        
        // Test validation with null
        assertFalse(gameStateRedisService.validateGameState(null));
    }
    
    private RoomConfig createTestRoomConfig() {
        RoomConfig config = new RoomConfig();
        config.setPlayers(3);
        config.setTiles("WAN_ONLY");
        config.setAllowPeng(true);
        config.setAllowGang(true);
        config.setAllowChi(false);
        return config;
    }
}