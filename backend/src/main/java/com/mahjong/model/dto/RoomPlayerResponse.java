package com.mahjong.model.dto;

import com.mahjong.model.entity.RoomPlayer;

import java.time.LocalDateTime;

/**
 * Response DTO for room player information
 */
public class RoomPlayerResponse {
    
    private Long userId;
    private Integer seatIndex;
    private Integer totalScore;
    private Boolean isReady;
    private Boolean isOnline;
    private LocalDateTime joinedAt;
    private LocalDateTime lastSeenAt;
    private String nickname;
    private String avatar;
    
    public RoomPlayerResponse() {}
    
    public RoomPlayerResponse(RoomPlayer roomPlayer) {
        this.userId = roomPlayer.getUserId();
        this.seatIndex = roomPlayer.getSeatIndex();
        this.totalScore = roomPlayer.getTotalScore();
        this.isReady = roomPlayer.getIsReady();
        this.isOnline = roomPlayer.getIsOnline();
        this.joinedAt = roomPlayer.getJoinedAt();
        this.lastSeenAt = roomPlayer.getLastSeenAt();
        
        // Include user info if available
        if (roomPlayer.getUser() != null) {
            this.nickname = roomPlayer.getUser().getNickname();
            this.avatar = roomPlayer.getUser().getAvatar();
        }
    }
    
    // Getters and Setters
    public Long getUserId() {
        return userId;
    }
    
    public void setUserId(Long userId) {
        this.userId = userId;
    }
    
    public Integer getSeatIndex() {
        return seatIndex;
    }
    
    public void setSeatIndex(Integer seatIndex) {
        this.seatIndex = seatIndex;
    }
    
    public Integer getTotalScore() {
        return totalScore;
    }
    
    public void setTotalScore(Integer totalScore) {
        this.totalScore = totalScore;
    }
    
    public Boolean getIsReady() {
        return isReady;
    }
    
    public void setIsReady(Boolean isReady) {
        this.isReady = isReady;
    }
    
    public Boolean getIsOnline() {
        return isOnline;
    }
    
    public void setIsOnline(Boolean isOnline) {
        this.isOnline = isOnline;
    }
    
    public LocalDateTime getJoinedAt() {
        return joinedAt;
    }
    
    public void setJoinedAt(LocalDateTime joinedAt) {
        this.joinedAt = joinedAt;
    }
    
    public LocalDateTime getLastSeenAt() {
        return lastSeenAt;
    }
    
    public void setLastSeenAt(LocalDateTime lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
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
    
    @Override
    public String toString() {
        return "RoomPlayerResponse{" +
                "userId=" + userId +
                ", seatIndex=" + seatIndex +
                ", totalScore=" + totalScore +
                ", isReady=" + isReady +
                ", isOnline=" + isOnline +
                ", nickname='" + nickname + '\'' +
                '}';
    }
}