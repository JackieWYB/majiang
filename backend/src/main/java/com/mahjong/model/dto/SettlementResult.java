package com.mahjong.model.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Represents the settlement result of a completed Mahjong game
 */
public class SettlementResult {
    
    private String gameId;
    private String roomId;
    private LocalDateTime settlementTime;
    private List<PlayerResult> playerResults;
    private List<GangScore> gangScores;
    private Map<String, Integer> finalScores;
    private boolean isMultipleWinners;
    private String gameEndReason; // WIN, DRAW, TIMEOUT
    
    public SettlementResult() {}
    
    public SettlementResult(String gameId, String roomId) {
        this.gameId = gameId;
        this.roomId = roomId;
        this.settlementTime = LocalDateTime.now();
    }
    
    // Getters and Setters
    public String getGameId() {
        return gameId;
    }
    
    public void setGameId(String gameId) {
        this.gameId = gameId;
    }
    
    public String getRoomId() {
        return roomId;
    }
    
    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }
    
    public LocalDateTime getSettlementTime() {
        return settlementTime;
    }
    
    public void setSettlementTime(LocalDateTime settlementTime) {
        this.settlementTime = settlementTime;
    }
    
    public List<PlayerResult> getPlayerResults() {
        return playerResults;
    }
    
    public void setPlayerResults(List<PlayerResult> playerResults) {
        this.playerResults = playerResults;
    }
    
    public List<GangScore> getGangScores() {
        return gangScores;
    }
    
    public void setGangScores(List<GangScore> gangScores) {
        this.gangScores = gangScores;
    }
    
    public Map<String, Integer> getFinalScores() {
        return finalScores;
    }
    
    public void setFinalScores(Map<String, Integer> finalScores) {
        this.finalScores = finalScores;
    }
    
    public boolean isMultipleWinners() {
        return isMultipleWinners;
    }
    
    public void setMultipleWinners(boolean multipleWinners) {
        isMultipleWinners = multipleWinners;
    }
    
    public String getGameEndReason() {
        return gameEndReason;
    }
    
    public void setGameEndReason(String gameEndReason) {
        this.gameEndReason = gameEndReason;
    }
}