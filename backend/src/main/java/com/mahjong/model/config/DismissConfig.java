package com.mahjong.model.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Configuration for room dismissal rules
 */
public class DismissConfig {
    
    @NotNull
    @JsonProperty("requireAllAgree")
    private Boolean requireAllAgree = true;
    
    @NotNull
    @Min(1)
    @JsonProperty("voteTimeLimit")
    private Integer voteTimeLimit = 60; // seconds
    
    @NotNull
    @Min(10)
    @JsonProperty("autoDissolveTimeout")
    private Integer autoDissolveTimeout = 1800; // 30 minutes
    
    // Constructors
    public DismissConfig() {}
    
    // Getters and Setters
    public Boolean getRequireAllAgree() {
        return requireAllAgree;
    }
    
    public void setRequireAllAgree(Boolean requireAllAgree) {
        this.requireAllAgree = requireAllAgree;
    }
    
    public Integer getVoteTimeLimit() {
        return voteTimeLimit;
    }
    
    public void setVoteTimeLimit(Integer voteTimeLimit) {
        this.voteTimeLimit = voteTimeLimit;
    }
    
    public Integer getAutoDissolveTimeout() {
        return autoDissolveTimeout;
    }
    
    public void setAutoDissolveTimeout(Integer autoDissolveTimeout) {
        this.autoDissolveTimeout = autoDissolveTimeout;
    }
}