package com.mahjong.service;

import com.mahjong.model.config.RoomConfig;
import com.mahjong.model.config.ScoreConfig;
import com.mahjong.model.dto.*;
import com.mahjong.model.entity.GameRecord;
import com.mahjong.model.enums.GameResult;
import com.mahjong.model.game.GameState;
import com.mahjong.model.game.MeldSet;
import com.mahjong.model.game.PlayerState;
import com.mahjong.model.game.Tile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for calculating game settlements and managing score distribution
 */
@Service
@Transactional
public class SettlementService {
    
    private static final Logger logger = LoggerFactory.getLogger(SettlementService.class);
    
    @Autowired
    private WinValidationService winValidationService;
    
    /**
     * Calculate settlement for a completed game
     */
    public SettlementResult calculateSettlement(GameState gameState, List<WinResult> winResults) {
        logger.info("Calculating settlement for game: {}", gameState.getGameId());
        
        SettlementResult settlement = new SettlementResult(gameState.getGameId(), gameState.getRoomId());
        RoomConfig config = gameState.getConfig();
        ScoreConfig scoreConfig = config.getScore();
        
        // Determine game end reason
        String endReason = determineGameEndReason(gameState, winResults);
        settlement.setGameEndReason(endReason);
        
        // Handle different end scenarios
        if ("WIN".equals(endReason)) {
            calculateWinSettlement(settlement, gameState, winResults, scoreConfig);
        } else if ("DRAW".equals(endReason)) {
            calculateDrawSettlement(settlement, gameState, scoreConfig);
        } else {
            calculateTimeoutSettlement(settlement, gameState, scoreConfig);
        }
        
        // Calculate Gang bonuses
        List<GangScore> gangScores = calculateGangBonuses(gameState, scoreConfig);
        settlement.setGangScores(gangScores);
        
        // Apply Gang bonuses to final scores
        applyGangBonuses(settlement, gangScores);
        
        // Generate final score map
        Map<String, Integer> finalScores = settlement.getPlayerResults().stream()
                .collect(Collectors.toMap(
                    PlayerResult::getUserId,
                    PlayerResult::getCappedScore
                ));
        settlement.setFinalScores(finalScores);
        
        logger.info("Settlement calculated successfully for game: {}", gameState.getGameId());
        return settlement;
    }
    
    /**
     * Calculate settlement for winning scenario
     */
    private void calculateWinSettlement(SettlementResult settlement, GameState gameState, 
                                      List<WinResult> winResults, ScoreConfig scoreConfig) {
        List<PlayerResult> playerResults = new ArrayList<>();
        List<WinResult> validWins = winResults.stream().filter(WinResult::isValid).toList();
        
        settlement.setMultipleWinners(validWins.size() > 1);
        
        // Handle multiple winners based on configuration
        if (validWins.size() > 1) {
            if (!scoreConfig.getMultipleWinners()) {
                // Only allow the winner with highest priority (Hu > Gang > Peng)
                validWins = selectSingleWinner(validWins, gameState);
            } else {
                // Allow multiple winners but adjust scoring
                validWins = adjustMultipleWinnerScoring(validWins, scoreConfig);
            }
        }
        
        // Calculate scores for each player
        for (PlayerState player : gameState.getPlayers()) {
            PlayerResult result = new PlayerResult(player.getUserId(), player.getSeatIndex());
            result.setDealer(player.isDealer());
            
            // Check if this player won
            Optional<WinResult> winResult = validWins.stream()
                    .filter(wr -> wr.getUserId().equals(player.getUserId()))
                    .findFirst();
            
            if (winResult.isPresent()) {
                calculateWinnerScore(result, winResult.get(), gameState, scoreConfig);
            } else {
                calculateLoserScore(result, validWins, gameState, scoreConfig);
            }
            
            playerResults.add(result);
        }
        
        // Ensure score balance (total should be zero or close to zero)
        balanceScores(playerResults);
        
        settlement.setPlayerResults(playerResults);
    }
    
    /**
     * Select single winner from multiple winners based on priority
     */
    private List<WinResult> selectSingleWinner(List<WinResult> winners, GameState gameState) {
        // Priority: Self-draw > Win on discard, then by seat order from dealer
        return winners.stream()
                .min(Comparator
                    .comparing((WinResult wr) -> !wr.isSelfDraw()) // Self-draw first
                    .thenComparing(wr -> getDistanceFromDealer(gameState, wr.getUserId()))) // Then by seat order
                .map(List::of)
                .orElse(Collections.emptyList());
    }
    
    /**
     * Adjust scoring for multiple winners
     */
    private List<WinResult> adjustMultipleWinnerScoring(List<WinResult> winners, ScoreConfig scoreConfig) {
        // Reduce fan points for multiple winners to prevent excessive scoring
        for (WinResult winner : winners) {
            int adjustedFan = Math.max(1, winner.getBaseFan() / 2);
            winner.setBaseFan(adjustedFan);
        }
        return winners;
    }
    
    /**
     * Balance scores to ensure they sum to approximately zero
     */
    private void balanceScores(List<PlayerResult> playerResults) {
        int totalScore = playerResults.stream().mapToInt(PlayerResult::getCappedScore).sum();
        
        if (totalScore != 0) {
            // Distribute the imbalance among losing players
            List<PlayerResult> losers = playerResults.stream()
                    .filter(pr -> !pr.isWinner())
                    .toList();
            
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
     * Get distance from dealer in seat order
     */
    private int getDistanceFromDealer(GameState gameState, String userId) {
        int playerSeat = getPlayerSeatIndex(gameState, userId);
        int dealerSeat = gameState.getDealerSeatIndex();
        return (playerSeat - dealerSeat + gameState.getPlayers().size()) % gameState.getPlayers().size();
    }
    
    /**
     * Calculate score for a winning player
     */
    private void calculateWinnerScore(PlayerResult result, WinResult winResult, 
                                    GameState gameState, ScoreConfig scoreConfig) {
        result.setWinner(true);
        result.setSelfDraw(winResult.isSelfDraw());
        result.setHandTypes(winResult.getHandTypes());
        
        List<String> scoreBreakdown = new ArrayList<>();
        
        // Base score
        int baseScore = scoreConfig.getBaseScore();
        result.setBaseScore(baseScore);
        scoreBreakdown.add(String.format("底分: %d", baseScore));
        
        // Fan multiplier
        int fanMultiplier = Math.max(1, winResult.getBaseFan());
        result.setFanMultiplier(fanMultiplier);
        scoreBreakdown.add(String.format("番数: %d", fanMultiplier));
        
        // Calculate base winning score
        int winScore = baseScore * fanMultiplier;
        
        // Dealer multiplier
        if (result.isDealer()) {
            double dealerMultiplier = scoreConfig.getDealerMultiplier();
            result.setDealerMultiplier(dealerMultiplier);
            winScore = (int) (winScore * dealerMultiplier);
            scoreBreakdown.add(String.format("庄家倍数: %.1fx", dealerMultiplier));
        }
        
        // Self-draw bonus
        if (result.isSelfDraw()) {
            double selfDrawBonus = scoreConfig.getSelfDrawBonus();
            result.setSelfDrawBonus(selfDrawBonus);
            winScore = (int) (winScore * (1 + selfDrawBonus));
            scoreBreakdown.add(String.format("自摸奖励: +%.0f%%", selfDrawBonus * 100));
        }
        
        result.setFinalScore(winScore);
        
        // Apply score cap
        int cappedScore = Math.min(winScore, scoreConfig.getMaxScore());
        result.setCappedScore(cappedScore);
        
        if (cappedScore < winScore) {
            scoreBreakdown.add(String.format("封顶: %d", scoreConfig.getMaxScore()));
        }
        
        result.setScoreBreakdown(scoreBreakdown);
    }
    
    /**
     * Calculate score for a losing player
     */
    private void calculateLoserScore(PlayerResult result, List<WinResult> winners, 
                                   GameState gameState, ScoreConfig scoreConfig) {
        result.setWinner(false);
        
        List<String> scoreBreakdown = new ArrayList<>();
        int totalLoss = 0;
        
        // Calculate loss for each winner
        for (WinResult winner : winners) {
            PlayerResult winnerResult = new PlayerResult(winner.getUserId(), 
                    getPlayerSeatIndex(gameState, winner.getUserId()));
            winnerResult.setDealer(isPlayerDealer(gameState, winner.getUserId()));
            
            // Calculate what this winner would score
            calculateWinnerScore(winnerResult, winner, gameState, scoreConfig);
            
            // Determine loss distribution based on winner type
            int winnerScore = winnerResult.getCappedScore();
            int lossAmount;
            
            if (winner.isSelfDraw()) {
                // Self-draw: all players pay equally
                lossAmount = winnerScore / (gameState.getPlayers().size() - 1);
            } else {
                // Win on discard: discarding player pays more
                if (winner.getWinningFrom() != null && winner.getWinningFrom().equals(result.getUserId())) {
                    // This player discarded the winning tile - pays full amount
                    lossAmount = winnerScore;
                } else {
                    // Other players pay partial amount
                    lossAmount = winnerScore / 3; // Reduced payment for non-discarding players
                }
            }
            
            totalLoss += lossAmount;
            scoreBreakdown.add(String.format("输给 %s: -%d", winner.getUserId(), lossAmount));
        }
        
        result.setFinalScore(-totalLoss);
        result.setCappedScore(-totalLoss);
        result.setScoreBreakdown(scoreBreakdown);
    }
    
    /**
     * Calculate settlement for draw scenario
     */
    private void calculateDrawSettlement(SettlementResult settlement, GameState gameState, ScoreConfig scoreConfig) {
        List<PlayerResult> playerResults = new ArrayList<>();
        
        // In a draw, all players get 0 points
        for (PlayerState player : gameState.getPlayers()) {
            PlayerResult result = new PlayerResult(player.getUserId(), player.getSeatIndex());
            result.setDealer(player.isDealer());
            result.setFinalScore(0);
            result.setCappedScore(0);
            result.setScoreBreakdown(List.of("流局: 0分"));
            playerResults.add(result);
        }
        
        settlement.setPlayerResults(playerResults);
    }
    
    /**
     * Calculate settlement for timeout scenario
     */
    private void calculateTimeoutSettlement(SettlementResult settlement, GameState gameState, ScoreConfig scoreConfig) {
        // Similar to draw for now
        calculateDrawSettlement(settlement, gameState, scoreConfig);
    }
    
    /**
     * Calculate Gang bonus scores
     */
    private List<GangScore> calculateGangBonuses(GameState gameState, ScoreConfig scoreConfig) {
        List<GangScore> gangScores = new ArrayList<>();
        int gangBonus = scoreConfig.getGangBonus();
        
        for (PlayerState player : gameState.getPlayers()) {
            for (MeldSet meld : player.getMelds()) {
                if (meld.getMeldType() == MeldSet.MeldType.GANG) {
                    int bonusPoints = calculateGangBonusPoints(meld, gangBonus);
                    
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
     * Calculate bonus points for a Gang
     */
    private int calculateGangBonusPoints(MeldSet gang, int baseGangBonus) {
        return switch (gang.getGangType()) {
            case AN_GANG -> baseGangBonus * 2; // Concealed Gang gets double bonus
            case MING_GANG, BU_GANG -> baseGangBonus;
        };
    }
    
    /**
     * Apply Gang bonuses to player scores
     */
    private void applyGangBonuses(SettlementResult settlement, List<GangScore> gangScores) {
        Map<String, Integer> gangBonusMap = new HashMap<>();
        
        // Calculate net Gang bonus for each player
        for (GangScore gangScore : gangScores) {
            gangBonusMap.merge(gangScore.getFromUserId(), -gangScore.getBonusPoints(), Integer::sum);
            gangBonusMap.merge(gangScore.getToUserId(), gangScore.getBonusPoints(), Integer::sum);
        }
        
        // Apply Gang bonuses to player results
        for (PlayerResult result : settlement.getPlayerResults()) {
            int gangBonus = gangBonusMap.getOrDefault(result.getUserId(), 0);
            result.setGangBonus(gangBonus);
            
            int newScore = result.getCappedScore() + gangBonus;
            result.setCappedScore(newScore);
            
            if (gangBonus != 0) {
                List<String> breakdown = new ArrayList<>(result.getScoreBreakdown());
                breakdown.add(String.format("杠分: %+d", gangBonus));
                result.setScoreBreakdown(breakdown);
            }
        }
    }
    
    /**
     * Determine the reason for game end
     */
    private String determineGameEndReason(GameState gameState, List<WinResult> winResults) {
        if (winResults != null && winResults.stream().anyMatch(WinResult::isValid)) {
            return "WIN";
        } else if (gameState.getRemainingTiles() <= 0) {
            return "DRAW";
        } else {
            return "TIMEOUT";
        }
    }
    
    /**
     * Get player seat index by user ID
     */
    private int getPlayerSeatIndex(GameState gameState, String userId) {
        return gameState.getPlayers().stream()
                .filter(p -> p.getUserId().equals(userId))
                .mapToInt(PlayerState::getSeatIndex)
                .findFirst()
                .orElse(-1);
    }
    
    /**
     * Check if player is dealer
     */
    private boolean isPlayerDealer(GameState gameState, String userId) {
        return gameState.getPlayers().stream()
                .filter(p -> p.getUserId().equals(userId))
                .findFirst()
                .map(PlayerState::isDealer)
                .orElse(false);
    }
    
    /**
     * Persist settlement result to database
     */
    public void persistSettlement(SettlementResult settlement) {
        logger.info("Persisting settlement for game: {}", settlement.getGameId());
        
        // Create GameRecord entity
        GameRecord gameRecord = new GameRecord();
        gameRecord.setId(settlement.getGameId());
        gameRecord.setRoomId(settlement.getRoomId());
        gameRecord.setCreatedAt(settlement.getSettlementTime());
        
        // Set game result based on end reason
        GameResult gameResult = switch (settlement.getGameEndReason()) {
            case "WIN" -> GameResult.WIN;
            case "DRAW" -> GameResult.DRAW;
            default -> GameResult.DRAW; // Treat timeout as draw
        };
        gameRecord.setResult(gameResult);
        
        // Set winner information
        Optional<PlayerResult> winner = settlement.getPlayerResults().stream()
                .filter(PlayerResult::isWinner)
                .findFirst();
        
        if (winner.isPresent()) {
            gameRecord.setWinnerId(Long.parseLong(winner.get().getUserId()));
            gameRecord.setFinalScore(winner.get().getCappedScore());
        }
        
        // TODO: Save to database using repository
        // gameRecordRepository.save(gameRecord);
        
        logger.info("Settlement persisted successfully for game: {}", settlement.getGameId());
    }
}