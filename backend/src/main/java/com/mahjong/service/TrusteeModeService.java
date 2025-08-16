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
import java.util.Random;

/**
 * Service for handling automatic play when players are in trustee mode
 */
@Service
public class TrusteeModeService {
    
    private static final Logger logger = LoggerFactory.getLogger(TrusteeModeService.class);
    
    @Autowired
    private GameService gameService;
    
    @Autowired
    private GameMessageBroadcastService broadcastService;
    
    private final Random random = new Random();
    
    /**
     * Execute automatic action for player in trustee mode
     */
    public void executeAutomaticAction(String roomId, String userId) {
        logger.debug("Executing automatic action for user {} in room {}", userId, roomId);
        
        try {
            GameState gameState = gameService.getGameState(roomId);
            if (gameState == null) {
                logger.warn("No game state found for automatic action: roomId={}", roomId);
                return;
            }
            
            PlayerState player = gameState.getPlayerByUserId(userId);
            if (player == null) {
                logger.warn("Player not found for automatic action: userId={}, roomId={}", userId, roomId);
                return;
            }
            
            if (player.getStatus() != PlayerState.PlayerStatus.TRUSTEE) {
                logger.debug("Player {} is not in trustee mode, skipping automatic action", userId);
                return;
            }
            
            // Check if it's the player's turn
            PlayerState currentPlayer = gameState.getCurrentPlayer();
            if (currentPlayer == null || !currentPlayer.getUserId().equals(userId)) {
                logger.debug("Not player's turn for automatic action: userId={}", userId);
                return;
            }
            
            // Execute appropriate automatic action based on available actions
            List<PlayerState.ActionType> availableActions = player.getAvailableActions();
            
            if (availableActions.contains(PlayerState.ActionType.HU)) {
                // Always win if possible
                executeAutomaticHu(roomId, userId);
            } else if (availableActions.contains(PlayerState.ActionType.GANG)) {
                // Sometimes form Gang (70% chance)
                if (random.nextDouble() < 0.7) {
                    executeAutomaticGang(roomId, userId, player);
                } else {
                    executeAutomaticDiscard(roomId, userId, player);
                }
            } else if (availableActions.contains(PlayerState.ActionType.PENG)) {
                // Sometimes form Peng (50% chance)
                if (random.nextDouble() < 0.5) {
                    executeAutomaticPeng(roomId, userId, player);
                } else {
                    executeAutomaticPass(roomId, userId);
                }
            } else if (availableActions.contains(PlayerState.ActionType.DISCARD)) {
                // Discard a tile
                executeAutomaticDiscard(roomId, userId, player);
            } else if (availableActions.contains(PlayerState.ActionType.PASS)) {
                // Pass on the action
                executeAutomaticPass(roomId, userId);
            } else {
                logger.warn("No available actions for trustee player: userId={}, roomId={}", userId, roomId);
            }
            
        } catch (Exception e) {
            logger.error("Error executing automatic action for user {} in room {}", userId, roomId, e);
        }
    }
    
    /**
     * Execute automatic Hu (win)
     */
    private void executeAutomaticHu(String roomId, String userId) {
        logger.info("Trustee executing automatic Hu for user {} in room {}", userId, roomId);
        
        try {
            PlayerAction huAction = new HuAction("", true); // Self-draw win
            ActionResult result = gameService.handlePlayerAction(roomId, userId, huAction);
            
            if (result.isSuccess()) {
                broadcastService.broadcastRoomEvent(roomId, "trusteeAction", 
                    Map.of("userId", userId, "action", "hu", "automatic", true));
            }
            
        } catch (Exception e) {
            logger.error("Error executing automatic Hu for user {} in room {}", userId, roomId, e);
        }
    }
    
    /**
     * Execute automatic Gang
     */
    private void executeAutomaticGang(String roomId, String userId, PlayerState player) {
        logger.info("Trustee executing automatic Gang for user {} in room {}", userId, roomId);
        
        try {
            // Find a tile that can form Gang
            List<Tile> handTiles = player.getHandTiles();
            for (Tile tile : handTiles) {
                if (player.canConcealedGang(tile)) {
                    PlayerAction gangAction = new GangAction(tile.toString(), GangAction.GangType.AN, null);
                    ActionResult result = gameService.handlePlayerAction(roomId, userId, gangAction);
                    
                    if (result.isSuccess()) {
                        broadcastService.broadcastRoomEvent(roomId, "trusteeAction", 
                            Map.of("userId", userId, "action", "gang", "tile", tile.toString(), "automatic", true));
                        return;
                    }
                }
            }
            
            // If no concealed Gang possible, try upgrade Gang
            for (Tile tile : handTiles) {
                if (player.canUpgradeGang(tile)) {
                    PlayerAction gangAction = new GangAction(tile.toString(), GangAction.GangType.BU, null);
                    ActionResult result = gameService.handlePlayerAction(roomId, userId, gangAction);
                    
                    if (result.isSuccess()) {
                        broadcastService.broadcastRoomEvent(roomId, "trusteeAction", 
                            Map.of("userId", userId, "action", "gang", "tile", tile.toString(), "automatic", true));
                        return;
                    }
                }
            }
            
            // If no Gang possible, discard instead
            executeAutomaticDiscard(roomId, userId, player);
            
        } catch (Exception e) {
            logger.error("Error executing automatic Gang for user {} in room {}", userId, roomId, e);
        }
    }
    
    /**
     * Execute automatic Peng
     */
    private void executeAutomaticPeng(String roomId, String userId, PlayerState player) {
        logger.info("Trustee executing automatic Peng for user {} in room {}", userId, roomId);
        
        try {
            // This would need the discarded tile information
            // For now, just pass
            executeAutomaticPass(roomId, userId);
            
        } catch (Exception e) {
            logger.error("Error executing automatic Peng for user {} in room {}", userId, roomId, e);
        }
    }
    
    /**
     * Execute automatic discard
     */
    private void executeAutomaticDiscard(String roomId, String userId, PlayerState player) {
        logger.debug("Trustee executing automatic discard for user {} in room {}", userId, roomId);
        
        try {
            List<Tile> handTiles = player.getHandTiles();
            if (handTiles.isEmpty()) {
                logger.warn("No tiles to discard for user {} in room {}", userId, roomId);
                return;
            }
            
            // Simple strategy: discard a random tile (could be improved with better AI)
            Tile tileToDiscard = handTiles.get(random.nextInt(handTiles.size()));
            
            PlayerAction discardAction = new DiscardAction(tileToDiscard.toString());
            ActionResult result = gameService.handlePlayerAction(roomId, userId, discardAction);
            
            if (result.isSuccess()) {
                broadcastService.broadcastRoomEvent(roomId, "trusteeAction", 
                    Map.of("userId", userId, "action", "discard", "tile", tileToDiscard.toString(), "automatic", true));
            }
            
        } catch (Exception e) {
            logger.error("Error executing automatic discard for user {} in room {}", userId, roomId, e);
        }
    }
    
    /**
     * Execute automatic pass
     */
    private void executeAutomaticPass(String roomId, String userId) {
        logger.debug("Trustee executing automatic pass for user {} in room {}", userId, roomId);
        
        try {
            PlayerAction passAction = new PassAction();
            ActionResult result = gameService.handlePlayerAction(roomId, userId, passAction);
            
            if (result.isSuccess()) {
                broadcastService.broadcastRoomEvent(roomId, "trusteeAction", 
                    Map.of("userId", userId, "action", "pass", "automatic", true));
            }
            
        } catch (Exception e) {
            logger.error("Error executing automatic pass for user {} in room {}", userId, roomId, e);
        }
    }
}