package com.mahjong.model.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents a meld set in Mahjong (Peng, Gang, or Chi)
 */
public class MeldSet {
    
    public enum MeldType {
        PENG,    // 碰 - Three identical tiles
        GANG,    // 杠 - Four identical tiles
        CHI      // 吃 - Three consecutive tiles of same suit
    }
    
    public enum GangType {
        MING_GANG,  // 明杠 - Open Gang (claimed from discard)
        AN_GANG,    // 暗杠 - Concealed Gang (from hand)
        BU_GANG     // 补杠 - Added Gang (add 4th tile to existing Peng)
    }
    
    private final MeldType meldType;
    private final List<Tile> tiles;
    private final GangType gangType; // Only relevant for GANG melds
    private final boolean isConcealed;
    private final String claimedFrom; // User ID who discarded the claimed tile (null if from hand)
    
    // Constructor for Peng
    public MeldSet(List<Tile> tiles, String claimedFrom) {
        if (tiles.size() != 3) {
            throw new IllegalArgumentException("Peng must have exactly 3 tiles");
        }
        if (!isValidPeng(tiles)) {
            throw new IllegalArgumentException("Invalid Peng: tiles must be identical");
        }
        
        this.meldType = MeldType.PENG;
        this.tiles = new ArrayList<>(tiles);
        this.gangType = null;
        this.isConcealed = false;
        this.claimedFrom = claimedFrom;
    }
    
    // Constructor for Gang
    public MeldSet(List<Tile> tiles, GangType gangType, String claimedFrom) {
        if (tiles.size() != 4) {
            throw new IllegalArgumentException("Gang must have exactly 4 tiles");
        }
        if (!isValidGang(tiles)) {
            throw new IllegalArgumentException("Invalid Gang: tiles must be identical");
        }
        
        this.meldType = MeldType.GANG;
        this.tiles = new ArrayList<>(tiles);
        this.gangType = gangType;
        this.isConcealed = (gangType == GangType.AN_GANG);
        this.claimedFrom = claimedFrom;
    }
    
    // Constructor for Chi
    public MeldSet(List<Tile> tiles, String claimedFrom, boolean isChi) {
        if (!isChi) {
            throw new IllegalArgumentException("Use other constructors for non-Chi melds");
        }
        if (tiles.size() != 3) {
            throw new IllegalArgumentException("Chi must have exactly 3 tiles");
        }
        if (!isValidChi(tiles)) {
            throw new IllegalArgumentException("Invalid Chi: tiles must be consecutive of same suit");
        }
        
        this.meldType = MeldType.CHI;
        this.tiles = new ArrayList<>(tiles);
        Collections.sort(this.tiles, (t1, t2) -> Integer.compare(t1.getRank(), t2.getRank()));
        this.gangType = null;
        this.isConcealed = false;
        this.claimedFrom = claimedFrom;
    }
    
    // Static factory methods
    public static MeldSet createPeng(List<Tile> tiles, String claimedFrom) {
        return new MeldSet(tiles, claimedFrom);
    }
    
    public static MeldSet createGang(List<Tile> tiles, GangType gangType, String claimedFrom) {
        return new MeldSet(tiles, gangType, claimedFrom);
    }
    
    public static MeldSet createChi(List<Tile> tiles, String claimedFrom) {
        return new MeldSet(tiles, claimedFrom, true);
    }
    
    // Validation methods
    private boolean isValidPeng(List<Tile> tiles) {
        if (tiles.size() != 3) return false;
        Tile first = tiles.get(0);
        return tiles.stream().allMatch(tile -> tile.equals(first));
    }
    
    private boolean isValidGang(List<Tile> tiles) {
        if (tiles.size() != 4) return false;
        Tile first = tiles.get(0);
        return tiles.stream().allMatch(tile -> tile.equals(first));
    }
    
    private boolean isValidChi(List<Tile> tiles) {
        if (tiles.size() != 3) return false;
        
        // Check if all tiles are same suit
        Tile.Suit suit = tiles.get(0).getSuit();
        if (!tiles.stream().allMatch(tile -> tile.getSuit() == suit)) {
            return false;
        }
        
        // Sort by rank and check if consecutive
        List<Integer> ranks = tiles.stream()
                .map(Tile::getRank)
                .sorted()
                .toList();
        
        return ranks.get(1) == ranks.get(0) + 1 && ranks.get(2) == ranks.get(1) + 1;
    }
    
    /**
     * Checks if this meld can be upgraded to a Gang by adding a tile
     */
    public boolean canUpgradeToGang(Tile tile) {
        if (meldType != MeldType.PENG) return false;
        return tiles.get(0).equals(tile);
    }
    
    /**
     * Upgrades a Peng to a Bu Gang by adding the fourth tile
     */
    public MeldSet upgradeToGang(Tile tile) {
        if (!canUpgradeToGang(tile)) {
            throw new IllegalArgumentException("Cannot upgrade this meld to Gang with the given tile");
        }
        
        List<Tile> newTiles = new ArrayList<>(tiles);
        newTiles.add(tile);
        return new MeldSet(newTiles, GangType.BU_GANG, claimedFrom);
    }
    
    /**
     * Gets the base tile of this meld (for scoring purposes)
     */
    public Tile getBaseTile() {
        return tiles.get(0);
    }
    
    /**
     * Calculates the score contribution of this meld
     */
    public int getScoreValue() {
        switch (meldType) {
            case PENG:
                return isConcealed ? 2 : 1;
            case GANG:
                switch (gangType) {
                    case AN_GANG:
                        return 8;
                    case MING_GANG:
                        return 4;
                    case BU_GANG:
                        return 4;
                    default:
                        return 0;
                }
            case CHI:
                return 0; // Chi doesn't contribute to base score
            default:
                return 0;
        }
    }
    
    // Getters
    public MeldType getMeldType() {
        return meldType;
    }
    
    public List<Tile> getTiles() {
        return new ArrayList<>(tiles);
    }
    
    public GangType getGangType() {
        return gangType;
    }
    
    public boolean isConcealed() {
        return isConcealed;
    }
    
    public String getClaimedFrom() {
        return claimedFrom;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        MeldSet meldSet = (MeldSet) obj;
        return isConcealed == meldSet.isConcealed &&
                meldType == meldSet.meldType &&
                Objects.equals(tiles, meldSet.tiles) &&
                gangType == meldSet.gangType &&
                Objects.equals(claimedFrom, meldSet.claimedFrom);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(meldType, tiles, gangType, isConcealed, claimedFrom);
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(meldType.name());
        if (gangType != null) {
            sb.append("(").append(gangType.name()).append(")");
        }
        sb.append(": ");
        tiles.forEach(tile -> sb.append(tile.toString()).append(" "));
        return sb.toString().trim();
    }
}