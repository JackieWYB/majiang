package com.mahjong.model.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Configuration for turn and timing rules
 */
public class TurnConfig {
    
    @NotNull
    @Min(5)
    @JsonProperty("turnTimeLimit")
    private Integer turnTimeLimit = 15; // seconds
    
    @NotNull
    @Min(1)
    @JsonProperty("actionTimeLimit")
    private Integer actionTimeLimit = 2; // seconds for peng/gang/hu
    
    @NotNull
    @JsonProperty("autoTrustee")
    private Boolean autoTrustee = true;
    
    @NotNull
    @Min(10)
    @JsonProperty("trusteeTimeout")
    private Integer trusteeTimeout = 30; // seconds
    
    // Constructors
    public TurnConfig() {}
    
    // Getters and Setters
    public Integer getTurnTimeLimit() {
        return turnTimeLimit;
    }
    
    public void setTurnTimeLimit(Integer turnTimeLimit) {
        this.turnTimeLimit = turnTimeLimit;
    }
    
    public Integer getActionTimeLimit() {
        return actionTimeLimit;
    }
    
    public void setActionTimeLimit(Integer actionTimeLimit) {
        this.actionTimeLimit = actionTimeLimit;
    }
    
    public Boolean getAutoTrustee() {
        return autoTrustee;
    }
    
    public void setAutoTrustee(Boolean autoTrustee) {
        this.autoTrustee = autoTrustee;
    }
    
    public Integer getTrusteeTimeout() {
        return trusteeTimeout;
    }
    
    public void setTrusteeTimeout(Integer trusteeTimeout) {
        this.trusteeTimeout = trusteeTimeout;
    }
}