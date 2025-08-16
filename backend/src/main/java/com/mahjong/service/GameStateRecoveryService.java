package com.mahjong.service;

import com.mahjong.model.game.GameState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Service for recovering game state from Redis with database fallback
 */
@Service
public class GameStateRecoveryService {
    
    private static final Logger logger = LoggerFactory.getLogger(GameStateRecoveryService.class);
    
    @Autowired
    private GameStateRedisService gameStateRedisService;
    
    @Autowired
    private GameStateConsistencyService gameStateConsistencyService;
    
    /**
     * Recover game state with Redis primary and database fallback
     */
    public GameState recoverGameState(String roomId) {
        logger.debug("Attempting to recover game state for room: {}", roomId);
        
        try {
            // First attempt: Load from Redis
            GameState gameState = gameStateRedisService.loadGameState(roomId);
            
            if (gameState != null) {
                // Validate the recovered state
                if (gameStateRedisService.validateGameState(gameState)) {
                    logger.debug("Successfully recovered game state from Redis for room: {}", roomId);
                    return gameState;
                } else {
                    logger.warn("Invalid game state recovered from Redis for room: {}, attempting database fallback", roomId);
                }
            } else {
                logger.debug("No game state found in Redis for room: {}, attempting database fallback", roomId);
            }
            
            // Second attempt: Load from database (not yet implemented)
            // gameState = recoverFromDatabase(roomId);
            
            logger.warn("Failed to recover game state for room: {} from both Redis and database", roomId);
            return null;
            
        } catch (Exception e) {
            logger.error("Error recovering game state for room: {}", roomId, e);
            return null;
        }
    }
    

}