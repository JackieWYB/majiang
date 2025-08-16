package com.mahjong.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO for play/discard action messages
 */
public class PlayAction {
    
    @JsonProperty("tile")
    private String tile;
    
    @JsonProperty("fromHand")
    private boolean fromHand = true;
    
    public PlayAction() {}
    
    public PlayAction(String tile) {
        this.tile = tile;
    }
    
    public PlayAction(String tile, boolean fromHand) {
        this.tile = tile;
        this.fromHand = fromHand;
    }
    
    public String getTile() {
        return tile;
    }
    
    public void setTile(String tile) {
        this.tile = tile;
    }
    
    public boolean isFromHand() {
        return fromHand;
    }
    
    public void setFromHand(boolean fromHand) {
        this.fromHand = fromHand;
    }
    
    @Override
    public String toString() {
        return String.format("PlayAction{tile='%s', fromHand=%s}", tile, fromHand);
    }
}