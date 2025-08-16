package com.mahjong.repository;

import com.mahjong.model.entity.RoomPlayer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for RoomPlayer entity
 */
@Repository
public interface RoomPlayerRepository extends JpaRepository<RoomPlayer, Long> {
    
    /**
     * Find room player by room ID and user ID
     */
    Optional<RoomPlayer> findByRoomIdAndUserId(String roomId, Long userId);
    
    /**
     * Find all players in a room
     */
    List<RoomPlayer> findByRoomIdOrderBySeatIndex(String roomId);
    
    /**
     * Find all rooms a user is in
     */
    List<RoomPlayer> findByUserId(Long userId);
    
    /**
     * Count players in a room
     */
    long countByRoomId(String roomId);
    
    /**
     * Check if user is in room
     */
    boolean existsByRoomIdAndUserId(String roomId, Long userId);
    
    /**
     * Find next available seat index in room
     */
    @Query("SELECT COALESCE(MIN(s.seatIndex), 0) FROM " +
           "(SELECT 0 as seatIndex UNION SELECT 1 UNION SELECT 2) s " +
           "WHERE s.seatIndex NOT IN (SELECT p.seatIndex FROM RoomPlayer p WHERE p.roomId = :roomId)")
    Integer findNextAvailableSeatIndex(@Param("roomId") String roomId);
    
    /**
     * Delete all players from a room
     */
    @Modifying
    @Query("DELETE FROM RoomPlayer p WHERE p.roomId = :roomId")
    void deleteByRoomId(@Param("roomId") String roomId);
    
    /**
     * Update player online status
     */
    @Modifying
    @Query("UPDATE RoomPlayer p SET p.isOnline = :isOnline, p.lastSeenAt = CURRENT_TIMESTAMP WHERE p.roomId = :roomId AND p.userId = :userId")
    void updatePlayerOnlineStatus(@Param("roomId") String roomId, @Param("userId") Long userId, @Param("isOnline") Boolean isOnline);
    
    /**
     * Update player ready status
     */
    @Modifying
    @Query("UPDATE RoomPlayer p SET p.isReady = :isReady WHERE p.roomId = :roomId AND p.userId = :userId")
    void updatePlayerReadyStatus(@Param("roomId") String roomId, @Param("userId") Long userId, @Param("isReady") Boolean isReady);
}