package com.mahjong.service;

import com.mahjong.model.game.GameState;
import com.mahjong.model.config.RoomConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for GameStateRecoveryService
 */
@ExtendWith(MockitoExtension.class)
class GameStateRecoveryServiceTest {
    
    @Mock
    private GameStateRedisService gameStateRedisService;
    
    @Mock
    private GameStateConsistencyService gameStateConsistencyService;
    
    @InjectMocks
    private GameStateRecoveryService gameStateRecoveryService;
    
    private GameState testGameState;
    private final String roomId = "123456";
    
    @BeforeEach
    void setUp() {
        // Create test game state
        List<String> playerIds = Arrays.asList("user1", "user2", "user3");
        RoomConfig config = new RoomConfig();
        testGameState = new GameState(roomId, "game1", playerIds, config);
        testGameState.setPhase(GameState.GamePhase.PLAYING);
    }
    
    @Test
    void testRecoverGameStateFromRedis() {
        // Given
        when(gameStateRedisService.loadGameState(roomId)).thenReturn(testGameState);
        when(gameStateRedisService.validateGameState(testGameState)).thenReturn(true);
        
        // When
        GameState result = gameStateRecoveryService.recoverGameState(roomId);
        
        // Then
        assertNotNull(result);
        assertEquals(testGameState, result);
        assertEquals(roomId, result.getRoomId());
        
        verify(gameStateRedisService).loadGameState(roomId);
        verify(gameStateRedisService).validateGameState(testGameState);
        verify(gameStateRedisService, never()).saveGameState(any()); // No need to save back to Redis
    }
    
    @Test
    void testRecoverGameStateFromRedisInvalid() {
        // Given - Redis returns invalid state
        when(gameStateRedisService.loadGameState(roomId)).thenReturn(testGameState);
        when(gameStateRedisService.validateGameState(testGameState)).thenReturn(false);
        
        // When
        GameState result = gameStateRecoveryService.recoverGameState(roomId);
        
        // Then - should return null since database recovery is not implemented
        assertNull(result);
        
        verify(gameStateRedisService).loadGameState(roomId);
        verify(gameStateRedisService).validateGameState(testGameState);
    }
    
    @Test
    void testRecoverGameStateRedisReturnsNull() {
        // Given - Redis returns null
        when(gameStateRedisService.loadGameState(roomId)).thenReturn(null);
        
        // When
        GameState result = gameStateRecoveryService.recoverGameState(roomId);
        
        // Then - should return null since database recovery is not implemented
        assertNull(result);
        
        verify(gameStateRedisService).loadGameState(roomId);
        verify(gameStateRedisService, never()).validateGameState(any());
    }
    
    @Test
    void testRecoverGameStateWithException() {
        // Given - Redis throws exception
        when(gameStateRedisService.loadGameState(roomId))
            .thenThrow(new RuntimeException("Redis connection error"));
        
        // When
        GameState result = gameStateRecoveryService.recoverGameState(roomId);
        
        // Then - should handle gracefully and return null
        assertNull(result);
        
        verify(gameStateRedisService).loadGameState(roomId);
    }
    
    @Test
    void testRecoverGameStateWithNullRoomId() {
        // When
        GameState result = gameStateRecoveryService.recoverGameState(null);
        
        // Then - should handle gracefully
        assertNull(result);
        
        verify(gameStateRedisService, never()).loadGameState(any());
    }
    
    @Test
    void testRecoverGameStateWithEmptyRoomId() {
        // When
        GameState result = gameStateRecoveryService.recoverGameState("");
        
        // Then - should attempt recovery but likely return null
        GameState recovered = gameStateRecoveryService.recoverGameState("");
        
        verify(gameStateRedisService).loadGameState("");
    }
}