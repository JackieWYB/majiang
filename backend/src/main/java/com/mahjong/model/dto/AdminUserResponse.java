package com.mahjong.model.dto;

import com.mahjong.model.entity.User;
import com.mahjong.model.enums.UserRole;
import com.mahjong.model.enums.UserStatus;

import java.time.LocalDateTime;

/**
 * Response DTO for admin user management operations
 */
public class AdminUserResponse {
    
    private Long id;
    private String openId;
    private String unionId;
    private String nickname;
    private String avatar;
    private Integer coins;
    private Integer roomCards;
    private UserStatus status;
    private UserRole role;
    private String banReason;
    private LocalDateTime bannedAt;
    private Long bannedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // Constructors
    public AdminUserResponse() {}
    
    public AdminUserResponse(User user) {
        this.id = user.getId();
        this.openId = user.getOpenId();
        this.unionId = user.getUnionId();
        this.nickname = user.getNickname();
        this.avatar = user.getAvatar();
        this.coins = user.getCoins();
        this.roomCards = user.getRoomCards();
        this.status = user.getStatus();
        this.role = user.getRole();
        this.banReason = user.getBanReason();
        this.bannedAt = user.getBannedAt();
        this.bannedBy = user.getBannedBy();
        this.createdAt = user.getCreatedAt();
        this.updatedAt = user.getUpdatedAt();
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getOpenId() {
        return openId;
    }
    
    public void setOpenId(String openId) {
        this.openId = openId;
    }
    
    public String getUnionId() {
        return unionId;
    }
    
    public void setUnionId(String unionId) {
        this.unionId = unionId;
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
    
    public UserStatus getStatus() {
        return status;
    }
    
    public void setStatus(UserStatus status) {
        this.status = status;
    }
    
    public UserRole getRole() {
        return role;
    }
    
    public void setRole(UserRole role) {
        this.role = role;
    }
    
    public String getBanReason() {
        return banReason;
    }
    
    public void setBanReason(String banReason) {
        this.banReason = banReason;
    }
    
    public LocalDateTime getBannedAt() {
        return bannedAt;
    }
    
    public void setBannedAt(LocalDateTime bannedAt) {
        this.bannedAt = bannedAt;
    }
    
    public Long getBannedBy() {
        return bannedBy;
    }
    
    public void setBannedBy(Long bannedBy) {
        this.bannedBy = bannedBy;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}