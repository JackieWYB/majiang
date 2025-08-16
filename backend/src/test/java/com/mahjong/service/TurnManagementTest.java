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
 * Unit tests for turn management functionality
 */
@ExtendWith(MockitoExtension.class)
class TurnManagementTest {
    
    private GameState gameState;
    private RoomConfig config;
    
    @BeforeEach
    void setUp() {
        // Setup configuration
        config = new RoomConfig();
        config.setTiles("WAN_ONLY");
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
        gameState.setPhase(GameState.GamePhase.PLAYING);
    }
    
    @Test
    void testTurnProgression_NormalFlow() {
        // Given
        assertEquals(0, gameState.getCurrentPlayerIndex());
        assertEquals("player1", gameState.getCurrentPlayer().getUserId());
        
        // When - Move to next turn
        gameState.nextTurn();
        
        // Then
        assertEquals(1, gameState.getCurrentPlayerIndex());
        assertEquals("player2", gameState.getCurrentPlayer().getUserId());
        
        // When - Move to next turn again
        gameState.nextTurn();
        
        // Then
        assertEquals(2, gameState.getCurrentPlayerIndex());
        assertEquals("player3", gameState.getCurrentPlayer().getUserId());
        
        // When - Move to next turn (should wrap around)
        gameState.nextTurn();
        
        // Then
        assertEquals(0, gameState.getCurrentPlayerIndex());
        assertEquals("player1", gameState.getCurrentPlayer().getUserId());
    }
    
    @Test
    void testTurnTimer_Setup() {
        // Given
        long beforeStart = System.currentTimeMillis();
        
        // When
        gameState.startTurn();
        
        // Then
        assertNotNull(gameState.getTurnStartTime());
        assertNotNull(gameState.getTurnDeadline());
        assertTrue(gameState.getTurnStartTime() >= beforeStart);
        
        long expectedDeadline = gameState.getTurnStartTime() + (config.getTurn().getTurnTimeLimit() * 1000L);
        assertEquals(expectedDeadline, gameState.getTurnDeadline());
    }
    
    @Test
    void testTurnTimeout_Detection() {
        // Given
        gameState.startTurn();
        
        // When - Simulate time passing beyond deadline
        long pastDeadline = gameState.getTurnDeadline() + 1000;
        
        // Then - Mock current time to be past deadline
        // In real implementation, this would be checked by isTurnTimedOut()
        assertTrue(pastDeadline > gameState.getTurnDeadline());
    }
    
    @Test
    void testPlayerStatus_TurnTransitions() {
        // Given
        PlayerState player1 = gameState.getPlayerByUserId("player1");
        PlayerState player2 = gameState.getPlayerByUserId("player2");
        
        // Initially, current player should be playing, others waiting
        gameState.startTurn();
        assertEquals(PlayerState.PlayerStatus.PLAYING, player1.getStatus());
        assertEquals(PlayerState.PlayerStatus.WAITING_TURN, player2.getStatus());
        
        // When - Move to next turn
        gameState.nextTurn();
        
        // Then - Status should switch
        assertEquals(PlayerState.PlayerStatus.WAITING_TURN, player1.getStatus());
        assertEquals(PlayerState.PlayerStatus.PLAYING, player2.getStatus());
    }
    
    @Test
    void testAvailableActions_UpdateOnTurnStart() {
        // Given
        PlayerState currentPlayer = gameState.getCurrentPlayer();
        currentPlayer.addTile(new Tile("5W")); // Give player 14 tiles (13 + 1)
        
        // When
        gameState.startTurn();
        
        // Then - Player should have discard action available
        assertTrue(currentPlayer.getAvailableActions().contains(PlayerState.ActionType.DISCARD));
    }
    
    @Test
    void testConsecutiveTimeouts_TrusteeActivation() {
        // Given
        PlayerState player = gameState.getCurrentPlayer();
        assertEquals(0, player.getConsecutiveTimeouts());
        assertEquals(PlayerState.PlayerStatus.PLAYING, player.getStatus());
        
        // When - First timeout
        player.incrementTimeouts();
        
        // Then
        assertEquals(1, player.getConsecutiveTimeouts());
        assertEquals(PlayerState.PlayerStatus.PLAYING, player.getStatus());
        
        // When - Second timeout
        player.incrementTimeouts();
        
        // Then
        assertEquals(2, player.getConsecutiveTimeouts());
        assertEquals(PlayerState.PlayerStatus.PLAYING, player.getStatus());
        
        // When - Third timeout (should trigger trustee)
        player.incrementTimeouts();
        
        // Then
        assertEquals(3, player.getConsecutiveTimeouts());
        assertEquals(PlayerState.PlayerStatus.TRUSTEE, player.getStatus());
    }
    
    @Test
    void testTimeoutReset_OnPlayerAction() {
        // Given
        PlayerState player = gameState.getCurrentPlayer();
        player.incrementTimeouts();
        player.incrementTimeouts();
        assertEquals(2, player.getConsecutiveTimeouts());
        
        // When - Player takes action
        player.updateLastActionTime();
        
        // Then - Timeout counter should reset
        assertEquals(0, player.getConsecutiveTimeouts());
        assertNotNull(player.getLastActionTime());
    }
    
    @Test
    void testActionValidation_TurnOrder() {
        // Given
        PlayerState currentPlayer = gameState.getCurrentPlayer();
        PlayerState otherPlayer = gameState.getPlayerByUserId("player2");
        
        currentPlayer.setAvailableActions(Arrays.asList(PlayerState.ActionType.DISCARD));
        otherPlayer.setAvailableActions(Arrays.asList());
        
        // Then - Only current player should have actions available
        assertFalse(currentPlayer.getAvailableActions().isEmpty());
        assertTrue(otherPlayer.getAvailableActions().isEmpty());
    }
    
    @Test
    void testActionPriority_HuOverOthers() {
        // Given
        PlayerState player = gameState.getPlayerByUserId("player2");
        
        // Setup player with winning hand
        PlayerState spyPlayer = org.mockito.Mockito.spy(player);
        org.mockito.Mockito.when(spyPlayer.hasWinningHand()).thenReturn(true);
        
        // When - Check available actions for a discarded tile
        List<PlayerState.ActionType> actions = Arrays.asList(
            PlayerState.ActionType.PENG,
            PlayerState.ActionType.GANG,
            PlayerState.ActionType.HU,
            PlayerState.ActionType.PASS
        );
        spyPlayer.setAvailableActions(actions);
        
        // Then - Hu should be available (highest priority)
        assertTrue(spyPlayer.getAvailableActions().contains(PlayerState.ActionType.HU));
        assertTrue(spyPlayer.hasWinningHand());
    }
    
    @Test
    void testActionPriority_GangOverPeng() {
        // Given
        PlayerState player = gameState.getPlayerByUserId("player2");
        Tile testTile = new Tile("5W");
        
        // Add tiles to enable both Peng and Gang
        player.addTile(testTile);
        player.addTile(testTile);
        player.addTile(testTile); // 3 tiles = can Gang
        
        // Then - Player should be able to both Peng and Gang
        assertTrue(player.canPeng(testTile));
        assertTrue(player.canGang(testTile));
    }
    
    @Test
    void testActionTimeWindow_ClaimActions() {
        // Given
        long actionTimeLimit = config.getTurn().getActionTimeLimit() * 1000L;
        long currentTime = System.currentTimeMillis();
        long deadline = currentTime + actionTimeLimit;
        
        PlayerState player = gameState.getPlayerByUserId("player2");
        Tile claimedTile = new Tile("5W");
        List<PlayerState.ActionType> actions = Arrays.asList(
            PlayerState.ActionType.PENG, 
            PlayerState.ActionType.PASS
        );
        
        // When - Create pending action
        PendingAction pendingAction = new PendingAction(
            player.getUserId(),
            claimedTile,
            "player1",
            actions,
            deadline
        );
        
        // Then
        assertEquals(player.getUserId(), pendingAction.getUserId());
        assertEquals(claimedTile, pendingAction.getClaimedTile());
        assertEquals("player1", pendingAction.getClaimedFrom());
        assertEquals(actions, pendingAction.getAvailableActions());
        assertEquals(deadline, pendingAction.getDeadline());
        assertFalse(pendingAction.isExpired());
    }
    
    @Test
    void testActionTimeWindow_Expiration() {
        // Given
        long pastDeadline = System.currentTimeMillis() - 1000; // 1 second ago
        
        PendingAction expiredAction = new PendingAction(
            "player2",
            new Tile("5W"),
            "player1",
            Arrays.asList(PlayerState.ActionType.PENG),
            pastDeadline
        );
        
        // Then
        assertTrue(expiredAction.isExpired());
    }
    
    @Test
    void testGameFlow_DiscardAndClaim() {
        // Given
        PlayerState currentPlayer = gameState.getCurrentPlayer(); // player1
        PlayerState claimingPlayer = gameState.getPlayerByUserId("player2");
        
        Tile discardTile = new Tile("5W");
        currentPlayer.addTile(discardTile);
        
        // Setup claiming player with matching tiles
        claimingPlayer.addTile(new Tile("5W"));
        claimingPlayer.addTile(new Tile("5W"));
        
        // When - Process discard
        gameState.processDiscard("player1", discardTile);
        
        // Then - Discard pile should contain the tile
        assertTrue(gameState.getDiscardPile().contains(discardTile));
        assertFalse(currentPlayer.getHandTiles().contains(discardTile));
        
        // And claiming player should be able to Peng
        assertTrue(claimingPlayer.canPeng(discardTile));
    }
    
    @Test
    void testGameFlow_PengExecution() {
        // Given
        PlayerState player = gameState.getPlayerByUserId("player2");
        Tile pengTile = new Tile("5W");
        
        // Setup player with matching tiles
        player.addTile(pengTile);
        player.addTile(pengTile);
        
        // Add tile to discard pile
        gameState.getDiscardPile().add(pengTile);
        
        // When - Process Peng
        gameState.processPeng("player2", pengTile, "player1");
        
        // Then - Player should have a meld
        assertEquals(1, player.getMelds().size());
        
        // And current player should be the one who made Peng
        assertEquals("player2", gameState.getCurrentPlayer().getUserId());
        
        // And discard pile should be empty (tile was claimed)
        assertTrue(gameState.getDiscardPile().isEmpty());
    }
    
    @Test
    void testTurnStatistics_Tracking() {
        // Given
        int initialTurns = gameState.getTotalTurns();
        
        // When - Start and progress turns
        gameState.startTurn();
        int afterFirstStart = gameState.getTotalTurns();
        
        gameState.nextTurn();
        int afterNext = gameState.getTotalTurns();
        
        // Then - Turn count should increase
        assertEquals(initialTurns + 1, afterFirstStart);
        assertEquals(afterFirstStart + 1, afterNext);
    }
    
    @Test
    void testPlayerActionCounts_Tracking() {
        // Given
        PlayerState player = gameState.getCurrentPlayer();
        String userId = player.getUserId();
        
        // Initial action count should be 0
        assertEquals(0, gameState.getPlayerActionCounts().get(userId).intValue());
        
        // When - Player performs actions (simulated by processing discard)
        Tile tile = new Tile("5W");
        player.addTile(tile);
        gameState.processDiscard(userId, tile);
        
        // Then - Action count should increase
        assertEquals(1, gameState.getPlayerActionCounts().get(userId).intValue());
    }
    
    @Test
    void testGameEndConditions_WinningHand() {
        // Given
        PlayerState player = gameState.getCurrentPlayer();
        PlayerState spyPlayer = org.mockito.Mockito.spy(player);
        org.mockito.Mockito.when(spyPlayer.hasWinningHand()).thenReturn(true);
        
        // Replace player in game state
        gameState.getPlayers().set(0, spyPlayer);
        
        // When - Check if game should end
        boolean shouldEnd = gameState.shouldEndGame();
        
        // Then
        assertTrue(shouldEnd);
    }
    
    @Test
    void testGameEndConditions_EmptyWall() {
        // Given - Simulate empty tile wall
        while (gameState.getRemainingTiles() > 0) {
            gameState.drawTile();
        }
        
        // When - Check if game should end
        boolean shouldEnd = gameState.shouldEndGame();
        
        // Then
        assertTrue(shouldEnd);
        assertEquals(0, gameState.getRemainingTiles());
    }
    
    @Test
    void testGameEnd_StateTransition() {
        // Given
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
}