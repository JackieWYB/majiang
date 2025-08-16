package com.mahjong.model.game;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TileShufflerTest {
    
    @Test
    @DisplayName("Should shuffle tiles with reproducible seed")
    void shouldShuffleTilesWithReproducibleSeed() {
        List<Tile> tiles1 = createOrderedTileList();
        List<Tile> tiles2 = createOrderedTileList();
        List<Tile> originalOrder = createOrderedTileList();
        
        long seed = 12345L;
        
        TileShuffler.shuffle(tiles1, seed);
        TileShuffler.shuffle(tiles2, seed);
        
        // Same seed should produce same shuffle
        assertEquals(tiles1, tiles2);
        
        // Should be different from original order (with high probability)
        assertNotEquals(originalOrder, tiles1);
    }
    
    @Test
    @DisplayName("Should shuffle with time seed and return seed")
    void shouldShuffleWithTimeSeedAndReturnSeed() {
        List<Tile> tiles = createOrderedTileList();
        List<Tile> originalOrder = createOrderedTileList();
        
        long seed = TileShuffler.shuffleWithTimeSeed(tiles);
        
        assertTrue(seed > 0);
        assertNotEquals(originalOrder, tiles); // Should be shuffled
        
        // Verify reproducibility with returned seed
        List<Tile> tiles2 = createOrderedTileList();
        TileShuffler.shuffle(tiles2, seed);
        assertEquals(tiles, tiles2);
    }
    
    @Test
    @DisplayName("Should create shuffled tile wall for WAN_ONLY")
    void shouldCreateShuffledTileWallForWanOnly() {
        long seed = 12345L;
        List<Tile> tileWall = TileShuffler.createShuffledTileWall("WAN_ONLY", seed);
        
        assertEquals(36, tileWall.size()); // 9 ranks * 4 tiles
        
        // Verify all tiles are WAN suit
        for (Tile tile : tileWall) {
            assertEquals(Tile.Suit.WAN, tile.getSuit());
        }
        
        // Verify we have 4 of each rank
        int[] rankCounts = new int[10];
        for (Tile tile : tileWall) {
            rankCounts[tile.getRank()]++;
        }
        
        for (int rank = 1; rank <= 9; rank++) {
            assertEquals(4, rankCounts[rank]);
        }
        
        // Verify reproducibility
        List<Tile> tileWall2 = TileShuffler.createShuffledTileWall("WAN_ONLY", seed);
        assertEquals(tileWall, tileWall2);
    }
    
    @Test
    @DisplayName("Should create shuffled tile wall for all suits")
    void shouldCreateShuffledTileWallForAllSuits() {
        long seed = 12345L;
        List<Tile> tileWall = TileShuffler.createShuffledTileWall("ALL", seed);
        
        assertEquals(108, tileWall.size()); // 3 suits * 9 ranks * 4 tiles
        
        // Count tiles by suit
        int wanCount = 0, tiaoCount = 0, tongCount = 0;
        for (Tile tile : tileWall) {
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
    @DisplayName("Should generate secure random seed")
    void shouldGenerateSecureRandomSeed() {
        long seed1 = TileShuffler.generateSecureRandomSeed();
        long seed2 = TileShuffler.generateSecureRandomSeed();
        
        assertNotEquals(seed1, seed2); // Should be different (with very high probability)
        assertTrue(seed1 != 0);
        assertTrue(seed2 != 0);
    }
    
    @Test
    @DisplayName("Should validate tile wall composition for WAN_ONLY")
    void shouldValidateTileWallCompositionForWanOnly() {
        List<Tile> validWall = TileShuffler.createShuffledTileWall("WAN_ONLY", 12345L);
        assertTrue(TileShuffler.validateTileWall(validWall, "WAN_ONLY"));
        
        // Test invalid walls
        List<Tile> tooFewTiles = validWall.subList(0, 35);
        assertFalse(TileShuffler.validateTileWall(tooFewTiles, "WAN_ONLY"));
        
        List<Tile> wrongSuit = new ArrayList<>(validWall);
        wrongSuit.set(0, new Tile("5T")); // Replace with TIAO tile
        assertFalse(TileShuffler.validateTileWall(wrongSuit, "WAN_ONLY"));
        
        List<Tile> tooManyOfOne = new ArrayList<>(validWall);
        tooManyOfOne.add(new Tile("1W")); // Add extra 1W
        assertFalse(TileShuffler.validateTileWall(tooManyOfOne, "WAN_ONLY"));
    }
    
    @Test
    @DisplayName("Should validate tile wall composition for all suits")
    void shouldValidateTileWallCompositionForAllSuits() {
        List<Tile> validWall = TileShuffler.createShuffledTileWall("ALL", 12345L);
        assertTrue(TileShuffler.validateTileWall(validWall, "ALL"));
        
        // Test invalid wall
        List<Tile> tooFewTiles = validWall.subList(0, 107);
        assertFalse(TileShuffler.validateTileWall(tooFewTiles, "ALL"));
    }
    
    @Test
    @DisplayName("Should verify shuffle reproducibility")
    void shouldVerifyShuffleReproducibility() {
        long seed = 12345L;
        
        assertTrue(TileShuffler.verifyShuffleReproducibility("WAN_ONLY", seed));
        assertTrue(TileShuffler.verifyShuffleReproducibility("ALL", seed));
        
        // Different seeds should produce different results
        List<Tile> wall1 = TileShuffler.createShuffledTileWall("WAN_ONLY", 12345L);
        List<Tile> wall2 = TileShuffler.createShuffledTileWall("WAN_ONLY", 54321L);
        assertNotEquals(wall1, wall2);
    }
    
    @Test
    @DisplayName("Should perform Fisher-Yates shuffle")
    void shouldPerformFisherYatesShuffle() {
        List<Tile> tiles = createOrderedTileList();
        List<Tile> originalOrder = createOrderedTileList();
        
        TileShuffler.fisherYatesShuffle(tiles, 12345L);
        
        assertNotEquals(originalOrder, tiles); // Should be shuffled
        assertEquals(originalOrder.size(), tiles.size()); // Same size
        
        // Should contain all original tiles
        for (Tile tile : originalOrder) {
            assertTrue(tiles.contains(tile));
        }
    }
    
    @Test
    @DisplayName("Should analyze shuffle quality")
    void shouldAnalyzeShuffleQuality() {
        List<Tile> originalWall = createOrderedTileList();
        List<Tile> shuffledWall = createOrderedTileList();
        TileShuffler.shuffle(shuffledWall, 12345L);
        
        TileShuffler.ShuffleAnalysis analysis = 
            TileShuffler.analyzeShuffleQuality(originalWall, shuffledWall);
        
        assertNotNull(analysis);
        assertTrue(analysis.getQuality() >= 0.0 && analysis.getQuality() <= 1.0);
        assertTrue(analysis.getSamePositions() >= 0);
        assertEquals(originalWall.size(), analysis.getTotalPositions());
        
        // A good shuffle should have high quality (low same positions)
        assertTrue(analysis.getQuality() > 0.5); // At least 50% different positions
        
        // Test with identical walls (no shuffle)
        TileShuffler.ShuffleAnalysis noShuffleAnalysis = 
            TileShuffler.analyzeShuffleQuality(originalWall, originalWall);
        assertEquals(0.0, noShuffleAnalysis.getQuality(), 0.001);
        assertFalse(noShuffleAnalysis.isWellShuffled());
    }
    
    @Test
    @DisplayName("Should handle empty tile list")
    void shouldHandleEmptyTileList() {
        List<Tile> emptyList = new ArrayList<>();
        
        // Should not throw exception
        assertDoesNotThrow(() -> TileShuffler.shuffle(emptyList, 12345L));
        assertTrue(emptyList.isEmpty());
        
        assertDoesNotThrow(() -> TileShuffler.fisherYatesShuffle(emptyList, 12345L));
        assertTrue(emptyList.isEmpty());
    }
    
    @Test
    @DisplayName("Should handle single tile list")
    void shouldHandleSingleTileList() {
        List<Tile> singleTile = List.of(new Tile("5W"));
        List<Tile> mutableSingleTile = new ArrayList<>(singleTile);
        
        TileShuffler.shuffle(mutableSingleTile, 12345L);
        assertEquals(1, mutableSingleTile.size());
        assertEquals(new Tile("5W"), mutableSingleTile.get(0));
    }
    
    private List<Tile> createOrderedTileList() {
        List<Tile> tiles = new ArrayList<>();
        for (int rank = 1; rank <= 9; rank++) {
            for (int count = 0; count < 4; count++) {
                tiles.add(new Tile(Tile.Suit.WAN, rank));
            }
        }
        return tiles;
    }
}