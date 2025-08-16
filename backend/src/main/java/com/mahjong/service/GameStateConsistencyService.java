package com.mahjong.service;

import com.mahjong.model.game.GameState;
import com.mahjong.model.game.PlayerState;
import com.mahjong.model.game.Tile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service for validating and maintaining game state consistency
 */
@Service
public class GameStateConsistencyService {
    
    private static final Logger logger = LoggerFactory.getLogger(GameStateConsistencyService.class);
    
    @Autowired
    private GameStateRedisService gameStateRedisService;
    
    /**
     * Comprehensive game state validation
     */
    public ValidationResult validateGameState(GameState gameState) {
        ValidationResult result = new ValidationResult();
        
        if (gameState == null) {
            result.addError("Game state is null");
            return result;
        }
        
        // Validate basic properties
        validateBasicProperties(gameState, result);
        
        // Validate players
        validatePlayers(gameState, result);
        
        // Validate tile distribution
        validateTileDistribution(gameState, result);
        
        // Validate game phase consistency
        validateGamePhase(gameState, result);
        
        // Validate turn state
        validateTurnState(gameState, result);
        
        return result;
    }
    
    /**
     * Validate basic game state properties
     */
    private void validateBasicProperties(GameState gameState, ValidationResult result) {
        if (gameState.getRoomId() == null || gameState.getRoomId().trim().isEmpty()) {
            result.addError("Room ID is null or empty");
        }
        
        if (gameState.getGameId() == null || gameState.getGameId().trim().isEmpty()) {
            result.addError("Game ID is null or empty");
        }
        
        if (gameState.getConfig() == null) {
            result.addError("Game configuration is null");
        }
        
        if (gameState.getPhase() == null) {
            result.addError("Game phase is null");
        }
        
        if (gameState.getRemainingTiles() < 0) {
            result.addError("Remaining tiles count is negative: " + gameState.getRemainingTiles());
        }
        
        if (gameState.getCurrentPlayerIndex() < 0 || 
            gameState.getCurrentPlayerIndex() >= gameState.getPlayers().size()) {
            result.addError("Current player index is invalid: " + gameState.getCurrentPlayerIndex());
        }
    }
    
    /**
     * Validate player states
     */
    private void validatePlayers(GameState gameState, ValidationResult result) {
        List<PlayerState> players = gameState.getPlayers();
        
        if (players == null || players.isEmpty()) {
            result.addError("No players in game state");
            return;
        }
        
        if (players.size() != 3) {
            result.addError("Invalid number of players: " + players.size() + " (expected 3)");
        }
        
        // Check for duplicate user IDs
        Set<String> userIds = players.stream()
                .map(PlayerState::getUserId)
                .collect(Collectors.toSet());
        
        if (userIds.size() != players.size()) {
            result.addError("Duplicate user IDs found in players");
        }
        
        // Check for duplicate seat indices
        Set<Integer> seatIndices = players.stream()
                .map(PlayerState::getSeatIndex)
                .collect(Collectors.toSet());
        
        if (seatIndices.size() != players.size()) {
            result.addError("Duplicate seat indices found in players");
        }
        
        // Validate each player
        for (PlayerState player : players) {
            validatePlayer(player, result);
        }
        
        // Validate dealer assignment
        long dealerCount = players.stream().filter(PlayerState::isDealer).count();
        if (dealerCount != 1) {
            result.addError("Invalid dealer count: " + dealerCount + " (expected 1)");
        }
        
        // Check if dealer user ID matches
        String dealerUserId = gameState.getDealerUserId();
        boolean dealerFound = players.stream()
                .anyMatch(p -> p.getUserId().equals(dealerUserId) && p.isDealer());
        
        if (!dealerFound) {
            result.addError("Dealer user ID does not match any dealer player: " + dealerUserId);
        }
    }
    
    /**
     * Validate individual player state
     */
    private void validatePlayer(PlayerState player, ValidationResult result) {
        if (player.getUserId() == null || player.getUserId().trim().isEmpty()) {
            result.addError("Player user ID is null or empty");
        }
        
        if (player.getSeatIndex() < 0 || player.getSeatIndex() > 2) {
            result.addError("Invalid seat index for player " + player.getUserId() + ": " + player.getSeatIndex());
        }
        
        if (player.getStatus() == null) {
            result.addError("Player status is null for player: " + player.getUserId());
        }
        
        if (player.getHandTiles() == null) {
            result.addError("Hand tiles is null for player: " + player.getUserId());
        }
        
        if (player.getMelds() == null) {
            result.addError("Melds is null for player: " + player.getUserId());
        }
        
        // Validate tile count
        int handSize = player.getHandTiles().size();
        int meldTileCount = player.getMelds().stream()
                .mapToInt(meld -> meld.getTiles().size())
                .sum();
        int totalTiles = handSize + meldTileCount;
        
        // In a 3-player game, each player should have 13-14 tiles total
        if (totalTiles < 13 || totalTiles > 14) {
            result.addWarning("Unusual tile count for player " + player.getUserId() + 
                            ": " + totalTiles + " (expected 13-14)");
        }
    }
    
    /**
     * Validate tile distribution across all players
     */
    private void validateTileDistribution(GameState gameState, ValidationResult result) {
        // Count all tiles in play
        Map<Tile, Integer> tileCount = countAllTiles(gameState);
        
        // Validate against expected tile distribution
        for (Map.Entry<Tile, Integer> entry : tileCount.entrySet()) {
            Tile tile = entry.getKey();
            int count = entry.getValue();
            
            if (count > 4) {
                result.addError("Too many instances of tile " + tile + ": " + count + " (max 4)");
            }
            
            if (count < 0) {
                result.addError("Negative count for tile " + tile + ": " + count);
            }
        }
        
        // Validate total tile count
        int totalTilesInPlay = tileCount.values().stream().mapToInt(Integer::intValue).sum();
        int expectedTotal = calculateExpectedTileCount(gameState);
        
        if (totalTilesInPlay != expectedTotal) {
            result.addWarning("Total tiles in play (" + totalTilesInPlay + 
                            ") does not match expected (" + expectedTotal + ")");
        }
    }
    
    /**
     * Count all tiles currently in the game
     */
    private Map<Tile, Integer> countAllTiles(GameState gameState) {
        Map<Tile, Integer> tileCount = new java.util.HashMap<>();
        
        // Count tiles in player hands
        for (PlayerState player : gameState.getPlayers()) {
            for (Tile tile : player.getHandTiles()) {
                tileCount.merge(tile, 1, Integer::sum);
            }
            
            // Count tiles in melds
            player.getMelds().forEach(meld -> 
                meld.getTiles().forEach(tile -> 
                    tileCount.merge(tile, 1, Integer::sum)));
        }
        
        // Count tiles in discard pile
        for (Tile tile : gameState.getDiscardPile()) {
            tileCount.merge(tile, 1, Integer::sum);
        }
        
        // Count tiles in wall
        for (Tile tile : gameState.getTileWall()) {
            tileCount.merge(tile, 1, Integer::sum);
        }
        
        return tileCount;
    }
    
    /**
     * Calculate expected total tile count based on game configuration
     */
    private int calculateExpectedTileCount(GameState gameState) {
        if ("WAN_ONLY".equals(gameState.getConfig().getTiles())) {
            return 36; // 9 ranks × 4 copies
        } else {
            return 108; // 3 suits × 9 ranks × 4 copies
        }
    }
    
    /**
     * Validate game phase consistency
     */
    private void validateGamePhase(GameState gameState, ValidationResult result) {
        GameState.GamePhase phase = gameState.getPhase();
        
        switch (phase) {
            case WAITING:
                // Should have players but no tiles dealt
                if (gameState.getPlayers().stream().anyMatch(p -> !p.getHandTiles().isEmpty())) {
                    result.addError("Players have tiles in WAITING phase");
                }
                break;
                
            case DEALING:
                // Transitional phase, allow some flexibility
                break;
                
            case PLAYING:
                // Should have tiles dealt and current player set
                if (gameState.getPlayers().stream().allMatch(p -> p.getHandTiles().isEmpty())) {
                    result.addError("No tiles dealt in PLAYING phase");
                }
                
                if (gameState.getCurrentPlayerIndex() < 0) {
                    result.addError("No current player set in PLAYING phase");
                }
                break;
                
            case SETTLEMENT:
                // Game should be ending or ended
                break;
                
            case FINISHED:
                // Game is complete
                if (gameState.getGameEndTime() == null) {
                    result.addWarning("Game marked as FINISHED but no end time set");
                }
                break;
        }
    }
    
    /**
     * Validate turn state consistency
     */
    private void validateTurnState(GameState gameState, ValidationResult result) {
        if (gameState.getPhase() == GameState.GamePhase.PLAYING) {
            if (gameState.getTurnStartTime() == null) {
                result.addError("Turn start time is null in PLAYING phase");
            }
            
            if (gameState.getTurnDeadline() == null) {
                result.addError("Turn deadline is null in PLAYING phase");
            }
            
            if (gameState.getTurnStartTime() != null && gameState.getTurnDeadline() != null) {
                if (gameState.getTurnDeadline() <= gameState.getTurnStartTime()) {
                    result.addError("Turn deadline is not after turn start time");
                }
            }
        }
    }
    
    /**
     * Attempt to recover a corrupted game state
     */
    public GameState recoverGameState(String roomId) {
        logger.info("Attempting to recover game state for room: {}", roomId);
        
        try {
            // Try to load from Redis first
            GameState gameState = gameStateRedisService.loadGameState(roomId);
            
            if (gameState != null) {
                ValidationResult validation = validateGameState(gameState);
                
                if (validation.isValid()) {
                    logger.info("Successfully recovered valid game state from Redis: {}", roomId);
                    return gameState;
                } else {
                    logger.warn("Game state from Redis is invalid: {} errors, {} warnings", 
                               validation.getErrors().size(), validation.getWarnings().size());
                    
                    // Try to repair the game state
                    GameState repairedState = repairGameState(gameState, validation);
                    if (repairedState != null) {
                        logger.info("Successfully repaired game state for room: {}", roomId);
                        gameStateRedisService.saveGameState(repairedState);
                        return repairedState;
                    }
                }
            }
            
            logger.error("Unable to recover game state for room: {}", roomId);
            return null;
            
        } catch (Exception e) {
            logger.error("Error during game state recovery for room: {}", roomId, e);
            return null;
        }
    }
    
    /**
     * Attempt to repair a corrupted game state
     */
    private GameState repairGameState(GameState gameState, ValidationResult validation) {
        logger.info("Attempting to repair game state for room: {}", gameState.getRoomId());
        
        // For now, only attempt simple repairs
        // More complex repairs would require game-specific logic
        
        try {
            // Fix current player index if out of bounds
            if (gameState.getCurrentPlayerIndex() >= gameState.getPlayers().size()) {
                gameState.setPhase(GameState.GamePhase.WAITING); // Reset to safe state
                logger.info("Reset game phase to WAITING due to invalid current player index");
            }
            
            // Validate the repaired state
            ValidationResult repairedValidation = validateGameState(gameState);
            if (repairedValidation.isValid()) {
                return gameState;
            }
            
            logger.warn("Unable to repair game state, too many issues remain");
            return null;
            
        } catch (Exception e) {
            logger.error("Error during game state repair", e);
            return null;
        }
    }
    
    /**
     * Validation result class
     */
    public static class ValidationResult {
        private final List<String> errors = new java.util.ArrayList<>();
        private final List<String> warnings = new java.util.ArrayList<>();
        
        public void addError(String error) {
            errors.add(error);
        }
        
        public void addWarning(String warning) {
            warnings.add(warning);
        }
        
        public boolean isValid() {
            return errors.isEmpty();
        }
        
        public List<String> getErrors() {
            return new java.util.ArrayList<>(errors);
        }
        
        public List<String> getWarnings() {
            return new java.util.ArrayList<>(warnings);
        }
        
        @Override
        public String toString() {
            return String.format("ValidationResult{errors=%d, warnings=%d}", 
                               errors.size(), warnings.size());
        }
    }
}