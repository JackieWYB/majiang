package com.mahjong.model.game;

import com.mahjong.model.config.RoomConfig;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Represents the complete state of a Mahjong game
 */
public class GameState {
    
    public enum GamePhase {
        WAITING,        // Waiting for players
        DEALING,        // Dealing initial tiles
        PLAYING,        // Active gameplay
        SETTLEMENT,     // Calculating scores
        FINISHED        // Game completed
    }
    
    private final String roomId;
    private final String gameId;
    private final List<PlayerState> players;
    private final List<Tile> tileWall;
    private final List<Tile> discardPile;
    private final RoomConfig config;
    
    private GamePhase phase;
    private int currentPlayerIndex;
    private String dealerUserId;
    private int dealerSeatIndex;
    private Long turnStartTime;
    private Long turnDeadline;
    private int remainingTiles;
    private int roundNumber;
    private Random random;
    private long randomSeed;
    
    // Game statistics
    private Long gameStartTime;
    private Long gameEndTime;
    private int totalTurns;
    private Map<String, Integer> playerActionCounts;
    
    public GameState(String roomId, String gameId, List<String> playerIds, RoomConfig config) {
        this.roomId = roomId;
        this.gameId = gameId;
        this.config = config;
        this.players = new ArrayList<>();
        this.tileWall = new ArrayList<>();
        this.discardPile = new ArrayList<>();
        this.phase = GamePhase.WAITING;
        this.currentPlayerIndex = 0;
        this.roundNumber = 1;
        this.totalTurns = 0;
        this.playerActionCounts = new HashMap<>();
        
        // Initialize players
        for (int i = 0; i < playerIds.size(); i++) {
            PlayerState player = new PlayerState(playerIds.get(i), i);
            players.add(player);
            playerActionCounts.put(playerIds.get(i), 0);
        }
        
        // Set initial dealer (first player)
        if (!players.isEmpty()) {
            this.dealerUserId = players.get(0).getUserId();
            this.dealerSeatIndex = 0;
            players.get(0).setDealer(true);
        }
        
        // Initialize random with current time as seed
        this.randomSeed = System.currentTimeMillis();
        this.random = new Random(randomSeed);
        
        initializeTileWall();
    }
    
    /**
     * Initialize the tile wall with all tiles
     */
    private void initializeTileWall() {
        tileWall.clear();
        
        // Add tiles based on configuration
        if ("WAN_ONLY".equals(config.getTiles())) {
            // Only WAN (Characters) tiles
            for (int rank = 1; rank <= 9; rank++) {
                for (int count = 0; count < 4; count++) {
                    tileWall.add(new Tile(Tile.Suit.WAN, rank));
                }
            }
        } else {
            // All three suits
            for (Tile.Suit suit : Tile.Suit.values()) {
                for (int rank = 1; rank <= 9; rank++) {
                    for (int count = 0; count < 4; count++) {
                        tileWall.add(new Tile(suit, rank));
                    }
                }
            }
        }
        
        remainingTiles = tileWall.size();
    }
    
    /**
     * Shuffle the tile wall using the random seed
     */
    public void shuffleTiles() {
        Collections.shuffle(tileWall, random);
    }
    
    /**
     * Deal initial tiles to all players
     */
    public void dealInitialTiles() {
        if (phase != GamePhase.WAITING) {
            throw new IllegalStateException("Can only deal tiles in WAITING phase");
        }
        
        phase = GamePhase.DEALING;
        shuffleTiles();
        
        // Deal 13 tiles to each player, 14 to dealer
        for (PlayerState player : players) {
            int tilesToDeal = player.isDealer() ? 14 : 13;
            List<Tile> playerTiles = drawTiles(tilesToDeal);
            player.addTiles(playerTiles);
            player.setStatus(PlayerState.PlayerStatus.WAITING_TURN);
        }
        
        phase = GamePhase.PLAYING;
        gameStartTime = System.currentTimeMillis();
        startTurn();
    }
    
    /**
     * Draw tiles from the wall
     */
    public List<Tile> drawTiles(int count) {
        if (count > remainingTiles) {
            throw new IllegalStateException("Not enough tiles remaining in wall");
        }
        
        List<Tile> drawnTiles = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            if (!tileWall.isEmpty()) {
                Tile tile = tileWall.remove(tileWall.size() - 1);
                drawnTiles.add(tile);
                remainingTiles--;
            }
        }
        
        return drawnTiles;
    }
    
    /**
     * Draw a single tile from the wall
     */
    public Tile drawTile() {
        List<Tile> tiles = drawTiles(1);
        return tiles.isEmpty() ? null : tiles.get(0);
    }
    
    /**
     * Start a new turn
     */
    public void startTurn() {
        turnStartTime = System.currentTimeMillis();
        turnDeadline = turnStartTime + (config.getTurn().getTurnTimeLimit() * 1000L);
        
        PlayerState currentPlayer = getCurrentPlayer();
        currentPlayer.setStatus(PlayerState.PlayerStatus.PLAYING);
        
        // Update available actions for current player
        updateAvailableActions(currentPlayer);
        
        totalTurns++;
    }
    
    /**
     * Advance to the next player's turn
     */
    public void nextTurn() {
        PlayerState currentPlayer = getCurrentPlayer();
        currentPlayer.setStatus(PlayerState.PlayerStatus.WAITING_TURN);
        
        currentPlayerIndex = (currentPlayerIndex + 1) % players.size();
        startTurn();
    }
    
    /**
     * Update available actions for a player
     */
    private void updateAvailableActions(PlayerState player) {
        List<PlayerState.ActionType> actions = new ArrayList<>();
        
        if (player.getHandTiles().size() % 3 == 2) { // Has drawn a tile
            actions.add(PlayerState.ActionType.DISCARD);
            
            // Check for concealed Gang
            for (Tile tile : player.getHandTiles()) {
                if (player.canConcealedGang(tile)) {
                    actions.add(PlayerState.ActionType.GANG);
                    break;
                }
            }
            
            // Check for upgrade Gang
            for (Tile tile : player.getHandTiles()) {
                if (player.canUpgradeGang(tile)) {
                    actions.add(PlayerState.ActionType.GANG);
                    break;
                }
            }
            
            // Check for Hu
            if (player.hasWinningHand()) {
                actions.add(PlayerState.ActionType.HU);
            }
        }
        
        player.setAvailableActions(actions);
    }
    
    /**
     * Process a discard action
     */
    public void processDiscard(String userId, Tile tile) {
        PlayerState player = getPlayerByUserId(userId);
        if (player == null || !player.getUserId().equals(getCurrentPlayer().getUserId())) {
            throw new IllegalArgumentException("Not player's turn");
        }
        
        if (!player.removeTile(tile)) {
            throw new IllegalArgumentException("Player doesn't have this tile");
        }
        
        discardPile.add(tile);
        player.updateLastActionTime();
        incrementPlayerActionCount(userId);
        
        // Check if other players can claim this tile
        checkClaimActions(tile, userId);
    }
    
    /**
     * Check if other players can claim the discarded tile
     */
    private void checkClaimActions(Tile discardedTile, String discardingUserId) {
        boolean anyCanClaim = false;
        
        for (PlayerState player : players) {
            if (player.getUserId().equals(discardingUserId)) continue;
            
            List<PlayerState.ActionType> claimActions = new ArrayList<>();
            
            if (config.getAllowPeng() && player.canPeng(discardedTile)) {
                claimActions.add(PlayerState.ActionType.PENG);
            }
            
            if (config.getAllowGang() && player.canGang(discardedTile)) {
                claimActions.add(PlayerState.ActionType.GANG);
            }
            
            if (config.getAllowChi() && player.canChi(discardedTile) && 
                isNextPlayer(player.getUserId(), discardingUserId)) {
                claimActions.add(PlayerState.ActionType.CHI);
            }
            
            if (player.hasWinningHand()) {
                claimActions.add(PlayerState.ActionType.HU);
            }
            
            if (!claimActions.isEmpty()) {
                claimActions.add(PlayerState.ActionType.PASS);
                player.setAvailableActions(claimActions);
                anyCanClaim = true;
            }
        }
        
        if (!anyCanClaim) {
            nextTurn();
        }
    }
    
    /**
     * Check if player is next in turn order
     */
    private boolean isNextPlayer(String playerId, String currentPlayerId) {
        int currentIndex = getPlayerIndexByUserId(currentPlayerId);
        int nextIndex = (currentIndex + 1) % players.size();
        return players.get(nextIndex).getUserId().equals(playerId);
    }
    
    /**
     * Process a Peng action
     */
    public void processPeng(String userId, Tile claimedTile, String claimedFrom) {
        PlayerState player = getPlayerByUserId(userId);
        if (player == null) {
            throw new IllegalArgumentException("Player not found");
        }
        
        if (!player.canPeng(claimedTile)) {
            throw new IllegalArgumentException("Cannot form Peng with this tile");
        }
        
        // Remove two matching tiles from hand
        List<Tile> pengTiles = new ArrayList<>();
        pengTiles.add(claimedTile); // The claimed tile
        
        int removed = 0;
        List<Tile> handTiles = player.getHandTiles();
        for (Tile tile : handTiles) {
            if (removed >= 2) break;
            if (tile.equals(claimedTile)) {
                pengTiles.add(tile);
                player.removeTile(tile);
                removed++;
            }
        }
        
        // Create and add meld
        MeldSet peng = MeldSet.createPeng(pengTiles, claimedFrom);
        player.addMeld(peng);
        
        // Remove claimed tile from discard pile
        if (!discardPile.isEmpty()) {
            discardPile.remove(discardPile.size() - 1);
        }
        
        // Set current player and update actions
        currentPlayerIndex = getPlayerIndexByUserId(userId);
        player.setStatus(PlayerState.PlayerStatus.PLAYING);
        updateAvailableActions(player);
        
        incrementPlayerActionCount(userId);
    }
    
    /**
     * Check if the game should end
     */
    public boolean shouldEndGame() {
        // Game ends if someone wins or tile wall is empty
        return players.stream().anyMatch(PlayerState::hasWinningHand) || remainingTiles <= 0;
    }
    
    /**
     * End the current game
     */
    public void endGame() {
        phase = GamePhase.SETTLEMENT;
        gameEndTime = System.currentTimeMillis();
        
        for (PlayerState player : players) {
            player.setStatus(PlayerState.PlayerStatus.FINISHED);
        }
    }
    
    /**
     * Get the current player
     */
    public PlayerState getCurrentPlayer() {
        if (currentPlayerIndex >= 0 && currentPlayerIndex < players.size()) {
            return players.get(currentPlayerIndex);
        }
        return null;
    }
    
    /**
     * Get player by user ID
     */
    public PlayerState getPlayerByUserId(String userId) {
        return players.stream()
                .filter(p -> p.getUserId().equals(userId))
                .findFirst()
                .orElse(null);
    }
    
    /**
     * Get player index by user ID
     */
    public int getPlayerIndexByUserId(String userId) {
        for (int i = 0; i < players.size(); i++) {
            if (players.get(i).getUserId().equals(userId)) {
                return i;
            }
        }
        return -1;
    }
    
    /**
     * Increment action count for a player
     */
    private void incrementPlayerActionCount(String userId) {
        playerActionCounts.merge(userId, 1, Integer::sum);
    }
    
    /**
     * Check if turn has timed out
     */
    public boolean isTurnTimedOut() {
        return System.currentTimeMillis() > turnDeadline;
    }
    
    /**
     * Get game duration in milliseconds
     */
    public long getGameDuration() {
        if (gameStartTime == null) return 0;
        long endTime = gameEndTime != null ? gameEndTime : System.currentTimeMillis();
        return endTime - gameStartTime;
    }
    
    // Getters
    public String getRoomId() {
        return roomId;
    }
    
    public String getGameId() {
        return gameId;
    }
    
    public List<PlayerState> getPlayers() {
        return new ArrayList<>(players);
    }
    
    public List<Tile> getTileWall() {
        return new ArrayList<>(tileWall);
    }
    
    public List<Tile> getDiscardPile() {
        return new ArrayList<>(discardPile);
    }
    
    public RoomConfig getConfig() {
        return config;
    }
    
    public GamePhase getPhase() {
        return phase;
    }
    
    public void setPhase(GamePhase phase) {
        this.phase = phase;
    }
    
    public int getCurrentPlayerIndex() {
        return currentPlayerIndex;
    }
    
    public String getDealerUserId() {
        return dealerUserId;
    }
    
    public int getDealerSeatIndex() {
        return dealerSeatIndex;
    }
    
    public Long getTurnStartTime() {
        return turnStartTime;
    }
    
    public Long getTurnDeadline() {
        return turnDeadline;
    }
    
    public int getRemainingTiles() {
        return remainingTiles;
    }
    
    public int getRoundNumber() {
        return roundNumber;
    }
    
    public long getRandomSeed() {
        return randomSeed;
    }
    
    public Long getGameStartTime() {
        return gameStartTime;
    }
    
    public Long getGameEndTime() {
        return gameEndTime;
    }
    
    public int getTotalTurns() {
        return totalTurns;
    }
    
    public Map<String, Integer> getPlayerActionCounts() {
        return new HashMap<>(playerActionCounts);
    }
    
    @Override
    public String toString() {
        return String.format("GameState{roomId='%s', gameId='%s', phase=%s, players=%d, remainingTiles=%d, currentPlayer=%d}",
                roomId, gameId, phase, players.size(), remainingTiles, currentPlayerIndex);
    }
}