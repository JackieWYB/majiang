package com.mahjong.model.dto;

import java.util.List;

/**
 * Represents a player's result in a game settlement
 */
public class PlayerResult {
    
    private String userId;
    private int seatIndex;
    private boolean isWinner;
    private boolean isDealer;
    private boolean isSelfDraw;
    private int baseScore;
    private int fanMultiplier;
    private double dealerMultiplier;
    private double selfDrawBonus;
    private int gangBonus;
    private int finalScore;
    private int cappedScore;
    private List<String> handTypes; // Special hand types achieved
    private List<String> scoreBreakdown; // Detailed score calculation
    
    public PlayerResult() {}
    
    public PlayerResult(String userId, int seatIndex) {
        this.userId = userId;
        this.seatIndex = seatIndex;
        this.isWinner = false;
        this.isDealer = false;
        this.isSelfDraw = false;
        this.baseScore = 0;
        this.fanMultiplier = 1;
        this.dealerMultiplier = 1.0;
        this.selfDrawBonus = 0.0;
        this.gangBonus = 0;
        this.finalScore = 0;
        this.cappedScore = 0;
    }
    
    // Getters and Setters
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
    }
    
    public int getSeatIndex() {
        return seatIndex;
    }
    
    public void setSeatIndex(int seatIndex) {
        this.seatIndex = seatIndex;
    }
    
    public boolean isWinner() {
        return isWinner;
    }
    
    public void setWinner(boolean winner) {
        isWinner = winner;
    }
    
    public boolean isDealer() {
        return isDealer;
    }
    
    public void setDealer(boolean dealer) {
        isDealer = dealer;
    }
    
    public boolean isSelfDraw() {
        return isSelfDraw;
    }
    
    public void setSelfDraw(boolean selfDraw) {
        isSelfDraw = selfDraw;
    }
    
    public int getBaseScore() {
        return baseScore;
    }
    
    public void setBaseScore(int baseScore) {
        this.baseScore = baseScore;
    }
    
    public int getFanMultiplier() {
        return fanMultiplier;
    }
    
    public void setFanMultiplier(int fanMultiplier) {
        this.fanMultiplier = fanMultiplier;
    }
    
    public double getDealerMultiplier() {
        return dealerMultiplier;
    }
    
    public void setDealerMultiplier(double dealerMultiplier) {
        this.dealerMultiplier = dealerMultiplier;
    }
    
    public double getSelfDrawBonus() {
        return selfDrawBonus;
    }
    
    public void setSelfDrawBonus(double selfDrawBonus) {
        this.selfDrawBonus = selfDrawBonus;
    }
    
    public int getGangBonus() {
        return gangBonus;
    }
    
    public void setGangBonus(int gangBonus) {
        this.gangBonus = gangBonus;
    }
    
    public int getFinalScore() {
        return finalScore;
    }
    
    public void setFinalScore(int finalScore) {
        this.finalScore = finalScore;
    }
    
    public int getCappedScore() {
        return cappedScore;
    }
    
    public void setCappedScore(int cappedScore) {
        this.cappedScore = cappedScore;
    }
    
    public List<String> getHandTypes() {
        return handTypes;
    }
    
    public void setHandTypes(List<String> handTypes) {
        this.handTypes = handTypes;
    }
    
    public List<String> getScoreBreakdown() {
        return scoreBreakdown;
    }
    
    public void setScoreBreakdown(List<String> scoreBreakdown) {
        this.scoreBreakdown = scoreBreakdown;
    }
}