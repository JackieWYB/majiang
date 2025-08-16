package com.mahjong.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mahjong.model.dto.GameAction;
import com.mahjong.model.dto.GameSnapshot;
import com.mahjong.model.dto.PlayerSnapshot;
import com.mahjong.model.entity.GameRecord;
import com.mahjong.model.game.GameState;
import com.mahjong.model.game.PlayerState;
import com.mahjong.model.game.Tile;
import com.mahjong.model.game.TileShuffler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for reconstructing and replaying games from saved action sequences
 */
@Service
public class GameReplayService {
    
    private static final Logger logger = LoggerFactory.getLogger(GameReplayService.class);
    
    @Autowired
    private GameHistoryService gameHistoryService;
    
    private final ObjectMapper objectMapper;
    
    public GameReplayService() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }
    
    /**
     * Replay result containing the reconstructed game state and metadata
     */
    public static class ReplayResult {
        private final GameState gameState;
        private final List<GameAction> actions;
        private final Map<String, Object> metadata;
        private final boolean successful;
        private final String errorMessage;
        
        public ReplayResult(GameState gameState, List<GameAction> actions, Map<String, Object> metadata) {
            this.gameState = gameState;
            this.actions = actions;
            this.metadata = metadata;
            this.successful = true;
            this.errorMessage = null;
        }
        
        public ReplayResult(String errorMessage) {
            this.gameState = null;
            this.actions = null;
            this.metadata = null;
            this.successful = false;
            this.errorMessage = errorMessage;
        }
        
        // Getters
        public GameState getGameState() { return gameState; }
        public List<GameAction> getActions() { return actions; }
        public Map<String, Object> getMetadata() { return metadata; }
        public boolean isSuccessful() { return successful; }
        public String getErrorMessage() { return errorMessage; }
    }
    
    /**
     * Replay step containing state at a specific point in the game
     */
    public static class ReplayStep {
        private final int stepNumber;
        private final GameAction action;
        private final GameSnapshot gameSnapshot;
        private final String description;
        
        public ReplayStep(int stepNumber, GameAction action, GameSnapshot gameSnapshot, String description) {
            this.stepNumber = stepNumber;
            this.action = action;
            this.gameSnapshot = gameSnapshot;
            this.description = description;
        }
        
        // Getters
        public int getStepNumber() { return stepNumber; }
        public GameAction getAction() { return action; }
        public GameSnapshot getGameSnapshot() { return gameSnapshot; }
        public String getDescription() { return description; }
    }
    
    /**
     * Reconstruct complete game from saved record
     */
    public ReplayResult reconstructGame(String gameId) {
        try {
            // Get game record
            Optional<GameRecord> gameRecordOpt = gameHistoryService.getGameRecordForReplay(gameId);
            if (gameRecordOpt.isEmpty()) {
                return new ReplayResult("Game record not found: " + gameId);
            }
            
            GameRecord gameRecord = gameRecordOpt.get();
            
            // Get action sequence
            List<GameAction> actions = gameHistoryService.reconstructGameActions(gameId);
            if (actions.isEmpty()) {
                return new ReplayResult("No action data found for game: " + gameId);
            }
            
            // Find game start action to get initial state
            GameAction.GameStartActionDto gameStartAction = actions.stream()
                    .filter(action -> action instanceof GameAction.GameStartActionDto)
                    .map(action -> (GameAction.GameStartActionDto) action)
                    .findFirst()
                    .orElse(null);
            
            if (gameStartAction == null) {
                return new ReplayResult("Game start action not found for game: " + gameId);
            }
            
            // Initialize game state with original random seed
            GameState gameState = initializeGameStateFromRecord(gameRecord, gameStartAction);
            
            // Apply all actions in sequence
            for (GameAction action : actions) {
                if (action instanceof GameAction.GameStartActionDto || 
                    action instanceof GameAction.GameEndActionDto) {
                    continue; // Skip meta actions
                }
                
                applyActionToGameState(gameState, action);
            }
            
            // Create metadata
            Map<String, Object> metadata = createReplayMetadata(gameRecord, actions);
            
            logger.info("Successfully reconstructed game: {} with {} actions", gameId, actions.size());
            return new ReplayResult(gameState, actions, metadata);
            
        } catch (Exception e) {
            logger.error("Failed to reconstruct game: {}", gameId, e);
            return new ReplayResult("Failed to reconstruct game: " + e.getMessage());
        }
    }
    
    /**
     * Get step-by-step replay of a game
     */
    public List<ReplayStep> getStepByStepReplay(String gameId) {
        ReplayResult replayResult = reconstructGame(gameId);
        if (!replayResult.isSuccessful()) {
            logger.warn("Cannot create step-by-step replay: {}", replayResult.getErrorMessage());
            return new ArrayList<>();
        }
        
        List<ReplayStep> steps = new ArrayList<>();
        List<GameAction> actions = replayResult.getActions();
        
        // Get game record for initial state
        Optional<GameRecord> gameRecordOpt = gameHistoryService.getGameRecordForReplay(gameId);
        if (gameRecordOpt.isEmpty()) {
            return steps;
        }
        
        GameRecord gameRecord = gameRecordOpt.get();
        
        // Find game start action
        GameAction.GameStartActionDto gameStartAction = actions.stream()
                .filter(action -> action instanceof GameAction.GameStartActionDto)
                .map(action -> (GameAction.GameStartActionDto) action)
                .findFirst()
                .orElse(null);
        
        if (gameStartAction == null) {
            return steps;
        }
        
        // Initialize game state
        GameState gameState = initializeGameStateFromRecord(gameRecord, gameStartAction);
        
        // Add initial step
        steps.add(new ReplayStep(0, gameStartAction, 
                createGameSnapshot(gameState), "Game started"));
        
        // Apply each action and create step
        int stepNumber = 1;
        for (GameAction action : actions) {
            if (action instanceof GameAction.GameStartActionDto || 
                action instanceof GameAction.GameEndActionDto) {
                continue;
            }
            
            applyActionToGameState(gameState, action);
            
            String description = createActionDescription(action);
            GameSnapshot snapshot = createGameSnapshot(gameState);
            
            steps.add(new ReplayStep(stepNumber++, action, snapshot, description));
        }
        
        logger.info("Created step-by-step replay for game: {} with {} steps", gameId, steps.size());
        return steps;
    }
    
    /**
     * Initialize game state from game record and start action
     */
    private GameState initializeGameStateFromRecord(GameRecord gameRecord, 
                                                   GameAction.GameStartActionDto gameStartAction) {
        // Parse final hands to get player information
        Map<String, Object> finalHandsData = parseFinalHands(gameRecord.getFinalHands());
        
        // Create players list
        List<String> playerIds = new ArrayList<>();
        for (String userIdStr : finalHandsData.keySet()) {
            playerIds.add(userIdStr);
        }
        
        // Initialize game state with a basic config (this is simplified for replay)
        com.mahjong.model.config.RoomConfig basicConfig = new com.mahjong.model.config.RoomConfig();
        GameState gameState = new GameState(gameRecord.getRoomId(), gameRecord.getId(), playerIds, basicConfig);
        
        // Set dealer and random seed
        gameState.setPhase(GameState.GamePhase.PLAYING);
        
        // Deal initial tiles (simplified - in real implementation this would be more complex)
        // dealInitialTiles(gameState); // Skip for now as it's complex
        
        return gameState;
    }
    
    /**
     * Apply a single action to the game state
     */
    private void applyActionToGameState(GameState gameState, GameAction action) {
        switch (action.getActionType()) {
            case DISCARD:
                applyDiscardAction(gameState, (GameAction.DiscardActionDto) action);
                break;
            case DRAW:
                applyDrawAction(gameState, (GameAction.DrawActionDto) action);
                break;
            case PENG:
                applyPengAction(gameState, (GameAction.PengActionDto) action);
                break;
            case GANG:
                applyGangAction(gameState, (GameAction.GangActionDto) action);
                break;
            case CHI:
                applyChiAction(gameState, (GameAction.ChiActionDto) action);
                break;
            case HU:
                applyHuAction(gameState, (GameAction.HuActionDto) action);
                break;
            case PASS:
                // Pass actions don't change game state
                break;
            default:
                logger.warn("Unknown action type for replay: {}", action.getActionType());
        }
    }
    
    /**
     * Apply discard action to game state
     */
    private void applyDiscardAction(GameState gameState, GameAction.DiscardActionDto action) {
        PlayerState player = getPlayerByUserId(gameState, action.getUserId());
        if (player != null) {
            // Remove tile from hand
            Tile discardedTile = new Tile(action.getTile());
            player.removeTile(discardedTile);
            
            // Add to discard pile
            gameState.getDiscardPile().add(discardedTile);
            // Note: PlayerState doesn't have discardedTiles list, so we skip this
        }
    }
    
    /**
     * Apply draw action to game state
     */
    private void applyDrawAction(GameState gameState, GameAction.DrawActionDto action) {
        PlayerState player = getPlayerByUserId(gameState, action.getUserId());
        if (player != null && !gameState.getTileWall().isEmpty()) {
            // Draw tile from wall
            Tile drawnTile = gameState.getTileWall().remove(0);
            player.addTile(drawnTile);
        }
    }
    
    /**
     * Apply peng action to game state
     */
    private void applyPengAction(GameState gameState, GameAction.PengActionDto action) {
        PlayerState player = getPlayerByUserId(gameState, action.getUserId());
        if (player != null) {
            // Remove claimed tile from discard pile
            Tile claimedTile = new Tile(action.getTile());
            gameState.getDiscardPile().remove(claimedTile);
            
            // Remove two matching tiles from player's hand
            for (int i = 0; i < 2; i++) {
                player.removeTile(claimedTile);
            }
            
            // Add peng meld (simplified)
            // In real implementation, this would create a proper MeldSet
        }
    }
    
    /**
     * Apply gang action to game state
     */
    private void applyGangAction(GameState gameState, GameAction.GangActionDto action) {
        PlayerState player = getPlayerByUserId(gameState, action.getUserId());
        if (player != null) {
            Tile gangTile = new Tile(action.getTile());
            
            switch (action.getGangType()) {
                case MING:
                    // Remove claimed tile from discard pile
                    gameState.getDiscardPile().remove(gangTile);
                    // Remove three matching tiles from hand
                    for (int i = 0; i < 3; i++) {
                        player.removeTile(gangTile);
                    }
                    break;
                case AN:
                    // Remove four tiles from hand
                    for (int i = 0; i < 4; i++) {
                        player.removeTile(gangTile);
                    }
                    break;
                case BU:
                    // Upgrade existing peng to gang
                    player.removeTile(gangTile);
                    break;
            }
            
            // Add gang meld (simplified)
        }
    }
    
    /**
     * Apply chi action to game state
     */
    private void applyChiAction(GameState gameState, GameAction.ChiActionDto action) {
        PlayerState player = getPlayerByUserId(gameState, action.getUserId());
        if (player != null) {
            // Remove claimed tile from discard pile
            Tile claimedTile = new Tile(action.getTile());
            gameState.getDiscardPile().remove(claimedTile);
            
            // Remove sequence tiles from hand (simplified)
            // In real implementation, this would parse the sequence properly
        }
    }
    
    /**
     * Apply hu action to game state
     */
    private void applyHuAction(GameState gameState, GameAction.HuActionDto action) {
        PlayerState player = getPlayerByUserId(gameState, action.getUserId());
        if (player != null) {
            if (!action.isSelfDraw()) {
                // Remove winning tile from discard pile
                Tile winningTile = new Tile(action.getWinningTile());
                gameState.getDiscardPile().remove(winningTile);
                player.addTile(winningTile);
            }
            
            // Mark game as ended
            gameState.setPhase(GameState.GamePhase.FINISHED);
        }
    }
    
    /**
     * Deal initial tiles (simplified implementation)
     */
    private void dealInitialTiles(GameState gameState) {
        List<Tile> tileWall = gameState.getTileWall();
        List<PlayerState> players = gameState.getPlayers();
        
        // Deal 13 tiles to each player, 14 to dealer
        for (PlayerState player : players) {
            boolean isDealer = Objects.equals(player.getUserId(), gameState.getDealerUserId());
            int tilesToDeal = isDealer ? 14 : 13;
            
            List<Tile> handTiles = new ArrayList<>();
            for (int i = 0; i < tilesToDeal && !tileWall.isEmpty(); i++) {
                handTiles.add(tileWall.remove(0));
            }
            player.addTiles(handTiles);
        }
    }
    
    /**
     * Get player by user ID
     */
    private PlayerState getPlayerByUserId(GameState gameState, Long userId) {
        return gameState.getPlayers().stream()
                .filter(player -> Objects.equals(Long.parseLong(player.getUserId()), userId))
                .findFirst()
                .orElse(null);
    }
    
    /**
     * Parse final hands JSON data
     */
    private Map<String, Object> parseFinalHands(String finalHandsJson) {
        if (finalHandsJson == null || finalHandsJson.trim().isEmpty()) {
            return new HashMap<>();
        }
        
        try {
            TypeReference<Map<String, Object>> typeRef = new TypeReference<Map<String, Object>>() {};
            return objectMapper.readValue(finalHandsJson, typeRef);
        } catch (JsonProcessingException e) {
            logger.error("Failed to parse final hands JSON", e);
            return new HashMap<>();
        }
    }
    
    /**
     * Create game snapshot from current state
     */
    private GameSnapshot createGameSnapshot(GameState gameState) {
        GameSnapshot snapshot = new GameSnapshot();
        snapshot.setRoomId(gameState.getRoomId());
        snapshot.setRemainingTiles(gameState.getTileWall().size());
        snapshot.setCurrentPlayerIndex(gameState.getCurrentPlayerIndex());
        
        // Create player snapshots
        List<PlayerSnapshot> playerSnapshots = gameState.getPlayers().stream()
                .map(this::createPlayerSnapshot)
                .collect(Collectors.toList());
        snapshot.setPlayers(playerSnapshots);
        
        // Set discard pile
        List<String> discardPile = gameState.getDiscardPile().stream()
                .map(Tile::toString)
                .collect(Collectors.toList());
        snapshot.setDiscardPile(discardPile);
        
        return snapshot;
    }
    
    /**
     * Create player snapshot from player state
     */
    private PlayerSnapshot createPlayerSnapshot(PlayerState playerState) {
        PlayerSnapshot snapshot = new PlayerSnapshot();
        snapshot.setUserId(playerState.getUserId());
        snapshot.setSeatIndex(playerState.getSeatIndex());
        snapshot.setHandCount(playerState.getHandTiles().size());
        
        // Convert tiles to strings
        List<String> handTiles = playerState.getHandTiles().stream()
                .map(Tile::toString)
                .collect(Collectors.toList());
        snapshot.setHandTiles(handTiles);
        
        // PlayerState doesn't have discardedTiles, so we'll use empty list
        snapshot.setDiscardedTiles(new ArrayList<>());
        
        return snapshot;
    }
    
    /**
     * Create action description for display
     */
    private String createActionDescription(GameAction action) {
        switch (action.getActionType()) {
            case DISCARD:
                GameAction.DiscardActionDto discardAction = (GameAction.DiscardActionDto) action;
                return String.format("Player %d discarded %s", action.getSeatIndex(), discardAction.getTile());
            case DRAW:
                GameAction.DrawActionDto drawAction = (GameAction.DrawActionDto) action;
                return String.format("Player %d drew %s", action.getSeatIndex(), drawAction.getTile());
            case PENG:
                GameAction.PengActionDto pengAction = (GameAction.PengActionDto) action;
                return String.format("Player %d called Peng on %s", action.getSeatIndex(), pengAction.getTile());
            case GANG:
                GameAction.GangActionDto gangAction = (GameAction.GangActionDto) action;
                return String.format("Player %d called Gang (%s) on %s", 
                        action.getSeatIndex(), gangAction.getGangType(), gangAction.getTile());
            case CHI:
                GameAction.ChiActionDto chiAction = (GameAction.ChiActionDto) action;
                return String.format("Player %d called Chi on %s (sequence: %s)", 
                        action.getSeatIndex(), chiAction.getTile(), chiAction.getSequence());
            case HU:
                GameAction.HuActionDto huAction = (GameAction.HuActionDto) action;
                return String.format("Player %d won with %s (%s)", 
                        action.getSeatIndex(), huAction.getWinningTile(), 
                        huAction.isSelfDraw() ? "self-draw" : "claimed");
            case PASS:
                return String.format("Player %d passed", action.getSeatIndex());
            default:
                return String.format("Player %d performed %s", action.getSeatIndex(), action.getActionType());
        }
    }
    
    /**
     * Create replay metadata
     */
    private Map<String, Object> createReplayMetadata(GameRecord gameRecord, List<GameAction> actions) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("gameId", gameRecord.getId());
        metadata.put("roomId", gameRecord.getRoomId());
        metadata.put("roundIndex", gameRecord.getRoundIndex());
        metadata.put("result", gameRecord.getResult());
        metadata.put("winnerId", gameRecord.getWinnerId());
        metadata.put("durationSeconds", gameRecord.getDurationSeconds());
        metadata.put("createdAt", gameRecord.getCreatedAt());
        metadata.put("totalActions", actions.size());
        metadata.put("randomSeed", gameRecord.getRandomSeed());
        
        return metadata;
    }
}