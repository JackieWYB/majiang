package com.mahjong.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO for Peng action messages
 */
public class PengActionDto {
    
    @JsonProperty("tile")
    private String tile;
    
    @JsonProperty("claimedFrom")
    private String claimedFrom;
    
    @JsonProperty("discardTile")
    private String discardTile; // Tile to discard after claiming
    
    public PengActionDto() {}
    
    public PengActionDto(String tile, String claimedFrom) {
        this.tile = tile;
        this.claimedFrom = claimedFrom;
    }
    
    public PengActionDto(String tile, String claimedFrom, String discardTile) {
        this.tile = tile;
        this.claimedFrom = claimedFrom;
        this.discardTile = discardTile;
    }
    
    public String getTile() {
        return tile;
    }
    
    public void setTile(String tile) {
        this.tile = tile;
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
        return String.format("PengActionDto{tile='%s', claimedFrom='%s', discardTile='%s'}", 
                tile, claimedFrom, discardTile);
    }
}