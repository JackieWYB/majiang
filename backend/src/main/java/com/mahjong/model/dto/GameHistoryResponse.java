package com.mahjong.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mahjong.model.enums.GameResult;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO for game history response
 */
public class GameHistoryResponse {
    
    @JsonProperty("gameId")
    private String gameId;
    
    @JsonProperty("roomId")
    private String roomId;
    
    @JsonProperty("roundIndex")
    private Integer roundIndex;
    
    @JsonProperty("result")
    private GameResult result;
    
    @JsonProperty("winnerId")
    private Long winnerId;
    
    @JsonProperty("winnerNickname")
    private String winnerNickname;
    
    @JsonProperty("durationSeconds")
    private Integer durationSeconds;
    
    @JsonProperty("formattedDuration")
    private String formattedDuration;
    
    @JsonProperty("winningTile")
    private String winningTile;
    
    @JsonProperty("winningType")
    private String winningType;
    
    @JsonProperty("finalScore")
    private Integer finalScore;
    
    @JsonProperty("dealerUserId")
    private Long dealerUserId;
    
    @JsonProperty("dealerNickname")
    private String dealerNickname;
    
    @JsonProperty("createdAt")
    private LocalDateTime createdAt;
    
    @JsonProperty("playerResults")
    private List<PlayerResultDto> playerResults;
    
    @JsonProperty("hasReplayData")
    private boolean hasReplayData;
    
    public GameHistoryResponse() {}
    
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
    
    public Integer getRoundIndex() {
        return roundIndex;
    }
    
    public void setRoundIndex(Integer roundIndex) {
        this.roundIndex = roundIndex;
    }
    
    public GameResult getResult() {
        return result;
    }
    
    public void setResult(GameResult result) {
        this.result = result;
    }
    
    public Long getWinnerId() {
        return winnerId;
    }
    
    public void setWinnerId(Long winnerId) {
        this.winnerId = winnerId;
    }
    
    public String getWinnerNickname() {
        return winnerNickname;
    }
    
    public void setWinnerNickname(String winnerNickname) {
        this.winnerNickname = winnerNickname;
    }
    
    public Integer getDurationSeconds() {
        return durationSeconds;
    }
    
    public void setDurationSeconds(Integer durationSeconds) {
        this.durationSeconds = durationSeconds;
    }
    
    public String getFormattedDuration() {
        return formattedDuration;
    }
    
    public void setFormattedDuration(String formattedDuration) {
        this.formattedDuration = formattedDuration;
    }
    
    public String getWinningTile() {
        return winningTile;
    }
    
    public void setWinningTile(String winningTile) {
        this.winningTile = winningTile;
    }
    
    public String getWinningType() {
        return winningType;
    }
    
    public void setWinningType(String winningType) {
        this.winningType = winningType;
    }
    
    public Integer getFinalScore() {
        return finalScore;
    }
    
    public void setFinalScore(Integer finalScore) {
        this.finalScore = finalScore;
    }
    
    public Long getDealerUserId() {
        return dealerUserId;
    }
    
    public void setDealerUserId(Long dealerUserId) {
        this.dealerUserId = dealerUserId;
    }
    
    public String getDealerNickname() {
        return dealerNickname;
    }
    
    public void setDealerNickname(String dealerNickname) {
        this.dealerNickname = dealerNickname;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public List<PlayerResultDto> getPlayerResults() {
        return playerResults;
    }
    
    public void setPlayerResults(List<PlayerResultDto> playerResults) {
        this.playerResults = playerResults;
    }
    
    public boolean isHasReplayData() {
        return hasReplayData;
    }
    
    public void setHasReplayData(boolean hasReplayData) {
        this.hasReplayData = hasReplayData;
    }
    
    /**
     * DTO for individual player results in a game
     */
    public static class PlayerResultDto {
        @JsonProperty("userId")
        private Long userId;
        
        @JsonProperty("nickname")
        private String nickname;
        
        @JsonProperty("avatar")
        private String avatar;
        
        @JsonProperty("seatIndex")
        private Integer seatIndex;
        
        @JsonProperty("result")
        private GameResult result;
        
        @JsonProperty("score")
        private Integer score;
        
        @JsonProperty("baseScore")
        private Integer baseScore;
        
        @JsonProperty("gangScore")
        private Integer gangScore;
        
        @JsonProperty("multiplier")
        private Double multiplier;
        
        @JsonProperty("isDealer")
        private Boolean isDealer;
        
        @JsonProperty("isSelfDraw")
        private Boolean isSelfDraw;
        
        public PlayerResultDto() {}
        
        // Getters and Setters
        public Long getUserId() {
            return userId;
        }
        
        public void setUserId(Long userId) {
            this.userId = userId;
        }
        
        public String getNickname() {
            return nickname;
        }
        
        public void setNickname(String nickname) {
            this.nickname = nickname;
        }
        
        public String getAvatar() {
            return avatar;
        }
        
        public void setAvatar(String avatar) {
            this.avatar = avatar;
        }
        
        public Integer getSeatIndex() {
            return seatIndex;
        }
        
        public void setSeatIndex(Integer seatIndex) {
            this.seatIndex = seatIndex;
        }
        
        public GameResult getResult() {
            return result;
        }
        
        public void setResult(GameResult result) {
            this.result = result;
        }
        
        public Integer getScore() {
            return score;
        }
        
        public void setScore(Integer score) {
            this.score = score;
        }
        
        public Integer getBaseScore() {
            return baseScore;
        }
        
        public void setBaseScore(Integer baseScore) {
            this.baseScore = baseScore;
        }
        
        public Integer getGangScore() {
            return gangScore;
        }
        
        public void setGangScore(Integer gangScore) {
            this.gangScore = gangScore;
        }
        
        public Double getMultiplier() {
            return multiplier;
        }
        
        public void setMultiplier(Double multiplier) {
            this.multiplier = multiplier;
        }
        
        public Boolean getIsDealer() {
            return isDealer;
        }
        
        public void setIsDealer(Boolean isDealer) {
            this.isDealer = isDealer;
        }
        
        public Boolean getIsSelfDraw() {
            return isSelfDraw;
        }
        
        public void setIsSelfDraw(Boolean isSelfDraw) {
            this.isSelfDraw = isSelfDraw;
        }
    }
}