package com.mahjong.service;

import com.mahjong.model.config.HuTypes;
import com.mahjong.model.config.RoomConfig;
import com.mahjong.model.config.ScoreConfig;
import com.mahjong.model.dto.WinResult;
import com.mahjong.model.game.MeldSet;
import com.mahjong.model.game.PlayerState;
import com.mahjong.model.game.Tile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for calculating detailed scores and fan points for winning hands
 */
@Service
public class ScoreCalculationService {
    
    private static final Logger logger = LoggerFactory.getLogger(ScoreCalculationService.class);
    
    /**
     * Analyze a winning hand and calculate fan points
     */
    public WinResult analyzeWinningHand(PlayerState player, Tile winningTile, 
                                       boolean isSelfDraw, RoomConfig config) {
        logger.debug("Analyzing winning hand for player: {}", player.getUserId());
        
        WinResult result = new WinResult(player.getUserId());
        result.setWinningTile(winningTile);
        result.setSelfDraw(isSelfDraw);
        
        List<Tile> handTiles = new ArrayList<>(player.getHandTiles());
        List<MeldSet> melds = player.getMelds();
        
        // Validate basic winning pattern
        if (!isValidWinningPattern(handTiles, melds, winningTile)) {
            result.setValid(false);
            return result;
        }
        
        result.setValid(true);
        
        // Calculate fan points
        int totalFan = calculateFanPoints(handTiles, melds, winningTile, isSelfDraw, config);
        result.setBaseFan(Math.max(1, totalFan));
        
        // Identify hand types
        List<String> handTypes = identifyHandTypes(handTiles, melds, winningTile, config);
        result.setHandTypes(handTypes);
        
        // Generate fan sources
        List<String> fanSources = generateFanSources(handTiles, melds, winningTile, isSelfDraw, config);
        result.setFanSources(fanSources);
        
        // Generate win pattern description
        String pattern = generateWinPattern(handTiles, melds, winningTile);
        result.setWinPattern(pattern);
        
        logger.debug("Win analysis complete: {} fan, types: {}", totalFan, handTypes);
        return result;
    }
    
    /**
     * Validate basic winning pattern (4 sets + 1 pair)
     */
    private boolean isValidWinningPattern(List<Tile> handTiles, List<MeldSet> melds, Tile winningTile) {
        // Add winning tile to hand for analysis
        List<Tile> allTiles = new ArrayList<>(handTiles);
        allTiles.add(winningTile);
        
        // Check for special patterns first
        if (isSevenPairs(allTiles)) {
            return true;
        }
        
        // Check standard pattern: 4 sets + 1 pair
        return isStandardWinningPattern(allTiles, melds);
    }
    
    /**
     * Check if hand is Seven Pairs pattern
     */
    private boolean isSevenPairs(List<Tile> tiles) {
        if (tiles.size() != 14) return false;
        
        Map<Tile, Integer> tileCount = tiles.stream()
                .collect(Collectors.groupingBy(t -> t, Collectors.summingInt(t -> 1)));
        
        // Must have exactly 7 different tiles, each appearing twice
        return tileCount.size() == 7 && tileCount.values().stream().allMatch(count -> count == 2);
    }
    
    /**
     * Check standard winning pattern
     */
    private boolean isStandardWinningPattern(List<Tile> tiles, List<MeldSet> melds) {
        // Calculate total tiles including melds
        int meldTileCount = melds.stream().mapToInt(meld -> meld.getTiles().size()).sum();
        int totalTiles = tiles.size() + meldTileCount;
        
        // Must have exactly 14 tiles total
        if (totalTiles != 14) return false;
        
        // For simplified validation, assume any 14-tile combination with proper melds is valid
        // In a real implementation, this would need proper mahjong hand validation
        
        // Check that we have the right structure: 4 sets + 1 pair
        // Each meld represents a set, remaining tiles should form sets + 1 pair
        int remainingTiles = tiles.size();
        int expectedSets = 4 - melds.size();
        
        // Remaining tiles should be able to form the required sets + 1 pair
        // Simplified: check if remaining tiles can form valid combinations
        if (expectedSets == 0) {
            // All sets are melds, remaining tiles should form exactly 1 pair
            return remainingTiles == 2 && tiles.get(0).equals(tiles.get(1));
        } else if (expectedSets == 1) {
            // Need 1 more set + 1 pair from remaining tiles (5 tiles total)
            return remainingTiles == 5;
        } else if (expectedSets == 2) {
            // Need 2 more sets + 1 pair from remaining tiles (8 tiles total)
            return remainingTiles == 8;
        } else if (expectedSets == 3) {
            // Need 3 more sets + 1 pair from remaining tiles (11 tiles total)
            return remainingTiles == 11;
        } else if (expectedSets == 4) {
            // Need 4 sets + 1 pair from remaining tiles (14 tiles total)
            return remainingTiles == 14;
        }
        
        return false;
    }
    
    /**
     * Calculate total fan points for a winning hand
     */
    private int calculateFanPoints(List<Tile> handTiles, List<MeldSet> melds, 
                                 Tile winningTile, boolean isSelfDraw, RoomConfig config) {
        int totalFan = 1; // Base fan
        HuTypes huTypes = config.getHuTypes();
        
        List<Tile> allTiles = new ArrayList<>(handTiles);
        allTiles.add(winningTile);
        
        // Self-draw bonus
        if (isSelfDraw && huTypes.getSelfDraw()) {
            totalFan += 1;
        }
        
        // Seven Pairs - highest priority special hand
        if (isSevenPairs(allTiles) && huTypes.getSevenPairs()) {
            totalFan += 4; // Increased value for Seven Pairs
            return totalFan; // Seven Pairs is exclusive, don't add other bonuses
        }
        
        // All Pungs (all sets are Peng/Gang)
        if (isAllPungs(melds, allTiles) && huTypes.getAllPungs()) {
            totalFan += 6; // High value for All Pungs
        }
        
        // All same suit (清一色)
        if (isAllSameSuit(allTiles)) {
            totalFan += 8; // Very high value for pure suit
        }
        
        // Mixed one suit (混一色) - all tiles same suit plus honors
        else if (isMixedOneSuit(allTiles)) {
            totalFan += 3;
        }
        
        // All terminals and honors (全幺九)
        if (isAllTerminalsAndHonors(allTiles)) {
            totalFan += 10; // Extremely rare hand
        }
        
        // All terminals (纯全带幺)
        else if (isAllTerminals(allTiles, melds)) {
            totalFan += 4;
        }
        
        // Mixed terminals (混全带幺)
        else if (isMixedTerminals(allTiles, melds)) {
            totalFan += 2;
        }
        
        // No honors (断幺九)
        if (isNoHonors(allTiles)) {
            totalFan += 1;
        }
        
        // Edge wait or pair wait
        if (isEdgeOrPairWait(handTiles, winningTile)) {
            if (huTypes.getEdgeWait() || huTypes.getPairWait()) {
                totalFan += 1;
            }
        }
        
        // Concealed hand bonus (门清)
        if (isAllConcealed(melds)) {
            totalFan += 2; // Increased value for fully concealed hand
        }
        
        // Single wait (单钓)
        if (isSingleWait(handTiles, winningTile)) {
            totalFan += 1;
        }
        
        // Four concealed pungs (四暗刻)
        int concealedPungs = countConcealedPungs(melds, handTiles, winningTile);
        if (concealedPungs == 4) {
            totalFan += 13; // Yakuman level hand
        } else if (concealedPungs == 3) {
            totalFan += 2;
        }
        
        // Three concealed gangs (三暗杠)
        int concealedGangs = countConcealedGangs(melds);
        if (concealedGangs >= 3) {
            totalFan += 2;
        }
        
        return Math.min(totalFan, 13); // Cap at 13 fan (yakuman equivalent)
    }
    
    /**
     * Check if all melds are Pungs (no Chi)
     */
    private boolean isAllPungs(List<MeldSet> melds, List<Tile> handTiles) {
        // Check melds
        boolean hasOnlyChi = melds.stream().anyMatch(meld -> meld.getMeldType() == MeldSet.MeldType.CHI);
        if (hasOnlyChi) return false;
        
        // For remaining hand tiles, check if they form only Pungs + pair
        // Simplified check - in real implementation would need proper analysis
        return true;
    }
    
    /**
     * Check if all tiles are same suit
     */
    private boolean isAllSameSuit(List<Tile> tiles) {
        if (tiles.isEmpty()) return false;
        
        Tile.Suit firstSuit = tiles.get(0).getSuit();
        return tiles.stream().allMatch(tile -> tile.getSuit() == firstSuit);
    }
    
    /**
     * Check if winning tile is edge wait or pair wait
     */
    private boolean isEdgeOrPairWait(List<Tile> handTiles, Tile winningTile) {
        // Simplified implementation
        // Would need to analyze the specific wait pattern
        return false;
    }
    
    /**
     * Check if all melds are concealed
     */
    private boolean isAllConcealed(List<MeldSet> melds) {
        return melds.stream().allMatch(MeldSet::isConcealed);
    }
    
    /**
     * Check if hand is mixed one suit (multiple suits but not all three)
     */
    private boolean isMixedOneSuit(List<Tile> tiles) {
        if (tiles.isEmpty()) return false;
        
        Set<Tile.Suit> suits = tiles.stream().map(Tile::getSuit).collect(Collectors.toSet());
        return suits.size() == 2; // Two suits mixed
    }
    
    /**
     * Check if all tiles are terminals (1 or 9)
     */
    private boolean isAllTerminalsAndHonors(List<Tile> tiles) {
        return tiles.stream().allMatch(tile -> tile.getRank() == 1 || tile.getRank() == 9);
    }
    
    /**
     * Check if all sets contain terminals
     */
    private boolean isAllTerminals(List<Tile> tiles, List<MeldSet> melds) {
        // Check if every set and pair contains at least one terminal (1 or 9)
        for (MeldSet meld : melds) {
            boolean hasTerminal = meld.getTiles().stream()
                .anyMatch(tile -> tile.getRank() == 1 || tile.getRank() == 9);
            if (!hasTerminal) return false;
        }
        
        // Check remaining tiles (simplified - would need proper set analysis)
        return tiles.stream().anyMatch(tile -> tile.getRank() == 1 || tile.getRank() == 9);
    }
    
    /**
     * Check if all sets contain terminals
     */
    private boolean isMixedTerminals(List<Tile> tiles, List<MeldSet> melds) {
        for (MeldSet meld : melds) {
            boolean hasTerminal = meld.getTiles().stream()
                .anyMatch(tile -> tile.getRank() == 1 || tile.getRank() == 9);
            if (!hasTerminal) return false;
        }
        return true;
    }
    
    /**
     * Check if hand contains no terminals (all middle tiles 2-8)
     */
    private boolean isNoHonors(List<Tile> tiles) {
        return tiles.stream().noneMatch(tile -> tile.getRank() == 1 || tile.getRank() == 9);
    }
    
    /**
     * Check if winning tile is single wait (pair wait)
     */
    private boolean isSingleWait(List<Tile> handTiles, Tile winningTile) {
        // Count occurrences of winning tile in hand
        long count = handTiles.stream().filter(tile -> tile.equals(winningTile)).count();
        return count == 1; // Single tile waiting to form pair
    }
    
    /**
     * Count concealed pungs in hand
     */
    private int countConcealedPungs(List<MeldSet> melds, List<Tile> handTiles, Tile winningTile) {
        int count = 0;
        
        // Count concealed pungs in melds
        count += (int) melds.stream()
            .filter(meld -> meld.getMeldType() == MeldSet.MeldType.PENG && meld.isConcealed())
            .count();
        
        // Count concealed gangs as pungs
        count += (int) melds.stream()
            .filter(meld -> meld.getMeldType() == MeldSet.MeldType.GANG && 
                          meld.getGangType() == MeldSet.GangType.AN_GANG)
            .count();
        
        // Check if winning tile forms a concealed pung
        List<Tile> allTiles = new ArrayList<>(handTiles);
        allTiles.add(winningTile);
        Map<Tile, Integer> tileCount = allTiles.stream()
            .collect(Collectors.groupingBy(t -> t, Collectors.summingInt(t -> 1)));
        
        for (Map.Entry<Tile, Integer> entry : tileCount.entrySet()) {
            if (entry.getValue() >= 3) {
                // Check if this forms a concealed pung (not claimed from discard)
                boolean isConcealedPung = true;
                for (MeldSet meld : melds) {
                    if (meld.getBaseTile().equals(entry.getKey()) && !meld.isConcealed()) {
                        isConcealedPung = false;
                        break;
                    }
                }
                if (isConcealedPung) count++;
            }
        }
        
        return count;
    }
    
    /**
     * Count concealed gangs
     */
    private int countConcealedGangs(List<MeldSet> melds) {
        return (int) melds.stream()
            .filter(meld -> meld.getMeldType() == MeldSet.MeldType.GANG && 
                          meld.getGangType() == MeldSet.GangType.AN_GANG)
            .count();
    }
    
    /**
     * Identify special hand types
     */
    private List<String> identifyHandTypes(List<Tile> handTiles, List<MeldSet> melds, 
                                         Tile winningTile, RoomConfig config) {
        List<String> handTypes = new ArrayList<>();
        List<Tile> allTiles = new ArrayList<>(handTiles);
        allTiles.add(winningTile);
        
        HuTypes huTypes = config.getHuTypes();
        
        // Special complete hands
        if (isSevenPairs(allTiles) && huTypes.getSevenPairs()) {
            handTypes.add("七对");
        }
        
        if (isAllTerminalsAndHonors(allTiles)) {
            handTypes.add("全幺九");
        }
        
        // Suit-based hands
        if (isAllSameSuit(allTiles)) {
            handTypes.add("清一色");
        } else if (isMixedOneSuit(allTiles)) {
            handTypes.add("混一色");
        }
        
        // Pung-based hands
        if (isAllPungs(melds, allTiles) && huTypes.getAllPungs()) {
            handTypes.add("碰碰胡");
        }
        
        int concealedPungs = countConcealedPungs(melds, handTiles, winningTile);
        if (concealedPungs == 4) {
            handTypes.add("四暗刻");
        } else if (concealedPungs == 3) {
            handTypes.add("三暗刻");
        }
        
        // Terminal-based hands
        if (isAllTerminals(allTiles, melds)) {
            handTypes.add("纯全带幺");
        } else if (isMixedTerminals(allTiles, melds)) {
            handTypes.add("混全带幺");
        }
        
        if (isNoHonors(allTiles)) {
            handTypes.add("断幺九");
        }
        
        // Concealment bonus
        if (isAllConcealed(melds)) {
            handTypes.add("门清");
        }
        
        // Gang bonuses
        int concealedGangs = countConcealedGangs(melds);
        if (concealedGangs >= 3) {
            handTypes.add("三暗杠");
        }
        
        // Wait patterns
        if (isSingleWait(handTiles, winningTile)) {
            handTypes.add("单钓");
        }
        
        return handTypes;
    }
    
    /**
     * Generate detailed fan sources
     */
    private List<String> generateFanSources(List<Tile> handTiles, List<MeldSet> melds, 
                                           Tile winningTile, boolean isSelfDraw, RoomConfig config) {
        List<String> fanSources = new ArrayList<>();
        List<Tile> allTiles = new ArrayList<>(handTiles);
        allTiles.add(winningTile);
        
        fanSources.add("基础: 1番");
        
        HuTypes huTypes = config.getHuTypes();
        
        if (isSelfDraw && huTypes.getSelfDraw()) {
            fanSources.add("自摸: +1番");
        }
        
        // Special hands (mutually exclusive with other bonuses)
        if (isSevenPairs(allTiles) && huTypes.getSevenPairs()) {
            fanSources.add("七对: +4番");
            return fanSources; // Seven pairs is exclusive
        }
        
        // High value hands
        if (isAllTerminalsAndHonors(allTiles)) {
            fanSources.add("全幺九: +10番");
        }
        
        if (isAllSameSuit(allTiles)) {
            fanSources.add("清一色: +8番");
        } else if (isMixedOneSuit(allTiles)) {
            fanSources.add("混一色: +3番");
        }
        
        if (isAllPungs(melds, allTiles) && huTypes.getAllPungs()) {
            fanSources.add("碰碰胡: +6番");
        }
        
        // Terminal bonuses
        if (isAllTerminals(allTiles, melds)) {
            fanSources.add("纯全带幺: +4番");
        } else if (isMixedTerminals(allTiles, melds)) {
            fanSources.add("混全带幺: +2番");
        }
        
        if (isNoHonors(allTiles)) {
            fanSources.add("断幺九: +1番");
        }
        
        // Concealed hand bonuses
        if (isAllConcealed(melds)) {
            fanSources.add("门清: +2番");
        }
        
        // Special pung/gang bonuses
        int concealedPungs = countConcealedPungs(melds, handTiles, winningTile);
        if (concealedPungs == 4) {
            fanSources.add("四暗刻: +13番");
        } else if (concealedPungs == 3) {
            fanSources.add("三暗刻: +2番");
        }
        
        int concealedGangs = countConcealedGangs(melds);
        if (concealedGangs >= 3) {
            fanSources.add("三暗杠: +2番");
        }
        
        // Wait pattern bonuses
        if (isEdgeOrPairWait(handTiles, winningTile)) {
            if (huTypes.getEdgeWait() || huTypes.getPairWait()) {
                fanSources.add("边张/嵌张: +1番");
            }
        }
        
        if (isSingleWait(handTiles, winningTile)) {
            fanSources.add("单钓: +1番");
        }
        
        return fanSources;
    }
    
    /**
     * Generate win pattern description
     */
    private String generateWinPattern(List<Tile> handTiles, List<MeldSet> melds, Tile winningTile) {
        StringBuilder pattern = new StringBuilder();
        
        // Add melds
        for (MeldSet meld : melds) {
            pattern.append(meld.toString()).append(" ");
        }
        
        // Add hand tiles (simplified)
        pattern.append("手牌: ");
        handTiles.stream()
                .sorted((t1, t2) -> {
                    int suitCompare = t1.getSuit().compareTo(t2.getSuit());
                    return suitCompare != 0 ? suitCompare : Integer.compare(t1.getRank(), t2.getRank());
                })
                .forEach(tile -> pattern.append(tile.toString()).append(" "));
        
        pattern.append("胡: ").append(winningTile.toString());
        
        return pattern.toString().trim();
    }
    
    /**
     * Calculate score with multipliers applied
     */
    public int calculateFinalScore(int baseScore, int fanMultiplier, boolean isDealer, 
                                 boolean isSelfDraw, ScoreConfig scoreConfig) {
        int score = baseScore * Math.max(1, fanMultiplier);
        
        // Apply dealer multiplier
        if (isDealer) {
            score = (int) (score * scoreConfig.getDealerMultiplier());
        }
        
        // Apply self-draw bonus
        if (isSelfDraw) {
            score = (int) (score * (1 + scoreConfig.getSelfDrawBonus()));
        }
        
        // Apply score cap
        return Math.min(score, scoreConfig.getMaxScore());
    }
}