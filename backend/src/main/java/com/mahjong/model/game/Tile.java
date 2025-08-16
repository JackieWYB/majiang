package com.mahjong.model.game;

import java.util.Objects;

/**
 * Represents a Mahjong tile with suit and rank properties
 */
public class Tile {
    
    public enum Suit {
        WAN("W"),    // 万 (Characters)
        TIAO("T"),   // 条 (Bamboos) 
        TONG("D");   // 筒 (Dots)
        
        private final String symbol;
        
        Suit(String symbol) {
            this.symbol = symbol;
        }
        
        public String getSymbol() {
            return symbol;
        }
    }
    
    private final Suit suit;
    private final int rank; // 1-9
    
    public Tile(Suit suit, int rank) {
        if (rank < 1 || rank > 9) {
            throw new IllegalArgumentException("Tile rank must be between 1 and 9");
        }
        this.suit = suit;
        this.rank = rank;
    }
    
    public Tile(String tileString) {
        if (tileString == null || tileString.length() != 2) {
            throw new IllegalArgumentException("Tile string must be 2 characters (e.g., '5W')");
        }
        
        char rankChar = tileString.charAt(0);
        char suitChar = tileString.charAt(1);
        
        this.rank = Character.getNumericValue(rankChar);
        if (this.rank < 1 || this.rank > 9) {
            throw new IllegalArgumentException("Invalid rank in tile string: " + rankChar);
        }
        
        switch (suitChar) {
            case 'W':
                this.suit = Suit.WAN;
                break;
            case 'T':
                this.suit = Suit.TIAO;
                break;
            case 'D':
                this.suit = Suit.TONG;
                break;
            default:
                throw new IllegalArgumentException("Invalid suit in tile string: " + suitChar);
        }
    }
    
    public Suit getSuit() {
        return suit;
    }
    
    public int getRank() {
        return rank;
    }
    
    /**
     * Returns string representation of tile (e.g., "5W")
     */
    @Override
    public String toString() {
        return rank + suit.getSymbol();
    }
    
    /**
     * Checks if this tile can form a sequence with two other tiles
     */
    public boolean canFormSequence(Tile tile2, Tile tile3) {
        if (this.suit != tile2.getSuit() || this.suit != tile3.getSuit()) {
            return false;
        }
        
        int[] ranks = {this.rank, tile2.getRank(), tile3.getRank()};
        java.util.Arrays.sort(ranks);
        
        return ranks[1] == ranks[0] + 1 && ranks[2] == ranks[1] + 1;
    }
    
    /**
     * Checks if this tile is consecutive to another tile
     */
    public boolean isConsecutive(Tile other) {
        return this.suit == other.getSuit() && Math.abs(this.rank - other.getRank()) == 1;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Tile tile = (Tile) obj;
        return rank == tile.rank && suit == tile.suit;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(suit, rank);
    }
}