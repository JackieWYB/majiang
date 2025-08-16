package com.mahjong.model.game;

import java.util.*;

/**
 * Utility class for shuffling tiles with reproducible random seeds
 */
public class TileShuffler {
    
    /**
     * Shuffles a list of tiles using a specific seed for reproducibility
     */
    public static void shuffle(List<Tile> tiles, long seed) {
        Random random = new Random(seed);
        Collections.shuffle(tiles, random);
    }
    
    /**
     * Shuffles a list of tiles using current time as seed
     */
    public static long shuffleWithTimeSeed(List<Tile> tiles) {
        long seed = System.currentTimeMillis();
        shuffle(tiles, seed);
        return seed;
    }
    
    /**
     * Creates a shuffled tile wall for the specified configuration
     */
    public static List<Tile> createShuffledTileWall(String tilesConfig, long seed) {
        List<Tile> tileWall = new ArrayList<>();
        
        if ("WAN_ONLY".equals(tilesConfig)) {
            // Only WAN (Characters) tiles - 36 tiles total
            for (int rank = 1; rank <= 9; rank++) {
                for (int count = 0; count < 4; count++) {
                    tileWall.add(new Tile(Tile.Suit.WAN, rank));
                }
            }
        } else {
            // All three suits - 108 tiles total
            for (Tile.Suit suit : Tile.Suit.values()) {
                for (int rank = 1; rank <= 9; rank++) {
                    for (int count = 0; count < 4; count++) {
                        tileWall.add(new Tile(suit, rank));
                    }
                }
            }
        }
        
        shuffle(tileWall, seed);
        return tileWall;
    }
    
    /**
     * Generates a cryptographically secure random seed
     */
    public static long generateSecureRandomSeed() {
        return new java.security.SecureRandom().nextLong();
    }
    
    /**
     * Validates that a tile wall has the correct composition
     */
    public static boolean validateTileWall(List<Tile> tileWall, String tilesConfig) {
        Map<String, Integer> tileCount = new HashMap<>();
        
        // Count each tile type
        for (Tile tile : tileWall) {
            String tileKey = tile.toString();
            tileCount.merge(tileKey, 1, Integer::sum);
        }
        
        if ("WAN_ONLY".equals(tilesConfig)) {
            // Should have 36 tiles total, 4 of each WAN tile
            if (tileWall.size() != 36) return false;
            
            for (int rank = 1; rank <= 9; rank++) {
                String tileKey = rank + "W";
                if (tileCount.getOrDefault(tileKey, 0) != 4) {
                    return false;
                }
            }
            
            // Should not have any TIAO or TONG tiles
            for (Tile tile : tileWall) {
                if (tile.getSuit() != Tile.Suit.WAN) {
                    return false;
                }
            }
        } else {
            // Should have 108 tiles total, 4 of each tile type
            if (tileWall.size() != 108) return false;
            
            for (Tile.Suit suit : Tile.Suit.values()) {
                for (int rank = 1; rank <= 9; rank++) {
                    String tileKey = rank + suit.getSymbol();
                    if (tileCount.getOrDefault(tileKey, 0) != 4) {
                        return false;
                    }
                }
            }
        }
        
        return true;
    }
    
    /**
     * Reproduces the same shuffle given the same seed and tile composition
     */
    public static boolean verifyShuffleReproducibility(String tilesConfig, long seed) {
        List<Tile> wall1 = createShuffledTileWall(tilesConfig, seed);
        List<Tile> wall2 = createShuffledTileWall(tilesConfig, seed);
        
        if (wall1.size() != wall2.size()) return false;
        
        for (int i = 0; i < wall1.size(); i++) {
            if (!wall1.get(i).equals(wall2.get(i))) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Fisher-Yates shuffle implementation for additional randomness verification
     */
    public static void fisherYatesShuffle(List<Tile> tiles, long seed) {
        Random random = new Random(seed);
        
        for (int i = tiles.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            Collections.swap(tiles, i, j);
        }
    }
    
    /**
     * Analyzes the randomness quality of a shuffle
     */
    public static ShuffleAnalysis analyzeShuffleQuality(List<Tile> originalWall, List<Tile> shuffledWall) {
        if (originalWall.size() != shuffledWall.size()) {
            throw new IllegalArgumentException("Walls must be same size");
        }
        
        int samePositions = 0;
        int totalPositions = originalWall.size();
        
        for (int i = 0; i < totalPositions; i++) {
            if (originalWall.get(i).equals(shuffledWall.get(i))) {
                samePositions++;
            }
        }
        
        double shuffleQuality = 1.0 - ((double) samePositions / totalPositions);
        boolean isWellShuffled = shuffleQuality > 0.9; // Less than 10% tiles in same position
        
        return new ShuffleAnalysis(shuffleQuality, samePositions, totalPositions, isWellShuffled);
    }
    
    /**
     * Result of shuffle quality analysis
     */
    public static class ShuffleAnalysis {
        private final double quality;
        private final int samePositions;
        private final int totalPositions;
        private final boolean wellShuffled;
        
        public ShuffleAnalysis(double quality, int samePositions, int totalPositions, boolean wellShuffled) {
            this.quality = quality;
            this.samePositions = samePositions;
            this.totalPositions = totalPositions;
            this.wellShuffled = wellShuffled;
        }
        
        public double getQuality() { return quality; }
        public int getSamePositions() { return samePositions; }
        public int getTotalPositions() { return totalPositions; }
        public boolean isWellShuffled() { return wellShuffled; }
        
        @Override
        public String toString() {
            return String.format("ShuffleAnalysis{quality=%.2f, samePositions=%d/%d, wellShuffled=%s}",
                    quality, samePositions, totalPositions, wellShuffled);
        }
    }
}