package com.mahjong.service;

import com.mahjong.model.config.HuTypes;
import com.mahjong.model.game.MeldSet;
import com.mahjong.model.game.PlayerState;
import com.mahjong.model.game.Tile;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for validating winning conditions in Mahjong
 */
@Service
public class WinValidationService {
    
    /**
     * Represents a winning hand validation result
     */
    public static class WinValidationResult {
        private final boolean isWinning;
        private final WinType winType;
        private final String description;
        private final List<List<Tile>> winningCombination;
        private final WaitType waitType;
        
        public WinValidationResult(boolean isWinning, WinType winType, String description, 
                                 List<List<Tile>> winningCombination, WaitType waitType) {
            this.isWinning = isWinning;
            this.winType = winType;
            this.description = description;
            this.winningCombination = winningCombination != null ? winningCombination : new ArrayList<>();
            this.waitType = waitType;
        }
        
        // Getters
        public boolean isWinning() { return isWinning; }
        public WinType getWinType() { return winType; }
        public String getDescription() { return description; }
        public List<List<Tile>> getWinningCombination() { return winningCombination; }
        public WaitType getWaitType() { return waitType; }
    }
    
    public enum WinType {
        BASIC_WIN,      // 4 sets + 1 pair
        SEVEN_PAIRS,    // 7 pairs
        ALL_PUNGS,      // All Pungs (4 triplets + 1 pair)
        ALL_HONORS      // All honor tiles (not applicable in WAN_ONLY mode)
    }
    
    public enum WaitType {
        PAIR_WAIT,      // Waiting for pair completion
        EDGE_WAIT,      // Waiting for edge of sequence (1-2 waiting for 3, or 8-9 waiting for 7)
        MIDDLE_WAIT,    // Waiting for middle of sequence (1-3 waiting for 2)
        MULTIPLE_WAIT   // Multiple possible winning tiles
    }
    
    /**
     * Validates if a player has a winning hand
     */
    public WinValidationResult validateWin(PlayerState player, Tile winningTile, HuTypes huConfig, boolean isSelfDraw) {
        List<Tile> allTiles = new ArrayList<>(player.getHandTiles());
        if (winningTile != null) {
            allTiles.add(winningTile);
        }
        
        // Check different winning patterns based on configuration
        
        // 1. Check Seven Pairs
        if (huConfig.getSevenPairs()) {
            WinValidationResult sevenPairsResult = validateSevenPairs(allTiles, winningTile);
            if (sevenPairsResult.isWinning()) {
                return sevenPairsResult;
            }
        }
        
        // 2. Check basic winning pattern (4 sets + 1 pair)
        WinValidationResult basicResult = validateBasicWin(allTiles, player.getMelds(), winningTile, huConfig);
        if (basicResult.isWinning()) {
            // Check if it's All Pungs
            if (huConfig.getAllPungs() && isAllPungs(allTiles, player.getMelds())) {
                return new WinValidationResult(true, WinType.ALL_PUNGS, "All Pungs", 
                    basicResult.getWinningCombination(), basicResult.getWaitType());
            }
            return basicResult;
        }
        
        return new WinValidationResult(false, null, "No winning pattern found", null, null);
    }
    
    /**
     * Validates Seven Pairs winning pattern
     */
    private WinValidationResult validateSevenPairs(List<Tile> tiles, Tile winningTile) {
        if (tiles.size() != 14) {
            return new WinValidationResult(false, null, "Invalid tile count for Seven Pairs", null, null);
        }
        
        Map<Tile, Integer> tileCount = tiles.stream()
            .collect(Collectors.groupingBy(tile -> tile, Collectors.summingInt(tile -> 1)));
        
        // Check if all tiles form pairs
        List<List<Tile>> pairs = new ArrayList<>();
        for (Map.Entry<Tile, Integer> entry : tileCount.entrySet()) {
            if (entry.getValue() != 2) {
                return new WinValidationResult(false, null, "Not all tiles form pairs", null, null);
            }
            pairs.add(Arrays.asList(entry.getKey(), entry.getKey()));
        }
        
        if (pairs.size() != 7) {
            return new WinValidationResult(false, null, "Must have exactly 7 pairs", null, null);
        }
        
        WaitType waitType = winningTile != null ? WaitType.PAIR_WAIT : WaitType.MULTIPLE_WAIT;
        return new WinValidationResult(true, WinType.SEVEN_PAIRS, "Seven Pairs", pairs, waitType);
    }
    
    /**
     * Validates basic winning pattern (4 sets + 1 pair)
     */
    private WinValidationResult validateBasicWin(List<Tile> handTiles, List<MeldSet> existingMelds, 
                                                Tile winningTile, HuTypes huConfig) {
        if (handTiles.size() + existingMelds.size() * 3 != 14) {
            return new WinValidationResult(false, null, "Invalid total tile count", null, null);
        }
        
        // Check if this is actually a Seven Pairs hand (all tiles appear exactly twice)
        if (existingMelds.isEmpty()) {
            Map<Tile, Integer> tileCount = handTiles.stream()
                .collect(Collectors.groupingBy(tile -> tile, Collectors.summingInt(tile -> 1)));
            
            boolean isSevenPairs = tileCount.size() == 7 && 
                tileCount.values().stream().allMatch(count -> count == 2);
            
            if (isSevenPairs) {
                return new WinValidationResult(false, null, "This is a Seven Pairs hand, not basic win", null, null);
            }
        }
        
        // Try to form sets and pairs from hand tiles
        List<List<List<Tile>>> possibleCombinations = findAllValidCombinations(handTiles);
        
        for (List<List<Tile>> combination : possibleCombinations) {
            if (isValidBasicWin(combination, existingMelds)) {
                WaitType waitType = determineWaitType(handTiles, winningTile, WinType.BASIC_WIN);
                List<List<Tile>> fullCombination = new ArrayList<>(combination);
                
                // Add existing melds to the combination
                for (MeldSet meld : existingMelds) {
                    fullCombination.add(new ArrayList<>(meld.getTiles()));
                }
                
                return new WinValidationResult(true, WinType.BASIC_WIN, "Basic Win (4 sets + 1 pair)", 
                    fullCombination, waitType);
            }
        }
        
        return new WinValidationResult(false, null, "Cannot form valid sets and pairs", null, null);
    }
    
    /**
     * Finds all possible valid combinations of sets and pairs from hand tiles
     */
    private List<List<List<Tile>>> findAllValidCombinations(List<Tile> tiles) {
        List<List<List<Tile>>> allCombinations = new ArrayList<>();
        List<Tile> sortedTiles = new ArrayList<>(tiles);
        Collections.sort(sortedTiles, (t1, t2) -> {
            int suitCompare = t1.getSuit().compareTo(t2.getSuit());
            if (suitCompare != 0) return suitCompare;
            return Integer.compare(t1.getRank(), t2.getRank());
        });
        findCombinationsRecursive(sortedTiles, new ArrayList<>(), allCombinations);
        return allCombinations;
    }
    
    /**
     * Recursively finds valid combinations
     */
    private void findCombinationsRecursive(List<Tile> remainingTiles, List<List<Tile>> currentCombination, 
                                         List<List<List<Tile>>> allCombinations) {
        if (remainingTiles.isEmpty()) {
            allCombinations.add(new ArrayList<>(currentCombination));
            return;
        }
        
        if (remainingTiles.size() < 2) {
            return; // Cannot form any valid set with less than 2 tiles
        }
        
        Tile firstTile = remainingTiles.get(0);
        
        // Try to form a pair
        if (remainingTiles.size() >= 2 && remainingTiles.get(1).equals(firstTile)) {
            List<Tile> newRemaining = new ArrayList<>(remainingTiles);
            newRemaining.remove(0);
            newRemaining.remove(0);
            
            List<List<Tile>> newCombination = new ArrayList<>(currentCombination);
            newCombination.add(Arrays.asList(firstTile, firstTile));
            
            findCombinationsRecursive(newRemaining, newCombination, allCombinations);
        }
        
        // Try to form a triplet (Pung)
        if (remainingTiles.size() >= 3 && 
            remainingTiles.get(1).equals(firstTile) && 
            remainingTiles.get(2).equals(firstTile)) {
            
            List<Tile> newRemaining = new ArrayList<>(remainingTiles);
            newRemaining.remove(0);
            newRemaining.remove(0);
            newRemaining.remove(0);
            
            List<List<Tile>> newCombination = new ArrayList<>(currentCombination);
            newCombination.add(Arrays.asList(firstTile, firstTile, firstTile));
            
            findCombinationsRecursive(newRemaining, newCombination, allCombinations);
        }
        
        // Try to form a sequence (Chi)
        if (remainingTiles.size() >= 3 && firstTile.getRank() <= 7) {
            try {
                Tile secondTile = new Tile(firstTile.getSuit(), firstTile.getRank() + 1);
                Tile thirdTile = new Tile(firstTile.getSuit(), firstTile.getRank() + 2);
                
                if (remainingTiles.contains(secondTile) && 
                    remainingTiles.contains(thirdTile)) {
                    
                    List<Tile> newRemaining = new ArrayList<>(remainingTiles);
                    newRemaining.remove(firstTile);
                    newRemaining.remove(secondTile);
                    newRemaining.remove(thirdTile);
                    
                    List<List<Tile>> newCombination = new ArrayList<>(currentCombination);
                    newCombination.add(Arrays.asList(firstTile, secondTile, thirdTile));
                    
                    findCombinationsRecursive(newRemaining, newCombination, allCombinations);
                }
            } catch (IllegalArgumentException e) {
                // Skip invalid tile ranks
            }
        }
    }
    
    /**
     * Checks if a combination is a valid basic win (4 sets + 1 pair)
     */
    private boolean isValidBasicWin(List<List<Tile>> handCombination, List<MeldSet> existingMelds) {
        int pairCount = 0;
        int setCount = existingMelds.size(); // Count existing melds as sets
        
        for (List<Tile> group : handCombination) {
            if (group.size() == 2) {
                pairCount++;
            } else if (group.size() == 3) {
                setCount++;
            } else {
                return false; // Invalid group size
            }
        }
        
        return pairCount == 1 && setCount == 4;
    }
    
    /**
     * Checks if the winning hand is All Pungs
     */
    private boolean isAllPungs(List<Tile> allTiles, List<MeldSet> existingMelds) {
        // Check existing melds - all should be Peng or Gang
        for (MeldSet meld : existingMelds) {
            if (meld.getMeldType() == MeldSet.MeldType.CHI) {
                return false;
            }
        }
        
        // Check hand tiles - should only form triplets and one pair
        Map<Tile, Integer> tileCount = allTiles.stream()
            .collect(Collectors.groupingBy(tile -> tile, Collectors.summingInt(tile -> 1)));
        
        int pairCount = 0;
        int tripletCount = 0;
        
        for (int count : tileCount.values()) {
            if (count == 2) {
                pairCount++;
            } else if (count == 3) {
                tripletCount++;
            } else {
                return false;
            }
        }
        
        return pairCount == 1 && (tripletCount + existingMelds.size()) == 4;
    }
    
    /**
     * Determines the wait type for the winning hand
     */
    private WaitType determineWaitType(List<Tile> tiles, Tile winningTile, WinType winType) {
        if (winningTile == null) {
            return WaitType.MULTIPLE_WAIT;
        }
        
        if (winType == WinType.SEVEN_PAIRS) {
            return WaitType.PAIR_WAIT;
        }
        
        // For basic wins, analyze the winning tile context
        List<Tile> tilesWithoutWinning = new ArrayList<>(tiles);
        tilesWithoutWinning.remove(winningTile);
        
        // Check if winning tile completes a pair
        long winningTileCount = tiles.stream().filter(t -> t.equals(winningTile)).count();
        if (winningTileCount == 2) {
            return WaitType.PAIR_WAIT;
        }
        
        // Check if winning tile completes a sequence
        if (isEdgeWait(tilesWithoutWinning, winningTile)) {
            return WaitType.EDGE_WAIT;
        }
        
        return WaitType.MIDDLE_WAIT;
    }
    
    /**
     * Checks if the winning tile creates an edge wait
     */
    private boolean isEdgeWait(List<Tile> tilesWithoutWinning, Tile winningTile) {
        Tile.Suit suit = winningTile.getSuit();
        int rank = winningTile.getRank();
        
        // Edge wait patterns: 1-2 waiting for 3, or 8-9 waiting for 7
        if (rank == 3) {
            Tile tile1 = new Tile(suit, 1);
            Tile tile2 = new Tile(suit, 2);
            return tilesWithoutWinning.contains(tile1) && tilesWithoutWinning.contains(tile2);
        }
        
        if (rank == 7) {
            Tile tile1 = new Tile(suit, 8);
            Tile tile2 = new Tile(suit, 9);
            return tilesWithoutWinning.contains(tile1) && tilesWithoutWinning.contains(tile2);
        }
        
        return false;
    }
    
    /**
     * Validates Kong robbery (Qiang Gang Hu) - when someone wins off a tile used to upgrade Peng to Gang
     */
    public boolean validateKongRobbery(PlayerState player, Tile robbedTile, HuTypes huConfig) {
        if (!huConfig.getSelfDraw()) {
            return false; // Kong robbery not allowed if self-draw only
        }
        
        // Check if player can win with the robbed tile
        WinValidationResult result = validateWin(player, robbedTile, huConfig, false);
        return result.isWinning();
    }
    
    /**
     * Gets all possible winning tiles for a player's current hand
     */
    public Set<Tile> getPossibleWinningTiles(PlayerState player, HuTypes huConfig) {
        Set<Tile> winningTiles = new HashSet<>();
        
        // Test all possible tiles (1-9 for each suit)
        for (Tile.Suit suit : Tile.Suit.values()) {
            for (int rank = 1; rank <= 9; rank++) {
                Tile testTile = new Tile(suit, rank);
                WinValidationResult result = validateWin(player, testTile, huConfig, false);
                if (result.isWinning()) {
                    winningTiles.add(testTile);
                }
            }
        }
        
        return winningTiles;
    }
    
    /**
     * Checks if a player is in a ready hand state (one tile away from winning)
     */
    public boolean isReadyHand(PlayerState player, HuTypes huConfig) {
        return !getPossibleWinningTiles(player, huConfig).isEmpty();
    }
}