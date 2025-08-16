package com.mahjong.model.entity;

import com.mahjong.model.enums.GameResult;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Game record entity for storing completed game information
 */
@Entity
@Table(name = "t_game_record", indexes = {
    @Index(name = "idx_game_record_room_id", columnList = "roomId"),
    @Index(name = "idx_game_record_created_at", columnList = "createdAt"),
    @Index(name = "idx_game_record_winner_id", columnList = "winnerId")
})
public class GameRecord {
    
    @Id
    @NotBlank
    @Size(max = 36)
    @Column(name = "id", length = 36)
    private String id; // UUID for game record
    
    @NotNull
    @Column(name = "room_id", nullable = false, length = 6)
    private String roomId;
    
    @NotNull
    @Column(name = "round_index", nullable = false)
    private Integer roundIndex;
    
    @Column(name = "winner_id")
    private Long winnerId;
    
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, length = 10)
    private GameResult result;
    
    @NotNull
    @Column(name = "duration_seconds", nullable = false)
    private Integer durationSeconds;
    
    @Column(name = "winning_tile", length = 10)
    private String winningTile;
    
    @Column(name = "winning_type", length = 50)
    private String winningType;
    
    @NotNull
    @Column(name = "base_score", nullable = false)
    private Integer baseScore = 0;
    
    @NotNull
    @Column(name = "multiplier", nullable = false)
    private Double multiplier = 1.0;
    
    @NotNull
    @Column(name = "final_score", nullable = false)
    private Integer finalScore = 0;
    
    @Column(name = "dealer_user_id")
    private Long dealerUserId;
    
    @Size(max = 20)
    @Column(name = "random_seed", length = 20)
    private String randomSeed;
    
    @Column(name = "game_actions", columnDefinition = "LONGTEXT")
    private String gameActions; // JSON array of all game actions for replay
    
    @Column(name = "final_hands", columnDefinition = "TEXT")
    private String finalHands; // JSON of all players' final hands
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    // Relationships
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", insertable = false, updatable = false)
    private Room room;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "winner_id", insertable = false, updatable = false)
    private User winner;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dealer_user_id", insertable = false, updatable = false)
    private User dealer;
    
    @OneToMany(mappedBy = "gameRecord", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<GamePlayerRecord> playerRecords = new ArrayList<>();
    
    // Constructors
    public GameRecord() {}
    
    public GameRecord(String id, String roomId, Integer roundIndex, GameResult result) {
        this.id = id;
        this.roomId = roomId;
        this.roundIndex = roundIndex;
        this.result = result;
    }
    
    // Getters and Setters
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getRoomId() {
        return roomId;
    }
    
    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }
    
    public Integer getRoundIndex() {
        return roundIndex;
    }
    
    public void setRoundIndex(Integer roundIndex) {
        this.roundIndex = roundIndex;
    }
    
    public Long getWinnerId() {
        return winnerId;
    }
    
    public void setWinnerId(Long winnerId) {
        this.winnerId = winnerId;
    }
    
    public GameResult getResult() {
        return result;
    }
    
    public void setResult(GameResult result) {
        this.result = result;
    }
    
    public Integer getDurationSeconds() {
        return durationSeconds;
    }
    
    public void setDurationSeconds(Integer durationSeconds) {
        this.durationSeconds = durationSeconds;
    }
    
    public String getWinningTile() {
        return winningTile;
    }
    
    public void setWinningTile(String winningTile) {
        this.winningTile = winningTile;
    }
    
    public String getWinningType() {
        return winningType;
    }
    
    public void setWinningType(String winningType) {
        this.winningType = winningType;
    }
    
    public Integer getBaseScore() {
        return baseScore;
    }
    
    public void setBaseScore(Integer baseScore) {
        this.baseScore = baseScore;
    }
    
    public Double getMultiplier() {
        return multiplier;
    }
    
    public void setMultiplier(Double multiplier) {
        this.multiplier = multiplier;
    }
    
    public Integer getFinalScore() {
        return finalScore;
    }
    
    public void setFinalScore(Integer finalScore) {
        this.finalScore = finalScore;
    }
    
    public Long getDealerUserId() {
        return dealerUserId;
    }
    
    public void setDealerUserId(Long dealerUserId) {
        this.dealerUserId = dealerUserId;
    }
    
    public String getRandomSeed() {
        return randomSeed;
    }
    
    public void setRandomSeed(String randomSeed) {
        this.randomSeed = randomSeed;
    }
    
    public String getGameActions() {
        return gameActions;
    }
    
    public void setGameActions(String gameActions) {
        this.gameActions = gameActions;
    }
    
    public String getFinalHands() {
        return finalHands;
    }
    
    public void setFinalHands(String finalHands) {
        this.finalHands = finalHands;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public Room getRoom() {
        return room;
    }
    
    public void setRoom(Room room) {
        this.room = room;
    }
    
    public User getWinner() {
        return winner;
    }
    
    public void setWinner(User winner) {
        this.winner = winner;
    }
    
    public User getDealer() {
        return dealer;
    }
    
    public void setDealer(User dealer) {
        this.dealer = dealer;
    }
    
    public List<GamePlayerRecord> getPlayerRecords() {
        return playerRecords;
    }
    
    public void setPlayerRecords(List<GamePlayerRecord> playerRecords) {
        this.playerRecords = playerRecords;
    }
    
    // Business methods
    public boolean hasWinner() {
        return winnerId != null && result == GameResult.WIN;
    }
    
    public boolean isDraw() {
        return result == GameResult.DRAW;
    }
    
    public boolean isWinner(Long userId) {
        return Objects.equals(winnerId, userId);
    }
    
    public boolean isDealer(Long userId) {
        return Objects.equals(dealerUserId, userId);
    }
    
    public void addPlayerRecord(GamePlayerRecord playerRecord) {
        playerRecords.add(playerRecord);
        playerRecord.setGameRecord(this);
    }
    
    public GamePlayerRecord getPlayerRecord(Long userId) {
        return playerRecords.stream()
                .filter(pr -> Objects.equals(pr.getUserId(), userId))
                .findFirst()
                .orElse(null);
    }
    
    public int getTotalPlayers() {
        return playerRecords.size();
    }
    
    public String getFormattedDuration() {
        if (durationSeconds == null) return "0:00";
        int minutes = durationSeconds / 60;
        int seconds = durationSeconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }
    
    // equals and hashCode
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GameRecord that = (GameRecord) o;
        return Objects.equals(id, that.id);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
    
    @Override
    public String toString() {
        return "GameRecord{" +
                "id='" + id + '\'' +
                ", roomId='" + roomId + '\'' +
                ", roundIndex=" + roundIndex +
                ", result=" + result +
                ", winnerId=" + winnerId +
                ", finalScore=" + finalScore +
                ", durationSeconds=" + durationSeconds +
                '}';
    }
}