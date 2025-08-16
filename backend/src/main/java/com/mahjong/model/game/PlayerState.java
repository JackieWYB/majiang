package com.mahjong.model.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents the state of a player in a Mahjong game
 */
public class PlayerState {
    
    public enum PlayerStatus {
        WAITING,        // Waiting for game to start
        PLAYING,        // Actively playing
        WAITING_TURN,   // Waiting for their turn
        DISCONNECTED,   // Temporarily disconnected
        TRUSTEE,        // Auto-play mode
        FINISHED        // Game finished for this player
    }
    
    private final String userId;
    private final int seatIndex; // 0, 1, 2 for 3-player game
    private final List<Tile> handTiles;
    private final List<MeldSet> melds;
    private PlayerStatus status;
    private boolean isDealer;
    private int score;
    private boolean isReady;
    private Long lastActionTime;
    private int consecutiveTimeouts;
    
    // Temporary state during turn
    private Tile lastDrawnTile;
    private List<ActionType> availableActions;
    
    public enum ActionType {
        DISCARD,
        PENG,
        GANG,
        CHI,
        HU,
        PASS
    }
    
    public PlayerState(String userId, int seatIndex) {
        this.userId = userId;
        this.seatIndex = seatIndex;
        this.handTiles = new ArrayList<>();
        this.melds = new ArrayList<>();
        this.status = PlayerStatus.WAITING;
        this.isDealer = false;
        this.score = 0;
        this.isReady = false;
        this.lastActionTime = System.currentTimeMillis();
        this.consecutiveTimeouts = 0;
        this.availableActions = new ArrayList<>();
    }
    
    /**
     * Adds a tile to the player's hand
     */
    public void addTile(Tile tile) {
        handTiles.add(tile);
        sortHand();
    }
    
    /**
     * Removes a tile from the player's hand
     */
    public boolean removeTile(Tile tile) {
        boolean removed = handTiles.remove(tile);
        if (removed) {
            sortHand();
        }
        return removed;
    }
    
    /**
     * Adds multiple tiles to the player's hand
     */
    public void addTiles(List<Tile> tiles) {
        handTiles.addAll(tiles);
        sortHand();
    }
    
    /**
     * Removes multiple tiles from the player's hand
     */
    public boolean removeTiles(List<Tile> tiles) {
        boolean allRemoved = true;
        for (Tile tile : tiles) {
            if (!handTiles.remove(tile)) {
                allRemoved = false;
            }
        }
        if (allRemoved) {
            sortHand();
        }
        return allRemoved;
    }
    
    /**
     * Sorts the hand tiles by suit and rank
     */
    private void sortHand() {
        Collections.sort(handTiles, (t1, t2) -> {
            int suitCompare = t1.getSuit().compareTo(t2.getSuit());
            if (suitCompare != 0) {
                return suitCompare;
            }
            return Integer.compare(t1.getRank(), t2.getRank());
        });
    }
    
    /**
     * Adds a meld to the player's melds
     */
    public void addMeld(MeldSet meld) {
        melds.add(meld);
    }
    
    /**
     * Checks if player can form a Peng with the given tile
     */
    public boolean canPeng(Tile tile) {
        long count = handTiles.stream().filter(t -> t.equals(tile)).count();
        return count >= 2;
    }
    
    /**
     * Checks if player can form a Gang with the given tile
     */
    public boolean canGang(Tile tile) {
        long count = handTiles.stream().filter(t -> t.equals(tile)).count();
        return count >= 3;
    }
    
    /**
     * Checks if player can form a concealed Gang
     */
    public boolean canConcealedGang(Tile tile) {
        long count = handTiles.stream().filter(t -> t.equals(tile)).count();
        return count == 4;
    }
    
    /**
     * Checks if player can upgrade a Peng to Gang
     */
    public boolean canUpgradeGang(Tile tile) {
        return melds.stream().anyMatch(meld -> meld.canUpgradeToGang(tile));
    }
    
    /**
     * Checks if player can form a Chi with the given tile
     */
    public boolean canChi(Tile tile) {
        // Check for possible sequences
        Tile.Suit suit = tile.getSuit();
        int rank = tile.getRank();
        
        // Check if we can form sequences like: tile-1, tile, tile+1
        // or tile, tile+1, tile+2 or tile-2, tile-1, tile
        
        // Pattern 1: [rank-2, rank-1, tile]
        if (rank >= 3) {
            Tile tile1 = new Tile(suit, rank - 2);
            Tile tile2 = new Tile(suit, rank - 1);
            if (handTiles.contains(tile1) && handTiles.contains(tile2)) {
                return true;
            }
        }
        
        // Pattern 2: [rank-1, tile, rank+1]
        if (rank >= 2 && rank <= 8) {
            Tile tile1 = new Tile(suit, rank - 1);
            Tile tile2 = new Tile(suit, rank + 1);
            if (handTiles.contains(tile1) && handTiles.contains(tile2)) {
                return true;
            }
        }
        
        // Pattern 3: [tile, rank+1, rank+2]
        if (rank <= 7) {
            Tile tile1 = new Tile(suit, rank + 1);
            Tile tile2 = new Tile(suit, rank + 2);
            if (handTiles.contains(tile1) && handTiles.contains(tile2)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Gets the total number of tiles (hand + melds)
     */
    public int getTotalTileCount() {
        int meldTileCount = melds.stream().mapToInt(meld -> meld.getTiles().size()).sum();
        return handTiles.size() + meldTileCount;
    }
    
    /**
     * Checks if the player has a winning hand
     */
    public boolean hasWinningHand() {
        // Basic winning pattern: 4 sets + 1 pair
        // This is a simplified check - full implementation would be more complex
        int totalTiles = handTiles.size();
        int meldCount = melds.size();
        
        // Each meld represents a set, need to check if remaining hand tiles form valid sets + pair
        // For now, just check if we have the right number of tiles
        return (totalTiles + meldCount * 3) == 14 && (totalTiles - 2) % 3 == 0;
    }
    
    /**
     * Updates the last action time
     */
    public void updateLastActionTime() {
        this.lastActionTime = System.currentTimeMillis();
        this.consecutiveTimeouts = 0;
    }
    
    /**
     * Increments timeout counter
     */
    public void incrementTimeouts() {
        this.consecutiveTimeouts++;
        if (consecutiveTimeouts >= 3) {
            this.status = PlayerStatus.TRUSTEE;
        }
    }
    
    /**
     * Resets the player state for a new game
     */
    public void resetForNewGame() {
        handTiles.clear();
        melds.clear();
        status = PlayerStatus.WAITING;
        score = 0;
        isReady = false;
        lastDrawnTile = null;
        availableActions.clear();
        consecutiveTimeouts = 0;
        updateLastActionTime();
    }
    
    // Getters and Setters
    public String getUserId() {
        return userId;
    }
    
    public int getSeatIndex() {
        return seatIndex;
    }
    
    public List<Tile> getHandTiles() {
        return new ArrayList<>(handTiles);
    }
    
    public List<MeldSet> getMelds() {
        return new ArrayList<>(melds);
    }
    
    public PlayerStatus getStatus() {
        return status;
    }
    
    public void setStatus(PlayerStatus status) {
        this.status = status;
    }
    
    public boolean isDealer() {
        return isDealer;
    }
    
    public void setDealer(boolean dealer) {
        isDealer = dealer;
    }
    
    public int getScore() {
        return score;
    }
    
    public void setScore(int score) {
        this.score = score;
    }
    
    public void addScore(int points) {
        this.score += points;
    }
    
    public boolean isReady() {
        return isReady;
    }
    
    public void setReady(boolean ready) {
        isReady = ready;
    }
    
    public Long getLastActionTime() {
        return lastActionTime;
    }
    
    public int getConsecutiveTimeouts() {
        return consecutiveTimeouts;
    }
    
    public Tile getLastDrawnTile() {
        return lastDrawnTile;
    }
    
    public void setLastDrawnTile(Tile lastDrawnTile) {
        this.lastDrawnTile = lastDrawnTile;
    }
    
    public List<ActionType> getAvailableActions() {
        return new ArrayList<>(availableActions);
    }
    
    public void setAvailableActions(List<ActionType> availableActions) {
        this.availableActions = new ArrayList<>(availableActions);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        PlayerState that = (PlayerState) obj;
        return seatIndex == that.seatIndex &&
                isDealer == that.isDealer &&
                score == that.score &&
                isReady == that.isReady &&
                Objects.equals(userId, that.userId) &&
                Objects.equals(handTiles, that.handTiles) &&
                Objects.equals(melds, that.melds) &&
                status == that.status;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(userId, seatIndex, handTiles, melds, status, isDealer, score, isReady);
    }
    
    @Override
    public String toString() {
        return String.format("PlayerState{userId='%s', seat=%d, tiles=%d, melds=%d, status=%s, dealer=%s, score=%d}",
                userId, seatIndex, handTiles.size(), melds.size(), status, isDealer, score);
    }
}