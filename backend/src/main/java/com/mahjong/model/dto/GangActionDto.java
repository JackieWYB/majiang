package com.mahjong.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO for Gang action messages
 */
public class GangActionDto {
    
    public enum GangType {
        MING,    // Exposed Gang (claimed from discard)
        AN,      // Concealed Gang (from hand)
        BU       // Upgrade Gang (upgrade Peng to Gang)
    }
    
    @JsonProperty("tile")
    private String tile;
    
    @JsonProperty("gangType")
    private GangType gangType;
    
    @JsonProperty("claimedFrom")
    private String claimedFrom; // null for concealed gang
    
    @JsonProperty("discardTile")
    private String discardTile; // Tile to discard after drawing replacement
    
    public GangActionDto() {}
    
    public GangActionDto(String tile, GangType gangType) {
        this.tile = tile;
        this.gangType = gangType;
    }
    
    public GangActionDto(String tile, GangType gangType, String claimedFrom) {
        this.tile = tile;
        this.gangType = gangType;
        this.claimedFrom = claimedFrom;
    }
    
    public String getTile() {
        return tile;
    }
    
    public void setTile(String tile) {
        this.tile = tile;
    }
    
    public GangType getGangType() {
        return gangType;
    }
    
    public void setGangType(GangType gangType) {
        this.gangType = gangType;
    }
    
    public String getClaimedFrom() {
        return claimedFrom;
    }
    
    public void setClaimedFrom(String claimedFrom) {
        this.claimedFrom = claimedFrom;
    }
    
    public String getDiscardTile() {
        return discardTile;
    }
    
    public void setDiscardTile(String discardTile) {
        this.discardTile = discardTile;
    }
    
    @Override
    public String toString() {
        return String.format("GangActionDto{tile='%s', type=%s, claimedFrom='%s', discardTile='%s'}", 
                tile, gangType, claimedFrom, discardTile);
    }
}