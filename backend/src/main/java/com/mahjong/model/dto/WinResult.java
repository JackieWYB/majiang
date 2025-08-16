package com.mahjong.model.dto;

import com.mahjong.model.game.Tile;
import java.util.List;

/**
 * Represents the result of a winning hand analysis
 */
public class WinResult {
    
    private String userId;
    private boolean isValid;
    private boolean isSelfDraw;
    private Tile winningTile;
    private String winningFrom; // User ID who discarded the winning tile (null for self-draw)
    private int baseFan;
    private List<String> handTypes;
    private List<String> fanSources; // Sources of fan points
    private String winPattern; // Description of winning pattern
    
    public WinResult() {}
    
    public WinResult(String userId) {
        this.userId = userId;
        this.isValid = false;
        this.isSelfDraw = false;
        this.baseFan = 1;
    }
    
    // Getters and Setters
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
    }
    
    public boolean isValid() {
        return isValid;
    }
    
    public void setValid(boolean valid) {
        isValid = valid;
    }
    
    public boolean isSelfDraw() {
        return isSelfDraw;
    }
    
    public void setSelfDraw(boolean selfDraw) {
        isSelfDraw = selfDraw;
    }
    
    public Tile getWinningTile() {
        return winningTile;
    }
    
    public void setWinningTile(Tile winningTile) {
        this.winningTile = winningTile;
    }
    
    public String getWinningFrom() {
        return winningFrom;
    }
    
    public void setWinningFrom(String winningFrom) {
        this.winningFrom = winningFrom;
    }
    
    public int getBaseFan() {
        return baseFan;
    }
    
    public void setBaseFan(int baseFan) {
        this.baseFan = baseFan;
    }
    
    public List<String> getHandTypes() {
        return handTypes;
    }
    
    public void setHandTypes(List<String> handTypes) {
        this.handTypes = handTypes;
    }
    
    public List<String> getFanSources() {
        return fanSources;
    }
    
    public void setFanSources(List<String> fanSources) {
        this.fanSources = fanSources;
    }
    
    public String getWinPattern() {
        return winPattern;
    }
    
    public void setWinPattern(String winPattern) {
        this.winPattern = winPattern;
    }
}