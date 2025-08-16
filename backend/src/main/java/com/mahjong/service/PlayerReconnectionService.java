package com.mahjong.service;

import com.mahjong.model.dto.GameSnapshot;
import com.mahjong.model.game.GameState;
import com.mahjong.model.game.PlayerState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Service for handling player disconnections and reconnections
 */
@Service
public class PlayerReconnectionService {
    
    private static final Logger logger = LoggerFactory.getLogger(PlayerReconnectionService.class);
    
    @Autowired
    private GameStateRedisService gameStateRedisService;
    
    @Autowired
    private GameStateConsistencyService gameStateConsistencyService;
    
    @Autowired
    private WebSocketSessionService webSocketSessionService;
    
    @Autowired
    private GameMessageBroadcastService broadcastService;
    
    @Autowired
    private JwtTokenService jwtTokenService;
    
    @Autowired
    private GameStateRecoveryService gameStateRecoveryService;
    
    @Value("${game.reconnection.grace-period-seconds:30}")
    private int gracePeriodSeconds;
    
    @Value("${game.reconnection.max-disconnection-time-minutes:5}")
    private int maxDisconnectionTimeMinutes;
    
    @Value("${game.reconnection.trustee-timeout-count:3}")
    private int trusteeTimeoutCount;
    
    // Track disconnected players: userId -> DisconnectionInfo
    private final Map<String, DisconnectionInfo> disconnectedPlayers = new ConcurrentHashMap<>();
    
    // Scheduled executor for handling timeouts
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    
    /**
     * Handle player disconnection
     */
    public void handlePlayerDisconnection(String userId, String sessionId, String roomId) {
        logger.info("Handling disconnection for user {} in room {}", userId, roomId);
        
        try {
            // Get current game state
            GameState gameState = gameStateRedisService.loadGameState(roomId);
            if (gameState == null) {
                logger.warn("No game state found for room {} during disconnection", roomId);
                return;
            }
            
            PlayerState player = gameState.getPlayerByUserId(userId);
            if (player == null) {
                logger.warn("Player {} not found in game state for room {}", userId, roomId);
                return;
            }
            
            // Mark player as disconnected
            player.setStatus(PlayerState.PlayerStatus.DISCONNECTED);
            
            // Create disconnection info
            DisconnectionInfo disconnectionInfo = new DisconnectionInfo(
                userId, roomId, sessionId, System.currentTimeMillis()
            );
            disconnectedPlayers.put(userId, disconnectionInfo);
            
            // Save updated game state
            gameStateRedisService.saveGameState(gameState);
            
            // Remove session mappings
            gameStateRedisService.removePlayerSession(userId, sessionId);
            
            // Schedule grace period timeout
            scheduleGracePeriodTimeout(userId, roomId);
            
            // Notify other players
            broadcastService.broadcastRoomEvent(roomId, "playerDisconnected", 
                Map.of("userId", userId, "gracePeriod", gracePeriodSeconds));
            
            logger.info("Player {} marked as disconnected in room {}, grace period: {}s", 
                       userId, roomId, gracePeriodSeconds);
            
        } catch (Exception e) {
            logger.error("Error handling disconnection for user {} in room {}", userId, roomId, e);
        }
    }
    
    /**
     * Handle player reconnection
     */
    public ReconnectionResult handlePlayerReconnection(String userId, String newSessionId, String token) {
        logger.info("Handling reconnection for user {}", userId);
        
        try {
            // Validate token
            if (!jwtTokenService.validateToken(token)) {
                logger.warn("Invalid token for reconnection: userId={}", userId);
                return ReconnectionResult.failure("Invalid authentication token");
            }
            
            // Check if player was disconnected
            DisconnectionInfo disconnectionInfo = disconnectedPlayers.get(userId);
            if (disconnectionInfo == null) {
                logger.debug("No disconnection record found for user {}", userId);
                return ReconnectionResult.failure("No active disconnection found");
            }
            
            String roomId = disconnectionInfo.getRoomId();
            
            // Check if disconnection time hasn't exceeded maximum
            long disconnectionDuration = System.currentTimeMillis() - disconnectionInfo.getDisconnectionTime();
            if (disconnectionDuration > maxDisconnectionTimeMinutes * 60 * 1000L) {
                logger.warn("Reconnection timeout exceeded for user {}: {}ms", userId, disconnectionDuration);
                cleanupDisconnectedPlayer(userId);
                return ReconnectionResult.failure("Reconnection timeout exceeded");
            }
            
            // Get current game state
            GameState gameState = gameStateRecoveryService.recoverGameState(roomId);
            if (gameState == null) {
                logger.warn("Cannot recover game state for room {} during reconnection", roomId);
                cleanupDisconnectedPlayer(userId);
                return ReconnectionResult.failure("Game state not available");
            }
            
            PlayerState player = gameState.getPlayerByUserId(userId);
            if (player == null) {
                logger.warn("Player {} not found in recovered game state for room {}", userId, roomId);
                cleanupDisconnectedPlayer(userId);
                return ReconnectionResult.failure("Player not found in game");
            }
            
            // Update player status
            if (player.getStatus() == PlayerState.PlayerStatus.TRUSTEE) {
                player.setStatus(PlayerState.PlayerStatus.WAITING_TURN);
                logger.info("Player {} reconnected from trustee mode in room {}", userId, roomId);
            } else {
                player.setStatus(PlayerState.PlayerStatus.WAITING_TURN);
            }
            
            // Register new session
            webSocketSessionService.addUserToRoom(userId, roomId);
            gameStateRedisService.savePlayerSession(userId, newSessionId, roomId);
            
            // Save updated game state
            gameStateRedisService.saveGameState(gameState);
            
            // Generate game snapshot for reconnecting player
            GameSnapshot gameSnapshot = createReconnectionSnapshot(gameState, userId);
            
            // Clean up disconnection info
            cleanupDisconnectedPlayer(userId);
            
            // Notify other players
            broadcastService.broadcastRoomEvent(roomId, "playerReconnected", 
                Map.of("userId", userId));
            
            logger.info("Player {} successfully reconnected to room {}", userId, roomId);
            
            return ReconnectionResult.success(gameSnapshot, roomId);
            
        } catch (Exception e) {
            logger.error("Error handling reconnection for user {}", userId, e);
            return ReconnectionResult.failure("Internal error during reconnection");
        }
    }
    
    /**
     * Schedule grace period timeout for disconnected player
     */
    private void scheduleGracePeriodTimeout(String userId, String roomId) {
        scheduler.schedule(() -> {
            try {
                DisconnectionInfo info = disconnectedPlayers.get(userId);
                if (info != null) {
                    activateTrusteeMode(userId, roomId);
                }
            } catch (Exception e) {
                logger.error("Error in grace period timeout for user {}", userId, e);
            }
        }, gracePeriodSeconds, TimeUnit.SECONDS);
    }
    
    /**
     * Activate trustee mode for disconnected player
     */
    private void activateTrusteeMode(String userId, String roomId) {
        logger.info("Activating trustee mode for user {} in room {}", userId, roomId);
        
        try {
            GameState gameState = gameStateRedisService.loadGameState(roomId);
            if (gameState == null) {
                logger.warn("No game state found for trustee activation: userId={}, roomId={}", userId, roomId);
                return;
            }
            
            PlayerState player = gameState.getPlayerByUserId(userId);
            if (player == null) {
                logger.warn("Player not found for trustee activation: userId={}, roomId={}", userId, roomId);
                return;
            }
            
            // Only activate trustee if player is still disconnected
            if (player.getStatus() == PlayerState.PlayerStatus.DISCONNECTED) {
                player.setStatus(PlayerState.PlayerStatus.TRUSTEE);
                
                // Save updated state
                gameStateRedisService.saveGameState(gameState);
                
                // Notify other players
                broadcastService.broadcastRoomEvent(roomId, "playerTrusteeActivated", 
                    Map.of("userId", userId));
                
                logger.info("Trustee mode activated for user {} in room {}", userId, roomId);
            }
            
        } catch (Exception e) {
            logger.error("Error activating trustee mode for user {} in room {}", userId, roomId, e);
        }
    }
    
    /**
     * Create game snapshot for reconnecting player
     */
    private GameSnapshot createReconnectionSnapshot(GameState gameState, String userId) {
        try {
            // Create comprehensive snapshot with player-specific data
            GameSnapshot snapshot = new GameSnapshot();
            snapshot.setRoomId(gameState.getRoomId());
            snapshot.setGameId(gameState.getGameId());
            snapshot.setGamePhase(gameState.getPhase().name());
            snapshot.setCurrentPlayerIndex(gameState.getCurrentPlayerIndex());
            snapshot.setCurrentPlayerId(gameState.getCurrentPlayer() != null ? 
                gameState.getCurrentPlayer().getUserId() : null);
            snapshot.setDealerId(gameState.getDealerUserId());
            snapshot.setRemainingTiles(gameState.getRemainingTiles());
            snapshot.setTurnDeadline(gameState.getTurnDeadline());
            snapshot.setRoundIndex(gameState.getRoundNumber());
            
            // Convert discard pile to string representation
            snapshot.setDiscardPile(gameState.getDiscardPile().stream()
                .map(tile -> tile.getSuit().name() + tile.getRank())
                .toList());
            
            // Create player snapshots with full data for reconnecting player
            PlayerState reconnectingPlayer = gameState.getPlayerByUserId(userId);
            if (reconnectingPlayer != null) {
                // Set available actions for reconnecting player
                snapshot.setAvailableActions(reconnectingPlayer.getAvailableActions().stream()
                    .map(Enum::name)
                    .toList());
            }
            
            // Add game configuration
            snapshot.setConfig(Map.of(
                "allowPeng", gameState.getConfig().getAllowPeng(),
                "allowGang", gameState.getConfig().getAllowGang(),
                "allowChi", gameState.getConfig().getAllowChi(),
                "turnTimeLimit", gameState.getConfig().getTurn().getTurnTimeLimit()
            ));
            
            return snapshot;
            
        } catch (Exception e) {
            logger.error("Error creating reconnection snapshot for user {} in room {}", 
                        userId, gameState.getRoomId(), e);
            return null;
        }
    }
    
    /**
     * Clean up disconnection info for a player
     */
    private void cleanupDisconnectedPlayer(String userId) {
        DisconnectionInfo removed = disconnectedPlayers.remove(userId);
        if (removed != null) {
            logger.debug("Cleaned up disconnection info for user {}", userId);
        }
    }
    
    /**
     * Check if player is currently disconnected
     */
    public boolean isPlayerDisconnected(String userId) {
        return disconnectedPlayers.containsKey(userId);
    }
    
    /**
     * Get disconnection info for a player
     */
    public DisconnectionInfo getDisconnectionInfo(String userId) {
        return disconnectedPlayers.get(userId);
    }
    
    /**
     * Force cleanup of expired disconnections
     */
    public void cleanupExpiredDisconnections() {
        long currentTime = System.currentTimeMillis();
        long maxDisconnectionTime = maxDisconnectionTimeMinutes * 60 * 1000L;
        
        disconnectedPlayers.entrySet().removeIf(entry -> {
            DisconnectionInfo info = entry.getValue();
            boolean expired = (currentTime - info.getDisconnectionTime()) > maxDisconnectionTime;
            if (expired) {
                logger.info("Cleaning up expired disconnection for user {}", entry.getKey());
            }
            return expired;
        });
    }
    
    /**
     * Get count of currently disconnected players
     */
    public int getDisconnectedPlayerCount() {
        return disconnectedPlayers.size();
    }
    
    /**
     * Disconnection information class
     */
    public static class DisconnectionInfo {
        private final String userId;
        private final String roomId;
        private final String sessionId;
        private final long disconnectionTime;
        
        public DisconnectionInfo(String userId, String roomId, String sessionId, long disconnectionTime) {
            this.userId = userId;
            this.roomId = roomId;
            this.sessionId = sessionId;
            this.disconnectionTime = disconnectionTime;
        }
        
        public String getUserId() { return userId; }
        public String getRoomId() { return roomId; }
        public String getSessionId() { return sessionId; }
        public long getDisconnectionTime() { return disconnectionTime; }
        
        public long getDisconnectionDuration() {
            return System.currentTimeMillis() - disconnectionTime;
        }
    }
    
    /**
     * Reconnection result class
     */
    public static class ReconnectionResult {
        private final boolean success;
        private final String message;
        private final GameSnapshot gameSnapshot;
        private final String roomId;
        
        private ReconnectionResult(boolean success, String message, GameSnapshot gameSnapshot, String roomId) {
            this.success = success;
            this.message = message;
            this.gameSnapshot = gameSnapshot;
            this.roomId = roomId;
        }
        
        public static ReconnectionResult success(GameSnapshot gameSnapshot, String roomId) {
            return new ReconnectionResult(true, "Reconnection successful", gameSnapshot, roomId);
        }
        
        public static ReconnectionResult failure(String message) {
            return new ReconnectionResult(false, message, null, null);
        }
        
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public GameSnapshot getGameSnapshot() { return gameSnapshot; }
        public String getRoomId() { return roomId; }
    }
}