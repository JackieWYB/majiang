package com.mahjong.model.game;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

class TileTest {
    
    @Test
    @DisplayName("Should create tile with valid suit and rank")
    void shouldCreateTileWithValidSuitAndRank() {
        Tile tile = new Tile(Tile.Suit.WAN, 5);
        
        assertEquals(Tile.Suit.WAN, tile.getSuit());
        assertEquals(5, tile.getRank());
        assertEquals("5W", tile.toString());
    }
    
    @Test
    @DisplayName("Should create tile from string representation")
    void shouldCreateTileFromString() {
        Tile tile = new Tile("7T");
        
        assertEquals(Tile.Suit.TIAO, tile.getSuit());
        assertEquals(7, tile.getRank());
        assertEquals("7T", tile.toString());
    }
    
    @Test
    @DisplayName("Should throw exception for invalid rank")
    void shouldThrowExceptionForInvalidRank() {
        assertThrows(IllegalArgumentException.class, () -> new Tile(Tile.Suit.WAN, 0));
        assertThrows(IllegalArgumentException.class, () -> new Tile(Tile.Suit.WAN, 10));
        assertThrows(IllegalArgumentException.class, () -> new Tile("0W"));
        assertThrows(IllegalArgumentException.class, () -> new Tile("10W"));
    }
    
    @Test
    @DisplayName("Should throw exception for invalid tile string")
    void shouldThrowExceptionForInvalidTileString() {
        assertThrows(IllegalArgumentException.class, () -> new Tile("5"));
        assertThrows(IllegalArgumentException.class, () -> new Tile("5X"));
        assertThrows(IllegalArgumentException.class, () -> new Tile("ABC"));
        assertThrows(IllegalArgumentException.class, () -> new Tile(null));
    }
    
    @Test
    @DisplayName("Should correctly identify consecutive tiles")
    void shouldIdentifyConsecutiveTiles() {
        Tile tile1 = new Tile("5W");
        Tile tile2 = new Tile("6W");
        Tile tile3 = new Tile("4W");
        Tile tile4 = new Tile("5T");
        
        assertTrue(tile1.isConsecutive(tile2));
        assertTrue(tile1.isConsecutive(tile3));
        assertFalse(tile1.isConsecutive(tile4)); // Different suit
        assertFalse(tile1.isConsecutive(new Tile("7W"))); // Not consecutive
    }
    
    @Test
    @DisplayName("Should correctly validate sequence formation")
    void shouldValidateSequenceFormation() {
        Tile tile1 = new Tile("5W");
        Tile tile2 = new Tile("6W");
        Tile tile3 = new Tile("7W");
        Tile tile4 = new Tile("5T");
        
        assertTrue(tile1.canFormSequence(tile2, tile3));
        assertTrue(tile2.canFormSequence(tile1, tile3));
        assertTrue(tile3.canFormSequence(tile1, tile2));
        
        assertFalse(tile1.canFormSequence(tile2, tile4)); // Different suit
        assertFalse(tile1.canFormSequence(tile2, new Tile("8W"))); // Not consecutive
    }
    
    @Test
    @DisplayName("Should handle edge cases for sequence formation")
    void shouldHandleEdgeCasesForSequenceFormation() {
        Tile tile1 = new Tile("1W");
        Tile tile2 = new Tile("2W");
        Tile tile3 = new Tile("3W");
        
        Tile tile7 = new Tile("7W");
        Tile tile8 = new Tile("8W");
        Tile tile9 = new Tile("9W");
        
        assertTrue(tile1.canFormSequence(tile2, tile3));
        assertTrue(tile7.canFormSequence(tile8, tile9));
        
        // Cannot form sequence wrapping around (9-1-2)
        assertFalse(tile9.canFormSequence(tile1, tile2));
    }
    
    @Test
    @DisplayName("Should correctly implement equals and hashCode")
    void shouldImplementEqualsAndHashCode() {
        Tile tile1 = new Tile("5W");
        Tile tile2 = new Tile("5W");
        Tile tile3 = new Tile("6W");
        Tile tile4 = new Tile("5T");
        
        assertEquals(tile1, tile2);
        assertNotEquals(tile1, tile3);
        assertNotEquals(tile1, tile4);
        
        assertEquals(tile1.hashCode(), tile2.hashCode());
        assertNotEquals(tile1.hashCode(), tile3.hashCode());
    }
    
    @Test
    @DisplayName("Should handle all suit types correctly")
    void shouldHandleAllSuitTypes() {
        Tile wan = new Tile("5W");
        Tile tiao = new Tile("5T");
        Tile tong = new Tile("5D");
        
        assertEquals(Tile.Suit.WAN, wan.getSuit());
        assertEquals(Tile.Suit.TIAO, tiao.getSuit());
        assertEquals(Tile.Suit.TONG, tong.getSuit());
        
        assertEquals("W", wan.getSuit().getSymbol());
        assertEquals("T", tiao.getSuit().getSymbol());
        assertEquals("D", tong.getSuit().getSymbol());
    }
    
    @Test
    @DisplayName("Should handle all valid ranks")
    void shouldHandleAllValidRanks() {
        for (int rank = 1; rank <= 9; rank++) {
            Tile tile = new Tile(Tile.Suit.WAN, rank);
            assertEquals(rank, tile.getRank());
            assertEquals(rank + "W", tile.toString());
        }
    }
}