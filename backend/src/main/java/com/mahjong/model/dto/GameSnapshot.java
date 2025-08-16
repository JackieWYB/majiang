package com.mahjong.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * Complete game state snapshot for client synchronization
 */
public class GameSnapshot {
    
    @JsonProperty("roomId")
    private String roomId;
    
    @JsonProperty("gameId")
    private String gameId;
    
    @JsonProperty("players")
    private List<PlayerSnapshot> players;
    
    @JsonProperty("discardPile")
    private List<String> discardPile;
    
    @JsonProperty("remainingTiles")
    private Integer remainingTiles;
    
    @JsonProperty("currentPlayerIndex")
    private Integer currentPlayerIndex;
    
    @JsonProperty("currentPlayerId")
    private String currentPlayerId;
    
    @JsonProperty("turnDeadline")
    private Long turnDeadline;
    
    @JsonProperty("availableActions")
    private List<String> availableActions;
    
    @JsonProperty("gamePhase")
    private String gamePhase;
    
    @JsonProperty("dealerId")
    private String dealerId;
    
    @JsonProperty("roundIndex")
    private Integer roundIndex;
    
    @JsonProperty("maxRounds")
    private Integer maxRounds;
    
    @JsonProperty("lastAction")
    private Map<String, Object> lastAction;
    
    @JsonProperty("config")
    private Map<String, Object> config;
    
    public GameSnapshot() {}
    
    public String getRoomId() {
        return roomId;
    }
    
    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }
    
    public String getGameId() {
        return gameId;
    }
    
    public void setGameId(String gameId) {
        this.gameId = gameId;
    }
    
    public List<PlayerSnapshot> getPlayers() {
        return players;
    }
    
    public void setPlayers(List<PlayerSnapshot> players) {
        this.players = players;
    }
    
    public List<String> getDiscardPile() {
        return discardPile;
    }
    
    public void setDiscardPile(List<String> discardPile) {
        this.discardPile = discardPile;
    }
    
    public Integer getRemainingTiles() {
        return remainingTiles;
    }
    
    public void setRemainingTiles(Integer remainingTiles) {
        this.remainingTiles = remainingTiles;
    }
    
    public Integer getCurrentPlayerIndex() {
        return currentPlayerIndex;
    }
    
    public void setCurrentPlayerIndex(Integer currentPlayerIndex) {
        this.currentPlayerIndex = currentPlayerIndex;
    }
    
    public String getCurrentPlayerId() {
        return currentPlayerId;
    }
    
    public void setCurrentPlayerId(String currentPlayerId) {
        this.currentPlayerId = currentPlayerId;
    }
    
    public Long getTurnDeadline() {
        return turnDeadline;
    }
    
    public void setTurnDeadline(Long turnDeadline) {
        this.turnDeadline = turnDeadline;
    }
    
    public List<String> getAvailableActions() {
        return availableActions;
    }
    
    public void setAvailableActions(List<String> availableActions) {
        this.availableActions = availableActions;
    }
    
    public String getGamePhase() {
        return gamePhase;
    }
    
    public void setGamePhase(String gamePhase) {
        this.gamePhase = gamePhase;
    }
    
    public String getDealerId() {
        return dealerId;
    }
    
    public void setDealerId(String dealerId) {
        this.dealerId = dealerId;
    }
    
    public Integer getRoundIndex() {
        return roundIndex;
    }
    
    public void setRoundIndex(Integer roundIndex) {
        this.roundIndex = roundIndex;
    }
    
    public Integer getMaxRounds() {
        return maxRounds;
    }
    
    public void setMaxRounds(Integer maxRounds) {
        this.maxRounds = maxRounds;
    }
    
    public Map<String, Object> getLastAction() {
        return lastAction;
    }
    
    public void setLastAction(Map<String, Object> lastAction) {
        this.lastAction = lastAction;
    }
    
    public Map<String, Object> getConfig() {
        return config;
    }
    
    public void setConfig(Map<String, Object> config) {
        this.config = config;
    }
    
    @Override
    public String toString() {
        return String.format("GameSnapshot{roomId='%s', gameId='%s', currentPlayer=%d, phase='%s'}", 
                roomId, gameId, currentPlayerIndex, gamePhase);
    }
}