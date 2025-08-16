package com.mahjong.model.entity;

import com.mahjong.model.enums.UserRole;
import com.mahjong.model.enums.UserStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * User entity representing a player in the system
 */
@Entity
@Table(name = "t_user", indexes = {
    @Index(name = "idx_user_open_id", columnList = "openId", unique = true),
    @Index(name = "idx_user_union_id", columnList = "unionId"),
    @Index(name = "idx_user_status", columnList = "status")
})
public class User {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @NotBlank
    @Size(max = 100)
    @Column(name = "open_id", unique = true, nullable = false, length = 100)
    private String openId;
    
    @Size(max = 100)
    @Column(name = "union_id", length = 100)
    private String unionId;
    
    @NotBlank
    @Size(max = 50)
    @Column(name = "nickname", nullable = false, length = 50)
    private String nickname;
    
    @Size(max = 500)
    @Column(name = "avatar", length = 500)
    private String avatar;
    
    @NotNull
    @Column(name = "coins", nullable = false)
    private Integer coins = 0;
    
    @NotNull
    @Column(name = "room_cards", nullable = false)
    private Integer roomCards = 0;
    
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;
    
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private UserRole role = UserRole.USER;
    
    @Size(max = 500)
    @Column(name = "ban_reason", length = 500)
    private String banReason;
    
    @Column(name = "banned_at")
    private LocalDateTime bannedAt;
    
    @Column(name = "banned_by")
    private Long bannedBy;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    // Constructors
    public User() {}
    
    public User(String openId, String nickname) {
        this.openId = openId;
        this.nickname = nickname;
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
    
    // Business methods
    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }
    
    public boolean isBanned() {
        return status == UserStatus.BANNED;
    }
    
    public boolean isAdmin() {
        return role == UserRole.ADMIN || role == UserRole.SUPER_ADMIN;
    }
    
    public boolean isSuperAdmin() {
        return role == UserRole.SUPER_ADMIN;
    }
    
    public void ban(String reason, Long bannedBy) {
        this.status = UserStatus.BANNED;
        this.banReason = reason;
        this.bannedAt = LocalDateTime.now();
        this.bannedBy = bannedBy;
    }
    
    public void unban() {
        this.status = UserStatus.ACTIVE;
        this.banReason = null;
        this.bannedAt = null;
        this.bannedBy = null;
    }
    
    public void addCoins(Integer amount) {
        if (amount != null && amount > 0) {
            this.coins += amount;
        }
    }
    
    public boolean deductCoins(Integer amount) {
        if (amount != null && amount > 0 && this.coins >= amount) {
            this.coins -= amount;
            return true;
        }
        return false;
    }
    
    public void addRoomCards(Integer amount) {
        if (amount != null && amount > 0) {
            this.roomCards += amount;
        }
    }
    
    public boolean deductRoomCards(Integer amount) {
        if (amount != null && amount > 0 && this.roomCards >= amount) {
            this.roomCards -= amount;
            return true;
        }
        return false;
    }
    
    // equals and hashCode
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return Objects.equals(id, user.id) && Objects.equals(openId, user.openId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id, openId);
    }
    
    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", openId='" + openId + '\'' +
                ", nickname='" + nickname + '\'' +
                ", status=" + status +
                ", coins=" + coins +
                ", roomCards=" + roomCards +
                '}';
    }
}