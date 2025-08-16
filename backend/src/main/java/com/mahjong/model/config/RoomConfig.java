package com.mahjong.model.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Room configuration for game rules
 */
public class RoomConfig {
    
    @NotNull
    @Min(3)
    @Max(3)
    @JsonProperty("players")
    private Integer players = 3;
    
    @NotNull
    @JsonProperty("tiles")
    private String tiles = "WAN_ONLY"; // WAN_ONLY, ALL_SUITS
    
    @NotNull
    @JsonProperty("allowPeng")
    private Boolean allowPeng = true;
    
    @NotNull
    @JsonProperty("allowGang")
    private Boolean allowGang = true;
    
    @NotNull
    @JsonProperty("allowChi")
    private Boolean allowChi = false;
    
    @Valid
    @JsonProperty("huTypes")
    private HuTypes huTypes = new HuTypes();
    
    @Valid
    @JsonProperty("score")
    private ScoreConfig score = new ScoreConfig();
    
    @Valid
    @JsonProperty("turn")
    private TurnConfig turn = new TurnConfig();
    
    @Valid
    @JsonProperty("dealer")
    private DealerConfig dealer = new DealerConfig();
    
    @NotNull
    @JsonProperty("replay")
    private Boolean replay = true;
    
    @Valid
    @JsonProperty("dismiss")
    private DismissConfig dismiss = new DismissConfig();
    
    // Constructors
    public RoomConfig() {}
    
    // Getters and Setters
    public Integer getPlayers() {
        return players;
    }
    
    public void setPlayers(Integer players) {
        this.players = players;
    }
    
    public String getTiles() {
        return tiles;
    }
    
    public void setTiles(String tiles) {
        this.tiles = tiles;
    }
    
    public Boolean getAllowPeng() {
        return allowPeng;
    }
    
    public void setAllowPeng(Boolean allowPeng) {
        this.allowPeng = allowPeng;
    }
    
    public Boolean getAllowGang() {
        return allowGang;
    }
    
    public void setAllowGang(Boolean allowGang) {
        this.allowGang = allowGang;
    }
    
    public Boolean getAllowChi() {
        return allowChi;
    }
    
    public void setAllowChi(Boolean allowChi) {
        this.allowChi = allowChi;
    }
    
    public HuTypes getHuTypes() {
        return huTypes;
    }
    
    public void setHuTypes(HuTypes huTypes) {
        this.huTypes = huTypes;
    }
    
    public ScoreConfig getScore() {
        return score;
    }
    
    public void setScore(ScoreConfig score) {
        this.score = score;
    }
    
    public TurnConfig getTurn() {
        return turn;
    }
    
    public void setTurn(TurnConfig turn) {
        this.turn = turn;
    }
    
    public DealerConfig getDealer() {
        return dealer;
    }
    
    public void setDealer(DealerConfig dealer) {
        this.dealer = dealer;
    }
    
    public Boolean getReplay() {
        return replay;
    }
    
    public void setReplay(Boolean replay) {
        this.replay = replay;
    }
    
    public DismissConfig getDismiss() {
        return dismiss;
    }
    
    public void setDismiss(DismissConfig dismiss) {
        this.dismiss = dismiss;
    }
}