package com.mahjong.service;

import com.mahjong.model.game.GameState;
import com.mahjong.model.game.PlayerState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Service for managing game state persistence in Redis
 */
@Service
public class GameStateRedisService {
    
    private static final Logger logger = LoggerFactory.getLogger(GameStateRedisService.class);
    
    private static final String GAME_STATE_PREFIX = "game:state:";
    private static final String GAME_SESSION_PREFIX = "game:session:";
    private static final String PLAYER_SESSION_PREFIX = "player:session:";
    private static final String ROOM_PLAYERS_PREFIX = "room:players:";
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    @Value("${game.redis.game-state-ttl-hours:2}")
    private int gameStateTtlHours;
    
    @Value("${game.redis.session-ttl-hours:24}")
    private int sessionTtlHours;
    
    /**
     * Save game state to Redis with TTL
     */
    public void saveGameState(GameState gameState) {
        try {
            String key = GAME_STATE_PREFIX + gameState.getRoomId();
            redisTemplate.opsForValue().set(key, gameState, Duration.ofHours(gameStateTtlHours));
            
            logger.debug("Saved game state for room: {} with TTL: {} hours", 
                        gameState.getRoomId(), gameStateTtlHours);
        } catch (Exception e) {
            logger.error("Failed to save game state for room: {}", gameState.getRoomId(), e);
            throw new RuntimeException("Failed to save game state", e);
        }
    }
    
    /**
     * Load game state from Redis
     */
    public GameState loadGameState(String roomId) {
        try {
            String key = GAME_STATE_PREFIX + roomId;
            Object result = redisTemplate.opsForValue().get(key);
            
            if (result instanceof GameState) {
                logger.debug("Loaded game state for room: {}", roomId);
                return (GameState) result;
            }
            
            logger.debug("No game state found for room: {}", roomId);
            return null;
        } catch (Exception e) {
            logger.error("Failed to load game state for room: {}", roomId, e);
            return null;
        }
    }
    
    /**
     * Delete game state from Redis
     */
    public void deleteGameState(String roomId) {
        try {
            String key = GAME_STATE_PREFIX + roomId;
            Boolean deleted = redisTemplate.delete(key);
            
            if (Boolean.TRUE.equals(deleted)) {
                logger.debug("Deleted game state for room: {}", roomId);
            } else {
                logger.debug("No game state to delete for room: {}", roomId);
            }
        } catch (Exception e) {
            logger.error("Failed to delete game state for room: {}", roomId, e);
        }
    }
    
    /**
     * Check if game state exists in Redis
     */
    public boolean gameStateExists(String roomId) {
        try {
            String key = GAME_STATE_PREFIX + roomId;
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception e) {
            logger.error("Failed to check game state existence for room: {}", roomId, e);
            return false;
        }
    }
    
    /**
     * Update game state TTL
     */
    public void refreshGameStateTtl(String roomId) {
        try {
            String key = GAME_STATE_PREFIX + roomId;
            redisTemplate.expire(key, Duration.ofHours(gameStateTtlHours));
            logger.debug("Refreshed TTL for game state: {}", roomId);
        } catch (Exception e) {
            logger.error("Failed to refresh TTL for game state: {}", roomId, e);
        }
    }
    
    /**
     * Save WebSocket session mapping
     */
    public void savePlayerSession(String userId, String sessionId, String roomId) {
        try {
            String playerKey = PLAYER_SESSION_PREFIX + userId;
            String sessionKey = GAME_SESSION_PREFIX + sessionId;
            
            // Map user to session
            redisTemplate.opsForValue().set(playerKey, sessionId, Duration.ofHours(sessionTtlHours));
            
            // Map session to room and user
            SessionInfo sessionInfo = new SessionInfo(userId, roomId, System.currentTimeMillis());
            redisTemplate.opsForValue().set(sessionKey, sessionInfo, Duration.ofHours(sessionTtlHours));
            
            // Add player to room's player set
            String roomPlayersKey = ROOM_PLAYERS_PREFIX + roomId;
            redisTemplate.opsForSet().add(roomPlayersKey, userId);
            redisTemplate.expire(roomPlayersKey, Duration.ofHours(sessionTtlHours));
            
            logger.debug("Saved session mapping: user={}, session={}, room={}", userId, sessionId, roomId);
        } catch (Exception e) {
            logger.error("Failed to save player session: user={}, session={}", userId, sessionId, e);
        }
    }
    
    /**
     * Get session ID for a user
     */
    public String getPlayerSessionId(String userId) {
        try {
            String key = PLAYER_SESSION_PREFIX + userId;
            Object result = redisTemplate.opsForValue().get(key);
            return result != null ? result.toString() : null;
        } catch (Exception e) {
            logger.error("Failed to get session ID for user: {}", userId, e);
            return null;
        }
    }
    
    /**
     * Get session info for a session ID
     */
    public SessionInfo getSessionInfo(String sessionId) {
        try {
            String key = GAME_SESSION_PREFIX + sessionId;
            Object result = redisTemplate.opsForValue().get(key);
            return result instanceof SessionInfo ? (SessionInfo) result : null;
        } catch (Exception e) {
            logger.error("Failed to get session info for session: {}", sessionId, e);
            return null;
        }
    }
    
    /**
     * Remove player session
     */
    public void removePlayerSession(String userId, String sessionId) {
        try {
            String playerKey = PLAYER_SESSION_PREFIX + userId;
            String sessionKey = GAME_SESSION_PREFIX + sessionId;
            
            // Get session info to find room
            SessionInfo sessionInfo = getSessionInfo(sessionId);
            
            // Remove mappings
            redisTemplate.delete(playerKey);
            redisTemplate.delete(sessionKey);
            
            // Remove from room's player set
            if (sessionInfo != null && sessionInfo.getRoomId() != null) {
                String roomPlayersKey = ROOM_PLAYERS_PREFIX + sessionInfo.getRoomId();
                redisTemplate.opsForSet().remove(roomPlayersKey, userId);
            }
            
            logger.debug("Removed session mapping: user={}, session={}", userId, sessionId);
        } catch (Exception e) {
            logger.error("Failed to remove player session: user={}, session={}", userId, sessionId, e);
        }
    }
    
    /**
     * Get all players in a room
     */
    public Set<Object> getRoomPlayers(String roomId) {
        try {
            String key = ROOM_PLAYERS_PREFIX + roomId;
            return redisTemplate.opsForSet().members(key);
        } catch (Exception e) {
            logger.error("Failed to get room players for room: {}", roomId, e);
            return Set.of();
        }
    }
    
    /**
     * Update session heartbeat
     */
    public void updateSessionHeartbeat(String sessionId) {
        try {
            SessionInfo sessionInfo = getSessionInfo(sessionId);
            if (sessionInfo != null) {
                sessionInfo.setLastHeartbeat(System.currentTimeMillis());
                String key = GAME_SESSION_PREFIX + sessionId;
                redisTemplate.opsForValue().set(key, sessionInfo, Duration.ofHours(sessionTtlHours));
            }
        } catch (Exception e) {
            logger.error("Failed to update session heartbeat: {}", sessionId, e);
        }
    }
    
    /**
     * Get all active game states
     */
    public Set<String> getAllActiveGameRooms() {
        try {
            Set<String> keys = redisTemplate.keys(GAME_STATE_PREFIX + "*");
            return keys != null ? keys : Set.of();
        } catch (Exception e) {
            logger.error("Failed to get active game rooms", e);
            return Set.of();
        }
    }
    
    /**
     * Clear all game state data for a room (for admin operations)
     */
    public void clearGameState(String roomId) {
        try {
            // Delete game state
            deleteGameState(roomId);
            
            // Clear room players
            String roomPlayersKey = ROOM_PLAYERS_PREFIX + roomId;
            redisTemplate.delete(roomPlayersKey);
            
            logger.debug("Cleared all game state data for room: {}", roomId);
        } catch (Exception e) {
            logger.error("Failed to clear game state for room: {}", roomId, e);
        }
    }
    
    /**
     * Ping Redis to check connectivity
     */
    public void ping() {
        try {
            redisTemplate.opsForValue().get("ping:test");
            logger.debug("Redis ping successful");
        } catch (Exception e) {
            logger.error("Redis ping failed", e);
            throw new RuntimeException("Redis connection failed", e);
        }
    }
    
    /**
     * Validate game state consistency
     */
    public boolean validateGameState(GameState gameState) {
        try {
            // Basic validation checks
            if (gameState == null) {
                return false;
            }
            
            if (gameState.getRoomId() == null || gameState.getGameId() == null) {
                logger.warn("Game state missing required IDs: roomId={}, gameId={}", 
                           gameState.getRoomId(), gameState.getGameId());
                return false;
            }
            
            if (gameState.getPlayers() == null || gameState.getPlayers().isEmpty()) {
                logger.warn("Game state has no players: roomId={}", gameState.getRoomId());
                return false;
            }
            
            // Validate player states
            for (PlayerState player : gameState.getPlayers()) {
                if (player.getUserId() == null) {
                    logger.warn("Player state missing user ID: roomId={}", gameState.getRoomId());
                    return false;
                }
                
                if (player.getSeatIndex() < 0 || player.getSeatIndex() >= gameState.getPlayers().size()) {
                    logger.warn("Invalid seat index for player: userId={}, seatIndex={}", 
                               player.getUserId(), player.getSeatIndex());
                    return false;
                }
            }
            
            // Validate game phase
            if (gameState.getPhase() == null) {
                logger.warn("Game state missing phase: roomId={}", gameState.getRoomId());
                return false;
            }
            
            return true;
        } catch (Exception e) {
            logger.error("Error validating game state: roomId={}", 
                        gameState != null ? gameState.getRoomId() : "null", e);
            return false;
        }
    }
    
    /**
     * Create game state snapshot for persistence
     */
    public GameStateSnapshot createSnapshot(GameState gameState) {
        try {
            return new GameStateSnapshot(
                gameState.getRoomId(),
                gameState.getGameId(),
                gameState.getPhase(),
                gameState.getCurrentPlayerIndex(),
                gameState.getDealerUserId(),
                gameState.getRemainingTiles(),
                gameState.getRoundNumber(),
                gameState.getTotalTurns(),
                gameState.getGameStartTime(),
                gameState.getRandomSeed(),
                System.currentTimeMillis()
            );
        } catch (Exception e) {
            logger.error("Failed to create game state snapshot: roomId={}", gameState.getRoomId(), e);
            return null;
        }
    }
    
    /**
     * Session information class
     */
    public static class SessionInfo {
        private String userId;
        private String roomId;
        private long connectionTime;
        private long lastHeartbeat;
        
        public SessionInfo() {}
        
        public SessionInfo(String userId, String roomId, long connectionTime) {
            this.userId = userId;
            this.roomId = roomId;
            this.connectionTime = connectionTime;
            this.lastHeartbeat = connectionTime;
        }
        
        // Getters and setters
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        
        public String getRoomId() { return roomId; }
        public void setRoomId(String roomId) { this.roomId = roomId; }
        
        public long getConnectionTime() { return connectionTime; }
        public void setConnectionTime(long connectionTime) { this.connectionTime = connectionTime; }
        
        public long getLastHeartbeat() { return lastHeartbeat; }
        public void setLastHeartbeat(long lastHeartbeat) { this.lastHeartbeat = lastHeartbeat; }
    }
    
    /**
     * Game state snapshot for lightweight persistence
     */
    public static class GameStateSnapshot {
        private String roomId;
        private String gameId;
        private GameState.GamePhase phase;
        private int currentPlayerIndex;
        private String dealerUserId;
        private int remainingTiles;
        private int roundNumber;
        private int totalTurns;
        private Long gameStartTime;
        private long randomSeed;
        private long snapshotTime;
        
        public GameStateSnapshot() {}
        
        public GameStateSnapshot(String roomId, String gameId, GameState.GamePhase phase,
                               int currentPlayerIndex, String dealerUserId, int remainingTiles,
                               int roundNumber, int totalTurns, Long gameStartTime,
                               long randomSeed, long snapshotTime) {
            this.roomId = roomId;
            this.gameId = gameId;
            this.phase = phase;
            this.currentPlayerIndex = currentPlayerIndex;
            this.dealerUserId = dealerUserId;
            this.remainingTiles = remainingTiles;
            this.roundNumber = roundNumber;
            this.totalTurns = totalTurns;
            this.gameStartTime = gameStartTime;
            this.randomSeed = randomSeed;
            this.snapshotTime = snapshotTime;
        }
        
        // Getters and setters
        public String getRoomId() { return roomId; }
        public void setRoomId(String roomId) { this.roomId = roomId; }
        
        public String getGameId() { return gameId; }
        public void setGameId(String gameId) { this.gameId = gameId; }
        
        public GameState.GamePhase getPhase() { return phase; }
        public void setPhase(GameState.GamePhase phase) { this.phase = phase; }
        
        public int getCurrentPlayerIndex() { return currentPlayerIndex; }
        public void setCurrentPlayerIndex(int currentPlayerIndex) { this.currentPlayerIndex = currentPlayerIndex; }
        
        public String getDealerUserId() { return dealerUserId; }
        public void setDealerUserId(String dealerUserId) { this.dealerUserId = dealerUserId; }
        
        public int getRemainingTiles() { return remainingTiles; }
        public void setRemainingTiles(int remainingTiles) { this.remainingTiles = remainingTiles; }
        
        public int getRoundNumber() { return roundNumber; }
        public void setRoundNumber(int roundNumber) { this.roundNumber = roundNumber; }
        
        public int getTotalTurns() { return totalTurns; }
        public void setTotalTurns(int totalTurns) { this.totalTurns = totalTurns; }
        
        public Long getGameStartTime() { return gameStartTime; }
        public void setGameStartTime(Long gameStartTime) { this.gameStartTime = gameStartTime; }
        
        public long getRandomSeed() { return randomSeed; }
        public void setRandomSeed(long randomSeed) { this.randomSeed = randomSeed; }
        
        public long getSnapshotTime() { return snapshotTime; }
        public void setSnapshotTime(long snapshotTime) { this.snapshotTime = snapshotTime; }
    }
}