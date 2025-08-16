package com.mahjong.model.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Room player entity representing the relationship between Room and User
 */
@Entity
@Table(name = "t_room_player", 
       uniqueConstraints = {
           @UniqueConstraint(name = "uk_room_player", columnNames = {"roomId", "userId"}),
           @UniqueConstraint(name = "uk_room_seat", columnNames = {"roomId", "seatIndex"})
       },
       indexes = {
           @Index(name = "idx_room_player_room_id", columnList = "roomId"),
           @Index(name = "idx_room_player_user_id", columnList = "userId")
       })
public class RoomPlayer {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @NotNull
    @Column(name = "room_id", nullable = false, length = 6)
    private String roomId;
    
    @NotNull
    @Column(name = "user_id", nullable = false)
    private Long userId;
    
    @NotNull
    @Min(0)
    @Max(2)
    @Column(name = "seat_index", nullable = false)
    private Integer seatIndex;
    
    @NotNull
    @Column(name = "total_score", nullable = false)
    private Integer totalScore = 0;
    
    @NotNull
    @Column(name = "is_ready", nullable = false)
    private Boolean isReady = false;
    
    @NotNull
    @Column(name = "is_online", nullable = false)
    private Boolean isOnline = true;
    
    @CreationTimestamp
    @Column(name = "joined_at", nullable = false, updatable = false)
    private LocalDateTime joinedAt;
    
    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;
    
    // Relationships
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", insertable = false, updatable = false)
    private Room room;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;
    
    // Constructors
    public RoomPlayer() {
        this.lastSeenAt = LocalDateTime.now();
    }
    
    public RoomPlayer(String roomId, Long userId, Integer seatIndex) {
        this();
        this.roomId = roomId;
        this.userId = userId;
        this.seatIndex = seatIndex;
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getRoomId() {
        return roomId;
    }
    
    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }
    
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
        if (Boolean.TRUE.equals(isOnline)) {
            this.lastSeenAt = LocalDateTime.now();
        }
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
    
    public Room getRoom() {
        return room;
    }
    
    public void setRoom(Room room) {
        this.room = room;
    }
    
    public User getUser() {
        return user;
    }
    
    public void setUser(User user) {
        this.user = user;
    }
    
    // Business methods
    public boolean isReady() {
        return Boolean.TRUE.equals(isReady);
    }
    
    public boolean isOnline() {
        return Boolean.TRUE.equals(isOnline);
    }
    
    public void setReady() {
        this.isReady = true;
        updateLastSeen();
    }
    
    public void setNotReady() {
        this.isReady = false;
        updateLastSeen();
    }
    
    public void goOnline() {
        this.isOnline = true;
        updateLastSeen();
    }
    
    public void goOffline() {
        this.isOnline = false;
    }
    
    public void updateLastSeen() {
        this.lastSeenAt = LocalDateTime.now();
    }
    
    public void addScore(Integer score) {
        if (score != null) {
            this.totalScore += score;
        }
    }
    
    public void resetScore() {
        this.totalScore = 0;
    }
    
    public boolean isDisconnected() {
        return !isOnline() && lastSeenAt != null && 
               lastSeenAt.isBefore(LocalDateTime.now().minusSeconds(30));
    }
    
    // equals and hashCode
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RoomPlayer that = (RoomPlayer) o;
        return Objects.equals(roomId, that.roomId) && Objects.equals(userId, that.userId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(roomId, userId);
    }
    
    @Override
    public String toString() {
        return "RoomPlayer{" +
                "id=" + id +
                ", roomId='" + roomId + '\'' +
                ", userId=" + userId +
                ", seatIndex=" + seatIndex +
                ", totalScore=" + totalScore +
                ", isReady=" + isReady +
                ", isOnline=" + isOnline +
                '}';
    }
}