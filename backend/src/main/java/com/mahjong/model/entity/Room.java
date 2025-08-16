package com.mahjong.model.entity;

import com.mahjong.model.enums.RoomStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Room entity representing a game room
 */
@Entity
@Table(name = "t_room", indexes = {
    @Index(name = "idx_room_owner_id", columnList = "ownerId"),
    @Index(name = "idx_room_status", columnList = "status"),
    @Index(name = "idx_room_created_at", columnList = "createdAt")
})
public class Room {
    
    @Id
    @NotBlank
    @Column(name = "id", length = 6)
    private String id; // 6-digit room number
    
    @NotNull
    @Column(name = "owner_id", nullable = false)
    private Long ownerId;
    
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RoomStatus status = RoomStatus.WAITING;
    
    @NotNull
    @Column(name = "rule_id", nullable = false)
    private Long ruleId;
    
    @Column(name = "current_dealer_user_id")
    private Long currentDealerUserId;
    
    @NotNull
    @Min(0)
    @Column(name = "round_index", nullable = false)
    private Integer roundIndex = 0;
    
    @NotNull
    @Min(1)
    @Max(16)
    @Column(name = "max_rounds", nullable = false)
    private Integer maxRounds = 8;
    
    @Column(name = "current_game_id", length = 36)
    private String currentGameId;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @Column(name = "last_activity_at")
    private LocalDateTime lastActivityAt;
    
    // Relationships
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", insertable = false, updatable = false)
    private User owner;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_id", insertable = false, updatable = false)
    private RoomRule roomRule;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_dealer_user_id", insertable = false, updatable = false)
    private User currentDealer;
    
    @OneToMany(mappedBy = "room", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<RoomPlayer> players = new ArrayList<>();
    
    @OneToMany(mappedBy = "room", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<GameRecord> gameRecords = new ArrayList<>();
    
    // Constructors
    public Room() {
        this.lastActivityAt = LocalDateTime.now();
    }
    
    public Room(String id, Long ownerId, Long ruleId) {
        this();
        this.id = id;
        this.ownerId = ownerId;
        this.ruleId = ruleId;
    }
    
    // Getters and Setters
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public Long getOwnerId() {
        return ownerId;
    }
    
    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }
    
    public RoomStatus getStatus() {
        return status;
    }
    
    public void setStatus(RoomStatus status) {
        this.status = status;
        this.lastActivityAt = LocalDateTime.now();
    }
    
    public Long getRuleId() {
        return ruleId;
    }
    
    public void setRuleId(Long ruleId) {
        this.ruleId = ruleId;
    }
    
    public Long getCurrentDealerUserId() {
        return currentDealerUserId;
    }
    
    public void setCurrentDealerUserId(Long currentDealerUserId) {
        this.currentDealerUserId = currentDealerUserId;
    }
    
    public Integer getRoundIndex() {
        return roundIndex;
    }
    
    public void setRoundIndex(Integer roundIndex) {
        this.roundIndex = roundIndex;
    }
    
    public Integer getMaxRounds() {
        return maxRounds;
    }
    
    public void setMaxRounds(Integer maxRounds) {
        this.maxRounds = maxRounds;
    }
    
    public String getCurrentGameId() {
        return currentGameId;
    }
    
    public void setCurrentGameId(String currentGameId) {
        this.currentGameId = currentGameId;
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
    
    public LocalDateTime getLastActivityAt() {
        return lastActivityAt;
    }
    
    public void setLastActivityAt(LocalDateTime lastActivityAt) {
        this.lastActivityAt = lastActivityAt;
    }
    
    public User getOwner() {
        return owner;
    }
    
    public void setOwner(User owner) {
        this.owner = owner;
    }
    
    public RoomRule getRoomRule() {
        return roomRule;
    }
    
    public void setRoomRule(RoomRule roomRule) {
        this.roomRule = roomRule;
    }
    
    public User getCurrentDealer() {
        return currentDealer;
    }
    
    public void setCurrentDealer(User currentDealer) {
        this.currentDealer = currentDealer;
    }
    
    public List<RoomPlayer> getPlayers() {
        return players;
    }
    
    public void setPlayers(List<RoomPlayer> players) {
        this.players = players;
    }
    
    public List<GameRecord> getGameRecords() {
        return gameRecords;
    }
    
    public void setGameRecords(List<GameRecord> gameRecords) {
        this.gameRecords = gameRecords;
    }
    
    // Business methods
    public boolean isFull() {
        return players.size() >= 3;
    }
    
    public boolean isEmpty() {
        return players.isEmpty();
    }
    
    public boolean isOwner(Long userId) {
        return Objects.equals(ownerId, userId);
    }
    
    public boolean hasPlayer(Long userId) {
        return players.stream().anyMatch(p -> Objects.equals(p.getUserId(), userId));
    }
    
    public RoomPlayer getPlayer(Long userId) {
        return players.stream()
                .filter(p -> Objects.equals(p.getUserId(), userId))
                .findFirst()
                .orElse(null);
    }
    
    public boolean canStart() {
        return status == RoomStatus.WAITING && players.size() == 3;
    }
    
    public boolean isActive() {
        return status == RoomStatus.WAITING || status == RoomStatus.READY || status == RoomStatus.PLAYING;
    }
    
    public boolean isInactive() {
        return lastActivityAt != null && 
               lastActivityAt.isBefore(LocalDateTime.now().minusMinutes(30));
    }
    
    public void updateActivity() {
        this.lastActivityAt = LocalDateTime.now();
    }
    
    public void nextRound() {
        this.roundIndex++;
        updateActivity();
    }
    
    public boolean isLastRound() {
        return roundIndex >= maxRounds;
    }
    
    public void transferOwnership(Long newOwnerId) {
        if (hasPlayer(newOwnerId)) {
            this.ownerId = newOwnerId;
            updateActivity();
        }
    }
    
    // Helper methods for player management
    public void addPlayer(RoomPlayer player) {
        if (!isFull() && !hasPlayer(player.getUserId())) {
            players.add(player);
            player.setRoom(this);
            updateActivity();
        }
    }
    
    public void removePlayer(Long userId) {
        players.removeIf(p -> Objects.equals(p.getUserId(), userId));
        updateActivity();
    }
    
    // equals and hashCode
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Room room = (Room) o;
        return Objects.equals(id, room.id);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
    
    @Override
    public String toString() {
        return "Room{" +
                "id='" + id + '\'' +
                ", ownerId=" + ownerId +
                ", status=" + status +
                ", playerCount=" + players.size() +
                ", roundIndex=" + roundIndex +
                ", maxRounds=" + maxRounds +
                '}';
    }
}