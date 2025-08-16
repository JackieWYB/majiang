package com.mahjong.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

/**
 * DTO for user statistics response
 */
public class UserStatisticsResponse {
    
    @JsonProperty("userId")
    private Long userId;
    
    @JsonProperty("totalGames")
    private Long totalGames;
    
    @JsonProperty("wins")
    private Long wins;
    
    @JsonProperty("losses")
    private Long losses;
    
    @JsonProperty("draws")
    private Long draws;
    
    @JsonProperty("winRate")
    private Double winRate;
    
    @JsonProperty("totalScore")
    private Long totalScore;
    
    @JsonProperty("averageScore")
    private Double averageScore;
    
    @JsonProperty("bestScore")
    private Integer bestScore;
    
    @JsonProperty("worstScore")
    private Integer worstScore;
    
    @JsonProperty("dealerGames")
    private Long dealerGames;
    
    @JsonProperty("dealerWins")
    private Long dealerWins;
    
    @JsonProperty("dealerWinRate")
    private Double dealerWinRate;
    
    @JsonProperty("selfDrawWins")
    private Long selfDrawWins;
    
    @JsonProperty("claimedWins")
    private Long claimedWins;
    
    @JsonProperty("averageGameDuration")
    private Double averageGameDuration;
    
    @JsonProperty("longestWinStreak")
    private Integer longestWinStreak;
    
    @JsonProperty("currentWinStreak")
    private Integer currentWinStreak;
    
    @JsonProperty("startDate")
    private LocalDateTime startDate;
    
    @JsonProperty("endDate")
    private LocalDateTime endDate;
    
    @JsonProperty("lastGameDate")
    private LocalDateTime lastGameDate;
    
    public UserStatisticsResponse() {}
    
    // Getters and Setters
    public Long getUserId() {
        return userId;
    }
    
    public void setUserId(Long userId) {
        this.userId = userId;
    }
    
    public Long getTotalGames() {
        return totalGames;
    }
    
    public void setTotalGames(Long totalGames) {
        this.totalGames = totalGames;
    }
    
    public Long getWins() {
        return wins;
    }
    
    public void setWins(Long wins) {
        this.wins = wins;
    }
    
    public Long getLosses() {
        return losses;
    }
    
    public void setLosses(Long losses) {
        this.losses = losses;
    }
    
    public Long getDraws() {
        return draws;
    }
    
    public void setDraws(Long draws) {
        this.draws = draws;
    }
    
    public Double getWinRate() {
        return winRate;
    }
    
    public void setWinRate(Double winRate) {
        this.winRate = winRate;
    }
    
    public Long getTotalScore() {
        return totalScore;
    }
    
    public void setTotalScore(Long totalScore) {
        this.totalScore = totalScore;
    }
    
    public Double getAverageScore() {
        return averageScore;
    }
    
    public void setAverageScore(Double averageScore) {
        this.averageScore = averageScore;
    }
    
    public Integer getBestScore() {
        return bestScore;
    }
    
    public void setBestScore(Integer bestScore) {
        this.bestScore = bestScore;
    }
    
    public Integer getWorstScore() {
        return worstScore;
    }
    
    public void setWorstScore(Integer worstScore) {
        this.worstScore = worstScore;
    }
    
    public Long getDealerGames() {
        return dealerGames;
    }
    
    public void setDealerGames(Long dealerGames) {
        this.dealerGames = dealerGames;
    }
    
    public Long getDealerWins() {
        return dealerWins;
    }
    
    public void setDealerWins(Long dealerWins) {
        this.dealerWins = dealerWins;
    }
    
    public Double getDealerWinRate() {
        return dealerWinRate;
    }
    
    public void setDealerWinRate(Double dealerWinRate) {
        this.dealerWinRate = dealerWinRate;
    }
    
    public Long getSelfDrawWins() {
        return selfDrawWins;
    }
    
    public void setSelfDrawWins(Long selfDrawWins) {
        this.selfDrawWins = selfDrawWins;
    }
    
    public Long getClaimedWins() {
        return claimedWins;
    }
    
    public void setClaimedWins(Long claimedWins) {
        this.claimedWins = claimedWins;
    }
    
    public Double getAverageGameDuration() {
        return averageGameDuration;
    }
    
    public void setAverageGameDuration(Double averageGameDuration) {
        this.averageGameDuration = averageGameDuration;
    }
    
    public Integer getLongestWinStreak() {
        return longestWinStreak;
    }
    
    public void setLongestWinStreak(Integer longestWinStreak) {
        this.longestWinStreak = longestWinStreak;
    }
    
    public Integer getCurrentWinStreak() {
        return currentWinStreak;
    }
    
    public void setCurrentWinStreak(Integer currentWinStreak) {
        this.currentWinStreak = currentWinStreak;
    }
    
    public LocalDateTime getStartDate() {
        return startDate;
    }
    
    public void setStartDate(LocalDateTime startDate) {
        this.startDate = startDate;
    }
    
    public LocalDateTime getEndDate() {
        return endDate;
    }
    
    public void setEndDate(LocalDateTime endDate) {
        this.endDate = endDate;
    }
    
    public LocalDateTime getLastGameDate() {
        return lastGameDate;
    }
    
    public void setLastGameDate(LocalDateTime lastGameDate) {
        this.lastGameDate = lastGameDate;
    }
}