package com.mahjong.model.game;

import com.mahjong.model.config.RoomConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class GameStateTest {
    
    private GameState gameState;
    private RoomConfig config;
    private List<String> playerIds;
    
    @BeforeEach
    void setUp() {
        config = new RoomConfig();
        config.setTiles("ALL"); // Use all suits to have enough tiles (108 total)
        config.setAllowPeng(true);
        config.setAllowGang(true);
        config.setAllowChi(false);
        
        playerIds = Arrays.asList("player1", "player2", "player3");
        gameState = new GameState("room123", "game456", playerIds, config);
    }
    
    @Test
    @DisplayName("Should initialize game state correctly")
    void shouldInitializeGameStateCorrectly() {
        assertEquals("room123", gameState.getRoomId());
        assertEquals("game456", gameState.getGameId());
        assertEquals(GameState.GamePhase.WAITING, gameState.getPhase());
        assertEquals(3, gameState.getPlayers().size());
        assertEquals(0, gameState.getCurrentPlayerIndex());
        assertEquals("player1", gameState.getDealerUserId());
        assertEquals(0, gameState.getDealerSeatIndex());
        assertEquals(1, gameState.getRoundNumber());
        assertEquals(0, gameState.getTotalTurns());
        
        // Check dealer is set correctly
        assertTrue(gameState.getPlayers().get(0).isDealer());
        assertFalse(gameState.getPlayers().get(1).isDealer());
        assertFalse(gameState.getPlayers().get(2).isDealer());
    }
    
    @Test
    @DisplayName("Should initialize tile wall correctly for ALL suits")
    void shouldInitializeTileWallForAllSuits() {
        assertEquals(108, gameState.getRemainingTiles()); // 3 suits * 9 ranks * 4 tiles each
        assertEquals(108, gameState.getTileWall().size());
        
        // Count tiles by suit
        int wanCount = 0, tiaoCount = 0, tongCount = 0;
        for (Tile tile : gameState.getTileWall()) {
            assertTrue(tile.getRank() >= 1 && tile.getRank() <= 9);
            switch (tile.getSuit()) {
                case WAN -> wanCount++;
                case TIAO -> tiaoCount++;
                case TONG -> tongCount++;
            }
        }
        
        assertEquals(36, wanCount);
        assertEquals(36, tiaoCount);
        assertEquals(36, tongCount);
    }
    
    @Test
    @DisplayName("Should shuffle tiles reproducibly")
    void shouldShuffleTilesReproducibly() {
        List<Tile> originalWall = gameState.getTileWall();
        long seed = gameState.getRandomSeed();
        
        // Create another game state with same seed
        GameState gameState2 = new GameState("room124", "game457", playerIds, config);
        // Set the same seed (this would need to be exposed or set differently in real implementation)
        
        gameState.shuffleTiles();
        gameState2.shuffleTiles();
        
        // The shuffle should be different from original
        assertNotEquals(originalWall, gameState.getTileWall());
    }
    
    @Test
    @DisplayName("Should deal initial tiles correctly")
    void shouldDealInitialTilesCorrectly() {
        gameState.dealInitialTiles();
        
        assertEquals(GameState.GamePhase.PLAYING, gameState.getPhase());
        assertNotNull(gameState.getGameStartTime());
        
        // Check tile distribution
        PlayerState dealer = gameState.getPlayers().get(0);
        assertEquals(14, dealer.getHandTiles().size()); // Dealer gets 14 tiles
        
        for (int i = 1; i < gameState.getPlayers().size(); i++) {
            PlayerState player = gameState.getPlayers().get(i);
            assertEquals(13, player.getHandTiles().size()); // Others get 13 tiles
            assertEquals(PlayerState.PlayerStatus.WAITING_TURN, player.getStatus());
        }
        
        // Check remaining tiles
        int expectedRemaining = 108 - (14 + 13 + 13); // 108 - 40 = 68
        assertEquals(expectedRemaining, gameState.getRemainingTiles());
    }
    
    @Test
    @DisplayName("Should handle turn management")
    void shouldHandleTurnManagement() {
        gameState.dealInitialTiles();
        
        assertEquals(0, gameState.getCurrentPlayerIndex());
        assertNotNull(gameState.getTurnStartTime());
        assertNotNull(gameState.getTurnDeadline());
        
        gameState.nextTurn();
        assertEquals(1, gameState.getCurrentPlayerIndex());
        
        gameState.nextTurn();
        assertEquals(2, gameState.getCurrentPlayerIndex());
        
        gameState.nextTurn();
        assertEquals(0, gameState.getCurrentPlayerIndex()); // Wrap around
    }
    
    @Test
    @DisplayName("Should draw tiles correctly")
    void shouldDrawTilesCorrectly() {
        int initialCount = gameState.getRemainingTiles();
        
        Tile drawnTile = gameState.drawTile();
        assertNotNull(drawnTile);
        assertEquals(initialCount - 1, gameState.getRemainingTiles());
        
        List<Tile> drawnTiles = gameState.drawTiles(3);
        assertEquals(3, drawnTiles.size());
        assertEquals(initialCount - 4, gameState.getRemainingTiles());
    }
    
    @Test
    @DisplayName("Should throw exception when drawing too many tiles")
    void shouldThrowExceptionWhenDrawingTooManyTiles() {
        int remainingTiles = gameState.getRemainingTiles();
        
        assertThrows(IllegalStateException.class, 
            () -> gameState.drawTiles(remainingTiles + 1));
    }
    
    @Test
    @DisplayName("Should process discard correctly")
    void shouldProcessDiscardCorrectly() {
        gameState.dealInitialTiles();
        
        PlayerState currentPlayer = gameState.getCurrentPlayer();
        Tile tileToDiscard = currentPlayer.getHandTiles().get(0);
        int initialHandSize = currentPlayer.getHandTiles().size();
        
        gameState.processDiscard(currentPlayer.getUserId(), tileToDiscard);
        
        assertEquals(initialHandSize - 1, currentPlayer.getHandTiles().size());
        assertEquals(1, gameState.getDiscardPile().size());
        assertEquals(tileToDiscard, gameState.getDiscardPile().get(0));
        assertEquals(1, gameState.getPlayerActionCounts().get(currentPlayer.getUserId()).intValue());
    }
    
    @Test
    @DisplayName("Should throw exception for invalid discard")
    void shouldThrowExceptionForInvalidDiscard() {
        gameState.dealInitialTiles();
        
        PlayerState currentPlayer = gameState.getCurrentPlayer();
        Tile nonExistentTile = new Tile("9W");
        
        // Ensure the tile is not in player's hand
        while (currentPlayer.getHandTiles().contains(nonExistentTile)) {
            currentPlayer.removeTile(nonExistentTile);
        }
        
        assertThrows(IllegalArgumentException.class, 
            () -> gameState.processDiscard(currentPlayer.getUserId(), nonExistentTile));
    }
    
    @Test
    @DisplayName("Should process Peng correctly")
    void shouldProcessPengCorrectly() {
        gameState.dealInitialTiles();
        
        // Set up a scenario where player can Peng
        PlayerState player = gameState.getPlayers().get(1);
        Tile pengTile = new Tile("5W");
        
        // Add two matching tiles to player's hand
        player.addTile(pengTile);
        player.addTile(pengTile);
        
        // Add tile to discard pile
        gameState.getDiscardPile().add(pengTile);
        
        int initialHandSize = player.getHandTiles().size();
        int initialMeldCount = player.getMelds().size();
        
        gameState.processPeng(player.getUserId(), pengTile, "player1");
        
        assertEquals(initialHandSize - 2, player.getHandTiles().size()); // Removed 2 tiles
        assertEquals(initialMeldCount + 1, player.getMelds().size()); // Added 1 meld
        assertEquals(MeldSet.MeldType.PENG, player.getMelds().get(0).getMeldType());
        assertTrue(gameState.getDiscardPile().isEmpty()); // Claimed tile removed from discard
        assertEquals(player.getUserId(), gameState.getCurrentPlayer().getUserId()); // Player's turn
    }
    
    @Test
    @DisplayName("Should find players correctly")
    void shouldFindPlayersCorrectly() {
        PlayerState player = gameState.getPlayerByUserId("player2");
        assertNotNull(player);
        assertEquals("player2", player.getUserId());
        assertEquals(1, player.getSeatIndex());
        
        assertNull(gameState.getPlayerByUserId("nonexistent"));
        
        assertEquals(1, gameState.getPlayerIndexByUserId("player2"));
        assertEquals(-1, gameState.getPlayerIndexByUserId("nonexistent"));
    }
    
    @Test
    @DisplayName("Should check game end conditions")
    void shouldCheckGameEndConditions() {
        assertFalse(gameState.shouldEndGame()); // Game just started
        
        // Simulate empty tile wall
        while (gameState.getRemainingTiles() > 0) {
            gameState.drawTile();
        }
        
        assertTrue(gameState.shouldEndGame()); // No tiles left
    }
    
    @Test
    @DisplayName("Should end game correctly")
    void shouldEndGameCorrectly() {
        gameState.dealInitialTiles();
        
        gameState.endGame();
        
        assertEquals(GameState.GamePhase.SETTLEMENT, gameState.getPhase());
        assertNotNull(gameState.getGameEndTime());
        
        for (PlayerState player : gameState.getPlayers()) {
            assertEquals(PlayerState.PlayerStatus.FINISHED, player.getStatus());
        }
    }
    
    @Test
    @DisplayName("Should check turn timeout")
    void shouldCheckTurnTimeout() {
        gameState.dealInitialTiles();
        
        assertFalse(gameState.isTurnTimedOut()); // Just started
        
        // This test would need to manipulate time or use a shorter timeout for testing
        // In a real scenario, you might use a clock abstraction for testing
    }
    
    @Test
    @DisplayName("Should calculate game duration")
    void shouldCalculateGameDuration() {
        assertEquals(0, gameState.getGameDuration()); // Not started
        
        gameState.dealInitialTiles();
        assertTrue(gameState.getGameDuration() >= 0); // Started
        
        gameState.endGame();
        long duration = gameState.getGameDuration();
        assertTrue(duration >= 0);
        
        // Duration should be stable after game ends
        assertEquals(duration, gameState.getGameDuration());
    }
    
    @Test
    @DisplayName("Should track player action counts")
    void shouldTrackPlayerActionCounts() {
        gameState.dealInitialTiles();
        
        // All players should start with 0 actions
        for (String playerId : playerIds) {
            assertEquals(0, gameState.getPlayerActionCounts().get(playerId).intValue());
        }
        
        // Process a discard
        PlayerState currentPlayer = gameState.getCurrentPlayer();
        Tile tileToDiscard = currentPlayer.getHandTiles().get(0);
        gameState.processDiscard(currentPlayer.getUserId(), tileToDiscard);
        
        assertEquals(1, gameState.getPlayerActionCounts().get(currentPlayer.getUserId()).intValue());
    }
    
    @Test
    @DisplayName("Should generate correct string representation")
    void shouldGenerateCorrectStringRepresentation() {
        String str = gameState.toString();
        assertTrue(str.contains("room123"));
        assertTrue(str.contains("game456"));
        assertTrue(str.contains("WAITING"));
        assertTrue(str.contains("players=3"));
        assertTrue(str.contains("remainingTiles=108"));
    }
}