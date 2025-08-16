package com.mahjong.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mahjong.model.dto.GameMessage;
import com.mahjong.model.dto.GameSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Service for broadcasting game messages to players
 */
@Service
public class GameMessageBroadcastService {
    
    private static final Logger logger = LoggerFactory.getLogger(GameMessageBroadcastService.class);
    
    private final SimpMessagingTemplate messagingTemplate;
    private final WebSocketSessionService sessionService;
    private final ObjectMapper objectMapper;
    
    @Autowired
    public GameMessageBroadcastService(SimpMessagingTemplate messagingTemplate,
                                     WebSocketSessionService sessionService,
                                     ObjectMapper objectMapper) {
        this.messagingTemplate = messagingTemplate;
        this.sessionService = sessionService;
        this.objectMapper = objectMapper;
    }
    
    /**
     * Send message to a specific user
     */
    public void sendToUser(String userId, GameMessage message) {
        try {
            if (!sessionService.isUserOnline(userId)) {
                logger.debug("User {} is not online, skipping message: {}", userId, message.getCommand());
                return;
            }
            
            messagingTemplate.convertAndSendToUser(userId, "/queue/game", message);
            logger.debug("Sent message to user {}: {}", userId, message.getCommand());
            
        } catch (Exception e) {
            logger.error("Failed to send message to user {}: {}", userId, e.getMessage(), e);
        }
    }
    
    /**
     * Broadcast message to all users in a room
     */
    public void broadcastToRoom(String roomId, GameMessage message) {
        broadcastToRoom(roomId, message, null);
    }
    
    /**
     * Broadcast message to all users in a room except one
     */
    public void broadcastToRoom(String roomId, GameMessage message, String excludeUserId) {
        try {
            Set<String> roomUsers = sessionService.getRoomUsers(roomId);
            if (roomUsers.isEmpty()) {
                logger.debug("No users in room {}, skipping broadcast", roomId);
                return;
            }
            
            int sentCount = 0;
            for (String userId : roomUsers) {
                if (!userId.equals(excludeUserId)) {
                    sendToUser(userId, message);
                    sentCount++;
                }
            }
            
            logger.debug("Broadcasted message to {} users in room {}: {}", 
                    sentCount, roomId, message.getCommand());
            
        } catch (Exception e) {
            logger.error("Failed to broadcast message to room {}: {}", roomId, e.getMessage(), e);
        }
    }
    
    /**
     * Broadcast message to specific users
     */
    public void broadcastToUsers(List<String> userIds, GameMessage message) {
        try {
            int sentCount = 0;
            for (String userId : userIds) {
                sendToUser(userId, message);
                sentCount++;
            }
            
            logger.debug("Broadcasted message to {} users: {}", sentCount, message.getCommand());
            
        } catch (Exception e) {
            logger.error("Failed to broadcast message to users: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Send game snapshot to a specific user
     */
    public void sendGameSnapshot(String userId, GameSnapshot snapshot) {
        GameMessage message = GameMessage.event("gameSnapshot", snapshot.getRoomId(), snapshot);
        sendToUser(userId, message);
    }
    
    /**
     * Broadcast game snapshot to all users in a room
     */
    public void broadcastGameSnapshot(String roomId, GameSnapshot snapshot) {
        GameMessage message = GameMessage.event("gameSnapshot", roomId, snapshot);
        broadcastToRoom(roomId, message);
    }
    
    /**
     * Send personalized game snapshot to each user in a room
     * (different users see different hand tiles)
     */
    public void broadcastPersonalizedSnapshots(String roomId, Map<String, GameSnapshot> userSnapshots) {
        try {
            Set<String> roomUsers = sessionService.getRoomUsers(roomId);
            
            for (String userId : roomUsers) {
                GameSnapshot personalSnapshot = userSnapshots.get(userId);
                if (personalSnapshot != null) {
                    sendGameSnapshot(userId, personalSnapshot);
                }
            }
            
            logger.debug("Sent personalized snapshots to {} users in room {}", 
                    userSnapshots.size(), roomId);
            
        } catch (Exception e) {
            logger.error("Failed to broadcast personalized snapshots to room {}: {}", 
                    roomId, e.getMessage(), e);
        }
    }
    
    /**
     * Send action result to the acting player
     */
    public void sendActionResult(String userId, String requestId, ActionResult result) {
        GameMessage message = GameMessage.response("gameAction", requestId, result);
        sendToUser(userId, message);
    }
    
    /**
     * Broadcast action event to other players in the room
     */
    public void broadcastActionEvent(String roomId, String actingUserId, String actionType, Object actionData) {
        Map<String, Object> eventData = Map.of(
                "userId", actingUserId,
                "actionType", actionType,
                "actionData", actionData,
                "timestamp", System.currentTimeMillis()
        );
        
        GameMessage message = GameMessage.event("playerAction", roomId, eventData);
        broadcastToRoom(roomId, message, actingUserId);
    }
    
    /**
     * Send error message to a user
     */
    public void sendError(String userId, String command, String requestId, String error) {
        GameMessage errorMessage = GameMessage.error(command, requestId, error);
        sendToUser(userId, errorMessage);
    }
    
    /**
     * Broadcast room event (user joined, left, etc.)
     */
    public void broadcastRoomEvent(String roomId, String eventType, Object eventData) {
        GameMessage message = GameMessage.event(eventType, roomId, eventData);
        broadcastToRoom(roomId, message);
    }
    
    /**
     * Send turn notification to the current player
     */
    public void sendTurnNotification(String userId, String roomId, List<String> availableActions, long turnDeadline) {
        Map<String, Object> turnData = Map.of(
                "availableActions", availableActions,
                "turnDeadline", turnDeadline,
                "timestamp", System.currentTimeMillis()
        );
        
        GameMessage message = GameMessage.event("yourTurn", roomId, turnData);
        sendToUser(userId, message);
    }
    
    /**
     * Broadcast turn change notification to all players
     */
    public void broadcastTurnChange(String roomId, String currentUserId, int currentPlayerIndex, long turnDeadline) {
        Map<String, Object> turnData = Map.of(
                "currentUserId", currentUserId,
                "currentPlayerIndex", currentPlayerIndex,
                "turnDeadline", turnDeadline,
                "timestamp", System.currentTimeMillis()
        );
        
        GameMessage message = GameMessage.event("turnChanged", roomId, turnData);
        broadcastToRoom(roomId, message);
    }
    
    /**
     * Broadcast game end event with settlement results
     */
    public void broadcastGameEnd(String roomId, Object settlementResult) {
        GameMessage message = GameMessage.event("gameEnd", roomId, settlementResult);
        broadcastToRoom(roomId, message);
    }
    
    /**
     * Send heartbeat response
     */
    public void sendHeartbeat(String userId, String requestId) {
        Map<String, Object> heartbeatData = Map.of(
                "timestamp", System.currentTimeMillis(),
                "status", "alive"
        );
        
        GameMessage message = GameMessage.response("heartbeat", requestId, heartbeatData);
        sendToUser(userId, message);
    }
    
    /**
     * Serialize object to JSON string for logging
     */
    private String toJsonString(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return obj.toString();
        }
    }
}