package com.mahjong.service;

import com.mahjong.model.entity.GameRecord;
import com.mahjong.model.entity.Room;
import com.mahjong.model.entity.User;
import com.mahjong.model.enums.RoomStatus;
import com.mahjong.model.enums.UserStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Query Optimization Service
 * 
 * Provides optimized database queries for frequently accessed data
 * with proper pagination, sorting, and filtering.
 */
@Service
public class QueryOptimizationService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Get active users with pagination and optimized query
     */
    public List<Map<String, Object>> getActiveUsersOptimized(int page, int size) {
        String sql = """
            SELECT u.id, u.open_id, u.nickname, u.avatar, u.coins, u.room_cards, u.created_at
            FROM t_user u
            WHERE u.status = ?
            ORDER BY u.created_at DESC
            LIMIT ? OFFSET ?
            """;
        
        int offset = page * size;
        return jdbcTemplate.queryForList(sql, UserStatus.ACTIVE.name(), size, offset);
    }

    /**
     * Get user game statistics with optimized query
     */
    public Map<String, Object> getUserGameStatistics(Long userId) {
        String sql = """
            SELECT 
                COUNT(*) as total_games,
                SUM(CASE WHEN gpr.final_score > 0 THEN 1 ELSE 0 END) as wins,
                SUM(CASE WHEN gpr.final_score < 0 THEN 1 ELSE 0 END) as losses,
                AVG(gpr.final_score) as avg_score,
                MAX(gpr.final_score) as max_score,
                MIN(gpr.final_score) as min_score,
                SUM(gpr.final_score) as total_score
            FROM t_game_player_record gpr
            JOIN t_game_record gr ON gpr.game_record_id = gr.id
            WHERE gpr.user_id = ?
            AND gr.status = 'COMPLETED'
            """;
        
        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, userId);
        return results.isEmpty() ? Map.of() : results.get(0);
    }

    /**
     * Get room statistics with optimized query
     */
    public List<Map<String, Object>> getRoomStatistics(LocalDateTime fromDate, LocalDateTime toDate) {
        String sql = """
            SELECT 
                DATE(r.created_at) as date,
                COUNT(*) as rooms_created,
                COUNT(CASE WHEN r.status = 'COMPLETED' THEN 1 END) as rooms_completed,
                COUNT(CASE WHEN r.status = 'PLAYING' THEN 1 END) as rooms_active,
                AVG(CASE WHEN gr.end_time IS NOT NULL 
                    THEN TIMESTAMPDIFF(MINUTE, gr.start_time, gr.end_time) END) as avg_game_duration
            FROM t_room r
            LEFT JOIN t_game_record gr ON r.id = gr.room_id
            WHERE r.created_at BETWEEN ? AND ?
            GROUP BY DATE(r.created_at)
            ORDER BY date DESC
            """;
        
        return jdbcTemplate.queryForList(sql, fromDate, toDate);
    }

    /**
     * Get top players leaderboard with optimized query
     */
    public List<Map<String, Object>> getTopPlayersLeaderboard(int limit) {
        String sql = """
            SELECT 
                u.id,
                u.nickname,
                u.avatar,
                COUNT(gpr.id) as total_games,
                SUM(CASE WHEN gpr.final_score > 0 THEN 1 ELSE 0 END) as wins,
                SUM(gpr.final_score) as total_score,
                AVG(gpr.final_score) as avg_score,
                ROUND(
                    (SUM(CASE WHEN gpr.final_score > 0 THEN 1 ELSE 0 END) * 100.0 / COUNT(gpr.id)), 2
                ) as win_rate
            FROM t_user u
            JOIN t_game_player_record gpr ON u.id = gpr.user_id
            JOIN t_game_record gr ON gpr.game_record_id = gr.id
            WHERE u.status = 'ACTIVE'
            AND gr.status = 'COMPLETED'
            AND gr.created_at >= DATE_SUB(NOW(), INTERVAL 30 DAY)
            GROUP BY u.id, u.nickname, u.avatar
            HAVING total_games >= 10
            ORDER BY total_score DESC, win_rate DESC
            LIMIT ?
            """;
        
        return jdbcTemplate.queryForList(sql, limit);
    }

    /**
     * Get recent game activity with optimized query
     */
    public List<Map<String, Object>> getRecentGameActivity(int limit) {
        String sql = """
            SELECT 
                gr.id as game_id,
                gr.room_id,
                gr.start_time,
                gr.end_time,
                gr.status,
                COUNT(gpr.id) as player_count,
                MAX(gpr.final_score) as highest_score,
                MIN(gpr.final_score) as lowest_score
            FROM t_game_record gr
            JOIN t_game_player_record gpr ON gr.id = gpr.game_record_id
            WHERE gr.created_at >= DATE_SUB(NOW(), INTERVAL 24 HOUR)
            GROUP BY gr.id, gr.room_id, gr.start_time, gr.end_time, gr.status
            ORDER BY gr.created_at DESC
            LIMIT ?
            """;
        
        return jdbcTemplate.queryForList(sql, limit);
    }

    /**
     * Get system performance metrics with optimized query
     */
    public Map<String, Object> getSystemPerformanceMetrics() {
        String sql = """
            SELECT 
                (SELECT COUNT(*) FROM t_user WHERE status = 'ACTIVE') as active_users,
                (SELECT COUNT(*) FROM t_room WHERE status IN ('WAITING', 'PLAYING')) as active_rooms,
                (SELECT COUNT(*) FROM t_game_record WHERE status = 'PLAYING') as ongoing_games,
                (SELECT COUNT(*) FROM t_game_record WHERE DATE(created_at) = CURDATE()) as games_today,
                (SELECT AVG(TIMESTAMPDIFF(MINUTE, start_time, end_time)) 
                 FROM t_game_record 
                 WHERE status = 'COMPLETED' 
                 AND DATE(created_at) = CURDATE()) as avg_game_duration_today
            """;
        
        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql);
        return results.isEmpty() ? Map.of() : results.get(0);
    }

    /**
     * Clean up old inactive rooms with optimized query
     */
    public int cleanupInactiveRooms(int hoursOld) {
        String sql = """
            DELETE FROM t_room 
            WHERE status = 'WAITING' 
            AND created_at < DATE_SUB(NOW(), INTERVAL ? HOUR)
            AND id NOT IN (
                SELECT DISTINCT room_id 
                FROM t_game_record 
                WHERE status = 'PLAYING'
            )
            """;
        
        return jdbcTemplate.update(sql, hoursOld);
    }

    /**
     * Get user activity summary with optimized query
     */
    public Map<String, Object> getUserActivitySummary(Long userId, int days) {
        String sql = """
            SELECT 
                u.nickname,
                u.coins,
                u.room_cards,
                COUNT(DISTINCT r.id) as rooms_joined,
                COUNT(DISTINCT gr.id) as games_played,
                SUM(CASE WHEN gpr.final_score > 0 THEN 1 ELSE 0 END) as games_won,
                SUM(gpr.final_score) as total_score_change,
                MAX(gpr.final_score) as best_game_score,
                MIN(gpr.final_score) as worst_game_score
            FROM t_user u
            LEFT JOIN t_room_player rp ON u.id = rp.user_id
            LEFT JOIN t_room r ON rp.room_id = r.id
            LEFT JOIN t_game_record gr ON r.id = gr.room_id
            LEFT JOIN t_game_player_record gpr ON gr.id = gpr.game_record_id AND gpr.user_id = u.id
            WHERE u.id = ?
            AND (r.created_at IS NULL OR r.created_at >= DATE_SUB(NOW(), INTERVAL ? DAY))
            GROUP BY u.id, u.nickname, u.coins, u.room_cards
            """;
        
        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, userId, days);
        return results.isEmpty() ? Map.of() : results.get(0);
    }
}