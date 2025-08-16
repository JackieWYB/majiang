package com.mahjong.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Player state snapshot for game synchronization
 */
public class PlayerSnapshot {
    
    @JsonProperty("userId")
    private String userId;
    
    @JsonProperty("nickname")
    private String nickname;
    
    @JsonProperty("avatar")
    private String avatar;
    
    @JsonProperty("seatIndex")
    private Integer seatIndex;
    
    @JsonProperty("handTiles")
    private List<String> handTiles; // Only visible to the player themselves
    
    @JsonProperty("handCount")
    private Integer handCount; // Number of tiles in hand (visible to others)
    
    @JsonProperty("melds")
    private List<MeldSnapshot> melds; // Exposed melds (Peng, Gang sets)
    
    @JsonProperty("discardedTiles")
    private List<String> discardedTiles;
    
    @JsonProperty("isDealer")
    private Boolean isDealer;
    
    @JsonProperty("status")
    private String status; // ACTIVE, DISCONNECTED, TRUSTEE, etc.
    
    @JsonProperty("score")
    private Integer score;
    
    @JsonProperty("isReady")
    private Boolean isReady;
    
    @JsonProperty("lastActionTime")
    private Long lastActionTime;
    
    public PlayerSnapshot() {}
    
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
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
    
    public List<String> getHandTiles() {
        return handTiles;
    }
    
    public void setHandTiles(List<String> handTiles) {
        this.handTiles = handTiles;
    }
    
    public Integer getHandCount() {
        return handCount;
    }
    
    public void setHandCount(Integer handCount) {
        this.handCount = handCount;
    }
    
    public List<MeldSnapshot> getMelds() {
        return melds;
    }
    
    public void setMelds(List<MeldSnapshot> melds) {
        this.melds = melds;
    }
    
    public List<String> getDiscardedTiles() {
        return discardedTiles;
    }
    
    public void setDiscardedTiles(List<String> discardedTiles) {
        this.discardedTiles = discardedTiles;
    }
    
    public Boolean getIsDealer() {
        return isDealer;
    }
    
    public void setIsDealer(Boolean isDealer) {
        this.isDealer = isDealer;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public Integer getScore() {
        return score;
    }
    
    public void setScore(Integer score) {
        this.score = score;
    }
    
    public Boolean getIsReady() {
        return isReady;
    }
    
    public void setIsReady(Boolean isReady) {
        this.isReady = isReady;
    }
    
    public Long getLastActionTime() {
        return lastActionTime;
    }
    
    public void setLastActionTime(Long lastActionTime) {
        this.lastActionTime = lastActionTime;
    }
    
    @Override
    public String toString() {
        return String.format("PlayerSnapshot{userId='%s', seatIndex=%d, handCount=%d, status='%s'}", 
                userId, seatIndex, handCount, status);
    }
}