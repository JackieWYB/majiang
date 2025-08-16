package com.mahjong.model.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

/**
 * Configuration for winning hand types
 */
public class HuTypes {
    
    @NotNull
    @JsonProperty("sevenPairs")
    private Boolean sevenPairs = true;
    
    @NotNull
    @JsonProperty("allPungs")
    private Boolean allPungs = true;
    
    @NotNull
    @JsonProperty("allHonors")
    private Boolean allHonors = true;
    
    @NotNull
    @JsonProperty("edgeWait")
    private Boolean edgeWait = true;
    
    @NotNull
    @JsonProperty("pairWait")
    private Boolean pairWait = true;
    
    @NotNull
    @JsonProperty("selfDraw")
    private Boolean selfDraw = true;
    
    // Constructors
    public HuTypes() {}
    
    // Getters and Setters
    public Boolean getSevenPairs() {
        return sevenPairs;
    }
    
    public void setSevenPairs(Boolean sevenPairs) {
        this.sevenPairs = sevenPairs;
    }
    
    public Boolean getAllPungs() {
        return allPungs;
    }
    
    public void setAllPungs(Boolean allPungs) {
        this.allPungs = allPungs;
    }
    
    public Boolean getAllHonors() {
        return allHonors;
    }
    
    public void setAllHonors(Boolean allHonors) {
        this.allHonors = allHonors;
    }
    
    public Boolean getEdgeWait() {
        return edgeWait;
    }
    
    public void setEdgeWait(Boolean edgeWait) {
        this.edgeWait = edgeWait;
    }
    
    public Boolean getPairWait() {
        return pairWait;
    }
    
    public void setPairWait(Boolean pairWait) {
        this.pairWait = pairWait;
    }
    
    public Boolean getSelfDraw() {
        return selfDraw;
    }
    
    public void setSelfDraw(Boolean selfDraw) {
        this.selfDraw = selfDraw;
    }
}