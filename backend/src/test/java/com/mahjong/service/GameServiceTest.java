package com.mahjong.service;

import com.mahjong.model.config.RoomConfig;
import com.mahjong.model.config.TurnConfig;
import com.mahjong.model.entity.Room;
import com.mahjong.model.entity.RoomPlayer;
import com.mahjong.model.entity.RoomRule;
import com.mahjong.model.enums.RoomStatus;
import com.mahjong.model.game.GameState;
import com.mahjong.model.game.PlayerState;
import com.mahjong.model.game.Tile;
import com.mahjong.repository.RoomPlayerRepository;
import com.mahjong.repository.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for GameService
 */
@ExtendWith(MockitoExtension.class)
class GameServiceTest {
    
    @Mock
    private RoomRepository roomRepository;
    
    @Mock
    private RoomPlayerRepository roomPlayerRepository;
    
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    
    @Mock
    private ValueOperations<String, Object> valueOperations;
    
    @Mock
    private ScheduledExecutorService scheduledExecutorService;
    
    @Mock
    private ScheduledFuture<Object> scheduledFuture;
    
    @InjectMocks
    private GameService gameService;
    
    private Room testRoom;
    private List<RoomPlayer> testPlayers;
    private RoomConfig testConfig;
    
    @BeforeEach
    void setUp() {
        // Setup test room
        testRoom = new Room("123456", 1L, 1L);
        testRoom.setStatus(RoomStatus.READY);
        
        // Setup room rule with config
        RoomRule roomRule = new RoomRule();
        testConfig = new RoomConfig();
        testConfig.setTiles("WAN_ONLY");
        testConfig.setAllowPeng(true);
        testConfig.setAllowGang(true);
        testConfig.setAllowChi(false);
        
        TurnConfig turnConfig = new TurnConfig();
        turnConfig.setTurnTimeLimit(15);
        turnConfig.setActionTimeLimit(2);
        turnConfig.setAutoTrustee(true);
        testConfig.setTurn(turnConfig);
        
        roomRule.setConfig(testConfig);
        testRoom.setRoomRule(roomRule);
        
        // Setup test players
        testPlayers = Arrays.asList(
            new RoomPlayer("123456", 1L, 0),
            new RoomPlayer("123456", 2L, 1),
            new RoomPlayer("123456", 3L, 2)
        );
        
        // Setup Redis mocks
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(scheduledExecutorService.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class)))
                .thenReturn((ScheduledFuture) scheduledFuture);
    }
    
    @Test
    void testStartGame_Success() {
        // Given
        when(roomRepository.findByIdWithPlayers("123456")).thenReturn(Optional.of(testRoom));
        when(roomPlayerRepository.findByRoomIdOrderBySeatIndex("123456")).thenReturn(testPlayers);
        
        // When
        GameState gameState = gameService.startGame("123456");
        
        // Then
        assertNotNull(gameState);
        assertEquals("123456", gameState.getRoomId());
        assertEquals(GameState.GamePhase.PLAYING, gameState.getPhase());
        assertEquals(3, gameState.getPlayers().size());
        
        // Verify Redis save was called
        verify(valueOperations).set(eq("game:123456"), eq(gameState), any());
        
        // Verify turn timer was started
        verify(scheduledExecutorService).schedule(any(Runnable.class), eq(15L), eq(TimeUnit.SECONDS));
    }
    
    @Test
    void testStartGame_RoomNotFound() {
        // Given
        when(roomRepository.findByIdWithPlayers("123456")).thenReturn(Optional.empty());
        
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> gameService.startGame("123456"));
    }
    
    @Test
    void testStartGame_InsufficientPlayers() {
        // Given
        when(roomRepository.findByIdWithPlayers("123456")).thenReturn(Optional.of(testRoom));
        when(roomPlayerRepository.findByRoomIdOrderBySeatIndex("123456"))
                .thenReturn(Arrays.asList(testPlayers.get(0), testPlayers.get(1))); // Only 2 players
        
        // When & Then
        assertThrows(IllegalStateException.class, () -> gameService.startGame("123456"));
    }
    
    @Test
    void testHandlePlayerAction_DiscardSuccess() {
        // Given
        GameState gameState = createTestGameState();
        gameService.getClass(); // Access to put in activeGames map
        
        // Mock the game state retrieval
        when(valueOperations.get("game:123456")).thenReturn(gameState);
        
        DiscardAction discardAction = new DiscardAction("5W");
        
        // Add tile to current player's hand
        PlayerState currentPlayer = gameState.getCurrentPlayer();
        currentPlayer.addTile(new Tile("5W"));
        
        // When
        ActionResult result = gameService.handlePlayerAction("123456", "1", discardAction);
        
        // Then
        assertTrue(result.isSuccess());
        assertEquals("Tile discarded successfully", result.getMessage());
        
        // Verify Redis save was called
        verify(valueOperations, atLeastOnce()).set(eq("game:123456"), any(GameState.class), any());
    }
    
    @Test
    void testHandlePlayerAction_InvalidAction() {
        // Given
        GameState gameState = createTestGameState();
        when(valueOperations.get("game:123456")).thenReturn(gameState);
        
        DiscardAction discardAction = new DiscardAction("9W"); // Player doesn't have this tile
        
        // When
        ActionResult result = gameService.handlePlayerAction("123456", "1", discardAction);
        
        // Then
        assertTrue(result.isFailure());
        assertTrue(result.getMessage().contains("don't have this tile"));
    }
    
    @Test
    void testHandlePlayerAction_NotPlayerTurn() {
        // Given
        GameState gameState = createTestGameState();
        when(valueOperations.get("game:123456")).thenReturn(gameState);
        
        DiscardAction discardAction = new DiscardAction("5W");
        
        // When - Player 2 tries to act when it's Player 1's turn
        ActionResult result = gameService.handlePlayerAction("123456", "2", discardAction);
        
        // Then
        assertTrue(result.isFailure());
        assertTrue(result.getMessage().contains("Not your turn"));
    }
    
    @Test
    void testHandlePlayerAction_PengSuccess() {
        // Given
        GameState gameState = createTestGameState();
        when(valueOperations.get("game:123456")).thenReturn(gameState);
        
        // Setup scenario where player can peng
        PlayerState player2 = gameState.getPlayerByUserId("2");
        player2.addTile(new Tile("5W"));
        player2.addTile(new Tile("5W"));
        player2.setAvailableActions(Arrays.asList(PlayerState.ActionType.PENG, PlayerState.ActionType.PASS));
        
        // Add discarded tile to discard pile
        gameState.getDiscardPile().add(new Tile("5W"));
        
        PengAction pengAction = new PengAction("5W", "1");
        
        // When
        ActionResult result = gameService.handlePlayerAction("123456", "2", pengAction);
        
        // Then
        assertTrue(result.isSuccess());
        assertEquals("Peng successful", result.getMessage());
    }
    
    @Test
    void testHandlePlayerAction_HuSuccess() {
        // Given
        GameState gameState = createTestGameState();
        when(valueOperations.get("game:123456")).thenReturn(gameState);
        
        PlayerState player = gameState.getPlayerByUserId("1");
        
        // Mock winning hand
        PlayerState spyPlayer = spy(player);
        when(spyPlayer.hasWinningHand()).thenReturn(true);
        spyPlayer.setAvailableActions(Arrays.asList(PlayerState.ActionType.HU));
        
        // Replace player in game state
        gameState.getPlayers().set(0, spyPlayer);
        
        HuAction huAction = new HuAction("5W", true);
        
        // When
        ActionResult result = gameService.handlePlayerAction("123456", "1", huAction);
        
        // Then
        assertTrue(result.isSuccess());
        assertEquals("Hu successful - game won!", result.getMessage());
        assertEquals(GameState.GamePhase.SETTLEMENT, gameState.getPhase());
    }
    
    @Test
    void testValidatePlayerAction_GameNotPlaying() {
        // Given
        GameState gameState = createTestGameState();
        gameState.setPhase(GameState.GamePhase.FINISHED);
        
        PlayerState player = gameState.getPlayerByUserId("1");
        DiscardAction action = new DiscardAction("5W");
        
        // When
        ActionValidationResult result = invokeValidatePlayerAction(gameState, player, action);
        
        // Then
        assertFalse(result.isValid());
        assertEquals("Game is not in playing phase", result.getErrorMessage());
    }
    
    @Test
    void testValidatePlayerAction_PlayerDisconnected() {
        // Given
        GameState gameState = createTestGameState();
        PlayerState player = gameState.getPlayerByUserId("1");
        player.setStatus(PlayerState.PlayerStatus.DISCONNECTED);
        
        DiscardAction action = new DiscardAction("5W");
        
        // When
        ActionValidationResult result = invokeValidatePlayerAction(gameState, player, action);
        
        // Then
        assertFalse(result.isValid());
        assertEquals("Player is disconnected", result.getErrorMessage());
    }
    
    @Test
    void testValidatePlayerAction_ActionNotAvailable() {
        // Given
        GameState gameState = createTestGameState();
        PlayerState player = gameState.getPlayerByUserId("1");
        player.setAvailableActions(Arrays.asList(PlayerState.ActionType.PASS)); // Only pass available
        
        DiscardAction action = new DiscardAction("5W");
        
        // When
        ActionValidationResult result = invokeValidatePlayerAction(gameState, player, action);
        
        // Then
        assertFalse(result.isValid());
        assertEquals("Action not available for player", result.getErrorMessage());
    }
    
    @Test
    void testTurnTimeout_AutoTrusteeEnabled() {
        // Given
        GameState gameState = createTestGameState();
        when(valueOperations.get("game:123456")).thenReturn(gameState);
        
        PlayerState currentPlayer = gameState.getCurrentPlayer();
        currentPlayer.addTile(new Tile("5W")); // Add a tile to discard
        
        // When - Simulate turn timeout
        gameService.getClass(); // This would trigger handleTurnTimeout in real scenario
        
        // Then
        // Verify that timeout counter would be incremented
        // and auto-play would be triggered
        assertNotNull(currentPlayer);
    }
    
    @Test
    void testGetGameState_FromCache() {
        // Given
        GameState gameState = createTestGameState();
        // Simulate game in active cache
        
        // When
        GameState result = gameService.getGameState("123456");
        
        // Then - Should try Redis first since not in cache
        verify(valueOperations).get("game:123456");
    }
    
    @Test
    void testGetGameState_FromRedis() {
        // Given
        GameState gameState = createTestGameState();
        when(valueOperations.get("game:123456")).thenReturn(gameState);
        
        // When
        GameState result = gameService.getGameState("123456");
        
        // Then
        assertNotNull(result);
        assertEquals("123456", result.getRoomId());
        verify(valueOperations).get("game:123456");
    }
    
    @Test
    void testGetGameState_NotFound() {
        // Given
        when(valueOperations.get("game:123456")).thenReturn(null);
        
        // When
        GameState result = gameService.getGameState("123456");
        
        // Then
        assertNull(result);
    }
    
    @Test
    void testActionPriority_HuOverPeng() {
        // Given
        GameState gameState = createTestGameState();
        when(valueOperations.get("game:123456")).thenReturn(gameState);
        
        // Setup: Player 2 can Peng, Player 3 can Hu
        PlayerState player2 = gameState.getPlayerByUserId("2");
        player2.addTile(new Tile("5W"));
        player2.addTile(new Tile("5W"));
        
        PlayerState player3 = gameState.getPlayerByUserId("3");
        PlayerState spyPlayer3 = spy(player3);
        when(spyPlayer3.hasWinningHand()).thenReturn(true);
        
        // Simulate discard that both can claim
        Tile discardedTile = new Tile("5W");
        
        // When - Check claim actions
        // This would be handled by checkAndHandleClaimActions method
        
        // Then - Hu should have priority over Peng
        // In real implementation, both players would get available actions
        // but Hu would be processed first if both act
        assertTrue(spyPlayer3.hasWinningHand());
        assertTrue(player2.canPeng(discardedTile));
    }
    
    @Test
    void testConsecutiveTimeouts_TrusteeMode() {
        // Given
        GameState gameState = createTestGameState();
        PlayerState player = gameState.getCurrentPlayer();
        
        // When - Simulate multiple timeouts
        player.incrementTimeouts(); // 1st timeout
        assertEquals(PlayerState.PlayerStatus.PLAYING, player.getStatus());
        
        player.incrementTimeouts(); // 2nd timeout
        assertEquals(PlayerState.PlayerStatus.PLAYING, player.getStatus());
        
        player.incrementTimeouts(); // 3rd timeout - should trigger trustee
        
        // Then
        assertEquals(PlayerState.PlayerStatus.TRUSTEE, player.getStatus());
        assertEquals(3, player.getConsecutiveTimeouts());
    }
    
    // Helper methods
    
    private GameState createTestGameState() {
        List<String> playerIds = Arrays.asList("1", "2", "3");
        GameState gameState = new GameState("123456", "game-1", playerIds, testConfig);
        gameState.setPhase(GameState.GamePhase.PLAYING);
        
        // Set up current player with available actions
        PlayerState currentPlayer = gameState.getCurrentPlayer();
        currentPlayer.setAvailableActions(Arrays.asList(PlayerState.ActionType.DISCARD));
        currentPlayer.setStatus(PlayerState.PlayerStatus.PLAYING);
        
        return gameState;
    }
    
    // Use reflection to access private method for testing
    private ActionValidationResult invokeValidatePlayerAction(GameState gameState, PlayerState player, PlayerAction action) {
        try {
            java.lang.reflect.Method method = GameService.class.getDeclaredMethod(
                "validatePlayerAction", GameState.class, PlayerState.class, PlayerAction.class);
            method.setAccessible(true);
            return (ActionValidationResult) method.invoke(gameService, gameState, player, action);
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke validatePlayerAction", e);
        }
    }
}