package com.mahjong.model.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Configuration for scoring rules
 */
public class ScoreConfig {
    
    @NotNull
    @Min(1)
    @JsonProperty("baseScore")
    private Integer baseScore = 2;
    
    @NotNull
    @Min(1)
    @JsonProperty("maxScore")
    private Integer maxScore = 24;
    
    @NotNull
    @JsonProperty("dealerMultiplier")
    private Double dealerMultiplier = 2.0;
    
    @NotNull
    @JsonProperty("selfDrawBonus")
    private Double selfDrawBonus = 1.0;
    
    @NotNull
    @JsonProperty("gangBonus")
    private Integer gangBonus = 1;
    
    @NotNull
    @JsonProperty("multipleWinners")
    private Boolean multipleWinners = false;
    
    // Constructors
    public ScoreConfig() {}
    
    // Getters and Setters
    public Integer getBaseScore() {
        return baseScore;
    }
    
    public void setBaseScore(Integer baseScore) {
        this.baseScore = baseScore;
    }
    
    public Integer getMaxScore() {
        return maxScore;
    }
    
    public void setMaxScore(Integer maxScore) {
        this.maxScore = maxScore;
    }
    
    public Double getDealerMultiplier() {
        return dealerMultiplier;
    }
    
    public void setDealerMultiplier(Double dealerMultiplier) {
        this.dealerMultiplier = dealerMultiplier;
    }
    
    public Double getSelfDrawBonus() {
        return selfDrawBonus;
    }
    
    public void setSelfDrawBonus(Double selfDrawBonus) {
        this.selfDrawBonus = selfDrawBonus;
    }
    
    public Integer getGangBonus() {
        return gangBonus;
    }
    
    public void setGangBonus(Integer gangBonus) {
        this.gangBonus = gangBonus;
    }
    
    public Boolean getMultipleWinners() {
        return multipleWinners;
    }
    
    public void setMultipleWinners(Boolean multipleWinners) {
        this.multipleWinners = multipleWinners;
    }
}