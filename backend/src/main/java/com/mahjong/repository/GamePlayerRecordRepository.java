package com.mahjong.repository;

import com.mahjong.model.entity.GamePlayerRecord;
import com.mahjong.model.enums.GameResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for GamePlayerRecord entity
 */
@Repository
public interface GamePlayerRecordRepository extends JpaRepository<GamePlayerRecord, Long> {
    
    /**
     * Find player records by game record ID
     */
    List<GamePlayerRecord> findByGameRecordIdOrderBySeatIndex(String gameRecordId);
    
    /**
     * Find player records by user ID
     */
    Page<GamePlayerRecord> findByUserIdOrderByIdDesc(Long userId, Pageable pageable);
    
    /**
     * Find player records by user ID and result
     */
    Page<GamePlayerRecord> findByUserIdAndResultOrderByIdDesc(Long userId, GameResult result, Pageable pageable);
    
    /**
     * Get player statistics for a specific user
     */
    @Query("SELECT " +
           "COUNT(gpr), " +
           "SUM(CASE WHEN gpr.result = 'WIN' THEN 1 ELSE 0 END), " +
           "SUM(CASE WHEN gpr.result = 'LOSE' THEN 1 ELSE 0 END), " +
           "SUM(CASE WHEN gpr.result = 'DRAW' THEN 1 ELSE 0 END), " +
           "COALESCE(SUM(gpr.score), 0), " +
           "COALESCE(AVG(gpr.score), 0) " +
           "FROM GamePlayerRecord gpr " +
           "WHERE gpr.userId = :userId")
    Object[] getPlayerStatistics(@Param("userId") Long userId);
    
    /**
     * Get player statistics for a date range
     */
    @Query("SELECT " +
           "COUNT(gpr), " +
           "SUM(CASE WHEN gpr.result = 'WIN' THEN 1 ELSE 0 END), " +
           "SUM(CASE WHEN gpr.result = 'LOSE' THEN 1 ELSE 0 END), " +
           "SUM(CASE WHEN gpr.result = 'DRAW' THEN 1 ELSE 0 END), " +
           "COALESCE(SUM(gpr.score), 0), " +
           "COALESCE(AVG(gpr.score), 0) " +
           "FROM GamePlayerRecord gpr " +
           "JOIN gpr.gameRecord gr " +
           "WHERE gpr.userId = :userId " +
           "AND gr.createdAt BETWEEN :startDate AND :endDate")
    Object[] getPlayerStatisticsForDateRange(
            @Param("userId") Long userId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
    
    /**
     * Get recent performance for a user (last N games)
     */
    @Query("SELECT gpr FROM GamePlayerRecord gpr " +
           "JOIN gpr.gameRecord gr " +
           "WHERE gpr.userId = :userId " +
           "ORDER BY gr.createdAt DESC")
    Page<GamePlayerRecord> getRecentPerformance(@Param("userId") Long userId, Pageable pageable);
    
    /**
     * Get win rate for a user
     */
    @Query("SELECT " +
           "CAST(SUM(CASE WHEN gpr.result = 'WIN' THEN 1 ELSE 0 END) AS DOUBLE) / COUNT(gpr) " +
           "FROM GamePlayerRecord gpr " +
           "WHERE gpr.userId = :userId")
    Double getWinRate(@Param("userId") Long userId);
    
    /**
     * Get average score for a user
     */
    @Query("SELECT AVG(gpr.score) FROM GamePlayerRecord gpr " +
           "WHERE gpr.userId = :userId")
    Double getAverageScore(@Param("userId") Long userId);
    
    /**
     * Get dealer performance statistics
     */
    @Query("SELECT " +
           "COUNT(gpr), " +
           "SUM(CASE WHEN gpr.result = 'WIN' THEN 1 ELSE 0 END), " +
           "COALESCE(AVG(gpr.score), 0) " +
           "FROM GamePlayerRecord gpr " +
           "WHERE gpr.userId = :userId AND gpr.isDealer = true")
    Object[] getDealerStatistics(@Param("userId") Long userId);
    
    /**
     * Get self-draw statistics
     */
    @Query("SELECT " +
           "COUNT(gpr), " +
           "SUM(CASE WHEN gpr.result = 'WIN' THEN 1 ELSE 0 END) " +
           "FROM GamePlayerRecord gpr " +
           "WHERE gpr.userId = :userId AND gpr.isSelfDraw = true")
    Object[] getSelfDrawStatistics(@Param("userId") Long userId);
    
    /**
     * Find top performers by total score
     */
    @Query("SELECT gpr.userId, SUM(gpr.score) as totalScore " +
           "FROM GamePlayerRecord gpr " +
           "GROUP BY gpr.userId " +
           "ORDER BY totalScore DESC")
    Page<Object[]> findTopPerformersByScore(Pageable pageable);
    
    /**
     * Find top performers by win rate
     */
    @Query("SELECT gpr.userId, " +
           "CAST(SUM(CASE WHEN gpr.result = 'WIN' THEN 1 ELSE 0 END) AS DOUBLE) / COUNT(gpr) as winRate, " +
           "COUNT(gpr) as totalGames " +
           "FROM GamePlayerRecord gpr " +
           "GROUP BY gpr.userId " +
           "HAVING COUNT(gpr) >= :minGames " +
           "ORDER BY winRate DESC")
    Page<Object[]> findTopPerformersByWinRate(@Param("minGames") Long minGames, Pageable pageable);
    
    /**
     * Get best and worst scores for a user
     */
    @Query("SELECT MAX(gpr.score), MIN(gpr.score) FROM GamePlayerRecord gpr " +
           "WHERE gpr.userId = :userId")
    Object[] getBestAndWorstScores(@Param("userId") Long userId);
    
    /**
     * Get recent games for streak calculation
     */
    @Query("SELECT gpr FROM GamePlayerRecord gpr " +
           "JOIN gpr.gameRecord gr " +
           "WHERE gpr.userId = :userId " +
           "ORDER BY gr.createdAt DESC")
    List<GamePlayerRecord> getRecentGamesForStreaks(@Param("userId") Long userId, Pageable pageable);
}