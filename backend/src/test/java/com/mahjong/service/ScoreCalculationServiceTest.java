package com.mahjong.service;

import com.mahjong.model.config.HuTypes;
import com.mahjong.model.config.RoomConfig;
import com.mahjong.model.config.ScoreConfig;
import com.mahjong.model.dto.WinResult;
import com.mahjong.model.game.MeldSet;
import com.mahjong.model.game.PlayerState;
import com.mahjong.model.game.Tile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ScoreCalculationServiceTest {
    
    @InjectMocks
    private ScoreCalculationService scoreCalculationService;
    
    private RoomConfig roomConfig;
    private PlayerState player;
    
    @BeforeEach
    void setUp() {
        roomConfig = createDefaultRoomConfig();
        player = new PlayerState("player1", 0);
    }
    
    @Test
    void shouldCalculateBasicWinningHand() {
        // Arrange: Basic winning hand (4 sets + 1 pair)
        List<Tile> handTiles = Arrays.asList(
            new Tile("1W"), new Tile("1W"), // Pair
            new Tile("2W"), new Tile("3W"), new Tile("4W"), // Sequence
            new Tile("5W"), new Tile("6W"), new Tile("7W"), // Sequence
            new Tile("8W"), new Tile("8W"), new Tile("8W") // Triplet
        );
        player.addTiles(handTiles);
        Tile winningTile = new Tile("9W");
        
        // Act
        WinResult result = scoreCalculationService.analyzeWinningHand(player, winningTile, false, roomConfig);
        
        // Assert
        assertTrue(result.isValid());
        assertEquals(1, result.getBaseFan()); // Base fan only
        assertFalse(result.isSelfDraw());
        assertEquals("player1", result.getUserId());
    }
    
    @Test
    void shouldCalculateSelfDrawBonus() {
        // Arrange: Self-draw winning hand
        List<Tile> handTiles = createBasicWinningHand();
        player.addTiles(handTiles);
        Tile winningTile = new Tile("9W");
        
        // Act
        WinResult result = scoreCalculationService.analyzeWinningHand(player, winningTile, true, roomConfig);
        
        // Assert
        assertTrue(result.isValid());
        assertEquals(2, result.getBaseFan()); // Base + self-draw
        assertTrue(result.isSelfDraw());
        assertTrue(result.getFanSources().contains("自摸: +1番"));
    }
    
    @Test
    void shouldCalculateSevenPairs() {
        // Arrange: Seven pairs hand
        List<Tile> handTiles = Arrays.asList(
            new Tile("1W"), new Tile("1W"),
            new Tile("2W"), new Tile("2W"),
            new Tile("3W"), new Tile("3W"),
            new Tile("4W"), new Tile("4W"),
            new Tile("5W"), new Tile("5W"),
            new Tile("6W"), new Tile("6W"),
            new Tile("7W")
        );
        player.addTiles(handTiles);
        Tile winningTile = new Tile("7W");
        
        // Act
        WinResult result = scoreCalculationService.analyzeWinningHand(player, winningTile, false, roomConfig);
        
        // Assert
        assertTrue(result.isValid());
        assertEquals(5, result.getBaseFan()); // Base + seven pairs (4 fan)
        assertTrue(result.getHandTypes().contains("七对"));
        assertTrue(result.getFanSources().contains("七对: +4番"));
    }
    
    @Test
    void shouldCalculateAllPungs() {
        // Arrange: All pungs hand
        List<Tile> handTiles = Arrays.asList(
            new Tile("1W"), new Tile("1W"), // Pair
            new Tile("2W"), new Tile("2W"), new Tile("2W"), // Triplet
            new Tile("3W"), new Tile("3W"), new Tile("3W"), // Triplet
            new Tile("4W"), new Tile("4W"), new Tile("4W") // Triplet
        );
        player.addTiles(handTiles);
        
        // Add one meld (Peng)
        MeldSet peng = MeldSet.createPeng(Arrays.asList(
            new Tile("5W"), new Tile("5W"), new Tile("5W")
        ), "player2");
        player.addMeld(peng);
        
        Tile winningTile = new Tile("6W");
        
        // Act
        WinResult result = scoreCalculationService.analyzeWinningHand(player, winningTile, false, roomConfig);
        
        // Assert
        assertTrue(result.isValid());
        assertEquals(7, result.getBaseFan()); // Base + all pungs (6 fan)
        assertTrue(result.getHandTypes().contains("碰碰胡"));
        assertTrue(result.getFanSources().contains("碰碰胡: +6番"));
    }
    
    @Test
    void shouldCalculateAllSameSuit() {
        // Arrange: All same suit (清一色)
        List<Tile> handTiles = Arrays.asList(
            new Tile("1W"), new Tile("1W"), // Pair
            new Tile("2W"), new Tile("3W"), new Tile("4W"), // Sequence
            new Tile("5W"), new Tile("6W"), new Tile("7W"), // Sequence
            new Tile("8W"), new Tile("8W"), new Tile("8W") // Triplet
        );
        player.addTiles(handTiles);
        Tile winningTile = new Tile("9W");
        
        // Act
        WinResult result = scoreCalculationService.analyzeWinningHand(player, winningTile, false, roomConfig);
        
        // Assert
        assertTrue(result.isValid());
        assertEquals(9, result.getBaseFan()); // Base + pure suit (8 fan)
        assertTrue(result.getHandTypes().contains("清一色"));
        assertTrue(result.getFanSources().contains("清一色: +8番"));
    }
    
    @Test
    void shouldCalculateConcealedHandBonus() {
        // Arrange: All concealed hand
        List<Tile> handTiles = createBasicWinningHand();
        player.addTiles(handTiles);
        
        // Add concealed Gang
        MeldSet concealedGang = MeldSet.createGang(Arrays.asList(
            new Tile("5W"), new Tile("5W"), new Tile("5W"), new Tile("5W")
        ), MeldSet.GangType.AN_GANG, null);
        player.addMeld(concealedGang);
        
        Tile winningTile = new Tile("9W");
        
        // Act
        WinResult result = scoreCalculationService.analyzeWinningHand(player, winningTile, true, roomConfig);
        
        // Assert
        assertTrue(result.isValid());
        assertEquals(5, result.getBaseFan()); // Base + self-draw + concealed
        assertTrue(result.getHandTypes().contains("门清"));
        assertTrue(result.getFanSources().contains("门清: +2番"));
    }
    
    @Test
    void shouldCalculateAllTerminals() {
        // Arrange: All terminals hand (全幺九)
        List<Tile> handTiles = Arrays.asList(
            new Tile("1W"), new Tile("1W"), // Pair
            new Tile("9W"), new Tile("9W"), new Tile("9W"), // Triplet
            new Tile("1T"), new Tile("1T"), new Tile("1T"), // Triplet
            new Tile("9T"), new Tile("9T"), new Tile("9T") // Triplet
        );
        player.addTiles(handTiles);
        Tile winningTile = new Tile("1D");
        
        // Act
        WinResult result = scoreCalculationService.analyzeWinningHand(player, winningTile, false, roomConfig);
        
        // Assert
        assertTrue(result.isValid());
        assertEquals(11, result.getBaseFan()); // Base + all terminals (10 fan)
        assertTrue(result.getHandTypes().contains("全幺九"));
        assertTrue(result.getFanSources().contains("全幺九: +10番"));
    }
    
    @Test
    void shouldCalculateFourConcealedPungs() {
        // Arrange: Four concealed pungs (四暗刻)
        List<Tile> handTiles = Arrays.asList(
            new Tile("1W"), new Tile("1W"), // Pair
            new Tile("2W"), new Tile("2W"), new Tile("2W"), // Concealed triplet
            new Tile("3W"), new Tile("3W"), new Tile("3W"), // Concealed triplet
            new Tile("4W"), new Tile("4W"), new Tile("4W"), // Concealed triplet
            new Tile("5W"), new Tile("5W"), new Tile("5W") // Concealed triplet
        );
        player.addTiles(handTiles);
        Tile winningTile = new Tile("6W");
        
        // Act
        WinResult result = scoreCalculationService.analyzeWinningHand(player, winningTile, true, roomConfig);
        
        // Assert
        assertTrue(result.isValid());
        assertEquals(13, result.getBaseFan()); // Capped at 13 (yakuman level)
        assertTrue(result.getHandTypes().contains("四暗刻"));
        assertTrue(result.getFanSources().contains("四暗刻: +13番"));
    }
    
    @Test
    void shouldApplyScoreCap() {
        // Arrange: Hand that would exceed maximum fan
        List<Tile> handTiles = Arrays.asList(
            new Tile("1W"), new Tile("1W"), // Pair
            new Tile("9W"), new Tile("9W"), new Tile("9W"), // All terminals
            new Tile("1T"), new Tile("1T"), new Tile("1T"), // All same suit would conflict
            new Tile("9T"), new Tile("9T"), new Tile("9T")
        );
        player.addTiles(handTiles);
        Tile winningTile = new Tile("1D");
        
        // Act
        WinResult result = scoreCalculationService.analyzeWinningHand(player, winningTile, true, roomConfig);
        
        // Assert
        assertTrue(result.isValid());
        assertTrue(result.getBaseFan() <= 13); // Should be capped at 13
    }
    
    @Test
    void shouldCalculateFinalScoreWithMultipliers() {
        // Arrange
        int baseScore = 2;
        int fanMultiplier = 4;
        boolean isDealer = true;
        boolean isSelfDraw = true;
        ScoreConfig scoreConfig = roomConfig.getScore();
        
        // Act
        int finalScore = scoreCalculationService.calculateFinalScore(
            baseScore, fanMultiplier, isDealer, isSelfDraw, scoreConfig);
        
        // Assert
        // Expected: 2 * 4 * 2.0 (dealer) * 2.0 (self-draw) = 32, capped at 24
        assertEquals(24, finalScore); // Should be capped at max score
    }
    
    @Test
    void shouldValidateInvalidWinningHand() {
        // Arrange: Invalid hand (not enough tiles)
        List<Tile> handTiles = Arrays.asList(
            new Tile("1W"), new Tile("2W"), new Tile("3W")
        );
        player.addTiles(handTiles);
        Tile winningTile = new Tile("4W");
        
        // Act
        WinResult result = scoreCalculationService.analyzeWinningHand(player, winningTile, false, roomConfig);
        
        // Assert
        assertFalse(result.isValid());
        assertEquals("player1", result.getUserId());
    }
    
    @Test
    void shouldCalculateNoHonorsBonus() {
        // Arrange: Hand with no terminals (断幺九)
        List<Tile> handTiles = Arrays.asList(
            new Tile("2W"), new Tile("2W"), // Pair
            new Tile("3W"), new Tile("4W"), new Tile("5W"), // Sequence
            new Tile("6W"), new Tile("7W"), new Tile("8W"), // Sequence
            new Tile("4T"), new Tile("4T"), new Tile("4T") // Triplet
        );
        player.addTiles(handTiles);
        Tile winningTile = new Tile("5T");
        
        // Act
        WinResult result = scoreCalculationService.analyzeWinningHand(player, winningTile, false, roomConfig);
        
        // Assert
        assertTrue(result.isValid());
        assertEquals(2, result.getBaseFan()); // Base + no terminals
        assertTrue(result.getHandTypes().contains("断幺九"));
        assertTrue(result.getFanSources().contains("断幺九: +1番"));
    }
    
    // Helper methods
    private List<Tile> createBasicWinningHand() {
        return Arrays.asList(
            new Tile("1W"), new Tile("1W"), // Pair
            new Tile("2W"), new Tile("3W"), new Tile("4W"), // Sequence
            new Tile("5W"), new Tile("6W"), new Tile("7W"), // Sequence
            new Tile("8W"), new Tile("8W"), new Tile("8W") // Triplet
        );
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