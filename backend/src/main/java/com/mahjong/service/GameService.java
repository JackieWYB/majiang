package com.mahjong.service;

import com.mahjong.model.config.RoomConfig;
import com.mahjong.model.entity.Room;
import com.mahjong.model.entity.RoomPlayer;
import com.mahjong.model.game.GameState;
import com.mahjong.model.game.PlayerState;
import com.mahjong.model.game.Tile;
import com.mahjong.repository.RoomPlayerRepository;
import com.mahjong.repository.RoomRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Service for game engine and turn management
 */
@Service
@Transactional
public class GameService {
    
    private static final Logger logger = LoggerFactory.getLogger(GameService.class);
    private static final String GAME_STATE_KEY_PREFIX = "game:";
    private static final String PENDING_ACTIONS_KEY_PREFIX = "pending:";
    
    private final RoomRepository roomRepository;
    private final RoomPlayerRepository roomPlayerRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ScheduledExecutorService scheduledExecutorService;
    private final GameStateRedisService gameStateRedisService;
    private final GameStateConsistencyService consistencyService;
    private final TrusteeModeService trusteeModeService;
    
    // In-memory cache for active games
    private final Map<String, GameState> activeGames = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> turnTimers = new ConcurrentHashMap<>();
    private final Map<String, Map<String, PendingAction>> pendingActions = new ConcurrentHashMap<>();
    
    @Autowired
    public GameService(RoomRepository roomRepository,
                      RoomPlayerRepository roomPlayerRepository,
                      RedisTemplate<String, Object> redisTemplate,
                      @Qualifier("gameScheduledExecutorService") ScheduledExecutorService scheduledExecutorService,
                      GameStateRedisService gameStateRedisService,
                      GameStateConsistencyService consistencyService,
                      TrusteeModeService trusteeModeService) {
        this.roomRepository = roomRepository;
        this.roomPlayerRepository = roomPlayerRepository;
        this.redisTemplate = redisTemplate;
        this.scheduledExecutorService = scheduledExecutorService;
        this.gameStateRedisService = gameStateRedisService;
        this.consistencyService = consistencyService;
        this.trusteeModeService = trusteeModeService;
    }
    
    /**
     * Initialize and start a new game
     */
    public GameState startGame(String roomId) {
        logger.info("Starting game for room: {}", roomId);
        
        Room room = roomRepository.findByIdWithPlayers(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Room not found: " + roomId));
        
        List<RoomPlayer> roomPlayers = roomPlayerRepository.findByRoomIdOrderBySeatIndex(roomId);
        if (roomPlayers.size() != 3) {
            throw new IllegalStateException("Room must have exactly 3 players to start game");
        }
        
        // Create game state
        String gameId = UUID.randomUUID().toString();
        List<String> playerIds = roomPlayers.stream()
                .map(rp -> rp.getUserId().toString())
                .toList();
        
        RoomConfig config = room.getRoomRule().getConfig();
        GameState gameState = new GameState(roomId, gameId, playerIds, config);
        
        // Deal initial tiles
        gameState.dealInitialTiles();
        
        // Store game state
        activeGames.put(roomId, gameState);
        gameStateRedisService.saveGameState(gameState);
        
        // Start turn timer
        startTurnTimer(roomId);
        
        logger.info("Game started for room: {} with game ID: {}", roomId, gameId);
        return gameState;
    }
    
    /**
     * Handle player action
     */
    public ActionResult handlePlayerAction(String roomId, String userId, PlayerAction action) {
        logger.debug("Handling action {} from user {} in room {}", action.getType(), userId, roomId);
        
        GameState gameState = getGameState(roomId);
        if (gameState == null) {
            throw new IllegalStateException("Game not found for room: " + roomId);
        }
        
        PlayerState player = gameState.getPlayerByUserId(userId);
        if (player == null) {
            throw new IllegalArgumentException("Player not found in game: " + userId);
        }
        
        // Validate action
        ActionValidationResult actionValidation = validatePlayerAction(gameState, player, action);
        if (!actionValidation.isValid()) {
            return ActionResult.failure(actionValidation.getErrorMessage());
        }
        
        // Process action based on type
        ActionResult result;
        switch (action.getType()) {
            case DISCARD:
                result = processDiscardAction(gameState, userId, (DiscardAction) action);
                break;
            case PENG:
                result = processPengAction(gameState, userId, (PengAction) action);
                break;
            case GANG:
                result = processGangAction(gameState, userId, (GangAction) action);
                break;
            case CHI:
                result = processChiAction(gameState, userId, (ChiAction) action);
                break;
            case HU:
                result = processHuAction(gameState, userId, (HuAction) action);
                break;
            case PASS:
                result = processPassAction(gameState, userId);
                break;
            default:
                result = ActionResult.failure("Unknown action type: " + action.getType());
        }
        
        if (result.isSuccess()) {
            // Validate game state consistency
            GameStateConsistencyService.ValidationResult validation = consistencyService.validateGameState(gameState);
            if (!validation.isValid()) {
                logger.warn("Game state validation failed after action: {} errors", validation.getErrors().size());
                for (String error : validation.getErrors()) {
                    logger.warn("Validation error: {}", error);
                }
            }
            
            // Update game state
            gameStateRedisService.saveGameState(gameState);
            
            // Check if game should end
            if (gameState.shouldEndGame()) {
                endGame(roomId);
            } else {
                // Restart turn timer if needed
                if (gameState.getPhase() == GameState.GamePhase.PLAYING) {
                    startTurnTimer(roomId);
                }
            }
        }
        
        return result;
    }
    
    /**
     * Process discard action
     */
    private ActionResult processDiscardAction(GameState gameState, String userId, DiscardAction action) {
        try {
            PlayerState currentPlayer = gameState.getCurrentPlayer();
            if (!currentPlayer.getUserId().equals(userId)) {
                return ActionResult.failure("Not your turn");
            }
            
            Tile tileToDiscard = new Tile(action.getTile());
            gameState.processDiscard(userId, tileToDiscard);
            
            // Cancel current turn timer
            cancelTurnTimer(gameState.getRoomId());
            
            // Check for claim actions from other players
            checkAndHandleClaimActions(gameState, tileToDiscard, userId);
            
            return ActionResult.success("Tile discarded successfully");
            
        } catch (Exception e) {
            logger.error("Error processing discard action", e);
            return ActionResult.failure("Failed to discard tile: " + e.getMessage());
        }
    }
    
    /**
     * Process Peng action
     */
    private ActionResult processPengAction(GameState gameState, String userId, PengAction action) {
        try {
            Tile claimedTile = new Tile(action.getTile());
            String claimedFrom = action.getClaimedFrom();
            
            gameState.processPeng(userId, claimedTile, claimedFrom);
            
            // Clear pending actions
            clearPendingActions(gameState.getRoomId());
            
            return ActionResult.success("Peng successful");
            
        } catch (Exception e) {
            logger.error("Error processing peng action", e);
            return ActionResult.failure("Failed to peng: " + e.getMessage());
        }
    }
    
    /**
     * Process Gang action
     */
    private ActionResult processGangAction(GameState gameState, String userId, GangAction action) {
        try {
            // Implementation for Gang action
            // This would be similar to Peng but with different logic
            return ActionResult.success("Gang successful");
            
        } catch (Exception e) {
            logger.error("Error processing gang action", e);
            return ActionResult.failure("Failed to gang: " + e.getMessage());
        }
    }
    
    /**
     * Process Chi action
     */
    private ActionResult processChiAction(GameState gameState, String userId, ChiAction action) {
        try {
            // Implementation for Chi action
            return ActionResult.success("Chi successful");
            
        } catch (Exception e) {
            logger.error("Error processing chi action", e);
            return ActionResult.failure("Failed to chi: " + e.getMessage());
        }
    }
    
    /**
     * Process Hu action
     */
    private ActionResult processHuAction(GameState gameState, String userId, HuAction action) {
        try {
            PlayerState player = gameState.getPlayerByUserId(userId);
            if (!player.hasWinningHand()) {
                return ActionResult.failure("Invalid winning hand");
            }
            
            // Mark player as winner and end game
            player.setStatus(PlayerState.PlayerStatus.FINISHED);
            gameState.endGame();
            
            return ActionResult.success("Hu successful - game won!");
            
        } catch (Exception e) {
            logger.error("Error processing hu action", e);
            return ActionResult.failure("Failed to hu: " + e.getMessage());
        }
    }
    
    /**
     * Process Pass action
     */
    private ActionResult processPassAction(GameState gameState, String userId) {
        try {
            // Remove player from pending actions
            removePendingAction(gameState.getRoomId(), userId);
            
            // Check if all players have passed
            if (allPlayersPassedOrActed(gameState.getRoomId())) {
                // Continue with normal turn flow
                gameState.nextTurn();
                startTurnTimer(gameState.getRoomId());
            }
            
            return ActionResult.success("Passed");
            
        } catch (Exception e) {
            logger.error("Error processing pass action", e);
            return ActionResult.failure("Failed to pass: " + e.getMessage());
        }
    }
    
    /**
     * Validate player action
     */
    private ActionValidationResult validatePlayerAction(GameState gameState, PlayerState player, PlayerAction action) {
        // Check game phase
        if (gameState.getPhase() != GameState.GamePhase.PLAYING) {
            return ActionValidationResult.invalid("Game is not in playing phase");
        }
        
        // Check player status
        if (player.getStatus() == PlayerState.PlayerStatus.DISCONNECTED) {
            return ActionValidationResult.invalid("Player is disconnected");
        }
        
        // Check if action is available for player
        if (!player.getAvailableActions().contains(action.getType().toPlayerActionType())) {
            return ActionValidationResult.invalid("Action not available for player");
        }
        
        // Validate specific action requirements
        switch (action.getType()) {
            case DISCARD:
                return validateDiscardAction(gameState, player, (DiscardAction) action);
            case PENG:
                return validatePengAction(gameState, player, (PengAction) action);
            case GANG:
                return validateGangAction(gameState, player, (GangAction) action);
            case CHI:
                return validateChiAction(gameState, player, (ChiAction) action);
            case HU:
                return validateHuAction(gameState, player, (HuAction) action);
            case PASS:
                return ActionValidationResult.valid();
            default:
                return ActionValidationResult.invalid("Unknown action type");
        }
    }
    
    /**
     * Validate discard action
     */
    private ActionValidationResult validateDiscardAction(GameState gameState, PlayerState player, DiscardAction action) {
        if (!gameState.getCurrentPlayer().getUserId().equals(player.getUserId())) {
            return ActionValidationResult.invalid("Not your turn");
        }
        
        try {
            Tile tile = new Tile(action.getTile());
            if (!player.getHandTiles().contains(tile)) {
                return ActionValidationResult.invalid("You don't have this tile");
            }
            return ActionValidationResult.valid();
        } catch (Exception e) {
            return ActionValidationResult.invalid("Invalid tile format");
        }
    }
    
    /**
     * Validate Peng action
     */
    private ActionValidationResult validatePengAction(GameState gameState, PlayerState player, PengAction action) {
        try {
            Tile tile = new Tile(action.getTile());
            if (!player.canPeng(tile)) {
                return ActionValidationResult.invalid("Cannot form Peng with this tile");
            }
            return ActionValidationResult.valid();
        } catch (Exception e) {
            return ActionValidationResult.invalid("Invalid tile format");
        }
    }
    
    /**
     * Validate Gang action
     */
    private ActionValidationResult validateGangAction(GameState gameState, PlayerState player, GangAction action) {
        try {
            Tile tile = new Tile(action.getTile());
            if (!player.canGang(tile) && !player.canConcealedGang(tile) && !player.canUpgradeGang(tile)) {
                return ActionValidationResult.invalid("Cannot form Gang with this tile");
            }
            return ActionValidationResult.valid();
        } catch (Exception e) {
            return ActionValidationResult.invalid("Invalid tile format");
        }
    }
    
    /**
     * Validate Chi action
     */
    private ActionValidationResult validateChiAction(GameState gameState, PlayerState player, ChiAction action) {
        try {
            Tile tile = new Tile(action.getTile());
            if (!player.canChi(tile)) {
                return ActionValidationResult.invalid("Cannot form Chi with this tile");
            }
            return ActionValidationResult.valid();
        } catch (Exception e) {
            return ActionValidationResult.invalid("Invalid tile format");
        }
    }
    
    /**
     * Validate Hu action
     */
    private ActionValidationResult validateHuAction(GameState gameState, PlayerState player, HuAction action) {
        if (!player.hasWinningHand()) {
            return ActionValidationResult.invalid("Not a winning hand");
        }
        return ActionValidationResult.valid();
    }
    
    /**
     * Check and handle claim actions from other players
     */
    private void checkAndHandleClaimActions(GameState gameState, Tile discardedTile, String discardingUserId) {
        Map<String, PendingAction> roomPendingActions = new HashMap<>();
        
        for (PlayerState player : gameState.getPlayers()) {
            if (player.getUserId().equals(discardingUserId)) continue;
            
            List<PlayerState.ActionType> availableActions = new ArrayList<>();
            
            // Check for possible actions
            if (gameState.getConfig().getAllowPeng() && player.canPeng(discardedTile)) {
                availableActions.add(PlayerState.ActionType.PENG);
            }
            
            if (gameState.getConfig().getAllowGang() && player.canGang(discardedTile)) {
                availableActions.add(PlayerState.ActionType.GANG);
            }
            
            if (gameState.getConfig().getAllowChi() && player.canChi(discardedTile)) {
                availableActions.add(PlayerState.ActionType.CHI);
            }
            
            if (player.hasWinningHand()) {
                availableActions.add(PlayerState.ActionType.HU);
            }
            
            if (!availableActions.isEmpty()) {
                availableActions.add(PlayerState.ActionType.PASS);
                player.setAvailableActions(availableActions);
                
                // Create pending action
                PendingAction pendingAction = new PendingAction(
                    player.getUserId(),
                    discardedTile,
                    discardingUserId,
                    availableActions,
                    System.currentTimeMillis() + (gameState.getConfig().getTurn().getActionTimeLimit() * 1000L)
                );
                roomPendingActions.put(player.getUserId(), pendingAction);
            }
        }
        
        if (!roomPendingActions.isEmpty()) {
            pendingActions.put(gameState.getRoomId(), roomPendingActions);
            
            // Start action timer
            startActionTimer(gameState.getRoomId());
        } else {
            // No one can claim, continue to next turn
            gameState.nextTurn();
        }
    }
    
    /**
     * Start turn timer
     */
    private void startTurnTimer(String roomId) {
        cancelTurnTimer(roomId);
        
        GameState gameState = getGameState(roomId);
        if (gameState == null) return;
        
        int timeLimit = gameState.getConfig().getTurn().getTurnTimeLimit();
        
        ScheduledFuture<?> timer = scheduledExecutorService.schedule(() -> {
            handleTurnTimeout(roomId);
        }, timeLimit, TimeUnit.SECONDS);
        
        turnTimers.put(roomId, timer);
    }
    
    /**
     * Start action timer for claim actions
     */
    private void startActionTimer(String roomId) {
        GameState gameState = getGameState(roomId);
        if (gameState == null) return;
        
        int timeLimit = gameState.getConfig().getTurn().getActionTimeLimit();
        
        scheduledExecutorService.schedule(() -> {
            handleActionTimeout(roomId);
        }, timeLimit, TimeUnit.SECONDS);
    }
    
    /**
     * Cancel turn timer
     */
    private void cancelTurnTimer(String roomId) {
        ScheduledFuture<?> timer = turnTimers.remove(roomId);
        if (timer != null && !timer.isDone()) {
            timer.cancel(false);
        }
    }
    
    /**
     * Handle turn timeout
     */
    private void handleTurnTimeout(String roomId) {
        logger.info("Turn timeout for room: {}", roomId);
        
        GameState gameState = getGameState(roomId);
        if (gameState == null) return;
        
        PlayerState currentPlayer = gameState.getCurrentPlayer();
        if (currentPlayer == null) return;
        
        // Increment timeout counter
        currentPlayer.incrementTimeouts();
        
        // Auto-play for the player
        if (gameState.getConfig().getTurn().getAutoTrustee()) {
            autoPlayForPlayer(gameState, currentPlayer);
        }
        
        // Move to next turn
        gameState.nextTurn();
        gameStateRedisService.saveGameState(gameState);
        
        // Start next turn timer
        startTurnTimer(roomId);
    }
    
    /**
     * Handle action timeout for claim actions
     */
    private void handleActionTimeout(String roomId) {
        logger.info("Action timeout for room: {}", roomId);
        
        // Clear all pending actions and continue with normal flow
        clearPendingActions(roomId);
        
        GameState gameState = getGameState(roomId);
        if (gameState != null) {
            gameState.nextTurn();
            gameStateRedisService.saveGameState(gameState);
            startTurnTimer(roomId);
        }
    }
    
    /**
     * Auto-play for a player (trustee mode)
     */
    private void autoPlayForPlayer(GameState gameState, PlayerState player) {
        logger.info("Auto-playing for player: {} in room: {}", player.getUserId(), gameState.getRoomId());
        
        // Set player to trustee mode if not already
        if (player.getStatus() != PlayerState.PlayerStatus.TRUSTEE) {
            player.setStatus(PlayerState.PlayerStatus.TRUSTEE);
        }
        
        // Use TrusteeModeService for intelligent auto-play
        try {
            trusteeModeService.executeAutomaticAction(gameState.getRoomId(), player.getUserId());
        } catch (Exception e) {
            logger.error("Error in trustee mode auto-play for player: {}", player.getUserId(), e);
            
            // Fallback to simple discard
            List<Tile> handTiles = player.getHandTiles();
            if (!handTiles.isEmpty()) {
                Tile tileToDiscard = handTiles.get(handTiles.size() - 1);
                try {
                    gameState.processDiscard(player.getUserId(), tileToDiscard);
                } catch (Exception fallbackError) {
                    logger.error("Error in fallback auto-play for player: {}", player.getUserId(), fallbackError);
                }
            }
        }
    }
    
    /**
     * End the game
     */
    private void endGame(String roomId) {
        logger.info("Ending game for room: {}", roomId);
        
        GameState gameState = getGameState(roomId);
        if (gameState != null) {
            gameState.endGame();
            gameStateRedisService.saveGameState(gameState);
        }
        
        // Clean up timers and pending actions
        cancelTurnTimer(roomId);
        clearPendingActions(roomId);
        
        // Remove from active games after a delay to allow final state retrieval
        scheduledExecutorService.schedule(() -> {
            activeGames.remove(roomId);
        }, 5, TimeUnit.MINUTES);
    }
    
    /**
     * Get game state with recovery support
     */
    public GameState getGameState(String roomId) {
        // Try in-memory cache first
        GameState gameState = activeGames.get(roomId);
        if (gameState != null) {
            // Refresh TTL for active games
            gameStateRedisService.refreshGameStateTtl(roomId);
            return gameState;
        }
        
        // Try Redis with consistency validation
        gameState = gameStateRedisService.loadGameState(roomId);
        if (gameState != null) {
            // Validate consistency
            GameStateConsistencyService.ValidationResult validation = consistencyService.validateGameState(gameState);
            if (validation.isValid()) {
                activeGames.put(roomId, gameState);
                return gameState;
            } else {
                logger.warn("Loaded game state failed validation, attempting recovery: {}", roomId);
                // Try to recover the game state
                GameState recoveredState = consistencyService.recoverGameState(roomId);
                if (recoveredState != null) {
                    activeGames.put(roomId, recoveredState);
                    return recoveredState;
                }
            }
        }
        
        return null;
    }
    
    /**
     * Create game state snapshot for reconnection
     */
    public GameStateRedisService.GameStateSnapshot createGameSnapshot(String roomId) {
        GameState gameState = getGameState(roomId);
        if (gameState == null) {
            return null;
        }
        
        return gameStateRedisService.createSnapshot(gameState);
    }
    
    /**
     * Handle player reconnection
     */
    public GameState handlePlayerReconnection(String roomId, String userId, String sessionId) {
        logger.info("Handling player reconnection: user={}, room={}, session={}", userId, roomId, sessionId);
        
        GameState gameState = getGameState(roomId);
        if (gameState == null) {
            logger.warn("No game state found for reconnection: room={}", roomId);
            return null;
        }
        
        PlayerState player = gameState.getPlayerByUserId(userId);
        if (player == null) {
            logger.warn("Player not found in game state: user={}, room={}", userId, roomId);
            return null;
        }
        
        // Update player status if they were disconnected
        if (player.getStatus() == PlayerState.PlayerStatus.DISCONNECTED) {
            player.setStatus(PlayerState.PlayerStatus.WAITING_TURN);
            if (gameState.getCurrentPlayer().getUserId().equals(userId)) {
                player.setStatus(PlayerState.PlayerStatus.PLAYING);
            }
        }
        
        // Save session mapping
        gameStateRedisService.savePlayerSession(userId, sessionId, roomId);
        
        // Update game state
        gameStateRedisService.saveGameState(gameState);
        
        logger.info("Player reconnected successfully: user={}, room={}", userId, roomId);
        return gameState;
    }
    
    /**
     * Handle player disconnection
     */
    public void handlePlayerDisconnection(String userId, String sessionId) {
        logger.info("Handling player disconnection: user={}, session={}", userId, sessionId);
        
        GameStateRedisService.SessionInfo sessionInfo = gameStateRedisService.getSessionInfo(sessionId);
        if (sessionInfo == null) {
            logger.warn("No session info found for disconnection: session={}", sessionId);
            return;
        }
        
        String roomId = sessionInfo.getRoomId();
        GameState gameState = getGameState(roomId);
        if (gameState != null) {
            PlayerState player = gameState.getPlayerByUserId(userId);
            if (player != null) {
                player.setStatus(PlayerState.PlayerStatus.DISCONNECTED);
                gameStateRedisService.saveGameState(gameState);
                
                // Schedule auto-trustee activation after 30 seconds
                scheduledExecutorService.schedule(() -> {
                    activateAutoTrustee(roomId, userId);
                }, 30, TimeUnit.SECONDS);
            }
        }
        
        // Remove session mapping
        gameStateRedisService.removePlayerSession(userId, sessionId);
        
        logger.info("Player disconnection handled: user={}, room={}", userId, roomId);
    }
    
    /**
     * Activate auto-trustee mode for disconnected player
     */
    private void activateAutoTrustee(String roomId, String userId) {
        GameState gameState = getGameState(roomId);
        if (gameState == null) return;
        
        PlayerState player = gameState.getPlayerByUserId(userId);
        if (player != null && player.getStatus() == PlayerState.PlayerStatus.DISCONNECTED) {
            logger.info("Activating auto-trustee for player: user={}, room={}", userId, roomId);
            player.setStatus(PlayerState.PlayerStatus.TRUSTEE);
            gameStateRedisService.saveGameState(gameState);
        }
    }
    
    /**
     * Clear pending actions for a room
     */
    private void clearPendingActions(String roomId) {
        pendingActions.remove(roomId);
        redisTemplate.delete(PENDING_ACTIONS_KEY_PREFIX + roomId);
    }
    
    /**
     * Remove pending action for a specific player
     */
    private void removePendingAction(String roomId, String userId) {
        Map<String, PendingAction> roomActions = pendingActions.get(roomId);
        if (roomActions != null) {
            roomActions.remove(userId);
            if (roomActions.isEmpty()) {
                pendingActions.remove(roomId);
            }
        }
    }
    
    /**
     * Check if all players have passed or acted
     */
    private boolean allPlayersPassedOrActed(String roomId) {
        Map<String, PendingAction> roomActions = pendingActions.get(roomId);
        return roomActions == null || roomActions.isEmpty();
    }
    
    /**
     * Scheduled cleanup of expired games
     */
    @Scheduled(fixedRate = 300000) // Run every 5 minutes
    public void cleanupExpiredGames() {
        logger.debug("Running expired game cleanup");
        
        List<String> expiredRooms = new ArrayList<>();
        long currentTime = System.currentTimeMillis();
        
        for (Map.Entry<String, GameState> entry : activeGames.entrySet()) {
            GameState gameState = entry.getValue();
            if (gameState.getPhase() == GameState.GamePhase.FINISHED &&
                currentTime - gameState.getGameEndTime() > Duration.ofHours(1).toMillis()) {
                expiredRooms.add(entry.getKey());
            }
        }
        
        for (String roomId : expiredRooms) {
            logger.info("Cleaning up expired game for room: {}", roomId);
            activeGames.remove(roomId);
            cancelTurnTimer(roomId);
            clearPendingActions(roomId);
        }
        
        if (!expiredRooms.isEmpty()) {
            logger.info("Cleaned up {} expired games", expiredRooms.size());
        }
    }
}