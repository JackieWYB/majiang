package com.mahjong.service;

import com.mahjong.model.config.RoomConfig;
import com.mahjong.model.game.GameState;
import com.mahjong.model.game.PlayerState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for GameStateRedisService
 */
@SpringBootTest
@ActiveProfiles("test")
class GameStateRedisServiceTest {
    
    @Autowired
    private GameStateRedisService gameStateRedisService;
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    private GameState testGameState;
    private String testRoomId;
    private String testUserId1;
    private String testUserId2;
    private String testUserId3;
    
    @BeforeEach
    void setUp() {
        // Clean up Redis before each test
        redisTemplate.getConnectionFactory().getConnection().flushAll();
        
        testRoomId = "123456";
        testUserId1 = "user1";
        testUserId2 = "user2";
        testUserId3 = "user3";
        
        // Create test game state
        List<String> playerIds = List.of(testUserId1, testUserId2, testUserId3);
        RoomConfig config = createTestRoomConfig();
        testGameState = new GameState(testRoomId, "game-123", playerIds, config);
    }
    
    @Test
    void shouldSaveAndLoadGameState() {
        // Save game state
        gameStateRedisService.saveGameState(testGameState);
        
        // Load game state
        GameState loadedState = gameStateRedisService.loadGameState(testRoomId);
        
        assertNotNull(loadedState);
        assertEquals(testGameState.getRoomId(), loadedState.getRoomId());
        assertEquals(testGameState.getGameId(), loadedState.getGameId());
        assertEquals(testGameState.getPlayers().size(), loadedState.getPlayers().size());
        assertEquals(testGameState.getPhase(), loadedState.getPhase());
    }
    
    @Test
    void shouldReturnNullForNonExistentGameState() {
        GameState loadedState = gameStateRedisService.loadGameState("nonexistent");
        assertNull(loadedState);
    }
    
    @Test
    void shouldCheckGameStateExistence() {
        assertFalse(gameStateRedisService.gameStateExists(testRoomId));
        
        gameStateRedisService.saveGameState(testGameState);
        
        assertTrue(gameStateRedisService.gameStateExists(testRoomId));
    }
    
    @Test
    void shouldDeleteGameState() {
        // Save game state
        gameStateRedisService.saveGameState(testGameState);
        assertTrue(gameStateRedisService.gameStateExists(testRoomId));
        
        // Delete game state
        gameStateRedisService.deleteGameState(testRoomId);
        assertFalse(gameStateRedisService.gameStateExists(testRoomId));
    }
    
    @Test
    void shouldRefreshGameStateTtl() {
        gameStateRedisService.saveGameState(testGameState);
        
        // This should not throw an exception
        assertDoesNotThrow(() -> gameStateRedisService.refreshGameStateTtl(testRoomId));
    }
    
    @Test
    void shouldSaveAndRetrievePlayerSession() {
        String sessionId = "session-123";
        
        gameStateRedisService.savePlayerSession(testUserId1, sessionId, testRoomId);
        
        // Check player session mapping
        String retrievedSessionId = gameStateRedisService.getPlayerSessionId(testUserId1);
        assertEquals(sessionId, retrievedSessionId);
        
        // Check session info
        GameStateRedisService.SessionInfo sessionInfo = gameStateRedisService.getSessionInfo(sessionId);
        assertNotNull(sessionInfo);
        assertEquals(testUserId1, sessionInfo.getUserId());
        assertEquals(testRoomId, sessionInfo.getRoomId());
        assertTrue(sessionInfo.getConnectionTime() > 0);
        assertTrue(sessionInfo.getLastHeartbeat() > 0);
    }
    
    @Test
    void shouldRemovePlayerSession() {
        String sessionId = "session-123";
        
        gameStateRedisService.savePlayerSession(testUserId1, sessionId, testRoomId);
        assertNotNull(gameStateRedisService.getPlayerSessionId(testUserId1));
        
        gameStateRedisService.removePlayerSession(testUserId1, sessionId);
        assertNull(gameStateRedisService.getPlayerSessionId(testUserId1));
        assertNull(gameStateRedisService.getSessionInfo(sessionId));
    }
    
    @Test
    void shouldManageRoomPlayers() {
        String sessionId1 = "session-1";
        String sessionId2 = "session-2";
        String sessionId3 = "session-3";
        
        // Add players to room
        gameStateRedisService.savePlayerSession(testUserId1, sessionId1, testRoomId);
        gameStateRedisService.savePlayerSession(testUserId2, sessionId2, testRoomId);
        gameStateRedisService.savePlayerSession(testUserId3, sessionId3, testRoomId);
        
        // Check room players
        Set<Object> roomPlayers = gameStateRedisService.getRoomPlayers(testRoomId);
        assertEquals(3, roomPlayers.size());
        assertTrue(roomPlayers.contains(testUserId1));
        assertTrue(roomPlayers.contains(testUserId2));
        assertTrue(roomPlayers.contains(testUserId3));
        
        // Remove one player
        gameStateRedisService.removePlayerSession(testUserId1, sessionId1);
        roomPlayers = gameStateRedisService.getRoomPlayers(testRoomId);
        assertEquals(2, roomPlayers.size());
        assertFalse(roomPlayers.contains(testUserId1));
    }
    
    @Test
    void shouldUpdateSessionHeartbeat() {
        String sessionId = "session-123";
        
        gameStateRedisService.savePlayerSession(testUserId1, sessionId, testRoomId);
        
        GameStateRedisService.SessionInfo originalInfo = gameStateRedisService.getSessionInfo(sessionId);
        long originalHeartbeat = originalInfo.getLastHeartbeat();
        
        // Wait a bit and update heartbeat
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        gameStateRedisService.updateSessionHeartbeat(sessionId);
        
        GameStateRedisService.SessionInfo updatedInfo = gameStateRedisService.getSessionInfo(sessionId);
        assertTrue(updatedInfo.getLastHeartbeat() > originalHeartbeat);
    }
    
    @Test
    void shouldValidateGameState() {
        // Valid game state
        assertTrue(gameStateRedisService.validateGameState(testGameState));
        
        // Invalid game state - null
        assertFalse(gameStateRedisService.validateGameState(null));
        
        // Invalid game state - missing room ID
        GameState invalidState = new GameState(null, "game-123", List.of(testUserId1), createTestRoomConfig());
        assertFalse(gameStateRedisService.validateGameState(invalidState));
    }
    
    @Test
    void shouldCreateGameStateSnapshot() {
        testGameState.dealInitialTiles();
        
        GameStateRedisService.GameStateSnapshot snapshot = gameStateRedisService.createSnapshot(testGameState);
        
        assertNotNull(snapshot);
        assertEquals(testGameState.getRoomId(), snapshot.getRoomId());
        assertEquals(testGameState.getGameId(), snapshot.getGameId());
        assertEquals(testGameState.getPhase(), snapshot.getPhase());
        assertEquals(testGameState.getCurrentPlayerIndex(), snapshot.getCurrentPlayerIndex());
        assertEquals(testGameState.getDealerUserId(), snapshot.getDealerUserId());
        assertEquals(testGameState.getRemainingTiles(), snapshot.getRemainingTiles());
        assertEquals(testGameState.getRoundNumber(), snapshot.getRoundNumber());
        assertEquals(testGameState.getTotalTurns(), snapshot.getTotalTurns());
        assertEquals(testGameState.getGameStartTime(), snapshot.getGameStartTime());
        assertEquals(testGameState.getRandomSeed(), snapshot.getRandomSeed());
        assertTrue(snapshot.getSnapshotTime() > 0);
    }
    
    @Test
    void shouldHandleRedisConnectionErrors() {
        // This test would require mocking Redis connection failures
        // For now, we'll just verify that methods don't throw exceptions
        assertDoesNotThrow(() -> {
            gameStateRedisService.saveGameState(testGameState);
            gameStateRedisService.loadGameState(testRoomId);
            gameStateRedisService.deleteGameState(testRoomId);
        });
    }
    
    @Test
    void shouldGetAllActiveGameRooms() {
        // Save multiple game states
        gameStateRedisService.saveGameState(testGameState);
        
        GameState anotherGameState = new GameState("654321", "game-456", 
                                                  List.of("user4", "user5", "user6"), 
                                                  createTestRoomConfig());
        gameStateRedisService.saveGameState(anotherGameState);
        
        Set<String> activeRooms = gameStateRedisService.getAllActiveGameRooms();
        assertTrue(activeRooms.size() >= 2);
    }
    
    @Test
    void shouldHandleComplexGameStateOperations() {
        // Start with initial game state
        gameStateRedisService.saveGameState(testGameState);
        
        // Deal tiles and update state
        testGameState.dealInitialTiles();
        gameStateRedisService.saveGameState(testGameState);
        
        // Load and verify
        GameState loadedState = gameStateRedisService.loadGameState(testRoomId);
        assertNotNull(loadedState);
        assertEquals(GameState.GamePhase.PLAYING, loadedState.getPhase());
        
        // Verify players have tiles
        for (PlayerState player : loadedState.getPlayers()) {
            assertFalse(player.getHandTiles().isEmpty());
            int expectedTileCount = player.isDealer() ? 14 : 13;
            assertEquals(expectedTileCount, player.getHandTiles().size());
        }
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