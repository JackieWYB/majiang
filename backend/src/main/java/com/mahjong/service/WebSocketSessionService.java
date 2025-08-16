package com.mahjong.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Service for managing WebSocket sessions and user connections
 */
@Service
public class WebSocketSessionService {
    
    private static final Logger logger = LoggerFactory.getLogger(WebSocketSessionService.class);
    
    // Map of userId -> WebSocketSession
    private final Map<String, WebSocketSession> userSessions = new ConcurrentHashMap<>();
    
    // Map of roomId -> Set of userIds
    private final Map<String, Set<String>> roomUsers = new ConcurrentHashMap<>();
    
    // Map of sessionId -> userId for cleanup
    private final Map<String, String> sessionToUser = new ConcurrentHashMap<>();
    
    /**
     * Register a user session
     */
    public void registerSession(String userId, WebSocketSession session) {
        logger.debug("Registering session for user: {}", userId);
        
        // Remove any existing session for this user
        WebSocketSession existingSession = userSessions.get(userId);
        if (existingSession != null && existingSession.isOpen()) {
            try {
                existingSession.close();
            } catch (Exception e) {
                logger.warn("Error closing existing session for user {}: {}", userId, e.getMessage());
            }
        }
        
        userSessions.put(userId, session);
        sessionToUser.put(session.getId(), userId);
        
        logger.info("Session registered for user: {} with sessionId: {}", userId, session.getId());
    }
    
    /**
     * Unregister a user session
     */
    public void unregisterSession(String sessionId) {
        if (sessionId == null) {
            return;
        }
        
        String userId = sessionToUser.remove(sessionId);
        if (userId != null) {
            logger.debug("Unregistering session for user: {}", userId);
            
            WebSocketSession session = userSessions.remove(userId);
            if (session != null && session.isOpen()) {
                try {
                    session.close();
                } catch (Exception e) {
                    logger.warn("Error closing session for user {}: {}", userId, e.getMessage());
                }
            }
            
            // Remove user from all rooms
            roomUsers.values().forEach(users -> users.remove(userId));
            
            logger.info("Session unregistered for user: {}", userId);
        }
    }
    
    /**
     * Add user to a room
     */
    public void addUserToRoom(String userId, String roomId) {
        logger.debug("Adding user {} to room {}", userId, roomId);
        roomUsers.computeIfAbsent(roomId, k -> new CopyOnWriteArraySet<>()).add(userId);
    }
    
    /**
     * Remove user from a room
     */
    public void removeUserFromRoom(String userId, String roomId) {
        logger.debug("Removing user {} from room {}", userId, roomId);
        Set<String> users = roomUsers.get(roomId);
        if (users != null) {
            users.remove(userId);
            if (users.isEmpty()) {
                roomUsers.remove(roomId);
            }
        }
    }
    
    /**
     * Get WebSocket session for a user
     */
    public WebSocketSession getSession(String userId) {
        return userSessions.get(userId);
    }
    
    /**
     * Get all users in a room
     */
    public Set<String> getRoomUsers(String roomId) {
        return roomUsers.getOrDefault(roomId, Set.of());
    }
    
    /**
     * Get user ID from session ID
     */
    public String getUserId(String sessionId) {
        if (sessionId == null) {
            return null;
        }
        return sessionToUser.get(sessionId);
    }
    
    /**
     * Check if user is online
     */
    public boolean isUserOnline(String userId) {
        WebSocketSession session = userSessions.get(userId);
        return session != null && session.isOpen();
    }
    
    /**
     * Get total number of active sessions
     */
    public int getActiveSessionCount() {
        return userSessions.size();
    }
    
    /**
     * Get total number of active rooms
     */
    public int getActiveRoomCount() {
        return roomUsers.size();
    }
    
    /**
     * Clean up inactive sessions
     */
    public void cleanupInactiveSessions() {
        logger.debug("Cleaning up inactive sessions");
        
        userSessions.entrySet().removeIf(entry -> {
            WebSocketSession session = entry.getValue();
            if (!session.isOpen()) {
                String userId = entry.getKey();
                sessionToUser.remove(session.getId());
                roomUsers.values().forEach(users -> users.remove(userId));
                logger.debug("Removed inactive session for user: {}", userId);
                return true;
            }
            return false;
        });
    }
    
    /**
     * Disconnect a specific user (admin operation)
     */
    public void disconnectUser(Long userId, String reason) {
        String userIdStr = userId.toString();
        WebSocketSession session = userSessions.get(userIdStr);
        
        if (session != null && session.isOpen()) {
            try {
                // Send disconnect message with reason
                session.sendMessage(new org.springframework.web.socket.TextMessage(
                    "{\"type\":\"DISCONNECT\",\"reason\":\"" + reason + "\"}"
                ));
                
                // Close the session
                session.close();
                
                // Clean up
                unregisterSession(session.getId());
                
                logger.info("Disconnected user {} with reason: {}", userId, reason);
            } catch (Exception e) {
                logger.error("Error disconnecting user {}: {}", userId, e.getMessage());
            }
        }
    }
    
    /**
     * Notify all players in a room (admin operation)
     */
    public void notifyRoomPlayers(String roomId, String message) {
        Set<String> users = getRoomUsers(roomId);
        
        for (String userId : users) {
            WebSocketSession session = getSession(userId);
            if (session != null && session.isOpen()) {
                try {
                    session.sendMessage(new org.springframework.web.socket.TextMessage(
                        "{\"type\":\"ADMIN_NOTIFICATION\",\"message\":\"" + message + "\"}"
                    ));
                } catch (Exception e) {
                    logger.error("Error notifying user {} in room {}: {}", userId, roomId, e.getMessage());
                }
            }
        }
        
        logger.info("Notified {} players in room {} with message: {}", users.size(), roomId, message);
    }
    
    /**
     * Get online user count
     */
    public long getOnlineUserCount() {
        return userSessions.values().stream()
                .mapToLong(session -> session.isOpen() ? 1 : 0)
                .sum();
    }
}