package com.mahjong.service;

import com.mahjong.model.config.HuTypes;
import com.mahjong.model.config.RoomConfig;
import com.mahjong.model.config.ScoreConfig;
import com.mahjong.model.dto.SettlementResult;
import com.mahjong.model.dto.WinResult;
import com.mahjong.model.game.GameState;
import com.mahjong.model.game.MeldSet;
import com.mahjong.model.game.PlayerState;
import com.mahjong.model.game.Tile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class ScoringSystemIntegrationTest {
    
    @Autowired
    private ScoreCalculationService scoreCalculationService;
    
    @Autowired
    private SettlementService settlementService;
    
    @Autowired
    private ComprehensiveScoreService comprehensiveScoreService;
    
    private RoomConfig roomConfig;
    private GameState gameState;
    
    @BeforeEach
    void setUp() {
        roomConfig = createDefaultRoomConfig();
        gameState = createTestGameState();
    }
    
    @Test
    void shouldCalculateCompleteSevenPairsSettlement() {
        // Arrange: Create a Seven Pairs winning hand
        PlayerState winner = gameState.getPlayers().get(0);
        List<Tile> sevenPairsHand = Arrays.asList(
            new Tile("1W"), new Tile("1W"),
            new Tile("2W"), new Tile("2W"),
            new Tile("3W"), new Tile("3W"),
            new Tile("4W"), new Tile("4W"),
            new Tile("5W"), new Tile("5W"),
            new Tile("6W"), new Tile("6W"),
            new Tile("7W")
        );
        winner.addTiles(sevenPairsHand);
        Tile winningTile = new Tile("7W");
        
        // Act: Analyze the winning hand
        WinResult winResult = scoreCalculationService.analyzeWinningHand(winner, winningTile, true, roomConfig);
        
        // Assert: Verify Seven Pairs is detected
        assertTrue(winResult.isValid());
        assertTrue(winResult.isSelfDraw());
        assertEquals(5, winResult.getBaseFan()); // Base + Seven Pairs + Self-draw
        assertTrue(winResult.getHandTypes().contains("七对"));
        assertTrue(winResult.getFanSources().contains("七对: +4番"));
        
        // Act: Calculate settlement
        List<WinResult> winResults = List.of(winResult);
        SettlementResult settlement = settlementService.calculateSettlement(gameState, winResults);
        
        // Assert: Verify settlement
        assertEquals("WIN", settlement.getGameEndReason());
        assertFalse(settlement.isMultipleWinners());
        assertEquals(3, settlement.getPlayerResults().size());
        
        // Verify winner gets positive score
        assertTrue(settlement.getPlayerResults().stream()
            .filter(pr -> pr.isWinner())
            .findFirst()
            .map(pr -> pr.getCappedScore() > 0)
            .orElse(false));
        
        // Verify losers get negative scores
        assertTrue(settlement.getPlayerResults().stream()
            .filter(pr -> !pr.isWinner())
            .allMatch(pr -> pr.getCappedScore() < 0));
        
        // Verify scores balance
        int totalScore = settlement.getPlayerResults().stream()
            .mapToInt(pr -> pr.getCappedScore())
            .sum();
        assertEquals(0, totalScore);
    }
    
    @Test
    void shouldCalculateGangBonusesCorrectly() {
        // Arrange: Add Gang to a player
        PlayerState playerWithGang = gameState.getPlayers().get(1);
        MeldSet concealedGang = MeldSet.createGang(Arrays.asList(
            new Tile("5W"), new Tile("5W"), new Tile("5W"), new Tile("5W")
        ), MeldSet.GangType.AN_GANG, null);
        playerWithGang.addMeld(concealedGang);
        
        // Create a basic win for another player
        PlayerState winner = gameState.getPlayers().get(0);
        List<Tile> basicHand = Arrays.asList(
            new Tile("1W"), new Tile("1W"),
            new Tile("2W"), new Tile("3W"), new Tile("4W"),
            new Tile("5W"), new Tile("6W"), new Tile("7W"),
            new Tile("8W"), new Tile("8W"), new Tile("8W")
        );
        winner.addTiles(basicHand);
        Tile winningTile = new Tile("9W");
        
        WinResult winResult = new WinResult(winner.getUserId());
        winResult.setValid(true);
        winResult.setSelfDraw(false);
        winResult.setBaseFan(1);
        winResult.setWinningTile(winningTile);
        
        // Act: Calculate settlement
        List<WinResult> winResults = List.of(winResult);
        SettlementResult settlement = settlementService.calculateSettlement(gameState, winResults);
        
        // Assert: Verify Gang bonuses are calculated
        assertNotNull(settlement.getGangScores());
        assertFalse(settlement.getGangScores().isEmpty());
        
        // Player with Gang should receive bonus points
        assertTrue(settlement.getPlayerResults().stream()
            .filter(pr -> pr.getUserId().equals(playerWithGang.getUserId()))
            .findFirst()
            .map(pr -> pr.getGangBonus() > 0)
            .orElse(false));
        
        // Other players should pay Gang bonuses
        assertTrue(settlement.getPlayerResults().stream()
            .filter(pr -> !pr.getUserId().equals(playerWithGang.getUserId()))
            .allMatch(pr -> pr.getGangBonus() <= 0));
    }
    
    @Test
    void shouldHandleComprehensiveScoring() {
        // Arrange: Create a complex winning hand
        PlayerState winner = gameState.getPlayers().get(0);
        winner.setDealer(true); // Make winner the dealer
        
        List<Tile> allSameSuitHand = Arrays.asList(
            new Tile("1W"), new Tile("1W"),
            new Tile("2W"), new Tile("3W"), new Tile("4W"),
            new Tile("5W"), new Tile("6W"), new Tile("7W"),
            new Tile("8W"), new Tile("8W"), new Tile("8W")
        );
        winner.addTiles(allSameSuitHand);
        Tile winningTile = new Tile("9W");
        
        WinResult winResult = new WinResult(winner.getUserId());
        winResult.setValid(true);
        winResult.setSelfDraw(true);
        winResult.setBaseFan(10); // High fan for all same suit
        winResult.setWinningTile(winningTile);
        winResult.setHandTypes(Arrays.asList("清一色", "门清"));
        
        // Act: Use comprehensive scoring service
        List<WinResult> winResults = List.of(winResult);
        SettlementResult settlement = comprehensiveScoreService.calculateComprehensiveSettlement(gameState, winResults);
        
        // Assert: Verify comprehensive scoring
        assertEquals("WIN", settlement.getGameEndReason());
        
        // Winner should get substantial score with dealer and self-draw bonuses
        assertTrue(settlement.getPlayerResults().stream()
            .filter(pr -> pr.isWinner())
            .findFirst()
            .map(pr -> {
                assertTrue(pr.isDealer());
                assertTrue(pr.isSelfDraw());
                assertTrue(pr.getDealerMultiplier() > 1.0);
                assertTrue(pr.getSelfDrawBonus() > 0);
                return pr.getCappedScore() > 10; // Should be substantial
            })
            .orElse(false));
        
        // Score should be capped at maximum
        assertTrue(settlement.getPlayerResults().stream()
            .filter(pr -> pr.isWinner())
            .findFirst()
            .map(pr -> pr.getCappedScore() <= roomConfig.getScore().getMaxScore())
            .orElse(false));
    }
    
    // Helper methods
    private GameState createTestGameState() {
        List<String> playerIds = Arrays.asList("player1", "player2", "player3");
        GameState state = new GameState("room123", "game456", playerIds, roomConfig);
        state.setPhase(GameState.GamePhase.PLAYING);
        return state;
    }
    
    private RoomConfig createDefaultRoomConfig() {
        RoomConfig config = new RoomConfig();
        config.setPlayers(3);
        config.setTiles("WAN_ONLY");
        config.setAllowPeng(true);
        config.setAllowGang(true);
        config.setAllowChi(false);
        
        // Set up HuTypes
        HuTypes huTypes = new HuTypes();
        huTypes.setSelfDraw(true);
        huTypes.setSevenPairs(true);
        huTypes.setAllPungs(true);
        huTypes.setEdgeWait(true);
        huTypes.setPairWait(true);
        config.setHuTypes(huTypes);
        
        // Set up ScoreConfig
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