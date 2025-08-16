package com.mahjong.model.dto;

import com.mahjong.model.entity.Room;
import com.mahjong.model.entity.RoomPlayer;
import com.mahjong.model.enums.RoomStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Response DTO for room information
 */
public class RoomResponse {
    
    private String id;
    private Long ownerId;
    private RoomStatus status;
    private Long ruleId;
    private Integer roundIndex;
    private Integer maxRounds;
    private LocalDateTime createdAt;
    private LocalDateTime lastActivityAt;
    private List<RoomPlayerResponse> players;
    
    public RoomResponse() {}
    
    public RoomResponse(Room room) {
        this.id = room.getId();
        this.ownerId = room.getOwnerId();
        this.status = room.getStatus();
        this.ruleId = room.getRuleId();
        this.roundIndex = room.getRoundIndex();
        this.maxRounds = room.getMaxRounds();
        this.createdAt = room.getCreatedAt();
        this.lastActivityAt = room.getLastActivityAt();
        this.players = room.getPlayers().stream()
                .map(RoomPlayerResponse::new)
                .collect(Collectors.toList());
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
    }
    
    public Long getRuleId() {
        return ruleId;
    }
    
    public void setRuleId(Long ruleId) {
        this.ruleId = ruleId;
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
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getLastActivityAt() {
        return lastActivityAt;
    }
    
    public void setLastActivityAt(LocalDateTime lastActivityAt) {
        this.lastActivityAt = lastActivityAt;
    }
    
    public List<RoomPlayerResponse> getPlayers() {
        return players;
    }
    
    public void setPlayers(List<RoomPlayerResponse> players) {
        this.players = players;
    }
    
    @Override
    public String toString() {
        return "RoomResponse{" +
                "id='" + id + '\'' +
                ", ownerId=" + ownerId +
                ", status=" + status +
                ", playerCount=" + (players != null ? players.size() : 0) +
                '}';
    }
}