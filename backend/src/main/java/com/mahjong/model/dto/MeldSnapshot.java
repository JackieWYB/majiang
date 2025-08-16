package com.mahjong.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Meld (Peng/Gang/Chi) snapshot for game state
 */
public class MeldSnapshot {
    
    public enum MeldType {
        PENG,    // Three identical tiles
        GANG,    // Four identical tiles
        CHI      // Three consecutive tiles
    }
    
    @JsonProperty("type")
    private MeldType type;
    
    @JsonProperty("tiles")
    private List<String> tiles;
    
    @JsonProperty("isConcealed")
    private Boolean isConcealed; // true for concealed gang
    
    @JsonProperty("claimedFrom")
    private String claimedFrom; // user ID who provided the claimed tile
    
    @JsonProperty("claimedTile")
    private String claimedTile; // which tile was claimed
    
    public MeldSnapshot() {}
    
    public MeldSnapshot(MeldType type, List<String> tiles) {
        this.type = type;
        this.tiles = tiles;
        this.isConcealed = false;
    }
    
    public MeldSnapshot(MeldType type, List<String> tiles, boolean isConcealed) {
        this.type = type;
        this.tiles = tiles;
        this.isConcealed = isConcealed;
    }
    
    public MeldType getType() {
        return type;
    }
    
    public void setType(MeldType type) {
        this.type = type;
    }
    
    public List<String> getTiles() {
        return tiles;
    }
    
    public void setTiles(List<String> tiles) {
        this.tiles = tiles;
    }
    
    public Boolean getIsConcealed() {
        return isConcealed;
    }
    
    public void setIsConcealed(Boolean isConcealed) {
        this.isConcealed = isConcealed;
    }
    
    public String getClaimedFrom() {
        return claimedFrom;
    }
    
    public void setClaimedFrom(String claimedFrom) {
        this.claimedFrom = claimedFrom;
    }
    
    public String getClaimedTile() {
        return claimedTile;
    }
    
    public void setClaimedTile(String claimedTile) {
        this.claimedTile = claimedTile;
    }
    
    @Override
    public String toString() {
        return String.format("MeldSnapshot{type=%s, tiles=%s, concealed=%s}", 
                type, tiles, isConcealed);
    }
}