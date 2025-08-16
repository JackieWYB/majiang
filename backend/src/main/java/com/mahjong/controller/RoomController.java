package com.mahjong.controller;

import com.mahjong.model.dto.*;
import com.mahjong.model.entity.Room;
import com.mahjong.model.entity.RoomPlayer;
import com.mahjong.service.RoomService;
import com.mahjong.service.JwtTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST controller for room management operations
 */
@RestController
@RequestMapping("/api/rooms")
public class RoomController {
    
    private static final Logger logger = LoggerFactory.getLogger(RoomController.class);
    
    private final RoomService roomService;
    private final JwtTokenService jwtTokenService;
    
    @Autowired
    public RoomController(RoomService roomService, JwtTokenService jwtTokenService) {
        this.roomService = roomService;
        this.jwtTokenService = jwtTokenService;
    }
    
    /**
     * Create a new room
     */
    @PostMapping
    public ResponseEntity<ApiResponse<RoomResponse>> createRoom(
            @Valid @RequestBody CreateRoomRequest request,
            HttpServletRequest httpRequest) {
        
        try {
            Long userId = getCurrentUserId(httpRequest);
            logger.info("Creating room for user: {} with rule: {}", userId, request.getRuleId());
            
            Room room = roomService.createRoom(userId, request.getRuleId());
            RoomResponse response = new RoomResponse(room);
            
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request for room creation: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("INVALID_REQUEST", e.getMessage()));
        } catch (IllegalStateException e) {
            logger.warn("Room creation failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error("ROOM_CREATION_FAILED", e.getMessage()));
        } catch (Exception e) {
            logger.error("Unexpected error creating room", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("INTERNAL_ERROR", "Failed to create room"));
        }
    }
    
    /**
     * Join a room
     */
    @PostMapping("/join")
    public ResponseEntity<ApiResponse<RoomResponse>> joinRoom(
            @Valid @RequestBody JoinRoomRequest request,
            HttpServletRequest httpRequest) {
        
        try {
            Long userId = getCurrentUserId(httpRequest);
            logger.info("User {} joining room: {}", userId, request.getRoomId());
            
            RoomPlayer roomPlayer = roomService.joinRoom(request.getRoomId(), userId);
            Room room = roomService.getRoomById(request.getRoomId())
                    .orElseThrow(() -> new IllegalStateException("Room not found after join"));
            
            RoomResponse response = new RoomResponse(room);
            
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request for room join: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("INVALID_REQUEST", e.getMessage()));
        } catch (IllegalStateException e) {
            logger.warn("Room join failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error("ROOM_JOIN_FAILED", e.getMessage()));
        } catch (Exception e) {
            logger.error("Unexpected error joining room", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("INTERNAL_ERROR", "Failed to join room"));
        }
    }
    
    /**
     * Leave a room
     */
    @PostMapping("/{roomId}/leave")
    public ResponseEntity<ApiResponse<Void>> leaveRoom(
            @PathVariable String roomId,
            HttpServletRequest httpRequest) {
        
        try {
            Long userId = getCurrentUserId(httpRequest);
            logger.info("User {} leaving room: {}", userId, roomId);
            
            roomService.leaveRoom(roomId, userId);
            
            return ResponseEntity.ok(ApiResponse.success(null));
            
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request for room leave: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("INVALID_REQUEST", e.getMessage()));
        } catch (Exception e) {
            logger.error("Unexpected error leaving room", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("INTERNAL_ERROR", "Failed to leave room"));
        }
    }
    
    /**
     * Dissolve a room (owner only)
     */
    @PostMapping("/{roomId}/dissolve")
    public ResponseEntity<ApiResponse<Void>> dissolveRoom(
            @PathVariable String roomId,
            HttpServletRequest httpRequest) {
        
        try {
            Long userId = getCurrentUserId(httpRequest);
            logger.info("User {} dissolving room: {}", userId, roomId);
            
            roomService.dissolveRoom(roomId, userId);
            
            return ResponseEntity.ok(ApiResponse.success(null));
            
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request for room dissolution: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("INVALID_REQUEST", e.getMessage()));
        } catch (IllegalStateException e) {
            logger.warn("Room dissolution failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("ROOM_DISSOLUTION_FAILED", e.getMessage()));
        } catch (Exception e) {
            logger.error("Unexpected error dissolving room", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("INTERNAL_ERROR", "Failed to dissolve room"));
        }
    }
    
    /**
     * Get room details
     */
    @GetMapping("/{roomId}")
    public ResponseEntity<ApiResponse<RoomResponse>> getRoomDetails(
            @PathVariable String roomId,
            HttpServletRequest httpRequest) {
        
        try {
            Long userId = getCurrentUserId(httpRequest);
            logger.debug("User {} requesting room details: {}", userId, roomId);
            
            Room room = roomService.getRoomById(roomId)
                    .orElseThrow(() -> new IllegalArgumentException("Room not found: " + roomId));
            
            // Check if user has access to this room (is a player or owner)
            if (!room.hasPlayer(userId) && !room.isOwner(userId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ApiResponse.error("ACCESS_DENIED", "You don't have access to this room"));
            }
            
            RoomResponse response = new RoomResponse(room);
            
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request for room details: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("INVALID_REQUEST", e.getMessage()));
        } catch (Exception e) {
            logger.error("Unexpected error getting room details", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("INTERNAL_ERROR", "Failed to get room details"));
        }
    }
    
    /**
     * Get user's rooms
     */
    @GetMapping("/my-rooms")
    public ResponseEntity<ApiResponse<List<RoomResponse>>> getMyRooms(
            HttpServletRequest httpRequest) {
        
        try {
            Long userId = getCurrentUserId(httpRequest);
            logger.debug("User {} requesting their rooms", userId);
            
            List<Room> rooms = roomService.getRoomsByUserId(userId);
            List<RoomResponse> response = rooms.stream()
                    .map(RoomResponse::new)
                    .collect(Collectors.toList());
            
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (Exception e) {
            logger.error("Unexpected error getting user rooms", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("INTERNAL_ERROR", "Failed to get user rooms"));
        }
    }
    
    /**
     * Update player ready status
     */
    @PostMapping("/{roomId}/ready")
    public ResponseEntity<ApiResponse<Void>> updateReadyStatus(
            @PathVariable String roomId,
            @RequestParam boolean ready,
            HttpServletRequest httpRequest) {
        
        try {
            Long userId = getCurrentUserId(httpRequest);
            logger.info("User {} updating ready status in room {}: {}", userId, roomId, ready);
            
            roomService.updatePlayerReadyStatus(roomId, userId, ready);
            
            return ResponseEntity.ok(ApiResponse.success(null));
            
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request for ready status update: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("INVALID_REQUEST", e.getMessage()));
        } catch (Exception e) {
            logger.error("Unexpected error updating ready status", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("INTERNAL_ERROR", "Failed to update ready status"));
        }
    }
    
    /**
     * Get active rooms (for admin or debugging)
     */
    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<RoomResponse>>> getActiveRooms(
            HttpServletRequest httpRequest) {
        
        try {
            Long userId = getCurrentUserId(httpRequest);
            logger.debug("User {} requesting active rooms", userId);
            
            List<Room> rooms = roomService.getActiveRooms();
            List<RoomResponse> response = rooms.stream()
                    .map(RoomResponse::new)
                    .collect(Collectors.toList());
            
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (Exception e) {
            logger.error("Unexpected error getting active rooms", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("INTERNAL_ERROR", "Failed to get active rooms"));
        }
    }
    
    /**
     * Extract user ID from JWT token in request
     */
    private Long getCurrentUserId(HttpServletRequest request) {
        String token = extractTokenFromRequest(request);
        if (token == null) {
            throw new IllegalArgumentException("No authentication token provided");
        }
        
        return jwtTokenService.extractUserId(token);
    }
    
    /**
     * Extract JWT token from Authorization header
     */
    private String extractTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}