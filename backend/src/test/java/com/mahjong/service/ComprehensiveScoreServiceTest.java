package com.mahjong.service;

import com.mahjong.model.config.RoomConfig;
import com.mahjong.model.config.ScoreConfig;
import com.mahjong.model.dto.*;
import com.mahjong.model.game.GameState;
import com.mahjong.model.game.MeldSet;
import com.mahjong.model.game.PlayerState;
import com.mahjong.model.game.Tile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComprehensiveScoreServiceTest {
    
    @Mock
    private ScoreCalculationService scoreCalculationService;
    
    @Mock
    private WinValidationService winValidationService;
    
    @InjectMocks
    private ComprehensiveScoreService comprehensiveScoreService;
    
    private GameState gameState;
    private RoomConfig roomConfig;
    
    @BeforeEach
    void setUp() {
        roomConfig = createDefaultRoomConfig();
        gameState = createTestGameState();
    }
    
    @Test
    void shouldCalculateComprehensiveSettlementForBasicWin() {
        // Arrange
        WinResult winResult = createEnhancedWinResult("player1", false, 3);
        List<WinResult> winResults = List.of(winResult);
        
        when(scoreCalculationService.analyzeWinningHand(any(), any(), any(), any()))
            .thenReturn(winResult);
        
        // Act
        SettlementResult settlement = comprehensiveScoreService.calculateComprehensiveSettlement(gameState, winResults);
        
        // Assert
        assertEquals("WIN", settlement.getGameEndReason());
        assertFalse(settlement.isMultipleWinners());
        assertEquals(3, settlement.getPlayerResults().size());
        
        // Verify winner gets comprehensive scoring
        PlayerResult winner = settlement.getPlayerResults().stream()
            .filter(PlayerResult::isWinner)
            .findFirst()
            .orElse(null);
        assertNotNull(winner);
        assertEquals("player1", winner.getUserId());
        assertTrue(winner.getFinalScore() > 0);
        assertNotNull(winner.getScoreBreakdown());
        assertFalse(winner.getScoreBreakdown().isEmpty());
        
        // Verify comprehensive score breakdown
        assertTrue(winner.getScoreBreakdown().stream()
            .anyMatch(breakdown -> breakdown.contains("底分")));
        assertTrue(winner.getScoreBreakdown().stream()
            .anyMatch(breakdown -> breakdown.contains("番数")));
    }
    
    @Test
    void shouldHandleMultipleWinnersWithPrioritySelection() {
        // Arrange
        WinResult selfDrawWin = createEnhancedWinResult("player1", true, 2);
        WinResult discardWin = createEnhancedWinResult("player2", false, 3);
        List<WinResult> winResults = Arrays.asList(selfDrawWin, discardWin);
        
        roomConfig.getScore().setMultipleWinners(false);
        
        when(scoreCalculationService.analyzeWinningHand(any(), any(), any(), any()))
            .thenReturn(selfDrawWin, discardWin);
        
        // Act
        SettlementResult settlement = comprehensiveScoreService.calculateComprehensiveSettlement(gameState, winResults);
        
        // Assert
        assertFalse(settlement.isMultipleWinners());
        
        // Self-draw should have priority over higher fan discard win
        PlayerResult winner = settlement.getPlayerResults().stream()
            .filter(PlayerResult::isWinner)
            .findFirst()
            .orElse(null);
        assertNotNull(winner);
        assertEquals("player1", winner.getUserId());
        assertTrue(winner.isSelfDraw());
    }
    
    @Test
    void shouldHandleMultipleWinnersWithScoreAdjustment() {
        // Arrange
        WinResult win1 = createEnhancedWinResult("player1", false, 4);
        WinResult win2 = createEnhancedWinResult("player2", false, 4);
        List<WinResult> winResults = Arrays.asList(win1, win2);
        
        roomConfig.getScore().setMultipleWinners(true);
        
        when(scoreCalculationService.analyzeWinningHand(any(), any(), any(), any()))
            .thenReturn(win1, win2);
        
        // Act
        SettlementResult settlement = comprehensiveScoreService.calculateComprehensiveSettlement(gameState, winResults);
        
        // Assert
        assertTrue(settlement.isMultipleWinners());
        
        // Both should be winners with adjusted scores
        long winnerCount = settlement.getPlayerResults().stream()
            .filter(PlayerResult::isWinner)
            .count();
        assertEquals(2, winnerCount);
        
        // Scores should be adjusted for multiple winners
        List<PlayerResult> winners = settlement.getPlayerResults().stream()
            .filter(PlayerResult::isWinner)
            .toList();
        
        for (PlayerResult winner : winners) {
            assertTrue(winner.getScoreBreakdown().stream()
                .anyMatch(breakdown -> breakdown.contains("多家和牌调整")));
        }
    }
    
    @Test
    void shouldCalculateComprehensiveGangBonuses() {
        // Arrange
        PlayerState player1 = gameState.getPlayers().get(0);
        PlayerState player2 = gameState.getPlayers().get(1);
        
        // Add different types of Gangs
        MeldSet concealedGang = MeldSet.createGang(Arrays.asList(
            new Tile("5W"), new Tile("5W"), new Tile("5W"), new Tile("5W")
        ), MeldSet.GangType.AN_GANG, null);
        player1.addMeld(concealedGang);
        
        MeldSet openGang = MeldSet.createGang(Arrays.asList(
            new Tile("7W"), new Tile("7W"), new Tile("7W"), new Tile("7W")
        ), MeldSet.GangType.MING_GANG, "player3");
        player2.addMeld(openGang);
        
        WinResult winResult = createEnhancedWinResult("player3", false, 2);
        List<WinResult> winResults = List.of(winResult);
        
        when(scoreCalculationService.analyzeWinningHand(any(), any(), any(), any()))
            .thenReturn(winResult);
        
        // Act
        SettlementResult settlement = comprehensiveScoreService.calculateComprehensiveSettlement(gameState, winResults);
        
        // Assert
        assertNotNull(settlement.getGangScores());
        assertFalse(settlement.getGangScores().isEmpty());
        
        // Verify Gang bonus calculations
        List<GangScore> gangScores = settlement.getGangScores();
        
        // Concealed Gang should have higher bonus (4x base)
        int concealedGangBonus = gangScores.stream()
            .filter(gs -> gs.getGangType() == MeldSet.GangType.AN_GANG)
            .mapToInt(GangScore::getBonusPoints)
            .findFirst()
            .orElse(0);
        
        // Open Gang should have lower bonus (2x base)
        int openGangBonus = gangScores.stream()
            .filter(gs -> gs.getGangType() == MeldSet.GangType.MING_GANG)
            .mapToInt(GangScore::getBonusPoints)
            .findFirst()
            .orElse(0);
        
        assertEquals(4, concealedGangBonus); // 4x base Gang bonus (1)
        assertEquals(2, openGangBonus); // 2x base Gang bonus (1)
        
        // Verify Gang bonuses are applied to player results
        PlayerResult player1Result = settlement.getPlayerResults().stream()
            .filter(pr -> pr.getUserId().equals("player1"))
            .findFirst()
            .orElse(null);
        assertNotNull(player1Result);
        assertTrue(player1Result.getGangBonus() > 0);
        
        PlayerResult player2Result = settlement.getPlayerResults().stream()
            .filter(pr -> pr.getUserId().equals("player2"))
            .findFirst()
            .orElse(null);
        assertNotNull(player2Result);
        assertTrue(player2Result.getGangBonus() > 0);
    }
    
    @Test
    void shouldApplyScoreCapsAndAdjustments() {
        // Arrange: Very high scoring hand
        WinResult highScoreWin = createEnhancedWinResult("player1", true, 10);
        List<WinResult> winResults = List.of(highScoreWin);
        
        when(scoreCalculationService.analyzeWinningHand(any(), any(), any(), any()))
            .thenReturn(highScoreWin);
        
        // Act
        SettlementResult settlement = comprehensiveScoreService.calculateComprehensiveSettlement(gameState, winResults);
        
        // Assert
        PlayerResult winner = settlement.getPlayerResults().stream()
            .filter(PlayerResult::isWinner)
            .findFirst()
            .orElse(null);
        assertNotNull(winner);
        
        // Score should be capped at maximum
        assertTrue(winner.getCappedScore() <= roomConfig.getScore().getMaxScore());
        
        // Losers should not exceed maximum loss
        List<PlayerResult> losers = settlement.getPlayerResults().stream()
            .filter(pr -> !pr.isWinner())
            .toList();
        
        for (PlayerResult loser : losers) {
            assertTrue(loser.getCappedScore() >= -roomConfig.getScore().getMaxScore());
        }
    }
    
    @Test
    void shouldBalanceScoresAccurately() {
        // Arrange
        WinResult winResult = createEnhancedWinResult("player1", false, 3);
        List<WinResult> winResults = List.of(winResult);
        
        when(scoreCalculationService.analyzeWinningHand(any(), any(), any(), any()))
            .thenReturn(winResult);
        
        // Act
        SettlementResult settlement = comprehensiveScoreService.calculateComprehensiveSettlement(gameState, winResults);
        
        // Assert
        int totalScore = settlement.getPlayerResults().stream()
            .mapToInt(PlayerResult::getCappedScore)
            .sum();
        
        // Total should be exactly zero or very close (allowing for rounding)
        assertTrue(Math.abs(totalScore) <= 1);
        
        // Final scores should match player results
        for (PlayerResult result : settlement.getPlayerResults()) {
            assertEquals(result.getCappedScore(), 
                settlement.getFinalScores().get(result.getUserId()));
        }
    }
    
    @Test
    void shouldHandleDrawScenario() {
        // Arrange: No valid wins
        List<WinResult> winResults = List.of();
        gameState.setPhase(GameState.GamePhase.FINISHED);
        
        // Act
        SettlementResult settlement = comprehensiveScoreService.calculateComprehensiveSettlement(gameState, winResults);
        
        // Assert
        assertEquals("DRAW", settlement.getGameEndReason());
        assertFalse(settlement.isMultipleWinners());
        
        // All players should have zero score
        assertTrue(settlement.getPlayerResults().stream()
            .allMatch(pr -> pr.getCappedScore() == 0));
        
        // All should have draw indication in breakdown
        assertTrue(settlement.getPlayerResults().stream()
            .allMatch(pr -> pr.getScoreBreakdown().contains("流局")));
    }
    
    @Test
    void shouldCalculateDetailedScoreBreakdown() {
        // Arrange: Complex winning hand
        WinResult complexWin = createEnhancedWinResult("player1", true, 5);
        complexWin.setHandTypes(Arrays.asList("清一色", "碰碰胡", "门清"));
        complexWin.setFanSources(Arrays.asList(
            "基础: 1番",
            "自摸: +1番", 
            "清一色: +8番",
            "碰碰胡: +6番",
            "门清: +2番"
        ));
        
        gameState.getPlayers().get(0).setDealer(true); // Make winner dealer
        List<WinResult> winResults = List.of(complexWin);
        
        when(scoreCalculationService.analyzeWinningHand(any(), any(), any(), any()))
            .thenReturn(complexWin);
        
        // Act
        SettlementResult settlement = comprehensiveScoreService.calculateComprehensiveSettlement(gameState, winResults);
        
        // Assert
        PlayerResult winner = settlement.getPlayerResults().stream()
            .filter(PlayerResult::isWinner)
            .findFirst()
            .orElse(null);
        assertNotNull(winner);
        
        List<String> breakdown = winner.getScoreBreakdown();
        assertNotNull(breakdown);
        assertFalse(breakdown.isEmpty());
        
        // Should contain detailed breakdown
        assertTrue(breakdown.stream().anyMatch(b -> b.contains("底分")));
        assertTrue(breakdown.stream().anyMatch(b -> b.contains("番数")));
        assertTrue(breakdown.stream().anyMatch(b -> b.contains("庄家奖励")));
        assertTrue(breakdown.stream().anyMatch(b -> b.contains("自摸奖励")));
        
        // Should contain fan sources
        assertTrue(breakdown.stream().anyMatch(b -> b.contains("清一色")));
        assertTrue(breakdown.stream().anyMatch(b -> b.contains("碰碰胡")));
        assertTrue(breakdown.stream().anyMatch(b -> b.contains("门清")));
    }
    
    @Test
    void shouldHandleDifferentLossDistribution() {
        // Arrange: Win on specific player's discard
        WinResult winResult = createEnhancedWinResult("player1", false, 3);
        winResult.setWinningFrom("player2"); // Won from player2's discard
        List<WinResult> winResults = List.of(winResult);
        
        when(scoreCalculationService.analyzeWinningHand(any(), any(), any(), any()))
            .thenReturn(winResult);
        
        // Act
        SettlementResult settlement = comprehensiveScoreService.calculateComprehensiveSettlement(gameState, winResults);
        
        // Assert
        PlayerResult winner = settlement.getPlayerResults().stream()
            .filter(PlayerResult::isWinner)
            .findFirst()
            .orElse(null);
        assertNotNull(winner);
        assertFalse(winner.isSelfDraw());
        
        // Player2 (discarding player) should pay more than player3
        PlayerResult player2 = settlement.getPlayerResults().stream()
            .filter(pr -> pr.getUserId().equals("player2"))
            .findFirst()
            .orElse(null);
        PlayerResult player3 = settlement.getPlayerResults().stream()
            .filter(pr -> pr.getUserId().equals("player3"))
            .findFirst()
            .orElse(null);
        
        assertNotNull(player2);
        assertNotNull(player3);
        
        // Discarding player should pay full amount, other player pays reduced
        assertTrue(Math.abs(player2.getCappedScore()) > Math.abs(player3.getCappedScore()));
        
        // Verify score breakdown shows different loss types
        assertTrue(player2.getScoreBreakdown().stream()
            .anyMatch(b -> b.contains("点炮")));
    }
    
    // Helper methods
    private GameState createTestGameState() {
        List<String> playerIds = Arrays.asList("player1", "player2", "player3");
        GameState state = new GameState("room123", "game456", playerIds, roomConfig);
        state.setPhase(GameState.GamePhase.PLAYING);
        return state;
    }
    
    private WinResult createEnhancedWinResult(String userId, boolean isSelfDraw, int baseFan) {
        WinResult result = new WinResult(userId);
        result.setValid(true);
        result.setSelfDraw(isSelfDraw);
        result.setBaseFan(baseFan);
        result.setWinningTile(new Tile("9W"));
        result.setHandTypes(Arrays.asList("基本和"));
        result.setFanSources(Arrays.asList(
            "基础: 1番",
            isSelfDraw ? "自摸: +1番" : "",
            "其他: +" + (baseFan - (isSelfDraw ? 2 : 1)) + "番"
        ));
        result.setWinPattern("基本和牌");
        return result;
    }
    
    private RoomConfig createDefaultRoomConfig() {
        RoomConfig config = new RoomConfig();
        config.setPlayers(3);
        config.setTiles("WAN_ONLY");
        config.setAllowPeng(true);
        config.setAllowGang(true);
        config.setAllowChi(false);
        
        ScoreConfig scoreConfig = new ScoreConfig();
        scoreConfig.setBaseScore(2);
        scoreConfig.setMaxScore(24);
        scoreConfig.setDealerMultiplier(2.0);
        scoreConfig.setSelfDrawBonus(1.0);
        scoreConfig.setGangBonus(1);
        scoreConfig.setMultipleWinners(false);
        config.setScore(scoreConfig);
        
        return config;
    }
}