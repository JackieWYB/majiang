package com.mahjong.service;

import com.mahjong.model.game.GameState;
import com.mahjong.model.game.PlayerState;
import com.mahjong.model.game.Tile;
import com.mahjong.model.config.RoomConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for TrusteeModeService
 */
@ExtendWith(MockitoExtension.class)
class TrusteeModeServiceTest {
    
    @Mock
    private GameService gameService;
    
    @Mock
    private GameMessageBroadcastService broadcastService;
    
    @InjectMocks
    private TrusteeModeService trusteeModeService;
    
    private GameState testGameState;
    private final String roomId = "123456";
    private final String userId = "user1";
    
    @BeforeEach
    void setUp() {
        // Create test game state
        List<String> playerIds = Arrays.asList("user1", "user2", "user3");
        RoomConfig config = new RoomConfig();
        testGameState = new GameState(roomId, "game1", playerIds, config);
        testGameState.setPhase(GameState.GamePhase.PLAYING);
        
        // Set up player in trustee mode
        PlayerState player = testGameState.getPlayerByUserId(userId);
        player.setStatus(PlayerState.PlayerStatus.TRUSTEE);
        
        // Add some tiles to player's hand
        player.addTile(new Tile(Tile.Suit.WAN, 1));
        player.addTile(new Tile(Tile.Suit.WAN, 2));
        player.addTile(new Tile(Tile.Suit.WAN, 3));
        player.addTile(new Tile(Tile.Suit.WAN, 4));
        
        // Set available actions
        player.setAvailableActions(Arrays.asList(PlayerState.ActionType.DISCARD));
    }
    
    @Test
    void testExecuteAutomaticActionForNonTrusteePlayer() {
        // Given - player not in trustee mode
        PlayerState player = testGameState.getPlayerByUserId(userId);
        player.setStatus(PlayerState.PlayerStatus.PLAYING);
        
        when(gameService.getGameState(roomId)).thenReturn(testGameState);
        
        // When
        trusteeModeService.executeAutomaticAction(roomId, userId);
        
        // Then - no action should be executed
        verify(gameService, never()).handlePlayerAction(any(), any(), any());
    }
    
    @Test
    void testExecuteAutomaticActionNotPlayerTurn() {
        // Given - not player's turn
        testGameState.nextTurn(); // Move to next player
        
        when(gameService.getGameState(roomId)).thenReturn(testGameState);
        
        // When
        trusteeModeService.executeAutomaticAction(roomId, userId);
        
        // Then - no action should be executed
        verify(gameService, never()).handlePlayerAction(any(), any(), any());
    }
    
    @Test
    void testExecuteAutomaticDiscard() {
        // Given
        when(gameService.getGameState(roomId)).thenReturn(testGameState);
        when(gameService.handlePlayerAction(eq(roomId), eq(userId), any()))
            .thenReturn(ActionResult.success("Discard successful"));
        
        // When
        trusteeModeService.executeAutomaticAction(roomId, userId);
        
        // Then
        verify(gameService).handlePlayerAction(eq(roomId), eq(userId), any());
        verify(broadcastService).broadcastRoomEvent(eq(roomId), eq("trusteeAction"), any());
    }
    
    @Test
    void testExecuteAutomaticHu() {
        // Given - player can win
        PlayerState player = testGameState.getPlayerByUserId(userId);
        player.setAvailableActions(Arrays.asList(PlayerState.ActionType.HU));
        
        when(gameService.getGameState(roomId)).thenReturn(testGameState);
        when(gameService.handlePlayerAction(eq(roomId), eq(userId), any()))
            .thenReturn(ActionResult.success("Hu successful"));
        
        // When
        trusteeModeService.executeAutomaticAction(roomId, userId);
        
        // Then - should execute Hu action
        verify(gameService).handlePlayerAction(eq(roomId), eq(userId), any());
        verify(broadcastService).broadcastRoomEvent(eq(roomId), eq("trusteeAction"), 
            argThat(map -> "hu".equals(((java.util.Map<?, ?>) map).get("action"))));
    }
    
    @Test
    void testExecuteAutomaticGang() {
        // Given - player can form Gang
        PlayerState player = testGameState.getPlayerByUserId(userId);
        player.setAvailableActions(Arrays.asList(PlayerState.ActionType.GANG, PlayerState.ActionType.DISCARD));
        
        // Add tiles for concealed Gang
        player.addTile(new Tile(Tile.Suit.WAN, 1));
        player.addTile(new Tile(Tile.Suit.WAN, 1));
        player.addTile(new Tile(Tile.Suit.WAN, 1));
        
        when(gameService.getGameState(roomId)).thenReturn(testGameState);
        when(gameService.handlePlayerAction(eq(roomId), eq(userId), any()))
            .thenReturn(ActionResult.success("Gang successful"));
        
        // When
        trusteeModeService.executeAutomaticAction(roomId, userId);
        
        // Then - should have a chance to execute Gang (70% probability)
        verify(gameService, atLeastOnce()).handlePlayerAction(eq(roomId), eq(userId), any());
        verify(broadcastService, atLeastOnce()).broadcastRoomEvent(eq(roomId), eq("trusteeAction"), any());
    }
    
    @Test
    void testExecuteAutomaticPass() {
        // Given - player can only pass
        PlayerState player = testGameState.getPlayerByUserId(userId);
        player.setAvailableActions(Arrays.asList(PlayerState.ActionType.PASS));
        
        when(gameService.getGameState(roomId)).thenReturn(testGameState);
        when(gameService.handlePlayerAction(eq(roomId), eq(userId), any()))
            .thenReturn(ActionResult.success("Pass successful"));
        
        // When
        trusteeModeService.executeAutomaticAction(roomId, userId);
        
        // Then
        verify(gameService).handlePlayerAction(eq(roomId), eq(userId), any());
        verify(broadcastService).broadcastRoomEvent(eq(roomId), eq("trusteeAction"), 
            argThat(map -> "pass".equals(((java.util.Map<?, ?>) map).get("action"))));
    }
    
    @Test
    void testExecuteAutomaticActionWithGameServiceError() {
        // Given
        when(gameService.getGameState(roomId)).thenReturn(testGameState);
        when(gameService.handlePlayerAction(eq(roomId), eq(userId), any()))
            .thenThrow(new RuntimeException("Game service error"));
        
        // When - should not throw exception
        trusteeModeService.executeAutomaticAction(roomId, userId);
        
        // Then - error should be logged but not propagated
        verify(gameService).handlePlayerAction(eq(roomId), eq(userId), any());
    }
    
    @Test
    void testExecuteAutomaticActionWithNoGameState() {
        // Given
        when(gameService.getGameState(roomId)).thenReturn(null);
        
        // When
        trusteeModeService.executeAutomaticAction(roomId, userId);
        
        // Then - should handle gracefully
        verify(gameService, never()).handlePlayerAction(any(), any(), any());
    }
    
    @Test
    void testExecuteAutomaticActionWithNoAvailableActions() {
        // Given - no available actions
        PlayerState player = testGameState.getPlayerByUserId(userId);
        player.setAvailableActions(Arrays.asList());
        
        when(gameService.getGameState(roomId)).thenReturn(testGameState);
        
        // When
        trusteeModeService.executeAutomaticAction(roomId, userId);
        
        // Then - should handle gracefully
        verify(gameService, never()).handlePlayerAction(any(), any(), any());
    }
}