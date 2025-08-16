package com.mahjong.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO for Hu (Win) action messages
 */
public class HuActionDto {
    
    @JsonProperty("winningTile")
    private String winningTile;
    
    @JsonProperty("selfDraw")
    private boolean selfDraw; // true if won by drawing, false if won by claiming
    
    @JsonProperty("claimedFrom")
    private String claimedFrom; // user ID who discarded the winning tile (null for self-draw)
    
    public HuActionDto() {}
    
    public HuActionDto(String winningTile, boolean selfDraw) {
        this.winningTile = winningTile;
        this.selfDraw = selfDraw;
    }
    
    public HuActionDto(String winningTile, boolean selfDraw, String claimedFrom) {
        this.winningTile = winningTile;
        this.selfDraw = selfDraw;
        this.claimedFrom = claimedFrom;
    }
    
    public String getWinningTile() {
        return winningTile;
    }
    
    public void setWinningTile(String winningTile) {
        this.winningTile = winningTile;
    }
    
    public boolean isSelfDraw() {
        return selfDraw;
    }
    
    public void setSelfDraw(boolean selfDraw) {
        this.selfDraw = selfDraw;
    }
    
    public String getClaimedFrom() {
        return claimedFrom;
    }
    
    public void setClaimedFrom(String claimedFrom) {
        this.claimedFrom = claimedFrom;
    }
    
    @Override
    public String toString() {
        return String.format("HuActionDto{winningTile='%s', selfDraw=%s, claimedFrom='%s'}", 
                winningTile, selfDraw, claimedFrom);
    }
}