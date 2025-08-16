package com.mahjong.controller;

import com.mahjong.model.dto.GameMessage;
import com.mahjong.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;

/**
 * WebSocket controller for handling game messages
 */
@Controller
public class GameWebSocketController {
    
    private static final Logger logger = LoggerFactory.getLogger(GameWebSocketController.class);
    
    private final GameService gameService;
    private final RoomService roomService;
    private final WebSocketSessionService sessionService;
    private final GameMessageBroadcastService broadcastService;
    private final GameMessageParser messageParser;
    private final PlayerReconnectionService reconnectionService;
    
    @Autowired
    public GameWebSocketController(GameService gameService,
                                 RoomService roomService,
                                 WebSocketSessionService sessionService,
                                 GameMessageBroadcastService broadcastService,
                                 GameMessageParser messageParser,
                                 PlayerReconnectionService reconnectionService) {
        this.gameService = gameService;
        this.roomService = roomService;
        this.sessionService = sessionService;
        this.broadcastService = broadcastService;
        this.messageParser = messageParser;
        this.reconnectionService = reconnectionService;
    }
    
    /**
     * Handle room join requests
     */
    @MessageMapping("/room/join")
    public void handleJoinRoom(@Payload GameMessage message, 
                              SimpMessageHeaderAccessor headerAccessor,
                              Principal principal) {
        try {
            messageParser.validateMessage(message);
            String userId = principal.getName();
            String roomId = message.getRoomId();
            
            logger.debug("User {} joining room {}", userId, roomId);
            
            // Add user to room in session service
            sessionService.addUserToRoom(userId, roomId);
            
            // Send success response
            GameMessage response = GameMessage.response("joinRoom", message.getRequestId(), 
                    Map.of("status", "joined", "roomId", roomId));
            broadcastService.sendToUser(userId, response);
            
            // Notify other room members
            broadcastService.broadcastRoomEvent(roomId, "userJoined", Map.of("userId", userId));
            
        } catch (Exception e) {
            logger.error("Error handling join room request", e);
            broadcastService.sendError(principal.getName(), message.getCommand(), 
                    message.getRequestId(), "Failed to join room: " + e.getMessage());
        }
    }
    
    /**
     * Handle room leave requests
     */
    @MessageMapping("/room/leave")
    public void handleLeaveRoom(@Payload GameMessage message,
                               SimpMessageHeaderAccessor headerAccessor,
                               Principal principal) {
        try {
            messageParser.validateMessage(message);
            String userId = principal.getName();
            String roomId = message.getRoomId();
            
            logger.debug("User {} leaving room {}", userId, roomId);
            
            // Remove user from room
            sessionService.removeUserFromRoom(userId, roomId);
            
            // Send success response
            GameMessage response = GameMessage.response("leaveRoom", message.getRequestId(),
                    Map.of("status", "left", "roomId", roomId));
            broadcastService.sendToUser(userId, response);
            
            // Notify other room members
            broadcastService.broadcastRoomEvent(roomId, "userLeft", Map.of("userId", userId));
            
        } catch (Exception e) {
            logger.error("Error handling leave room request", e);
            broadcastService.sendError(principal.getName(), message.getCommand(), 
                    message.getRequestId(), "Failed to leave room: " + e.getMessage());
        }
    }
    
    /**
     * Handle player actions (discard, peng, gang, hu, etc.)
     */
    @MessageMapping("/game/action")
    public void handlePlayerAction(@Payload GameMessage message,
                                  SimpMessageHeaderAccessor headerAccessor,
                                  Principal principal) {
        try {
            messageParser.validateMessage(message);
            String userId = principal.getName();
            String roomId = message.getRoomId();
            
            logger.debug("Handling game action from user {} in room {}: {}", 
                    userId, roomId, message.getCommand());
            
            // Parse action from message data
            PlayerAction action = messageParser.parsePlayerAction(message);
            
            // Process action through game service
            ActionResult result = gameService.handlePlayerAction(roomId, userId, action);
            
            // Send response to player
            Object responseData = messageParser.convertActionResultToResponseData(result);
            broadcastService.sendActionResult(userId, message.getRequestId(), result);
            
            // Broadcast action event to other players if action was successful
            if (result.isSuccess()) {
                broadcastService.broadcastActionEvent(roomId, userId, message.getCommand(), 
                        message.getData());
                
                // If the action resulted in a game state change, broadcast updated snapshot
                if (result.getData() != null) {
                    GameMessage stateUpdate = GameMessage.event("gameStateUpdate", roomId, 
                            result.getData());
                    broadcastService.broadcastToRoom(roomId, stateUpdate);
                }
            }
            
        } catch (Exception e) {
            logger.error("Error handling player action", e);
            broadcastService.sendError(principal.getName(), message.getCommand(), 
                    message.getRequestId(), "Failed to process action: " + e.getMessage());
        }
    }
    
    /**
     * Handle game snapshot requests
     */
    @MessageMapping("/game/snapshot")
    public void handleSnapshotRequest(@Payload GameMessage message,
                                     SimpMessageHeaderAccessor headerAccessor,
                                     Principal principal) {
        try {
            messageParser.validateMessage(message);
            String userId = principal.getName();
            String roomId = message.getRoomId();
            
            logger.debug("User {} requesting game snapshot for room {}", userId, roomId);
            
            // Get game snapshot from service
            Object snapshot = gameService.createGameSnapshot(roomId);
            
            // Send snapshot response
            GameMessage response = GameMessage.response("gameSnapshot", message.getRequestId(), snapshot);
            broadcastService.sendToUser(userId, response);
            
        } catch (Exception e) {
            logger.error("Error handling snapshot request", e);
            broadcastService.sendError(principal.getName(), message.getCommand(), 
                    message.getRequestId(), "Failed to get game snapshot: " + e.getMessage());
        }
    }
    
    /**
     * Handle reconnection requests
     */
    @MessageMapping("/reconnect")
    public void handleReconnection(@Payload GameMessage message,
                                  SimpMessageHeaderAccessor headerAccessor,
                                  Principal principal) {
        try {
            messageParser.validateMessage(message);
            String userId = principal.getName();
            String sessionId = headerAccessor.getSessionId();
            
            logger.info("Handling reconnection request for user: {}", userId);
            
            // Extract token from message data
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) message.getData();
            String token = data != null ? (String) data.get("token") : null;
            
            if (token == null) {
                broadcastService.sendError(userId, message.getCommand(), 
                    message.getRequestId(), "Authentication token required for reconnection");
                return;
            }
            
            // Handle reconnection
            PlayerReconnectionService.ReconnectionResult result = 
                reconnectionService.handlePlayerReconnection(userId, sessionId, token);
            
            if (result.isSuccess()) {
                // Send successful reconnection response with game snapshot
                GameMessage response = GameMessage.response("reconnect", message.getRequestId(), 
                    Map.of(
                        "status", "success",
                        "roomId", result.getRoomId(),
                        "gameSnapshot", result.getGameSnapshot()
                    ));
                broadcastService.sendToUser(userId, response);
                
                logger.info("Reconnection successful for user: {} to room: {}", userId, result.getRoomId());
            } else {
                // Send failure response
                broadcastService.sendError(userId, message.getCommand(), 
                    message.getRequestId(), result.getMessage());
                
                logger.warn("Reconnection failed for user: {}, reason: {}", userId, result.getMessage());
            }
            
        } catch (Exception e) {
            logger.error("Error handling reconnection request", e);
            broadcastService.sendError(principal.getName(), message.getCommand(), 
                message.getRequestId(), "Internal error during reconnection");
        }
    }
    
    /**
     * Handle heartbeat/ping messages
     */
    @MessageMapping("/ping")
    public void handlePing(@Payload GameMessage message,
                          SimpMessageHeaderAccessor headerAccessor,
                          Principal principal) {
        try {
            String userId = principal.getName();
            
            // Send heartbeat response
            broadcastService.sendHeartbeat(userId, message.getRequestId());
            
        } catch (Exception e) {
            logger.error("Error handling ping", e);
        }
    }
}