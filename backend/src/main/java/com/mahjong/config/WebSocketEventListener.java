package com.mahjong.config;

import com.mahjong.service.WebSocketSessionService;
import com.mahjong.service.PlayerReconnectionService;
import com.mahjong.service.GameStateRedisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;

/**
 * WebSocket event listener for handling connection lifecycle events
 */
@Component
public class WebSocketEventListener {
    
    private static final Logger logger = LoggerFactory.getLogger(WebSocketEventListener.class);
    
    private final WebSocketSessionService sessionService;
    private final PlayerReconnectionService reconnectionService;
    private final GameStateRedisService gameStateRedisService;
    
    @Autowired
    public WebSocketEventListener(WebSocketSessionService sessionService,
                                PlayerReconnectionService reconnectionService,
                                GameStateRedisService gameStateRedisService) {
        this.sessionService = sessionService;
        this.reconnectionService = reconnectionService;
        this.gameStateRedisService = gameStateRedisService;
    }
    
    /**
     * Handle WebSocket connection established
     */
    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        try {
            StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
            String sessionId = headerAccessor.getSessionId();
            Principal user = headerAccessor.getUser();
            
            if (user != null) {
                String userId = user.getName();
                logger.info("WebSocket connection established for user: {} with sessionId: {}", 
                        userId, sessionId);
                
                // Note: We can't register the session here because we don't have access to WebSocketSession
                // Session registration will happen in the authentication interceptor
                
            } else {
                logger.warn("WebSocket connection established without authenticated user, sessionId: {}", 
                        sessionId);
            }
            
        } catch (Exception e) {
            logger.error("Error handling WebSocket connection event", e);
        }
    }
    
    /**
     * Handle WebSocket disconnection
     */
    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        try {
            StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
            String sessionId = headerAccessor.getSessionId();
            Principal user = headerAccessor.getUser();
            
            if (user != null) {
                String userId = user.getName();
                logger.info("WebSocket disconnection for user: {} with sessionId: {}", 
                        userId, sessionId);
                
                // Get session info to find room
                GameStateRedisService.SessionInfo sessionInfo = gameStateRedisService.getSessionInfo(sessionId);
                String roomId = sessionInfo != null ? sessionInfo.getRoomId() : null;
                
                if (roomId != null) {
                    // Handle disconnection through reconnection service
                    reconnectionService.handlePlayerDisconnection(userId, sessionId, roomId);
                } else {
                    logger.debug("No room found for disconnecting user: {}", userId);
                }
            } else {
                logger.info("WebSocket disconnection for sessionId: {}", sessionId);
            }
            
            // Unregister session and clean up user from rooms
            sessionService.unregisterSession(sessionId);
            
        } catch (Exception e) {
            logger.error("Error handling WebSocket disconnection event", e);
        }
    }
}