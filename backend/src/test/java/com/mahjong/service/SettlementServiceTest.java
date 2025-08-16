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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettlementServiceTest {
    
    @Mock
    private WinValidationService winValidationService;
    
    @InjectMocks
    private SettlementService settlementService;
    
    private GameState gameState;
    private RoomConfig roomConfig;
    
    @BeforeEach
    void setUp() {
        roomConfig = createDefaultRoomConfig();
        gameState = createTestGameState();
    }
    
    @Test
    void shouldCalculateBasicWinSettlement() {
        // Arrange
        WinResult winResult = createBasicWinResult("player1", false, 2);
        List<WinResult> winResults = List.of(winResult);
        
        // Act
        SettlementResult settlement = settlementService.calculateSettlement(gameState, winResults);
        
        // Assert
        assertEquals("WIN", settlement.getGameEndReason());
        assertFalse(settlement.isMultipleWinners());
        assertEquals(3, settlement.getPlayerResults().size());
        
        // Check winner
        PlayerResult winner = settlement.getPlayerResults().stream()
            .filter(PlayerResult::isWinner)
            .findFirst()
            .orElse(null);
        assertNotNull(winner);
        assertEquals("player1", winner.getUserId());
        assertTrue(winner.getFinalScore() > 0);
        
        // Check losers
        List<PlayerResult> losers = settlement.getPlayerResults().stream()
            .filter(pr -> !pr.isWinner())
            .toList();
        assertEquals(2, losers.size());
        assertTrue(losers.stream().allMatch(pr -> pr.getFinalScore() < 0));
        
        // Check score balance
        int totalScore = settlement.getPlayerResults().stream()
            .mapToInt(PlayerResult::getCappedScore)
            .sum();
        assertEquals(0, totalScore); // Should balance to zero
    }
    
    @Test
    void shouldCalculateSelfDrawWinSettlement() {
        // Arrange
        WinResult winResult = createBasicWinResult("player1", true, 3);
        List<WinResult> winResults = List.of(winResult);
        
        // Act
        SettlementResult settlement = settlementService.calculateSettlement(gameState, winResults);
        
        // Assert
        assertEquals("WIN", settlement.getGameEndReason());
        
        PlayerResult winner = settlement.getPlayerResults().stream()
            .filter(PlayerResult::isWinner)
            .findFirst()
            .orElse(null);
        assertNotNull(winner);
        assertTrue(winner.isSelfDraw());
        assertTrue(winner.getSelfDrawBonus() > 0);
        
        // In self-draw, all losers should pay equally
        List<PlayerResult> losers = settlement.getPlayerResults().stream()
            .filter(pr -> !pr.isWinner())
            .toList();
        assertEquals(2, losers.size());
        
        int firstLoserLoss = Math.abs(losers.get(0).getCappedScore());
        int secondLoserLoss = Math.abs(losers.get(1).getCappedScore());
        assertEquals(firstLoserLoss, secondLoserLoss); // Should be equal
    }
    
    @Test
    void shouldCalculateDealerWinSettlement() {
        // Arrange
        gameState.getPlayers().get(0).setDealer(true); // Make player1 dealer
        WinResult winResult = createBasicWinResult("player1", false, 2);
        List<WinResult> winResults = List.of(winResult);
        
        // Act
        SettlementResult settlement = settlementService.calculateSettlement(gameState, winResults);
        
        // Assert
        PlayerResult winner = settlement.getPlayerResults().stream()
            .filter(PlayerResult::isWinner)
            .findFirst()
            .orElse(null);
        assertNotNull(winner);
        assertTrue(winner.isDealer());
        assertTrue(winner.getDealerMultiplier() > 1.0);
        
        // Dealer should get bonus points
        int expectedMinScore = roomConfig.getScore().getBaseScore() * 2 * 2; // base * fan * dealer
        assertTrue(winner.getFinalScore() >= expectedMinScore);
    }
    
    @Test
    void shouldHandleMultipleWinnersWhenDisabled() {
        // Arrange
        roomConfig.getScore().setMultipleWinners(false);
        WinResult win1 = createBasicWinResult("player1", false, 2);
        WinResult win2 = createBasicWinResult("player2", false, 3);
        List<WinResult> winResults = Arrays.asList(win1, win2);
        
        // Act
        SettlementResult settlement = settlementService.calculateSettlement(gameState, winResults);
        
        // Assert
        assertFalse(settlement.isMultipleWinners());
        
        // Only one winner should be selected
        long winnerCount = settlement.getPlayerResults().stream()
            .filter(PlayerResult::isWinner)
            .count();
        assertEquals(1, winnerCount);
        
        // Higher fan should win (player2 with 3 fan vs player1 with 2 fan)
        PlayerResult winner = settlement.getPlayerResults().stream()
            .filter(PlayerResult::isWinner)
            .findFirst()
            .orElse(null);
        assertNotNull(winner);
        assertEquals("player2", winner.getUserId());
    }
    
    @Test
    void shouldHandleMultipleWinnersWhenEnabled() {
        // Arrange
        roomConfig.getScore().setMultipleWinners(true);
        WinResult win1 = createBasicWinResult("player1", false, 2);
        WinResult win2 = createBasicWinResult("player2", false, 2);
        List<WinResult> winResults = Arrays.asList(win1, win2);
        
        // Act
        SettlementResult settlement = settlementService.calculateSettlement(gameState, winResults);
        
        // Assert
        assertTrue(settlement.isMultipleWinners());
        
        // Both should be winners
        long winnerCount = settlement.getPlayerResults().stream()
            .filter(PlayerResult::isWinner)
            .count();
        assertEquals(2, winnerCount);
        
        // Only one loser
        long loserCount = settlement.getPlayerResults().stream()
            .filter(pr -> !pr.isWinner())
            .count();
        assertEquals(1, loserCount);
    }
    
    @Test
    void shouldCalculateGangBonuses() {
        // Arrange
        PlayerState player1 = gameState.getPlayers().get(0);
        
        // Add concealed Gang
        MeldSet concealedGang = MeldSet.createGang(Arrays.asList(
            new Tile("5W"), new Tile("5W"), new Tile("5W"), new Tile("5W")
        ), MeldSet.GangType.AN_GANG, null);
        player1.addMeld(concealedGang);
        
        // Add open Gang
        MeldSet openGang = MeldSet.createGang(Arrays.asList(
            new Tile("7W"), new Tile("7W"), new Tile("7W"), new Tile("7W")
        ), MeldSet.GangType.MING_GANG, "player2");
        player1.addMeld(openGang);
        
        WinResult winResult = createBasicWinResult("player2", false, 2);
        List<WinResult> winResults = List.of(winResult);
        
        // Act
        SettlementResult settlement = settlementService.calculateSettlement(gameState, winResults);
        
        // Assert
        assertNotNull(settlement.getGangScores());
        assertFalse(settlement.getGangScores().isEmpty());
        
        // Player1 should receive Gang bonuses from other players
        PlayerResult player1Result = settlement.getPlayerResults().stream()
            .filter(pr -> pr.getUserId().equals("player1"))
            .findFirst()
            .orElse(null);
        assertNotNull(player1Result);
        assertTrue(player1Result.getGangBonus() > 0);
        
        // Other players should pay Gang bonuses
        List<PlayerResult> otherPlayers = settlement.getPlayerResults().stream()
            .filter(pr -> !pr.getUserId().equals("player1"))
            .toList();
        assertTrue(otherPlayers.stream().allMatch(pr -> pr.getGangBonus() <= 0));
    }
    
    @Test
    void shouldApplyScoreCap() {
        // Arrange: High fan win that should be capped
        WinResult winResult = createBasicWinResult("player1", false, 10); // High fan
        List<WinResult> winResults = List.of(winResult);
        
        // Act
        SettlementResult settlement = settlementService.calculateSettlement(gameState, winResults);
        
        // Assert
        PlayerResult winner = settlement.getPlayerResults().stream()
            .filter(PlayerResult::isWinner)
            .findFirst()
            .orElse(null);
        assertNotNull(winner);
        
        // Score should be capped at max score
        assertTrue(winner.getCappedScore() <= roomConfig.getScore().getMaxScore());
        
        // Should have score breakdown showing cap
        assertTrue(winner.getScoreBreakdown().stream()
            .anyMatch(breakdown -> breakdown.contains("封顶")));
    }
    
    @Test
    void shouldCalculateDrawSettlement() {
        // Arrange: No winners (draw scenario)
        gameState.setPhase(GameState.GamePhase.FINISHED);
        List<WinResult> winResults = List.of(); // No winners
        
        // Act
        SettlementResult settlement = settlementService.calculateSettlement(gameState, winResults);
        
        // Assert
        assertEquals("DRAW", settlement.getGameEndReason());
        assertFalse(settlement.isMultipleWinners());
        
        // All players should have 0 score
        assertTrue(settlement.getPlayerResults().stream()
            .allMatch(pr -> pr.getCappedScore() == 0));
        
        // All should have draw reason in breakdown
        assertTrue(settlement.getPlayerResults().stream()
            .allMatch(pr -> pr.getScoreBreakdown().contains("流局: 0分")));
    }
    
    @Test
    void shouldCalculateWinOnDiscardSettlement() {
        // Arrange: Win on specific player's discard
        WinResult winResult = createBasicWinResult("player1", false, 2);
        winResult.setWinningFrom("player2"); // Won from player2's discard
        List<WinResult> winResults = List.of(winResult);
        
        // Act
        SettlementResult settlement = settlementService.calculateSettlement(gameState, winResults);
        
        // Assert
        PlayerResult winner = settlement.getPlayerResults().stream()
            .filter(PlayerResult::isWinner)
            .findFirst()
            .orElse(null);
        assertNotNull(winner);
        assertFalse(winner.isSelfDraw());
        
        // Player2 (who discarded) should pay more than player3
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
        assertTrue(Math.abs(player2.getCappedScore()) > Math.abs(player3.getCappedScore()));
    }
    
    @Test
    void shouldBalanceScores() {
        // Arrange
        WinResult winResult = createBasicWinResult("player1", false, 2);
        List<WinResult> winResults = List.of(winResult);
        
        // Act
        SettlementResult settlement = settlementService.calculateSettlement(gameState, winResults);
        
        // Assert: Total scores should balance to zero (or very close)
        int totalScore = settlement.getPlayerResults().stream()
            .mapToInt(PlayerResult::getCappedScore)
            .sum();
        assertTrue(Math.abs(totalScore) <= 1); // Allow small rounding differences
        
        // Final scores map should be populated
        assertNotNull(settlement.getFinalScores());
        assertEquals(3, settlement.getFinalScores().size());
        
        // Final scores should match player results
        for (PlayerResult result : settlement.getPlayerResults()) {
            assertEquals(result.getCappedScore(), 
                settlement.getFinalScores().get(result.getUserId()));
        }
    }
    
    @Test
    void shouldHandleComplexGangScoring() {
        // Arrange: Multiple players with different Gang types
        PlayerState player1 = gameState.getPlayers().get(0);
        PlayerState player2 = gameState.getPlayers().get(1);
        
        // Player1 has concealed Gang (highest bonus)
        MeldSet concealedGang = MeldSet.createGang(Arrays.asList(
            new Tile("5W"), new Tile("5W"), new Tile("5W"), new Tile("5W")
        ), MeldSet.GangType.AN_GANG, null);
        player1.addMeld(concealedGang);
        
        // Player2 has open Gang (lower bonus)
        MeldSet openGang = MeldSet.createGang(Arrays.asList(
            new Tile("7W"), new Tile("7W"), new Tile("7W"), new Tile("7W")
        ), MeldSet.GangType.MING_GANG, "player3");
        player2.addMeld(openGang);
        
        WinResult winResult = createBasicWinResult("player3", false, 2);
        List<WinResult> winResults = List.of(winResult);
        
        // Act
        SettlementResult settlement = settlementService.calculateSettlement(gameState, winResults);
        
        // Assert
        List<GangScore> gangScores = settlement.getGangScores();
        assertNotNull(gangScores);
        assertFalse(gangScores.isEmpty());
        
        // Should have Gang scores for both players
        assertTrue(gangScores.stream().anyMatch(gs -> gs.getToUserId().equals("player1")));
        assertTrue(gangScores.stream().anyMatch(gs -> gs.getToUserId().equals("player2")));
        
        // Concealed Gang should have higher bonus than open Gang
        int concealedGangBonus = gangScores.stream()
            .filter(gs -> gs.getToUserId().equals("player1"))
            .mapToInt(GangScore::getBonusPoints)
            .findFirst()
            .orElse(0);
        
        int openGangBonus = gangScores.stream()
            .filter(gs -> gs.getToUserId().equals("player2"))
            .mapToInt(GangScore::getBonusPoints)
            .findFirst()
            .orElse(0);
        
        assertTrue(concealedGangBonus > openGangBonus);
    }
    
    // Helper methods
    private GameState createTestGameState() {
        List<String> playerIds = Arrays.asList("player1", "player2", "player3");
        GameState state = new GameState("room123", "game456", playerIds, roomConfig);
        state.setPhase(GameState.GamePhase.PLAYING);
        return state;
    }
    
    private WinResult createBasicWinResult(String userId, boolean isSelfDraw, int baseFan) {
        WinResult result = new WinResult(userId);
        result.setValid(true);
        result.setSelfDraw(isSelfDraw);
        result.setBaseFan(baseFan);
        result.setWinningTile(new Tile("9W"));
        result.setHandTypes(Arrays.asList("基本和"));
        result.setFanSources(Arrays.asList("基础: 1番", "其他: +" + (baseFan - 1) + "番"));
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