package com.example.mahjong.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "t_room")
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // This will be the room number

    @ManyToOne
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(nullable = false)
    private String status; // e.g., CREATED, WAITING, PLAYING, FINISHED

    @ManyToOne
    @JoinColumn(name = "rule_id", nullable = false)
    private RoomRule rule;

    @Column(name = "cur_dealer_user_id")
    private Long currentDealerUserId;

    @Column(name = "round_index")
    private Integer roundIndex;

    @Column(name = "max_rounds")
    private Integer maxRounds;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getOwner() {
        return owner;
    }

    public void setOwner(User owner) {
        this.owner = owner;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public RoomRule getRule() {
        return rule;
    }

    public void setRule(RoomRule rule) {
        this.rule = rule;
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
