package com.mahjong.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * DTO for game replay response
 */
public class GameReplayResponse {
    
    @JsonProperty("gameId")
    private String gameId;
    
    @JsonProperty("roomId")
    private String roomId;
    
    @JsonProperty("successful")
    private boolean successful;
    
    @JsonProperty("errorMessage")
    private String errorMessage;
    
    @JsonProperty("metadata")
    private Map<String, Object> metadata;
    
    @JsonProperty("totalSteps")
    private int totalSteps;
    
    @JsonProperty("steps")
    private List<ReplayStepDto> steps;
    
    public GameReplayResponse() {}
    
    public GameReplayResponse(String gameId, String roomId, boolean successful) {
        this.gameId = gameId;
        this.roomId = roomId;
        this.successful = successful;
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
    
    public boolean isSuccessful() {
        return successful;
    }
    
    public void setSuccessful(boolean successful) {
        this.successful = successful;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    
    public Map<String, Object> getMetadata() {
        return metadata;
    }
    
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
    
    public int getTotalSteps() {
        return totalSteps;
    }
    
    public void setTotalSteps(int totalSteps) {
        this.totalSteps = totalSteps;
    }
    
    public List<ReplayStepDto> getSteps() {
        return steps;
    }
    
    public void setSteps(List<ReplayStepDto> steps) {
        this.steps = steps;
    }
    
    /**
     * DTO for individual replay steps
     */
    public static class ReplayStepDto {
        @JsonProperty("stepNumber")
        private int stepNumber;
        
        @JsonProperty("action")
        private GameAction action;
        
        @JsonProperty("gameSnapshot")
        private GameSnapshot gameSnapshot;
        
        @JsonProperty("description")
        private String description;
        
        @JsonProperty("timestamp")
        private LocalDateTime timestamp;
        
        public ReplayStepDto() {}
        
        public ReplayStepDto(int stepNumber, GameAction action, GameSnapshot gameSnapshot, String description) {
            this.stepNumber = stepNumber;
            this.action = action;
            this.gameSnapshot = gameSnapshot;
            this.description = description;
            this.timestamp = action != null ? action.getTimestamp() : LocalDateTime.now();
        }
        
        // Getters and Setters
        public int getStepNumber() {
            return stepNumber;
        }
        
        public void setStepNumber(int stepNumber) {
            this.stepNumber = stepNumber;
        }
        
        public GameAction getAction() {
            return action;
        }
        
        public void setAction(GameAction action) {
            this.action = action;
        }
        
        public GameSnapshot getGameSnapshot() {
            return gameSnapshot;
        }
        
        public void setGameSnapshot(GameSnapshot gameSnapshot) {
            this.gameSnapshot = gameSnapshot;
        }
        
        public String getDescription() {
            return description;
        }
        
        public void setDescription(String description) {
            this.description = description;
        }
        
        public LocalDateTime getTimestamp() {
            return timestamp;
        }
        
        public void setTimestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp;
        }
    }
}