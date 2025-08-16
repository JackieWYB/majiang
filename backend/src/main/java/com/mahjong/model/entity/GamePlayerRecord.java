package com.mahjong.model.entity;

import com.mahjong.model.enums.GameResult;
import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.Objects;

/**
 * Game player record entity for storing individual player results in a game
 */
@Entity
@Table(name = "t_game_player_record",
       uniqueConstraints = {
           @UniqueConstraint(name = "uk_game_player", columnNames = {"gameRecordId", "userId"})
       },
       indexes = {
           @Index(name = "idx_game_player_record_game_id", columnList = "gameRecordId"),
           @Index(name = "idx_game_player_record_user_id", columnList = "userId")
       })
public class GamePlayerRecord {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @NotNull
    @Column(name = "game_record_id", nullable = false, length = 36)
    private String gameRecordId;
    
    @NotNull
    @Column(name = "user_id", nullable = false)
    private Long userId;
    
    @NotNull
    @Min(0)
    @Max(2)
    @Column(name = "seat_index", nullable = false)
    private Integer seatIndex;
    
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, length = 10)
    private GameResult result;
    
    @NotNull
    @Column(name = "score", nullable = false)
    private Integer score = 0;
    
    @NotNull
    @Column(name = "base_score", nullable = false)
    private Integer baseScore = 0;
    
    @NotNull
    @Column(name = "gang_score", nullable = false)
    private Integer gangScore = 0;
    
    @NotNull
    @Column(name = "multiplier", nullable = false)
    private Double multiplier = 1.0;
    
    @NotNull
    @Column(name = "is_dealer", nullable = false)
    private Boolean isDealer = false;
    
    @NotNull
    @Column(name = "is_self_draw", nullable = false)
    private Boolean isSelfDraw = false;
    
    @Column(name = "winning_hand", columnDefinition = "TEXT")
    private String winningHand; // JSON representation of winning hand
    
    @Column(name = "final_hand", columnDefinition = "TEXT")
    private String finalHand; // JSON representation of final hand
    
    @Column(name = "melds", columnDefinition = "TEXT")
    private String melds; // JSON representation of melds (peng, gang)
    
    // Relationships
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_record_id", insertable = false, updatable = false)
    private GameRecord gameRecord;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;
    
    // Constructors
    public GamePlayerRecord() {}
    
    public GamePlayerRecord(String gameRecordId, Long userId, Integer seatIndex, GameResult result) {
        this.gameRecordId = gameRecordId;
        this.userId = userId;
        this.seatIndex = seatIndex;
        this.result = result;
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getGameRecordId() {
        return gameRecordId;
    }
    
    public void setGameRecordId(String gameRecordId) {
        this.gameRecordId = gameRecordId;
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
    
    public GameResult getResult() {
        return result;
    }
    
    public void setResult(GameResult result) {
        this.result = result;
    }
    
    public Integer getScore() {
        return score;
    }
    
    public void setScore(Integer score) {
        this.score = score;
    }
    
    public Integer getBaseScore() {
        return baseScore;
    }
    
    public void setBaseScore(Integer baseScore) {
        this.baseScore = baseScore;
    }
    
    public Integer getGangScore() {
        return gangScore;
    }
    
    public void setGangScore(Integer gangScore) {
        this.gangScore = gangScore;
    }
    
    public Double getMultiplier() {
        return multiplier;
    }
    
    public void setMultiplier(Double multiplier) {
        this.multiplier = multiplier;
    }
    
    public Boolean getIsDealer() {
        return isDealer;
    }
    
    public void setIsDealer(Boolean isDealer) {
        this.isDealer = isDealer;
    }
    
    public Boolean getIsSelfDraw() {
        return isSelfDraw;
    }
    
    public void setIsSelfDraw(Boolean isSelfDraw) {
        this.isSelfDraw = isSelfDraw;
    }
    
    public String getWinningHand() {
        return winningHand;
    }
    
    public void setWinningHand(String winningHand) {
        this.winningHand = winningHand;
    }
    
    public String getFinalHand() {
        return finalHand;
    }
    
    public void setFinalHand(String finalHand) {
        this.finalHand = finalHand;
    }
    
    public String getMelds() {
        return melds;
    }
    
    public void setMelds(String melds) {
        this.melds = melds;
    }
    
    public GameRecord getGameRecord() {
        return gameRecord;
    }
    
    public void setGameRecord(GameRecord gameRecord) {
        this.gameRecord = gameRecord;
    }
    
    public User getUser() {
        return user;
    }
    
    public void setUser(User user) {
        this.user = user;
    }
    
    // Business methods
    public boolean isWinner() {
        return result == GameResult.WIN;
    }
    
    public boolean isLoser() {
        return result == GameResult.LOSE;
    }
    
    public boolean isDraw() {
        return result == GameResult.DRAW;
    }
    
    public boolean isDealer() {
        return Boolean.TRUE.equals(isDealer);
    }
    
    public boolean isSelfDraw() {
        return Boolean.TRUE.equals(isSelfDraw);
    }
    
    public int getTotalScore() {
        return baseScore + gangScore;
    }
    
    public int getFinalScore() {
        return (int) (getTotalScore() * multiplier);
    }
    
    public void addGangScore(Integer points) {
        if (points != null && points > 0) {
            this.gangScore += points;
        }
    }
    
    // equals and hashCode
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GamePlayerRecord that = (GamePlayerRecord) o;
        return Objects.equals(gameRecordId, that.gameRecordId) && Objects.equals(userId, that.userId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(gameRecordId, userId);
    }
    
    @Override
    public String toString() {
        return "GamePlayerRecord{" +
                "id=" + id +
                ", gameRecordId='" + gameRecordId + '\'' +
                ", userId=" + userId +
                ", seatIndex=" + seatIndex +
                ", result=" + result +
                ", score=" + score +
                ", isDealer=" + isDealer +
                ", isSelfDraw=" + isSelfDraw +
                '}';
    }
}