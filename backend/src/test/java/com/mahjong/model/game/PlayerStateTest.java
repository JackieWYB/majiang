package com.mahjong.model.game;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PlayerStateTest {
    
    private PlayerState playerState;
    
    @BeforeEach
    void setUp() {
        playerState = new PlayerState("user123", 0);
    }
    
    @Test
    @DisplayName("Should initialize player state correctly")
    void shouldInitializePlayerStateCorrectly() {
        assertEquals("user123", playerState.getUserId());
        assertEquals(0, playerState.getSeatIndex());
        assertEquals(PlayerState.PlayerStatus.WAITING, playerState.getStatus());
        assertFalse(playerState.isDealer());
        assertEquals(0, playerState.getScore());
        assertFalse(playerState.isReady());
        assertTrue(playerState.getHandTiles().isEmpty());
        assertTrue(playerState.getMelds().isEmpty());
        assertEquals(0, playerState.getConsecutiveTimeouts());
    }
    
    @Test
    @DisplayName("Should add and remove tiles correctly")
    void shouldAddAndRemoveTilesCorrectly() {
        Tile tile1 = new Tile("5W");
        Tile tile2 = new Tile("6W");
        
        playerState.addTile(tile1);
        playerState.addTile(tile2);
        
        assertEquals(2, playerState.getHandTiles().size());
        assertTrue(playerState.getHandTiles().contains(tile1));
        assertTrue(playerState.getHandTiles().contains(tile2));
        
        assertTrue(playerState.removeTile(tile1));
        assertEquals(1, playerState.getHandTiles().size());
        assertFalse(playerState.getHandTiles().contains(tile1));
        
        assertFalse(playerState.removeTile(tile1)); // Already removed
    }
    
    @Test
    @DisplayName("Should add and remove multiple tiles")
    void shouldAddAndRemoveMultipleTiles() {
        List<Tile> tiles = Arrays.asList(
            new Tile("5W"), new Tile("6W"), new Tile("7W")
        );
        
        playerState.addTiles(tiles);
        assertEquals(3, playerState.getHandTiles().size());
        
        List<Tile> tilesToRemove = Arrays.asList(
            new Tile("5W"), new Tile("6W")
        );
        
        assertTrue(playerState.removeTiles(tilesToRemove));
        assertEquals(1, playerState.getHandTiles().size());
        assertEquals(new Tile("7W"), playerState.getHandTiles().get(0));
    }
    
    @Test
    @DisplayName("Should sort hand tiles correctly")
    void shouldSortHandTilesCorrectly() {
        List<Tile> unsortedTiles = Arrays.asList(
            new Tile("9W"), new Tile("1T"), new Tile("5D"), 
            new Tile("3W"), new Tile("7T"), new Tile("2D")
        );
        
        playerState.addTiles(unsortedTiles);
        List<Tile> handTiles = playerState.getHandTiles();
        
        // Should be sorted by suit first, then by rank
        assertEquals("3W", handTiles.get(0).toString());
        assertEquals("9W", handTiles.get(1).toString());
        assertEquals("1T", handTiles.get(2).toString());
        assertEquals("7T", handTiles.get(3).toString());
        assertEquals("2D", handTiles.get(4).toString());
        assertEquals("5D", handTiles.get(5).toString());
    }
    
    @Test
    @DisplayName("Should correctly identify Peng possibilities")
    void shouldIdentifyPengPossibilities() {
        List<Tile> tiles = Arrays.asList(
            new Tile("5W"), new Tile("5W"), new Tile("6W"), new Tile("7W")
        );
        playerState.addTiles(tiles);
        
        assertTrue(playerState.canPeng(new Tile("5W")));
        assertFalse(playerState.canPeng(new Tile("6W")));
        assertFalse(playerState.canPeng(new Tile("8W")));
    }
    
    @Test
    @DisplayName("Should correctly identify Gang possibilities")
    void shouldIdentifyGangPossibilities() {
        List<Tile> tiles = Arrays.asList(
            new Tile("5W"), new Tile("5W"), new Tile("5W"), new Tile("6W")
        );
        playerState.addTiles(tiles);
        
        assertTrue(playerState.canGang(new Tile("5W")));
        assertFalse(playerState.canGang(new Tile("6W")));
        
        // Add fourth 5W for concealed gang
        playerState.addTile(new Tile("5W"));
        assertTrue(playerState.canConcealedGang(new Tile("5W")));
    }
    
    @Test
    @DisplayName("Should correctly identify Chi possibilities")
    void shouldIdentifyChiPossibilities() {
        List<Tile> tiles = Arrays.asList(
            new Tile("4W"), new Tile("6W"), new Tile("7W"), new Tile("8W")
        );
        playerState.addTiles(tiles);
        
        // Can form 4-5-6 with 5W
        assertTrue(playerState.canChi(new Tile("5W")));
        
        // Can form 6-7-8 with 6W (we have 7W, 8W)
        assertTrue(playerState.canChi(new Tile("6W")));
        
        // Can form 7-8-9 with 9W
        assertTrue(playerState.canChi(new Tile("9W")));
        
        // Cannot form chi with different suit
        assertFalse(playerState.canChi(new Tile("5T")));
    }
    
    @Test
    @DisplayName("Should handle meld operations")
    void shouldHandleMeldOperations() {
        List<Tile> pengTiles = Arrays.asList(
            new Tile("5W"), new Tile("5W"), new Tile("5W")
        );
        MeldSet peng = MeldSet.createPeng(pengTiles, "player2");
        
        playerState.addMeld(peng);
        assertEquals(1, playerState.getMelds().size());
        assertEquals(peng, playerState.getMelds().get(0));
        
        // Test upgrade gang possibility
        playerState.addTile(new Tile("5W"));
        assertTrue(playerState.canUpgradeGang(new Tile("5W")));
    }
    
    @Test
    @DisplayName("Should calculate total tile count correctly")
    void shouldCalculateTotalTileCountCorrectly() {
        // Add hand tiles
        List<Tile> handTiles = Arrays.asList(
            new Tile("1W"), new Tile("2W"), new Tile("3W")
        );
        playerState.addTiles(handTiles);
        
        // Add a meld
        List<Tile> pengTiles = Arrays.asList(
            new Tile("5W"), new Tile("5W"), new Tile("5W")
        );
        MeldSet peng = MeldSet.createPeng(pengTiles, "player2");
        playerState.addMeld(peng);
        
        assertEquals(6, playerState.getTotalTileCount()); // 3 hand + 3 meld
    }
    
    @Test
    @DisplayName("Should handle timeout management")
    void shouldHandleTimeoutManagement() {
        assertEquals(0, playerState.getConsecutiveTimeouts());
        
        playerState.incrementTimeouts();
        assertEquals(1, playerState.getConsecutiveTimeouts());
        assertEquals(PlayerState.PlayerStatus.WAITING, playerState.getStatus());
        
        playerState.incrementTimeouts();
        playerState.incrementTimeouts();
        assertEquals(3, playerState.getConsecutiveTimeouts());
        assertEquals(PlayerState.PlayerStatus.TRUSTEE, playerState.getStatus());
        
        playerState.updateLastActionTime();
        assertEquals(0, playerState.getConsecutiveTimeouts());
    }
    
    @Test
    @DisplayName("Should handle score management")
    void shouldHandleScoreManagement() {
        assertEquals(0, playerState.getScore());
        
        playerState.setScore(100);
        assertEquals(100, playerState.getScore());
        
        playerState.addScore(50);
        assertEquals(150, playerState.getScore());
        
        playerState.addScore(-30);
        assertEquals(120, playerState.getScore());
    }
    
    @Test
    @DisplayName("Should reset for new game correctly")
    void shouldResetForNewGameCorrectly() {
        // Set up some state
        playerState.addTile(new Tile("5W"));
        playerState.setScore(100);
        playerState.setStatus(PlayerState.PlayerStatus.PLAYING);
        playerState.setReady(true);
        playerState.incrementTimeouts();
        
        // Reset
        playerState.resetForNewGame();
        
        assertTrue(playerState.getHandTiles().isEmpty());
        assertTrue(playerState.getMelds().isEmpty());
        assertEquals(PlayerState.PlayerStatus.WAITING, playerState.getStatus());
        assertEquals(0, playerState.getScore());
        assertFalse(playerState.isReady());
        assertEquals(0, playerState.getConsecutiveTimeouts());
        assertNull(playerState.getLastDrawnTile());
        assertTrue(playerState.getAvailableActions().isEmpty());
    }
    
    @Test
    @DisplayName("Should handle available actions")
    void shouldHandleAvailableActions() {
        List<PlayerState.ActionType> actions = Arrays.asList(
            PlayerState.ActionType.DISCARD, 
            PlayerState.ActionType.PENG
        );
        
        playerState.setAvailableActions(actions);
        assertEquals(2, playerState.getAvailableActions().size());
        assertTrue(playerState.getAvailableActions().contains(PlayerState.ActionType.DISCARD));
        assertTrue(playerState.getAvailableActions().contains(PlayerState.ActionType.PENG));
    }
    
    @Test
    @DisplayName("Should implement equals and hashCode correctly")
    void shouldImplementEqualsAndHashCode() {
        PlayerState player1 = new PlayerState("user123", 0);
        PlayerState player2 = new PlayerState("user123", 0);
        PlayerState player3 = new PlayerState("user456", 1);
        
        player1.addTile(new Tile("5W"));
        player2.addTile(new Tile("5W"));
        
        assertEquals(player1, player2);
        assertNotEquals(player1, player3);
        assertEquals(player1.hashCode(), player2.hashCode());
    }
    
    @Test
    @DisplayName("Should generate correct string representation")
    void shouldGenerateCorrectStringRepresentation() {
        playerState.addTile(new Tile("5W"));
        playerState.setScore(100);
        playerState.setDealer(true);
        
        String str = playerState.toString();
        assertTrue(str.contains("user123"));
        assertTrue(str.contains("seat=0"));
        assertTrue(str.contains("tiles=1"));
        assertTrue(str.contains("score=100"));
        assertTrue(str.contains("dealer=true"));
    }
}