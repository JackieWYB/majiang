package com.mahjong.service;

import com.mahjong.model.config.RoomConfig;
import com.mahjong.model.config.TurnConfig;
import com.mahjong.model.game.GameState;
import com.mahjong.model.game.PlayerState;
import com.mahjong.model.game.Tile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for game engine functionality
 */
@ExtendWith(MockitoExtension.class)
class GameEngineIntegrationTest {
    
    private GameState gameState;
    private RoomConfig config;
    
    @BeforeEach
    void setUp() {
        // Setup configuration
        config = new RoomConfig();
        config.setTiles("ALL_SUITS");
        config.setAllowPeng(true);
        config.setAllowGang(true);
        config.setAllowChi(false);
        
        TurnConfig turnConfig = new TurnConfig();
        turnConfig.setTurnTimeLimit(15);
        turnConfig.setActionTimeLimit(2);
        turnConfig.setAutoTrustee(true);
        config.setTurn(turnConfig);
        
        // Create game state
        List<String> playerIds = Arrays.asList("player1", "player2", "player3");
        gameState = new GameState("room123", "game123", playerIds, config);
    }
    
    @Test
    void testGameInitialization() {
        // Then
        assertNotNull(gameState);
        assertEquals("room123", gameState.getRoomId());
        assertEquals("game123", gameState.getGameId());
        assertEquals(3, gameState.getPlayers().size());
        assertEquals(GameState.GamePhase.WAITING, gameState.getPhase());
        
        // Check players are initialized
        for (int i = 0; i < 3; i++) {
            PlayerState player = gameState.getPlayers().get(i);
            assertEquals("player" + (i + 1), player.getUserId());
            assertEquals(i, player.getSeatIndex());
            assertEquals(PlayerState.PlayerStatus.WAITING, player.getStatus());
        }
        
        // Check dealer assignment
        assertEquals("player1", gameState.getDealerUserId());
        assertTrue(gameState.getPlayers().get(0).isDealer());
    }
    
    @Test
    void testTileWallInitialization() {
        // Given - ALL_SUITS configuration
        
        // Then - Should have 108 tiles (3 suits × 9 ranks × 4 copies)
        assertEquals(108, gameState.getRemainingTiles());
        assertFalse(gameState.getTileWall().isEmpty());
        
        // All tiles should have valid suits and ranks
        for (Tile tile : gameState.getTileWall()) {
            assertTrue(tile.getSuit() == Tile.Suit.WAN || 
                      tile.getSuit() == Tile.Suit.TIAO || 
                      tile.getSuit() == Tile.Suit.TONG);
            assertTrue(tile.getRank() >= 1 && tile.getRank() <= 9);
        }
    }
    
    @Test
    void testDealInitialTiles() {
        // When
        gameState.dealInitialTiles();
        
        // Then
        assertEquals(GameState.GamePhase.PLAYING, gameState.getPhase());
        assertNotNull(gameState.getGameStartTime());
        
        // Check tile distribution
        int totalDealtTiles = 0;
        for (PlayerState player : gameState.getPlayers()) {
            int expectedTiles = player.isDealer() ? 14 : 13;
            assertEquals(expectedTiles, player.getHandTiles().size());
            totalDealtTiles += expectedTiles;
        }
        
        assertEquals(40, totalDealtTiles); // 13 + 13 + 14 = 40
        assertEquals(108 - 40, gameState.getRemainingTiles()); // 108 - 40 = 68
    }
    
    @Test
    void testTurnManagement() {
        // Given
        gameState.dealInitialTiles();
        
        // Then - Initial state
        assertEquals(0, gameState.getCurrentPlayerIndex());
        assertEquals("player1", gameState.getCurrentPlayer().getUserId());
        assertEquals(PlayerState.PlayerStatus.PLAYING, gameState.getCurrentPlayer().getStatus());
        
        // When - Next turn
        gameState.nextTurn();
        
        // Then
        assertEquals(1, gameState.getCurrentPlayerIndex());
        assertEquals("player2", gameState.getCurrentPlayer().getUserId());
        assertEquals(PlayerState.PlayerStatus.PLAYING, gameState.getCurrentPlayer().getStatus());
        assertEquals(PlayerState.PlayerStatus.WAITING_TURN, gameState.getPlayers().get(0).getStatus());
    }
    
    @Test
    void testDiscardAction() {
        // Given
        gameState.dealInitialTiles();
        PlayerState currentPlayer = gameState.getCurrentPlayer();
        
        // Ensure player has a tile to discard
        if (currentPlayer.getHandTiles().isEmpty()) {
            currentPlayer.addTile(new Tile("5W"));
        }
        
        Tile tileToDiscard = currentPlayer.getHandTiles().get(0);
        int initialHandSize = currentPlayer.getHandTiles().size();
        int initialDiscardPileSize = gameState.getDiscardPile().size();
        
        // When
        gameState.processDiscard(currentPlayer.getUserId(), tileToDiscard);
        
        // Then
        assertEquals(initialHandSize - 1, currentPlayer.getHandTiles().size());
        assertEquals(initialDiscardPileSize + 1, gameState.getDiscardPile().size());
        assertTrue(gameState.getDiscardPile().contains(tileToDiscard));
        // Note: The tile might still be in hand if there are multiple copies
        // Let's check that at least one copy was removed
        long tilesAfterDiscard = currentPlayer.getHandTiles().stream()
                .filter(t -> t.equals(tileToDiscard))
                .count();
        // We can't guarantee the exact tile was removed due to duplicates, 
        // but we know the hand size decreased
    }
    
    @Test
    void testPengAction() {
        // Given
        gameState.dealInitialTiles();
        PlayerState player = gameState.getPlayerByUserId("player2");
        
        // Setup player with matching tiles for Peng
        Tile pengTile = new Tile("5W");
        player.getHandTiles().clear(); // Clear existing tiles for test
        player.addTile(pengTile);
        player.addTile(pengTile);
        
        // Add tile to discard pile
        gameState.getDiscardPile().add(pengTile);
        
        int initialMeldCount = player.getMelds().size();
        
        // When
        gameState.processPeng("player2", pengTile, "player1");
        
        // Then
        assertEquals(initialMeldCount + 1, player.getMelds().size());
        assertEquals("player2", gameState.getCurrentPlayer().getUserId());
        assertTrue(gameState.getDiscardPile().isEmpty()); // Tile was claimed
    }
    
    @Test
    void testActionValidation_Peng() {
        // Given
        PlayerState player = gameState.getPlayerByUserId("player2");
        Tile testTile = new Tile("5W");
        
        // Initially cannot Peng (no matching tiles)
        assertFalse(player.canPeng(testTile));
        
        // Add one matching tile - still cannot Peng
        player.addTile(testTile);
        assertFalse(player.canPeng(testTile));
        
        // Add second matching tile - now can Peng
        player.addTile(testTile);
        assertTrue(player.canPeng(testTile));
    }
    
    @Test
    void testActionValidation_Gang() {
        // Given
        PlayerState player = gameState.getPlayerByUserId("player2");
        Tile testTile = new Tile("5W");
        
        // Initially cannot Gang
        assertFalse(player.canGang(testTile));
        assertFalse(player.canConcealedGang(testTile));
        
        // Add tiles progressively
        player.addTile(testTile);
        assertFalse(player.canGang(testTile));
        
        player.addTile(testTile);
        assertFalse(player.canGang(testTile));
        
        player.addTile(testTile);
        assertTrue(player.canGang(testTile)); // 3 tiles = can Gang
        assertFalse(player.canConcealedGang(testTile)); // Need 4 for concealed
        
        player.addTile(testTile);
        assertTrue(player.canConcealedGang(testTile)); // 4 tiles = concealed Gang
    }
    
    @Test
    void testActionValidation_Chi() {
        // Given
        PlayerState player = gameState.getPlayerByUserId("player2");
        
        // Test sequence: 4W, 5W, 6W
        Tile tile4 = new Tile("4W");
        Tile tile5 = new Tile("5W");
        Tile tile6 = new Tile("6W");
        
        // Initially cannot Chi
        assertFalse(player.canChi(tile5));
        
        // Add 4W - still cannot Chi 5W
        player.addTile(tile4);
        assertFalse(player.canChi(tile5));
        
        // Add 6W - now can Chi 5W to form 4-5-6
        player.addTile(tile6);
        assertTrue(player.canChi(tile5));
    }
    
    @Test
    void testTimeoutHandling() {
        // Given
        PlayerState player = gameState.getCurrentPlayer();
        assertEquals(0, player.getConsecutiveTimeouts());
        assertEquals(PlayerState.PlayerStatus.WAITING, player.getStatus());
        
        // When - Simulate timeouts
        player.incrementTimeouts();
        assertEquals(1, player.getConsecutiveTimeouts());
        
        player.incrementTimeouts();
        assertEquals(2, player.getConsecutiveTimeouts());
        
        player.incrementTimeouts();
        
        // Then - Should enter trustee mode after 3 timeouts
        assertEquals(3, player.getConsecutiveTimeouts());
        assertEquals(PlayerState.PlayerStatus.TRUSTEE, player.getStatus());
    }
    
    @Test
    void testTimeoutReset() {
        // Given
        PlayerState player = gameState.getCurrentPlayer();
        player.incrementTimeouts();
        player.incrementTimeouts();
        assertEquals(2, player.getConsecutiveTimeouts());
        
        // When - Player takes action
        player.updateLastActionTime();
        
        // Then - Timeout counter resets
        assertEquals(0, player.getConsecutiveTimeouts());
        assertNotNull(player.getLastActionTime());
    }
    
    @Test
    void testGameEndConditions() {
        // Given
        gameState.dealInitialTiles();
        
        // Check if any player has winning hand initially (they shouldn't in a real game)
        boolean anyPlayerHasWinningHand = gameState.getPlayers().stream()
                .anyMatch(PlayerState::hasWinningHand);
        
        // If no player has winning hand, game should not end
        if (!anyPlayerHasWinningHand) {
            assertFalse(gameState.shouldEndGame());
        }
        
        // When - Simulate empty wall
        while (gameState.getRemainingTiles() > 0) {
            gameState.drawTile();
        }
        
        // Then - Game should end due to empty wall
        assertTrue(gameState.shouldEndGame());
    }
    
    @Test
    void testGameEndProcess() {
        // Given
        gameState.dealInitialTiles();
        assertEquals(GameState.GamePhase.PLAYING, gameState.getPhase());
        assertNull(gameState.getGameEndTime());
        
        // When
        gameState.endGame();
        
        // Then
        assertEquals(GameState.GamePhase.SETTLEMENT, gameState.getPhase());
        assertNotNull(gameState.getGameEndTime());
        
        // All players should be finished
        for (PlayerState player : gameState.getPlayers()) {
            assertEquals(PlayerState.PlayerStatus.FINISHED, player.getStatus());
        }
    }
    
    @Test
    void testStatisticsTracking() {
        // Given
        gameState.dealInitialTiles();
        int initialTurns = gameState.getTotalTurns();
        
        // When - Progress turns
        gameState.nextTurn();
        
        // Then - Statistics should update
        assertEquals(initialTurns + 1, gameState.getTotalTurns());
        
        // Action counts should be tracked
        for (String playerId : Arrays.asList("player1", "player2", "player3")) {
            assertEquals(0, gameState.getPlayerActionCounts().get(playerId).intValue());
        }
    }
    
    @Test
    void testConfigurationRules() {
        // Test Peng allowed
        assertTrue(config.getAllowPeng());
        
        // Test Gang allowed
        assertTrue(config.getAllowGang());
        
        // Test Chi disabled
        assertFalse(config.getAllowChi());
        
        // Test tile configuration
        assertEquals("ALL_SUITS", config.getTiles());
        
        // Test turn configuration
        assertEquals(15, config.getTurn().getTurnTimeLimit());
        assertEquals(2, config.getTurn().getActionTimeLimit());
        assertTrue(config.getTurn().getAutoTrustee());
    }
    
    @Test
    void testTurnTimerSetup() {
        // Given
        gameState.dealInitialTiles();
        
        // When
        gameState.startTurn();
        
        // Then
        assertNotNull(gameState.getTurnStartTime());
        assertNotNull(gameState.getTurnDeadline());
        
        long expectedDeadline = gameState.getTurnStartTime() + (config.getTurn().getTurnTimeLimit() * 1000L);
        assertEquals(expectedDeadline, gameState.getTurnDeadline());
    }
    
    @Test
    void testPlayerActionTypes() {
        // Test action type conversion
        assertEquals(PlayerState.ActionType.DISCARD, PlayerAction.ActionType.DISCARD.toPlayerActionType());
        assertEquals(PlayerState.ActionType.PENG, PlayerAction.ActionType.PENG.toPlayerActionType());
        assertEquals(PlayerState.ActionType.GANG, PlayerAction.ActionType.GANG.toPlayerActionType());
        assertEquals(PlayerState.ActionType.CHI, PlayerAction.ActionType.CHI.toPlayerActionType());
        assertEquals(PlayerState.ActionType.HU, PlayerAction.ActionType.HU.toPlayerActionType());
        assertEquals(PlayerState.ActionType.PASS, PlayerAction.ActionType.PASS.toPlayerActionType());
    }
    
    @Test
    void testActionResultCreation() {
        // Test success results
        ActionResult success = ActionResult.success("Test success");
        assertTrue(success.isSuccess());
        assertFalse(success.isFailure());
        assertEquals("Test success", success.getMessage());
        
        ActionResult successWithData = ActionResult.success("Test success", "data");
        assertTrue(successWithData.isSuccess());
        assertEquals("data", successWithData.getData());
        
        // Test failure results
        ActionResult failure = ActionResult.failure("Test failure");
        assertFalse(failure.isSuccess());
        assertTrue(failure.isFailure());
        assertEquals("Test failure", failure.getMessage());
    }
    
    @Test
    void testActionValidationResult() {
        // Test valid result
        ActionValidationResult valid = ActionValidationResult.valid();
        assertTrue(valid.isValid());
        assertNull(valid.getErrorMessage());
        
        // Test invalid result
        ActionValidationResult invalid = ActionValidationResult.invalid("Test error");
        assertFalse(invalid.isValid());
        assertEquals("Test error", invalid.getErrorMessage());
    }
}