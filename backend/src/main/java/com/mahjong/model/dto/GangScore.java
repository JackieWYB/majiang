package com.mahjong.model.dto;

import com.mahjong.model.game.MeldSet;

/**
 * Represents Gang bonus scoring between players
 */
public class GangScore {
    
    private String fromUserId;
    private String toUserId;
    private MeldSet.GangType gangType;
    private String tile;
    private int bonusPoints;
    private String description;
    
    public GangScore() {}
    
    public GangScore(String fromUserId, String toUserId, MeldSet.GangType gangType, String tile, int bonusPoints) {
        this.fromUserId = fromUserId;
        this.toUserId = toUserId;
        this.gangType = gangType;
        this.tile = tile;
        this.bonusPoints = bonusPoints;
        this.description = generateDescription();
    }
    
    private String generateDescription() {
        String gangTypeName = switch (gangType) {
            case AN_GANG -> "暗杠";
            case MING_GANG -> "明杠";
            case BU_GANG -> "补杠";
        };
        return String.format("%s %s (+%d分)", gangTypeName, tile, bonusPoints);
    }
    
    // Getters and Setters
    public String getFromUserId() {
        return fromUserId;
    }
    
    public void setFromUserId(String fromUserId) {
        this.fromUserId = fromUserId;
    }
    
    public String getToUserId() {
        return toUserId;
    }
    
    public void setToUserId(String toUserId) {
        this.toUserId = toUserId;
    }
    
    public MeldSet.GangType getGangType() {
        return gangType;
    }
    
    public void setGangType(MeldSet.GangType gangType) {
        this.gangType = gangType;
    }
    
    public String getTile() {
        return tile;
    }
    
    public void setTile(String tile) {
        this.tile = tile;
    }
    
    public int getBonusPoints() {
        return bonusPoints;
    }
    
    public void setBonusPoints(int bonusPoints) {
        this.bonusPoints = bonusPoints;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
}