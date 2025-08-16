package com.mahjong.model.game;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MeldSetTest {
    
    @Test
    @DisplayName("Should create valid Peng meld")
    void shouldCreateValidPengMeld() {
        List<Tile> tiles = Arrays.asList(
            new Tile("5W"), new Tile("5W"), new Tile("5W")
        );
        
        MeldSet peng = MeldSet.createPeng(tiles, "player1");
        
        assertEquals(MeldSet.MeldType.PENG, peng.getMeldType());
        assertEquals(3, peng.getTiles().size());
        assertFalse(peng.isConcealed());
        assertEquals("player1", peng.getClaimedFrom());
        assertNull(peng.getGangType());
    }
    
    @Test
    @DisplayName("Should throw exception for invalid Peng")
    void shouldThrowExceptionForInvalidPeng() {
        List<Tile> invalidTiles = Arrays.asList(
            new Tile("5W"), new Tile("6W"), new Tile("7W")
        );
        
        assertThrows(IllegalArgumentException.class, 
            () -> MeldSet.createPeng(invalidTiles, "player1"));
        
        List<Tile> wrongCount = Arrays.asList(
            new Tile("5W"), new Tile("5W")
        );
        
        assertThrows(IllegalArgumentException.class, 
            () -> MeldSet.createPeng(wrongCount, "player1"));
    }
    
    @Test
    @DisplayName("Should create valid Gang melds")
    void shouldCreateValidGangMelds() {
        List<Tile> tiles = Arrays.asList(
            new Tile("5W"), new Tile("5W"), new Tile("5W"), new Tile("5W")
        );
        
        // Ming Gang (claimed from discard)
        MeldSet mingGang = MeldSet.createGang(tiles, MeldSet.GangType.MING_GANG, "player1");
        assertEquals(MeldSet.MeldType.GANG, mingGang.getMeldType());
        assertEquals(MeldSet.GangType.MING_GANG, mingGang.getGangType());
        assertFalse(mingGang.isConcealed());
        assertEquals(4, mingGang.getScoreValue());
        
        // An Gang (concealed)
        MeldSet anGang = MeldSet.createGang(tiles, MeldSet.GangType.AN_GANG, null);
        assertEquals(MeldSet.GangType.AN_GANG, anGang.getGangType());
        assertTrue(anGang.isConcealed());
        assertEquals(8, anGang.getScoreValue());
        
        // Bu Gang (upgrade from Peng)
        MeldSet buGang = MeldSet.createGang(tiles, MeldSet.GangType.BU_GANG, "player1");
        assertEquals(MeldSet.GangType.BU_GANG, buGang.getGangType());
        assertFalse(buGang.isConcealed());
        assertEquals(4, buGang.getScoreValue());
    }
    
    @Test
    @DisplayName("Should create valid Chi meld")
    void shouldCreateValidChiMeld() {
        List<Tile> tiles = Arrays.asList(
            new Tile("5W"), new Tile("6W"), new Tile("7W")
        );
        
        MeldSet chi = MeldSet.createChi(tiles, "player1");
        
        assertEquals(MeldSet.MeldType.CHI, chi.getMeldType());
        assertEquals(3, chi.getTiles().size());
        assertFalse(chi.isConcealed());
        assertEquals("player1", chi.getClaimedFrom());
        assertEquals(0, chi.getScoreValue()); // Chi doesn't contribute to score
        
        // Tiles should be sorted by rank
        List<Tile> sortedTiles = chi.getTiles();
        assertEquals("5W", sortedTiles.get(0).toString());
        assertEquals("6W", sortedTiles.get(1).toString());
        assertEquals("7W", sortedTiles.get(2).toString());
    }
    
    @Test
    @DisplayName("Should throw exception for invalid Chi")
    void shouldThrowExceptionForInvalidChi() {
        // Non-consecutive tiles
        List<Tile> nonConsecutive = Arrays.asList(
            new Tile("5W"), new Tile("7W"), new Tile("9W")
        );
        assertThrows(IllegalArgumentException.class, 
            () -> MeldSet.createChi(nonConsecutive, "player1"));
        
        // Different suits
        List<Tile> differentSuits = Arrays.asList(
            new Tile("5W"), new Tile("6T"), new Tile("7D")
        );
        assertThrows(IllegalArgumentException.class, 
            () -> MeldSet.createChi(differentSuits, "player1"));
        
        // Wrong count
        List<Tile> wrongCount = Arrays.asList(
            new Tile("5W"), new Tile("6W")
        );
        assertThrows(IllegalArgumentException.class, 
            () -> MeldSet.createChi(wrongCount, "player1"));
    }
    
    @Test
    @DisplayName("Should correctly identify upgrade to Gang")
    void shouldIdentifyUpgradeToGang() {
        List<Tile> pengTiles = Arrays.asList(
            new Tile("5W"), new Tile("5W"), new Tile("5W")
        );
        MeldSet peng = MeldSet.createPeng(pengTiles, "player1");
        
        assertTrue(peng.canUpgradeToGang(new Tile("5W")));
        assertFalse(peng.canUpgradeToGang(new Tile("6W")));
        
        // Chi cannot be upgraded
        List<Tile> chiTiles = Arrays.asList(
            new Tile("5W"), new Tile("6W"), new Tile("7W")
        );
        MeldSet chi = MeldSet.createChi(chiTiles, "player1");
        assertFalse(chi.canUpgradeToGang(new Tile("5W")));
    }
    
    @Test
    @DisplayName("Should upgrade Peng to Gang")
    void shouldUpgradePengToGang() {
        List<Tile> pengTiles = Arrays.asList(
            new Tile("5W"), new Tile("5W"), new Tile("5W")
        );
        MeldSet peng = MeldSet.createPeng(pengTiles, "player1");
        
        MeldSet gang = peng.upgradeToGang(new Tile("5W"));
        
        assertEquals(MeldSet.MeldType.GANG, gang.getMeldType());
        assertEquals(MeldSet.GangType.BU_GANG, gang.getGangType());
        assertEquals(4, gang.getTiles().size());
        assertEquals("player1", gang.getClaimedFrom());
        assertFalse(gang.isConcealed());
    }
    
    @Test
    @DisplayName("Should get correct base tile")
    void shouldGetCorrectBaseTile() {
        List<Tile> tiles = Arrays.asList(
            new Tile("5W"), new Tile("5W"), new Tile("5W")
        );
        MeldSet peng = MeldSet.createPeng(tiles, "player1");
        
        assertEquals(new Tile("5W"), peng.getBaseTile());
        
        List<Tile> chiTiles = Arrays.asList(
            new Tile("7W"), new Tile("5W"), new Tile("6W") // Unsorted input
        );
        MeldSet chi = MeldSet.createChi(chiTiles, "player1");
        
        assertEquals(new Tile("5W"), chi.getBaseTile()); // Should be first after sorting
    }
    
    @Test
    @DisplayName("Should calculate correct score values")
    void shouldCalculateCorrectScoreValues() {
        // Peng scores
        List<Tile> pengTiles = Arrays.asList(
            new Tile("5W"), new Tile("5W"), new Tile("5W")
        );
        MeldSet peng = MeldSet.createPeng(pengTiles, "player1");
        assertEquals(1, peng.getScoreValue()); // Open Peng
        
        // Gang scores
        List<Tile> gangTiles = Arrays.asList(
            new Tile("5W"), new Tile("5W"), new Tile("5W"), new Tile("5W")
        );
        
        MeldSet anGang = MeldSet.createGang(gangTiles, MeldSet.GangType.AN_GANG, null);
        assertEquals(8, anGang.getScoreValue());
        
        MeldSet mingGang = MeldSet.createGang(gangTiles, MeldSet.GangType.MING_GANG, "player1");
        assertEquals(4, mingGang.getScoreValue());
        
        MeldSet buGang = MeldSet.createGang(gangTiles, MeldSet.GangType.BU_GANG, "player1");
        assertEquals(4, buGang.getScoreValue());
        
        // Chi score
        List<Tile> chiTiles = Arrays.asList(
            new Tile("5W"), new Tile("6W"), new Tile("7W")
        );
        MeldSet chi = MeldSet.createChi(chiTiles, "player1");
        assertEquals(0, chi.getScoreValue());
    }
    
    @Test
    @DisplayName("Should implement equals and hashCode correctly")
    void shouldImplementEqualsAndHashCode() {
        List<Tile> tiles1 = Arrays.asList(
            new Tile("5W"), new Tile("5W"), new Tile("5W")
        );
        List<Tile> tiles2 = Arrays.asList(
            new Tile("5W"), new Tile("5W"), new Tile("5W")
        );
        List<Tile> tiles3 = Arrays.asList(
            new Tile("6W"), new Tile("6W"), new Tile("6W")
        );
        
        MeldSet peng1 = MeldSet.createPeng(tiles1, "player1");
        MeldSet peng2 = MeldSet.createPeng(tiles2, "player1");
        MeldSet peng3 = MeldSet.createPeng(tiles3, "player1");
        
        assertEquals(peng1, peng2);
        assertNotEquals(peng1, peng3);
        assertEquals(peng1.hashCode(), peng2.hashCode());
    }
    
    @Test
    @DisplayName("Should generate correct string representation")
    void shouldGenerateCorrectStringRepresentation() {
        List<Tile> pengTiles = Arrays.asList(
            new Tile("5W"), new Tile("5W"), new Tile("5W")
        );
        MeldSet peng = MeldSet.createPeng(pengTiles, "player1");
        assertTrue(peng.toString().contains("PENG"));
        assertTrue(peng.toString().contains("5W"));
        
        List<Tile> gangTiles = Arrays.asList(
            new Tile("5W"), new Tile("5W"), new Tile("5W"), new Tile("5W")
        );
        MeldSet gang = MeldSet.createGang(gangTiles, MeldSet.GangType.AN_GANG, null);
        assertTrue(gang.toString().contains("GANG"));
        assertTrue(gang.toString().contains("AN_GANG"));
    }
}