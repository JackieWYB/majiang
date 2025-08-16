package com.mahjong.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Service for WebSocket heartbeat monitoring and connection cleanup
 */
@Service
public class WebSocketHeartbeatService {
    
    private static final Logger logger = LoggerFactory.getLogger(WebSocketHeartbeatService.class);
    
    private final WebSocketSessionService sessionService;
    
    @Autowired
    public WebSocketHeartbeatService(WebSocketSessionService sessionService) {
        this.sessionService = sessionService;
    }
    
    /**
     * Clean up inactive WebSocket sessions every 30 seconds
     */
    @Scheduled(fixedRate = 30000)
    public void cleanupInactiveSessions() {
        try {
            logger.debug("Running WebSocket session cleanup");
            sessionService.cleanupInactiveSessions();
            
            int activeSessions = sessionService.getActiveSessionCount();
            int activeRooms = sessionService.getActiveRoomCount();
            
            logger.debug("WebSocket cleanup completed. Active sessions: {}, Active rooms: {}", 
                    activeSessions, activeRooms);
            
        } catch (Exception e) {
            logger.error("Error during WebSocket session cleanup", e);
        }
    }
    
    /**
     * Log WebSocket statistics every 5 minutes
     */
    @Scheduled(fixedRate = 300000)
    public void logWebSocketStatistics() {
        try {
            int activeSessions = sessionService.getActiveSessionCount();
            int activeRooms = sessionService.getActiveRoomCount();
            
            logger.info("WebSocket Statistics - Active sessions: {}, Active rooms: {}", 
                    activeSessions, activeRooms);
            
        } catch (Exception e) {
            logger.error("Error logging WebSocket statistics", e);
        }
    }
}