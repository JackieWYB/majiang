package com.mahjong.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for admin user management operations
 */
public class AdminUserRequest {
    
    @NotNull
    private Long userId;
    
    @Size(max = 500)
    private String reason;
    
    @Size(max = 50)
    private String nickname;
    
    private Integer coins;
    
    private Integer roomCards;
    
    // Constructors
    public AdminUserRequest() {}
    
    public AdminUserRequest(Long userId, String reason) {
        this.userId = userId;
        this.reason = reason;
    }
    
    // Getters and Setters
    public Long getUserId() {
        return userId;
    }
    
    public void setUserId(Long userId) {
        this.userId = userId;
    }
    
    public String getReason() {
        return reason;
    }
    
    public void setReason(String reason) {
        this.reason = reason;
    }
    
    public String getNickname() {
        return nickname;
    }
    
    public void setNickname(String nickname) {
        this.nickname = nickname;
    }
    
    public Integer getCoins() {
        return coins;
    }
    
    public void setCoins(Integer coins) {
        this.coins = coins;
    }
    
    public Integer getRoomCards() {
        return roomCards;
    }
    
    public void setRoomCards(Integer roomCards) {
        this.roomCards = roomCards;
    }
}