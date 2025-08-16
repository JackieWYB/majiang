package com.mahjong.service;

import com.mahjong.model.entity.Room;
import com.mahjong.model.entity.RoomPlayer;
import com.mahjong.model.entity.User;
import com.mahjong.model.enums.RoomStatus;
import com.mahjong.repository.RoomPlayerRepository;
import com.mahjong.repository.RoomRepository;
import com.mahjong.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service for room management operations
 */
@Service
@Transactional
public class RoomService {
    
    private static final Logger logger = LoggerFactory.getLogger(RoomService.class);
    private static final int MAX_ROOM_CREATION_ATTEMPTS = 10;
    private static final int ROOM_INACTIVITY_MINUTES = 30;
    private static final int MAX_ACTIVE_ROOMS_PER_USER = 3;
    
    private final RoomRepository roomRepository;
    private final RoomPlayerRepository roomPlayerRepository;
    private final UserRepository userRepository;
    private final SecureRandom random = new SecureRandom();
    
    @Autowired
    public RoomService(RoomRepository roomRepository, 
                      RoomPlayerRepository roomPlayerRepository,
                      UserRepository userRepository) {
        this.roomRepository = roomRepository;
        this.roomPlayerRepository = roomPlayerRepository;
        this.userRepository = userRepository;
    }
    
    /**
     * Create a new room
     */
    public Room createRoom(Long ownerId, Long ruleId) {
        logger.info("Creating room for owner: {}, rule: {}", ownerId, ruleId);
        
        // Validate owner exists
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new IllegalArgumentException("Owner not found: " + ownerId));
        
        // Check if user already has too many active rooms
        long activeRoomCount = roomRepository.countActiveRoomsByOwner(ownerId);
        if (activeRoomCount >= MAX_ACTIVE_ROOMS_PER_USER) {
            throw new IllegalStateException("User already has maximum number of active rooms: " + MAX_ACTIVE_ROOMS_PER_USER);
        }
        
        // Generate unique room ID
        String roomId = generateUniqueRoomId();
        
        // Create room
        Room room = new Room(roomId, ownerId, ruleId);
        room = roomRepository.save(room);
        
        // Add owner as first player
        RoomPlayer ownerPlayer = new RoomPlayer(roomId, ownerId, 0);
        roomPlayerRepository.save(ownerPlayer);
        
        logger.info("Created room: {} for owner: {}", roomId, ownerId);
        return room;
    }
    
    /**
     * Join a room
     */
    public RoomPlayer joinRoom(String roomId, Long userId) {
        logger.info("User {} attempting to join room: {}", userId, roomId);
        
        // Validate user exists
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        
        // Find room
        Room room = roomRepository.findByIdWithPlayers(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Room not found: " + roomId));
        
        // Validate room state
        if (room.getStatus() != RoomStatus.WAITING) {
            throw new IllegalStateException("Room is not accepting new players: " + room.getStatus());
        }
        
        if (room.isFull()) {
            throw new IllegalStateException("Room is full");
        }
        
        if (room.hasPlayer(userId)) {
            throw new IllegalStateException("User already in room");
        }
        
        // Check if user is already in another active room
        List<Room> userRooms = roomRepository.findRoomsByUserId(userId);
        if (!userRooms.isEmpty()) {
            throw new IllegalStateException("User is already in another active room");
        }
        
        // Find next available seat
        Integer seatIndex = roomPlayerRepository.findNextAvailableSeatIndex(roomId);
        if (seatIndex == null || seatIndex > 2) {
            throw new IllegalStateException("No available seats in room");
        }
        
        // Add player to room
        RoomPlayer roomPlayer = new RoomPlayer(roomId, userId, seatIndex);
        roomPlayer = roomPlayerRepository.save(roomPlayer);
        
        // Update room status if full
        if (room.getPlayers().size() + 1 >= 3) {
            room.setStatus(RoomStatus.READY);
            roomRepository.save(room);
        }
        
        room.updateActivity();
        roomRepository.save(room);
        
        logger.info("User {} joined room: {} at seat: {}", userId, roomId, seatIndex);
        return roomPlayer;
    }
    
    /**
     * Leave a room
     */
    public void leaveRoom(String roomId, Long userId) {
        logger.info("User {} leaving room: {}", userId, roomId);
        
        Room room = roomRepository.findByIdWithPlayers(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Room not found: " + roomId));
        
        RoomPlayer roomPlayer = roomPlayerRepository.findByRoomIdAndUserId(roomId, userId)
                .orElseThrow(() -> new IllegalArgumentException("User not in room"));
        
        // Remove player
        roomPlayerRepository.delete(roomPlayer);
        
        // Handle room state after player leaves
        long remainingPlayers = roomPlayerRepository.countByRoomId(roomId);
        
        if (remainingPlayers == 0) {
            // No players left, dissolve room
            dissolveRoom(roomId, userId);
        } else if (room.isOwner(userId)) {
            // Owner left, transfer ownership
            transferOwnership(roomId);
        } else {
            // Regular player left, update room status
            if (room.getStatus() == RoomStatus.READY && remainingPlayers < 3) {
                room.setStatus(RoomStatus.WAITING);
            }
            room.updateActivity();
            roomRepository.save(room);
        }
        
        logger.info("User {} left room: {}", userId, roomId);
    }
    
    /**
     * Dissolve a room
     */
    public void dissolveRoom(String roomId, Long requesterId) {
        logger.info("Dissolving room: {} by user: {}", roomId, requesterId);
        
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Room not found: " + roomId));
        
        // Only owner can dissolve room (or system for cleanup)
        if (requesterId != null && !room.isOwner(requesterId)) {
            throw new IllegalStateException("Only room owner can dissolve room");
        }
        
        // Remove all players
        roomPlayerRepository.deleteByRoomId(roomId);
        
        // Update room status
        room.setStatus(RoomStatus.DISSOLVED);
        roomRepository.save(room);
        
        logger.info("Room dissolved: {}", roomId);
    }
    
    /**
     * Transfer room ownership to another player
     */
    private void transferOwnership(String roomId) {
        logger.info("Transferring ownership for room: {}", roomId);
        
        Room room = roomRepository.findByIdWithPlayers(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Room not found: " + roomId));
        
        List<RoomPlayer> players = roomPlayerRepository.findByRoomIdOrderBySeatIndex(roomId);
        if (!players.isEmpty()) {
            // Transfer to first remaining player
            RoomPlayer newOwner = players.get(0);
            room.setOwnerId(newOwner.getUserId());
            room.updateActivity();
            roomRepository.save(room);
            
            logger.info("Ownership transferred to user: {} for room: {}", newOwner.getUserId(), roomId);
        }
    }
    
    /**
     * Get room details
     */
    @Transactional(readOnly = true)
    public Optional<Room> getRoomById(String roomId) {
        return roomRepository.findByIdWithPlayers(roomId);
    }
    
    /**
     * Get rooms by user ID
     */
    @Transactional(readOnly = true)
    public List<Room> getRoomsByUserId(Long userId) {
        return roomRepository.findRoomsByUserId(userId);
    }
    
    /**
     * Get active rooms
     */
    @Transactional(readOnly = true)
    public List<Room> getActiveRooms() {
        return roomRepository.findActiveRooms();
    }
    
    /**
     * Update player ready status
     */
    public void updatePlayerReadyStatus(String roomId, Long userId, boolean isReady) {
        logger.info("Updating ready status for user {} in room {}: {}", userId, roomId, isReady);
        
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Room not found: " + roomId));
        
        if (!room.hasPlayer(userId)) {
            throw new IllegalArgumentException("User not in room");
        }
        
        roomPlayerRepository.updatePlayerReadyStatus(roomId, userId, isReady);
        room.updateActivity();
        roomRepository.save(room);
    }
    
    /**
     * Update player online status
     */
    public void updatePlayerOnlineStatus(String roomId, Long userId, boolean isOnline) {
        logger.info("Updating online status for user {} in room {}: {}", userId, roomId, isOnline);
        
        roomPlayerRepository.updatePlayerOnlineStatus(roomId, userId, isOnline);
        
        Room room = roomRepository.findById(roomId).orElse(null);
        if (room != null) {
            room.updateActivity();
            roomRepository.save(room);
        }
    }
    
    /**
     * Generate unique 6-digit room ID
     */
    private String generateUniqueRoomId() {
        for (int attempt = 0; attempt < MAX_ROOM_CREATION_ATTEMPTS; attempt++) {
            String roomId = String.format("%06d", random.nextInt(1000000));
            if (!roomRepository.existsById(roomId)) {
                return roomId;
            }
        }
        throw new IllegalStateException("Failed to generate unique room ID after " + MAX_ROOM_CREATION_ATTEMPTS + " attempts");
    }
    
    /**
     * Scheduled task to clean up inactive rooms
     */
    @Scheduled(fixedRate = 300000) // Run every 5 minutes
    public void cleanupInactiveRooms() {
        logger.debug("Running inactive room cleanup");
        
        LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(ROOM_INACTIVITY_MINUTES);
        List<Room> inactiveRooms = roomRepository.findInactiveRooms(cutoffTime);
        
        for (Room room : inactiveRooms) {
            try {
                logger.info("Cleaning up inactive room: {}", room.getId());
                dissolveRoom(room.getId(), null); // System cleanup
            } catch (Exception e) {
                logger.error("Failed to cleanup room: {}", room.getId(), e);
            }
        }
        
        if (!inactiveRooms.isEmpty()) {
            logger.info("Cleaned up {} inactive rooms", inactiveRooms.size());
        }
    }
}