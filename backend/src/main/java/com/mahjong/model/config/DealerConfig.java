package com.mahjong.model.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

/**
 * Configuration for dealer rotation rules
 */
public class DealerConfig {
    
    @NotNull
    @JsonProperty("rotateOnWin")
    private Boolean rotateOnWin = false; // Dealer continues if wins
    
    @NotNull
    @JsonProperty("rotateOnDraw")
    private Boolean rotateOnDraw = true;
    
    @NotNull
    @JsonProperty("rotateOnLose")
    private Boolean rotateOnLose = true;
    
    // Constructors
    public DealerConfig() {}
    
    // Getters and Setters
    public Boolean getRotateOnWin() {
        return rotateOnWin;
    }
    
    public void setRotateOnWin(Boolean rotateOnWin) {
        this.rotateOnWin = rotateOnWin;
    }
    
    public Boolean getRotateOnDraw() {
        return rotateOnDraw;
    }
    
    public void setRotateOnDraw(Boolean rotateOnDraw) {
        this.rotateOnDraw = rotateOnDraw;
    }
    
    public Boolean getRotateOnLose() {
        return rotateOnLose;
    }
    
    public void setRotateOnLose(Boolean rotateOnLose) {
        this.rotateOnLose = rotateOnLose;
    }
}