package com.mahjong.repository;

import com.mahjong.model.entity.GameRecord;
import com.mahjong.model.enums.GameResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for GameRecord entity
 */
@Repository
public interface GameRecordRepository extends JpaRepository<GameRecord, String> {
    
    /**
     * Find game records by room ID
     */
    List<GameRecord> findByRoomIdOrderByCreatedAtDesc(String roomId);
    
    /**
     * Find game records by room ID with pagination
     */
    Page<GameRecord> findByRoomIdOrderByCreatedAtDesc(String roomId, Pageable pageable);
    
    /**
     * Find game records where a specific user participated
     */
    @Query("SELECT DISTINCT gr FROM GameRecord gr " +
           "JOIN gr.playerRecords pr " +
           "WHERE pr.userId = :userId " +
           "ORDER BY gr.createdAt DESC")
    Page<GameRecord> findByPlayerUserId(@Param("userId") Long userId, Pageable pageable);
    
    /**
     * Find game records where a specific user participated in a date range
     */
    @Query("SELECT DISTINCT gr FROM GameRecord gr " +
           "JOIN gr.playerRecords pr " +
           "WHERE pr.userId = :userId " +
           "AND gr.createdAt BETWEEN :startDate AND :endDate " +
           "ORDER BY gr.createdAt DESC")
    Page<GameRecord> findByPlayerUserIdAndDateRange(
            @Param("userId") Long userId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable);
    
    /**
     * Find game records by winner
     */
    Page<GameRecord> findByWinnerIdOrderByCreatedAtDesc(Long winnerId, Pageable pageable);
    
    /**
     * Find game records by result type
     */
    Page<GameRecord> findByResultOrderByCreatedAtDesc(GameResult result, Pageable pageable);
    
    /**
     * Find recent game records (last N days)
     */
    @Query("SELECT gr FROM GameRecord gr " +
           "WHERE gr.createdAt >= :sinceDate " +
           "ORDER BY gr.createdAt DESC")
    Page<GameRecord> findRecentGames(@Param("sinceDate") LocalDateTime sinceDate, Pageable pageable);
    
    /**
     * Count total games played by a user
     */
    @Query("SELECT COUNT(DISTINCT gr) FROM GameRecord gr " +
           "JOIN gr.playerRecords pr " +
           "WHERE pr.userId = :userId")
    Long countGamesByUserId(@Param("userId") Long userId);
    
    /**
     * Count wins by a user
     */
    @Query("SELECT COUNT(DISTINCT gr) FROM GameRecord gr " +
           "JOIN gr.playerRecords pr " +
           "WHERE pr.userId = :userId AND pr.result = 'WIN'")
    Long countWinsByUserId(@Param("userId") Long userId);
    
    /**
     * Get user's total score across all games
     */
    @Query("SELECT COALESCE(SUM(pr.score), 0) FROM GamePlayerRecord pr " +
           "WHERE pr.userId = :userId")
    Long getTotalScoreByUserId(@Param("userId") Long userId);
    
    /**
     * Find games with replay data (non-null gameActions)
     */
    @Query("SELECT gr FROM GameRecord gr " +
           "WHERE gr.gameActions IS NOT NULL " +
           "ORDER BY gr.createdAt DESC")
    Page<GameRecord> findGamesWithReplayData(Pageable pageable);
    
    /**
     * Find game record by room ID and round index
     */
    Optional<GameRecord> findByRoomIdAndRoundIndex(String roomId, Integer roundIndex);
    
    /**
     * Get average game duration
     */
    @Query("SELECT AVG(gr.durationSeconds) FROM GameRecord gr " +
           "WHERE gr.durationSeconds IS NOT NULL")
    Double getAverageGameDuration();
    
    /**
     * Get games statistics for a date range
     */
    @Query("SELECT COUNT(gr), AVG(gr.durationSeconds), " +
           "SUM(CASE WHEN gr.result = 'WIN' THEN 1 ELSE 0 END), " +
           "SUM(CASE WHEN gr.result = 'DRAW' THEN 1 ELSE 0 END) " +
           "FROM GameRecord gr " +
           "WHERE gr.createdAt BETWEEN :startDate AND :endDate")
    Object[] getGameStatistics(@Param("startDate") LocalDateTime startDate, 
                              @Param("endDate") LocalDateTime endDate);
    
    /**
     * Delete old game records (for cleanup)
     */
    @Query("DELETE FROM GameRecord gr WHERE gr.createdAt < :cutoffDate")
    void deleteOldRecords(@Param("cutoffDate") LocalDateTime cutoffDate);
    
    /**
     * Count game records created between dates
     */
    long countByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate);
}