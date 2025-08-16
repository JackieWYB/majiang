package com.mahjong.service;

import com.mahjong.model.config.RoomConfig;
import com.mahjong.model.config.ScoreConfig;
import com.mahjong.model.dto.*;
import com.mahjong.model.game.GameState;
import com.mahjong.model.game.MeldSet;
import com.mahjong.model.game.PlayerState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Comprehensive scoring service that handles all aspects of Mahjong scoring
 * including base scores, fan multipliers, special hands, and complex settlement scenarios
 */
@Service
public class ComprehensiveScoreService {
    
    private static final Logger logger = LoggerFactory.getLogger(ComprehensiveScoreService.class);
    
    @Autowired
    private ScoreCalculationService scoreCalculationService;
    
    @Autowired
    private WinValidationService winValidationService;
    
    /**
     * Calculate comprehensive settlement with all scoring rules applied
     */
    public SettlementResult calculateComprehensiveSettlement(GameState gameState, List<WinResult> winResults) {
        logger.info("Calculating comprehensive settlement for game: {}", gameState.getGameId());
        
        SettlementResult settlement = new SettlementResult(gameState.getGameId(), gameState.getRoomId());
        RoomConfig config = gameState.getConfig();
        ScoreConfig scoreConfig = config.getScore();
        
        // Validate and process win results
        List<WinResult> validatedWins = validateAndEnhanceWinResults(winResults, gameState, config);
        
        // Determine game end reason
        String endReason = determineGameEndReason(gameState, validatedWins);
        settlement.setGameEndReason(endReason);
        
        // Calculate base settlement
        if ("WIN".equals(endReason)) {
            calculateWinSettlement(settlement, gameState, validatedWins, scoreConfig);
        } else {
            calculateNonWinSettlement(settlement, gameState, endReason);
        }
        
        // Apply Gang bonuses
        applyGangBonuses(settlement, gameState, scoreConfig);
        
        // Apply score caps and adjustments
        applyScoreCapsAndAdjustments(settlement, scoreConfig);
        
        // Generate final score summary
        generateFinalScoreSummary(settlement);
        
        logger.info("Comprehensive settlement completed for game: {}", gameState.getGameId());
        return settlement;
    }
    
    /**
     * Validate and enhance win results with detailed analysis
     */
    private List<WinResult> validateAndEnhanceWinResults(List<WinResult> winResults, GameState gameState, RoomConfig config) {
        List<WinResult> enhancedResults = new ArrayList<>();
        
        for (WinResult winResult : winResults) {
            if (winResult.isValid()) {
                PlayerState player = gameState.getPlayerByUserId(winResult.getUserId());
                if (player != null) {
                    // Re-analyze the winning hand with comprehensive scoring
                    WinResult enhanced = scoreCalculationService.analyzeWinningHand(
                        player, winResult.getWinningTile(), winResult.isSelfDraw(), config);
                    
                    // Preserve original win context
                    enhanced.setWinningFrom(winResult.getWinningFrom());
                    enhancedResults.add(enhanced);
                }
            }
        }
        
        return enhancedResults;
    }
    
    /**
     * Calculate settlement for winning scenarios with comprehensive scoring
     */
    private void calculateWinSettlement(SettlementResult settlement, GameState gameState, 
                                      List<WinResult> winResults, ScoreConfig scoreConfig) {
        List<PlayerResult> playerResults = new ArrayList<>();
        
        // Handle multiple winners
        List<WinResult> finalWinners = procesMultipleWinners(winResults, gameState, scoreConfig);
        settlement.setMultipleWinners(finalWinners.size() > 1);
        
        // Calculate individual player results
        for (PlayerState player : gameState.getPlayers()) {
            PlayerResult result = createPlayerResult(player, finalWinners, gameState, scoreConfig);
            playerResults.add(result);
        }
        
        // Balance scores and apply final adjustments
        balanceAndAdjustScores(playerResults, finalWinners, scoreConfig);
        
        settlement.setPlayerResults(playerResults);
    }
    
    /**
     * Process multiple winners according to game rules
     */
    private List<WinResult> procesMultipleWinners(List<WinResult> winners, GameState gameState, ScoreConfig scoreConfig) {
        if (winners.size() <= 1) {
            return winners;
        }
        
        if (!scoreConfig.getMultipleWinners()) {
            // Select single winner based on priority rules
            return selectHighestPriorityWinner(winners, gameState);
        } else {
            // Allow multiple winners but adjust scoring
            return adjustMultipleWinnerScoring(winners);
        }
    }
    
    /**
     * Select highest priority winner from multiple winners
     */
    private List<WinResult> selectHighestPriorityWinner(List<WinResult> winners, GameState gameState) {
        // Priority rules:
        // 1. Self-draw beats win on discard
        // 2. Higher fan count beats lower fan count
        // 3. Closer to dealer in turn order
        
        return winners.stream()
            .min(Comparator
                .comparing((WinResult wr) -> !wr.isSelfDraw()) // Self-draw first
                .thenComparing((WinResult wr) -> -wr.getBaseFan()) // Higher fan first
                .thenComparing(wr -> getDistanceFromDealer(gameState, wr.getUserId()))) // Closer to dealer
            .map(List::of)
            .orElse(Collections.emptyList());
    }
    
    /**
     * Adjust scoring for multiple winners
     */
    private List<WinResult> adjustMultipleWinnerScoring(List<WinResult> winners) {
        // Reduce fan multipliers to prevent excessive scoring
        double reductionFactor = Math.max(0.5, 1.0 / winners.size());
        
        for (WinResult winner : winners) {
            int adjustedFan = Math.max(1, (int) (winner.getBaseFan() * reductionFactor));
            winner.setBaseFan(adjustedFan);
            
            // Add note about multiple winner adjustment
            List<String> fanSources = new ArrayList<>(winner.getFanSources());
            fanSources.add(String.format("多家和牌调整: ×%.1f", reductionFactor));
            winner.setFanSources(fanSources);
        }
        
        return winners;
    }
    
    /**
     * Create comprehensive player result
     */
    private PlayerResult createPlayerResult(PlayerState player, List<WinResult> winners, 
                                          GameState gameState, ScoreConfig scoreConfig) {
        PlayerResult result = new PlayerResult(player.getUserId(), player.getSeatIndex());
        result.setDealer(player.isDealer());
        
        // Check if this player won
        Optional<WinResult> winResult = winners.stream()
            .filter(wr -> wr.getUserId().equals(player.getUserId()))
            .findFirst();
        
        if (winResult.isPresent()) {
            calculateComprehensiveWinnerScore(result, winResult.get(), gameState, scoreConfig);
        } else {
            calculateComprehensiveLoserScore(result, winners, gameState, scoreConfig);
        }
        
        return result;
    }
    
    /**
     * Calculate comprehensive winner score with all bonuses
     */
    private void calculateComprehensiveWinnerScore(PlayerResult result, WinResult winResult, 
                                                 GameState gameState, ScoreConfig scoreConfig) {
        result.setWinner(true);
        result.setSelfDraw(winResult.isSelfDraw());
        result.setHandTypes(winResult.getHandTypes());
        
        List<String> scoreBreakdown = new ArrayList<>();
        
        // Base score calculation
        int baseScore = scoreConfig.getBaseScore();
        result.setBaseScore(baseScore);
        scoreBreakdown.add(String.format("底分: %d", baseScore));
        
        // Fan multiplier
        int fanMultiplier = Math.max(1, winResult.getBaseFan());
        result.setFanMultiplier(fanMultiplier);
        scoreBreakdown.add(String.format("番数: %d番", fanMultiplier));
        
        // Calculate base winning score
        int winScore = baseScore * fanMultiplier;
        scoreBreakdown.add(String.format("基础得分: %d × %d = %d", baseScore, fanMultiplier, winScore));
        
        // Apply dealer multiplier
        if (result.isDealer()) {
            double dealerMultiplier = scoreConfig.getDealerMultiplier();
            result.setDealerMultiplier(dealerMultiplier);
            int dealerBonus = (int) (winScore * (dealerMultiplier - 1));
            winScore += dealerBonus;
            scoreBreakdown.add(String.format("庄家奖励: +%d (×%.1f)", dealerBonus, dealerMultiplier));
        }
        
        // Apply self-draw bonus
        if (result.isSelfDraw()) {
            double selfDrawBonus = scoreConfig.getSelfDrawBonus();
            result.setSelfDrawBonus(selfDrawBonus);
            int selfDrawPoints = (int) (winScore * selfDrawBonus);
            winScore += selfDrawPoints;
            scoreBreakdown.add(String.format("自摸奖励: +%d (+%.0f%%)", selfDrawPoints, selfDrawBonus * 100));
        }
        
        result.setFinalScore(winScore);
        
        // Apply score cap
        int cappedScore = Math.min(winScore, scoreConfig.getMaxScore());
        result.setCappedScore(cappedScore);
        
        if (cappedScore < winScore) {
            scoreBreakdown.add(String.format("封顶限制: %d → %d", winScore, cappedScore));
        }
        
        // Add fan source details
        scoreBreakdown.addAll(winResult.getFanSources());
        result.setScoreBreakdown(scoreBreakdown);
    }
    
    /**
     * Calculate comprehensive loser score with detailed breakdown
     */
    private void calculateComprehensiveLoserScore(PlayerResult result, List<WinResult> winners, 
                                                GameState gameState, ScoreConfig scoreConfig) {
        result.setWinner(false);
        
        List<String> scoreBreakdown = new ArrayList<>();
        int totalLoss = 0;
        
        for (WinResult winner : winners) {
            int lossAmount = calculateLossAmount(result, winner, gameState, scoreConfig);
            totalLoss += lossAmount;
            
            String winnerName = getPlayerDisplayName(winner.getUserId());
            String lossType = winner.isSelfDraw() ? "自摸" : "点炮";
            scoreBreakdown.add(String.format("输给%s(%s): -%d", winnerName, lossType, lossAmount));
        }
        
        result.setFinalScore(-totalLoss);
        result.setCappedScore(-totalLoss);
        result.setScoreBreakdown(scoreBreakdown);
    }
    
    /**
     * Calculate loss amount for a specific winner
     */
    private int calculateLossAmount(PlayerResult loser, WinResult winner, GameState gameState, ScoreConfig scoreConfig) {
        // Calculate winner's score
        PlayerResult tempWinner = new PlayerResult(winner.getUserId(), getPlayerSeatIndex(gameState, winner.getUserId()));
        tempWinner.setDealer(isPlayerDealer(gameState, winner.getUserId()));
        calculateComprehensiveWinnerScore(tempWinner, winner, gameState, scoreConfig);
        
        int winnerScore = tempWinner.getCappedScore();
        
        if (winner.isSelfDraw()) {
            // Self-draw: all players pay equally
            return winnerScore / (gameState.getPlayers().size() - 1);
        } else {
            // Win on discard: different payment structure
            if (winner.getWinningFrom() != null && winner.getWinningFrom().equals(loser.getUserId())) {
                // Discarding player pays full amount
                return winnerScore;
            } else {
                // Other players pay reduced amount
                return winnerScore / 4; // Reduced payment for non-discarding players
            }
        }
    }
    
    /**
     * Apply Gang bonuses to all players
     */
    private void applyGangBonuses(SettlementResult settlement, GameState gameState, ScoreConfig scoreConfig) {
        List<GangScore> gangScores = calculateComprehensiveGangBonuses(gameState, scoreConfig);
        settlement.setGangScores(gangScores);
        
        // Apply Gang bonuses to player results
        Map<String, Integer> gangBonusMap = new HashMap<>();
        
        for (GangScore gangScore : gangScores) {
            gangBonusMap.merge(gangScore.getFromUserId(), -gangScore.getBonusPoints(), Integer::sum);
            gangBonusMap.merge(gangScore.getToUserId(), gangScore.getBonusPoints(), Integer::sum);
        }
        
        for (PlayerResult result : settlement.getPlayerResults()) {
            int gangBonus = gangBonusMap.getOrDefault(result.getUserId(), 0);
            result.setGangBonus(gangBonus);
            
            if (gangBonus != 0) {
                result.setCappedScore(result.getCappedScore() + gangBonus);
                
                List<String> breakdown = new ArrayList<>(result.getScoreBreakdown());
                breakdown.add(String.format("杠分: %+d", gangBonus));
                result.setScoreBreakdown(breakdown);
            }
        }
    }
    
    /**
     * Calculate comprehensive Gang bonuses
     */
    private List<GangScore> calculateComprehensiveGangBonuses(GameState gameState, ScoreConfig scoreConfig) {
        List<GangScore> gangScores = new ArrayList<>();
        int baseGangBonus = scoreConfig.getGangBonus();
        
        for (PlayerState player : gameState.getPlayers()) {
            for (MeldSet meld : player.getMelds()) {
                if (meld.getMeldType() == MeldSet.MeldType.GANG) {
                    int bonusPoints = calculateGangBonusPoints(meld, baseGangBonus);
                    
                    // Gang bonus is paid by all other players
                    for (PlayerState otherPlayer : gameState.getPlayers()) {
                        if (!otherPlayer.getUserId().equals(player.getUserId())) {
                            GangScore gangScore = new GangScore(
                                otherPlayer.getUserId(),
                                player.getUserId(),
                                meld.getGangType(),
                                meld.getBaseTile().toString(),
                                bonusPoints
                            );
                            gangScores.add(gangScore);
                        }
                    }
                }
            }
        }
        
        return gangScores;
    }
    
    /**
     * Calculate Gang bonus points based on Gang type
     */
    private int calculateGangBonusPoints(MeldSet gang, int baseGangBonus) {
        return switch (gang.getGangType()) {
            case AN_GANG -> baseGangBonus * 4; // Concealed Gang gets highest bonus
            case MING_GANG -> baseGangBonus * 2; // Open Gang gets medium bonus
            case BU_GANG -> baseGangBonus * 2; // Added Gang gets medium bonus
        };
    }
    
    /**
     * Apply score caps and final adjustments
     */
    private void applyScoreCapsAndAdjustments(SettlementResult settlement, ScoreConfig scoreConfig) {
        for (PlayerResult result : settlement.getPlayerResults()) {
            // Apply individual score caps
            if (result.isWinner() && result.getCappedScore() > scoreConfig.getMaxScore()) {
                result.setCappedScore(scoreConfig.getMaxScore());
            }
            
            // Apply minimum loss limits (prevent excessive losses)
            if (!result.isWinner() && result.getCappedScore() < -scoreConfig.getMaxScore()) {
                result.setCappedScore(-scoreConfig.getMaxScore());
            }
        }
    }
    
    /**
     * Balance and adjust scores to ensure fairness
     */
    private void balanceAndAdjustScores(List<PlayerResult> playerResults, List<WinResult> winners, ScoreConfig scoreConfig) {
        // Ensure total scores balance (sum should be close to zero)
        int totalScore = playerResults.stream().mapToInt(PlayerResult::getCappedScore).sum();
        
        if (Math.abs(totalScore) > 1) { // Allow small rounding differences
            // Distribute imbalance among non-winners
            List<PlayerResult> losers = playerResults.stream()
                .filter(pr -> !pr.isWinner())
                .collect(Collectors.toList());
            
            if (!losers.isEmpty()) {
                int adjustment = totalScore / losers.size();
                int remainder = totalScore % losers.size();
                
                for (int i = 0; i < losers.size(); i++) {
                    PlayerResult loser = losers.get(i);
                    int adjustmentAmount = adjustment + (i < remainder ? 1 : 0);
                    loser.setCappedScore(loser.getCappedScore() - adjustmentAmount);
                    
                    if (adjustmentAmount != 0) {
                        List<String> breakdown = new ArrayList<>(loser.getScoreBreakdown());
                        breakdown.add(String.format("平衡调整: %+d", -adjustmentAmount));
                        loser.setScoreBreakdown(breakdown);
                    }
                }
            }
        }
    }
    
    /**
     * Generate final score summary
     */
    private void generateFinalScoreSummary(SettlementResult settlement) {
        Map<String, Integer> finalScores = settlement.getPlayerResults().stream()
            .collect(Collectors.toMap(
                PlayerResult::getUserId,
                PlayerResult::getCappedScore
            ));
        settlement.setFinalScores(finalScores);
    }
    
    /**
     * Calculate non-win settlement (draw, timeout)
     */
    private void calculateNonWinSettlement(SettlementResult settlement, GameState gameState, String endReason) {
        List<PlayerResult> playerResults = new ArrayList<>();
        
        for (PlayerState player : gameState.getPlayers()) {
            PlayerResult result = new PlayerResult(player.getUserId(), player.getSeatIndex());
            result.setDealer(player.isDealer());
            result.setFinalScore(0);
            result.setCappedScore(0);
            
            String reasonText = switch (endReason) {
                case "DRAW" -> "流局";
                case "TIMEOUT" -> "超时";
                default -> "游戏结束";
            };
            result.setScoreBreakdown(List.of(reasonText + ": 0分"));
            
            playerResults.add(result);
        }
        
        settlement.setPlayerResults(playerResults);
    }
    
    // Helper methods
    private String determineGameEndReason(GameState gameState, List<WinResult> winResults) {
        if (winResults != null && winResults.stream().anyMatch(WinResult::isValid)) {
            return "WIN";
        } else if (gameState.getRemainingTiles() <= 0) {
            return "DRAW";
        } else {
            return "TIMEOUT";
        }
    }
    
    private int getPlayerSeatIndex(GameState gameState, String userId) {
        return gameState.getPlayers().stream()
            .filter(p -> p.getUserId().equals(userId))
            .mapToInt(PlayerState::getSeatIndex)
            .findFirst()
            .orElse(-1);
    }
    
    private boolean isPlayerDealer(GameState gameState, String userId) {
        return gameState.getPlayers().stream()
            .filter(p -> p.getUserId().equals(userId))
            .findFirst()
            .map(PlayerState::isDealer)
            .orElse(false);
    }
    
    private int getDistanceFromDealer(GameState gameState, String userId) {
        int playerSeat = getPlayerSeatIndex(gameState, userId);
        int dealerSeat = gameState.getDealerSeatIndex();
        return (playerSeat - dealerSeat + gameState.getPlayers().size()) % gameState.getPlayers().size();
    }
    
    private String getPlayerDisplayName(String userId) {
        // In a real implementation, this would fetch the player's display name
        return "玩家" + userId.substring(Math.max(0, userId.length() - 4));
    }
}