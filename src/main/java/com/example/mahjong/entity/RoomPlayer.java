package com.example.mahjong.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "t_room_player", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"room_id", "user_id"}),
    @UniqueConstraint(columnNames = {"room_id", "seat_index"})
})
public class RoomPlayer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "seat_index", nullable = false)
    private Integer seatIndex;

    @Column(name = "join_at")
    private LocalDateTime joinAt;

    @Column(name = "leave_at")
    private LocalDateTime leaveAt;

    @Column(name = "is_robot")
    private Boolean isRobot = false;

    @Column(name = "score_total")
    private Integer totalScore = 0;

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public Integer getSeatIndex() {
        return seatIndex;
    }

    public void setSeatIndex(Integer seatIndex) {
        this.seatIndex = seatIndex;
    }

    public LocalDateTime getJoinAt() {
        return joinAt;
    }

    public void setJoinAt(LocalDateTime joinAt) {
        this.joinAt = joinAt;
    }

    public LocalDateTime getLeaveAt() {
        return leaveAt;
    }

    public void setLeaveAt(LocalDateTime leaveAt) {
        this.leaveAt = leaveAt;
    }

    public Boolean getIsRobot() {
        return isRobot;
    }

    public void setIsRobot(Boolean isRobot) {
        this.isRobot = isRobot;
    }

    public Integer getTotalScore() {
        return totalScore;
    }

    public void setTotalScore(Integer totalScore) {
        this.totalScore = totalScore;
    }
}
