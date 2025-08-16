package com.mahjong.repository;

import com.mahjong.model.entity.Room;
import com.mahjong.model.enums.RoomStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Room entity
 */
@Repository
public interface RoomRepository extends JpaRepository<Room, String> {
    
    /**
     * Find room by ID with players loaded
     */
    @Query("SELECT r FROM Room r LEFT JOIN FETCH r.players WHERE r.id = :id")
    Optional<Room> findByIdWithPlayers(@Param("id") String id);
    
    /**
     * Find rooms by owner ID
     */
    List<Room> findByOwnerIdAndStatusIn(Long ownerId, List<RoomStatus> statuses);
    
    /**
     * Find active rooms (waiting, ready, playing)
     */
    @Query("SELECT r FROM Room r WHERE r.status IN ('WAITING', 'READY', 'PLAYING')")
    List<Room> findActiveRooms();
    
    /**
     * Find inactive rooms that should be cleaned up
     */
    @Query("SELECT r FROM Room r WHERE r.status IN ('WAITING', 'READY') AND r.lastActivityAt < :cutoffTime")
    List<Room> findInactiveRooms(@Param("cutoffTime") LocalDateTime cutoffTime);
    
    /**
     * Find rooms by status
     */
    List<Room> findByStatus(RoomStatus status);
    
    /**
     * Count active rooms by owner
     */
    @Query("SELECT COUNT(r) FROM Room r WHERE r.ownerId = :ownerId AND r.status IN ('WAITING', 'READY', 'PLAYING')")
    long countActiveRoomsByOwner(@Param("ownerId") Long ownerId);
    
    /**
     * Check if room ID exists
     */
    boolean existsById(String id);
    
    /**
     * Find rooms that a user is participating in
     */
    @Query("SELECT DISTINCT r FROM Room r JOIN r.players p WHERE p.userId = :userId AND r.status IN ('WAITING', 'READY', 'PLAYING')")
    List<Room> findRoomsByUserId(@Param("userId") Long userId);
    
    /**
     * Count rooms by status
     */
    long countByStatus(RoomStatus status);
}