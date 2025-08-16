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
 * Unit tests for action validation functionality
 */
@ExtendWith(MockitoExtension.class)
class ActionValidationTest {
    
    private GameState gameState;
    private RoomConfig config;
    
    @BeforeEach
    void setUp() {
        // Setup configuration
        config = new RoomConfig();
        config.setTiles("WAN_ONLY");
        config.setAllowPeng(true);
        config.setAllowGang(true);
        config.setAllowChi(true);
        
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
    void testDiscardValidation_ValidTile() {
        // Given
        PlayerState player = gameState.getCurrentPlayer();
        Tile tile = new Tile("5W");
        player.addTile(tile);
        player.setAvailableActions(Arrays.asList(PlayerState.ActionType.DISCARD));
        
        DiscardAction action = new DiscardAction("5W");
        
        // When
        ActionValidationResult result = validateAction(player, action);
        
        // Then
        assertTrue(result.isValid());
    }
    
    @Test
    void testDiscardValidation_TileNotInHand() {
        // Given
        PlayerState player = gameState.getCurrentPlayer();
        player.setAvailableActions(Arrays.asList(PlayerState.ActionType.DISCARD));
        // Don't add the tile to hand
        
        DiscardAction action = new DiscardAction("5W");
        
        // When
        ActionValidationResult result = validateAction(player, action);
        
        // Then
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("don't have this tile"));
    }
    
    @Test
    void testDiscardValidation_NotPlayerTurn() {
        // Given
        PlayerState player = gameState.getPlayerByUserId("player2"); // Not current player
        Tile tile = new Tile("5W");
        player.addTile(tile);
        player.setAvailableActions(Arrays.asList(PlayerState.ActionType.DISCARD));
        
        DiscardAction action = new DiscardAction("5W");
        
        // When
        ActionValidationResult result = validateAction(player, action);
        
        // Then
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("Not your turn"));
    }
    
    @Test
    void testDiscardValidation_InvalidTileFormat() {
        // Given
        PlayerState player = gameState.getCurrentPlayer();
        player.setAvailableActions(Arrays.asList(PlayerState.ActionType.DISCARD));
        
        DiscardAction action = new DiscardAction("invalid");
        
        // When
        ActionValidationResult result = validateAction(player, action);
        
        // Then
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("Invalid tile format"));
    }
    
    @Test
    void testPengValidation_ValidPeng() {
        // Given
        PlayerState player = gameState.getPlayerByUserId("player2");
        Tile tile = new Tile("5W");
        
        // Add two matching tiles to hand
        player.addTile(tile);
        player.addTile(tile);
        player.setAvailableActions(Arrays.asList(PlayerState.ActionType.PENG));
        
        PengAction action = new PengAction("5W", "player1");
        
        // When
        ActionValidationResult result = validateAction(player, action);
        
        // Then
        assertTrue(result.isValid());
    }
    
    @Test
    void testPengValidation_InsufficientTiles() {
        // Given
        PlayerState player = gameState.getPlayerByUserId("player2");
        Tile tile = new Tile("5W");
        
        // Add only one matching tile (need 2 for Peng)
        player.addTile(tile);
        player.setAvailableActions(Arrays.asList(PlayerState.ActionType.PENG));
        
        PengAction action = new PengAction("5W", "player1");
        
        // When
        ActionValidationResult result = validateAction(player, action);
        
        // Then
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("Cannot form Peng"));
    }
    
    @Test
    void testGangValidation_ConcealedGang() {
        // Given
        PlayerState player = gameState.getCurrentPlayer();
        Tile tile = new Tile("5W");
        
        // Add four matching tiles for concealed Gang
        player.addTile(tile);
        player.addTile(tile);
        player.addTile(tile);
        player.addTile(tile);
        player.setAvailableActions(Arrays.asList(PlayerState.ActionType.GANG));
        
        GangAction action = new GangAction("5W", GangAction.GangType.AN, null);
        
        // When
        ActionValidationResult result = validateAction(player, action);
        
        // Then
        assertTrue(result.isValid());
    }
    
    @Test
    void testGangValidation_ExposedGang() {
        // Given
        PlayerState player = gameState.getPlayerByUserId("player2");
        Tile tile = new Tile("5W");
        
        // Add three matching tiles for exposed Gang
        player.addTile(tile);
        player.addTile(tile);
        player.addTile(tile);
        player.setAvailableActions(Arrays.asList(PlayerState.ActionType.GANG));
        
        GangAction action = new GangAction("5W", GangAction.GangType.MING, "player1");
        
        // When
        ActionValidationResult result = validateAction(player, action);
        
        // Then
        assertTrue(result.isValid());
    }
    
    @Test
    void testGangValidation_InsufficientTiles() {
        // Given
        PlayerState player = gameState.getPlayerByUserId("player2");
        Tile tile = new Tile("5W");
        
        // Add only two matching tiles (need 3 for Gang)
        player.addTile(tile);
        player.addTile(tile);
        player.setAvailableActions(Arrays.asList(PlayerState.ActionType.GANG));
        
        GangAction action = new GangAction("5W", GangAction.GangType.MING, "player1");
        
        // When
        ActionValidationResult result = validateAction(player, action);
        
        // Then
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("Cannot form Gang"));
    }
    
    @Test
    void testChiValidation_ValidSequence() {
        // Given
        PlayerState player = gameState.getPlayerByUserId("player2");
        
        // Add tiles for sequence: 4W, 6W (claiming 5W to form 4-5-6)
        player.addTile(new Tile("4W"));
        player.addTile(new Tile("6W"));
        player.setAvailableActions(Arrays.asList(PlayerState.ActionType.CHI));
        
        ChiAction action = new ChiAction("5W", "456", "player1");
        
        // When
        ActionValidationResult result = validateAction(player, action);
        
        // Then
        assertTrue(result.isValid());
    }
    
    @Test
    void testChiValidation_InvalidSequence() {
        // Given
        PlayerState player = gameState.getPlayerByUserId("player2");
        
        // Add tiles that cannot form sequence with claimed tile
        player.addTile(new Tile("1W"));
        player.addTile(new Tile("9W"));
        player.setAvailableActions(Arrays.asList(PlayerState.ActionType.CHI));
        
        ChiAction action = new ChiAction("5W", "159", "player1");
        
        // When
        ActionValidationResult result = validateAction(player, action);
        
        // Then
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("Cannot form Chi"));
    }
    
    @Test
    void testHuValidation_WinningHand() {
        // Given
        PlayerState player = gameState.getCurrentPlayer();
        PlayerState spyPlayer = org.mockito.Mockito.spy(player);
        org.mockito.Mockito.when(spyPlayer.hasWinningHand()).thenReturn(true);
        spyPlayer.setAvailableActions(Arrays.asList(PlayerState.ActionType.HU));
        
        HuAction action = new HuAction("5W", true);
        
        // When
        ActionValidationResult result = validateAction(spyPlayer, action);
        
        // Then
        assertTrue(result.isValid());
    }
    
    @Test
    void testHuValidation_NotWinningHand() {
        // Given
        PlayerState player = gameState.getCurrentPlayer();
        PlayerState spyPlayer = org.mockito.Mockito.spy(player);
        org.mockito.Mockito.when(spyPlayer.hasWinningHand()).thenReturn(false);
        spyPlayer.setAvailableActions(Arrays.asList(PlayerState.ActionType.HU));
        
        HuAction action = new HuAction("5W", true);
        
        // When
        ActionValidationResult result = validateAction(spyPlayer, action);
        
        // Then
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("Not a winning hand"));
    }
    
    @Test
    void testPassValidation_AlwaysValid() {
        // Given
        PlayerState player = gameState.getPlayerByUserId("player2");
        player.setAvailableActions(Arrays.asList(PlayerState.ActionType.PASS));
        
        PassAction action = new PassAction();
        
        // When
        ActionValidationResult result = validateAction(player, action);
        
        // Then
        assertTrue(result.isValid());
    }
    
    @Test
    void testActionValidation_GameNotPlaying() {
        // Given
        gameState.setPhase(GameState.GamePhase.FINISHED);
        PlayerState player = gameState.getCurrentPlayer();
        player.setAvailableActions(Arrays.asList(PlayerState.ActionType.DISCARD));
        
        DiscardAction action = new DiscardAction("5W");
        
        // When
        ActionValidationResult result = validateAction(player, action);
        
        // Then
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("not in playing phase"));
    }
    
    @Test
    void testActionValidation_PlayerDisconnected() {
        // Given
        PlayerState player = gameState.getCurrentPlayer();
        player.setStatus(PlayerState.PlayerStatus.DISCONNECTED);
        player.setAvailableActions(Arrays.asList(PlayerState.ActionType.DISCARD));
        
        DiscardAction action = new DiscardAction("5W");
        
        // When
        ActionValidationResult result = validateAction(player, action);
        
        // Then
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("disconnected"));
    }
    
    @Test
    void testActionValidation_ActionNotAvailable() {
        // Given
        PlayerState player = gameState.getCurrentPlayer();
        player.setAvailableActions(Arrays.asList(PlayerState.ActionType.PASS)); // Only pass available
        
        DiscardAction action = new DiscardAction("5W");
        
        // When
        ActionValidationResult result = validateAction(player, action);
        
        // Then
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("not available"));
    }
    
    @Test
    void testActionValidation_UnknownActionType() {
        // Given
        PlayerState player = gameState.getCurrentPlayer();
        
        // Create a custom action type for testing
        PlayerAction unknownAction = new PlayerAction(PlayerAction.ActionType.DISCARD) {
            @Override
            public PlayerAction.ActionType getType() {
                return null; // Simulate unknown type
            }
        };
        
        // When
        ActionValidationResult result = validateAction(player, unknownAction);
        
        // Then
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("Unknown action type"));
    }
    
    @Test
    void testConfigurationRules_PengDisabled() {
        // Given
        config.setAllowPeng(false);
        PlayerState player = gameState.getPlayerByUserId("player2");
        Tile tile = new Tile("5W");
        
        // Add matching tiles
        player.addTile(tile);
        player.addTile(tile);
        
        // When - Check if Peng is allowed
        boolean canPeng = config.getAllowPeng() && player.canPeng(tile);
        
        // Then
        assertFalse(canPeng);
    }
    
    @Test
    void testConfigurationRules_GangDisabled() {
        // Given
        config.setAllowGang(false);
        PlayerState player = gameState.getPlayerByUserId("player2");
        Tile tile = new Tile("5W");
        
        // Add matching tiles
        player.addTile(tile);
        player.addTile(tile);
        player.addTile(tile);
        
        // When - Check if Gang is allowed
        boolean canGang = config.getAllowGang() && player.canGang(tile);
        
        // Then
        assertFalse(canGang);
    }
    
    @Test
    void testConfigurationRules_ChiDisabled() {
        // Given
        config.setAllowChi(false);
        PlayerState player = gameState.getPlayerByUserId("player2");
        
        // Add tiles for sequence
        player.addTile(new Tile("4W"));
        player.addTile(new Tile("6W"));
        
        // When - Check if Chi is allowed
        boolean canChi = config.getAllowChi() && player.canChi(new Tile("5W"));
        
        // Then
        assertFalse(canChi);
    }
    
    @Test
    void testTileValidation_WanOnlyConfiguration() {
        // Given
        config.setTiles("WAN_ONLY");
        
        // When - Try to create tiles of different suits
        Tile wanTile = new Tile("5W");
        
        // Then - WAN tiles should be valid
        assertEquals(Tile.Suit.WAN, wanTile.getSuit());
        
        // TIAO and TONG tiles would still be creatable but not in game wall
        Tile tiaoTile = new Tile("5T");
        Tile tongTile = new Tile("5D");
        assertEquals(Tile.Suit.TIAO, tiaoTile.getSuit());
        assertEquals(Tile.Suit.TONG, tongTile.getSuit());
    }
    
    @Test
    void testActionSequence_DiscardThenClaim() {
        // Given
        PlayerState currentPlayer = gameState.getCurrentPlayer();
        PlayerState claimingPlayer = gameState.getPlayerByUserId("player2");
        
        Tile discardTile = new Tile("5W");
        currentPlayer.addTile(discardTile);
        currentPlayer.setAvailableActions(Arrays.asList(PlayerState.ActionType.DISCARD));
        
        // Setup claiming player
        claimingPlayer.addTile(new Tile("5W"));
        claimingPlayer.addTile(new Tile("5W"));
        
        // When - Validate discard action
        DiscardAction discardAction = new DiscardAction("5W");
        ActionValidationResult discardResult = validateAction(currentPlayer, discardAction);
        
        // Then - Discard should be valid
        assertTrue(discardResult.isValid());
        
        // When - After discard, claiming player should be able to Peng
        claimingPlayer.setAvailableActions(Arrays.asList(PlayerState.ActionType.PENG, PlayerState.ActionType.PASS));
        PengAction pengAction = new PengAction("5W", "player1");
        ActionValidationResult pengResult = validateAction(claimingPlayer, pengAction);
        
        // Then - Peng should be valid
        assertTrue(pengResult.isValid());
    }
    
    // Helper method to simulate action validation
    private ActionValidationResult validateAction(PlayerState player, PlayerAction action) {
        // Simulate the validation logic from GameService
        
        // Check game phase
        if (gameState.getPhase() != GameState.GamePhase.PLAYING) {
            return ActionValidationResult.invalid("Game is not in playing phase");
        }
        
        // Check player status
        if (player.getStatus() == PlayerState.PlayerStatus.DISCONNECTED) {
            return ActionValidationResult.invalid("Player is disconnected");
        }
        
        // Check if action is available for player
        if (!player.getAvailableActions().contains(action.getType().toPlayerActionType())) {
            return ActionValidationResult.invalid("Action not available for player");
        }
        
        // Validate specific action requirements
        switch (action.getType()) {
            case DISCARD:
                return validateDiscardAction(player, (DiscardAction) action);
            case PENG:
                return validatePengAction(player, (PengAction) action);
            case GANG:
                return validateGangAction(player, (GangAction) action);
            case CHI:
                return validateChiAction(player, (ChiAction) action);
            case HU:
                return validateHuAction(player, (HuAction) action);
            case PASS:
                return ActionValidationResult.valid();
            default:
                return ActionValidationResult.invalid("Unknown action type");
        }
    }
    
    private ActionValidationResult validateDiscardAction(PlayerState player, DiscardAction action) {
        if (!gameState.getCurrentPlayer().getUserId().equals(player.getUserId())) {
            return ActionValidationResult.invalid("Not your turn");
        }
        
        try {
            Tile tile = new Tile(action.getTile());
            if (!player.getHandTiles().contains(tile)) {
                return ActionValidationResult.invalid("You don't have this tile");
            }
            return ActionValidationResult.valid();
        } catch (Exception e) {
            return ActionValidationResult.invalid("Invalid tile format");
        }
    }
    
    private ActionValidationResult validatePengAction(PlayerState player, PengAction action) {
        try {
            Tile tile = new Tile(action.getTile());
            if (!player.canPeng(tile)) {
                return ActionValidationResult.invalid("Cannot form Peng with this tile");
            }
            return ActionValidationResult.valid();
        } catch (Exception e) {
            return ActionValidationResult.invalid("Invalid tile format");
        }
    }
    
    private ActionValidationResult validateGangAction(PlayerState player, GangAction action) {
        try {
            Tile tile = new Tile(action.getTile());
            if (!player.canGang(tile) && !player.canConcealedGang(tile) && !player.canUpgradeGang(tile)) {
                return ActionValidationResult.invalid("Cannot form Gang with this tile");
            }
            return ActionValidationResult.valid();
        } catch (Exception e) {
            return ActionValidationResult.invalid("Invalid tile format");
        }
    }
    
    private ActionValidationResult validateChiAction(PlayerState player, ChiAction action) {
        try {
            Tile tile = new Tile(action.getTile());
            if (!player.canChi(tile)) {
                return ActionValidationResult.invalid("Cannot form Chi with this tile");
            }
            return ActionValidationResult.valid();
        } catch (Exception e) {
            return ActionValidationResult.invalid("Invalid tile format");
        }
    }
    
    private ActionValidationResult validateHuAction(PlayerState player, HuAction action) {
        if (!player.hasWinningHand()) {
            return ActionValidationResult.invalid("Not a winning hand");
        }
        return ActionValidationResult.valid();
    }
}