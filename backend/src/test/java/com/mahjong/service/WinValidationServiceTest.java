package com.mahjong.service;

import com.mahjong.model.config.HuTypes;
import com.mahjong.model.config.RoomConfig;
import com.mahjong.model.game.MeldSet;
import com.mahjong.model.game.PlayerState;
import com.mahjong.model.game.Tile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for WinValidationService
 * Tests all winning patterns and edge cases
 */
@ExtendWith(MockitoExtension.class)
class WinValidationServiceTest {

    @InjectMocks
    private WinValidationService winValidationService;

    private RoomConfig roomConfig;
    private HuTypes huTypes;

    @BeforeEach
    void setUp() {
        roomConfig = new RoomConfig();
        huTypes = new HuTypes();
        huTypes.setBasicWin(true);
        huTypes.setSevenPairs(true);
        huTypes.setAllPungs(true);
        huTypes.setAllHonors(false);
        huTypes.setEdgeWait(true);
        huTypes.setPairWait(true);
        roomConfig.setHuTypes(huTypes);
    }

    @Test
    @DisplayName("Should validate basic winning hand - 4 sets + 1 pair")
    void shouldValidateBasicWinningHand() {
        // Create basic winning hand: 111W 222W 333W 444W 55W
        List<Tile> handTiles = Arrays.asList(
            new Tile("1W"), new Tile("1W"), new Tile("1W"),
            new Tile("2W"), new Tile("2W"), new Tile("2W"),
            new Tile("3W"), new Tile("3W"), new Tile("3W"),
            new Tile("4W"), new Tile("4W"), new Tile("4W"),
            new Tile("5W"), new Tile("5W")
        );

        PlayerState player = createPlayerWithTiles(handTiles);
        Tile winningTile = new Tile("5W");

        WinValidationResult result = winValidationService.validateWin(player, winningTile, roomConfig);

        assertTrue(result.isValid());
        assertEquals("Basic Win", result.getWinType());
        assertEquals(1, result.getBaseFan());
        assertTrue(result.getWinningCombinations().size() > 0);
    }

    @Test
    @DisplayName("Should validate seven pairs winning hand")
    void shouldValidateSevenPairs() {
        // Create seven pairs: 11W 22W 33W 44W 55W 66W 77W
        List<Tile> handTiles = Arrays.asList(
            new Tile("1W"), new Tile("1W"),
            new Tile("2W"), new Tile("2W"),
            new Tile("3W"), new Tile("3W"),
            new Tile("4W"), new Tile("4W"),
            new Tile("5W"), new Tile("5W"),
            new Tile("6W"), new Tile("6W"),
            new Tile("7W"), new Tile("7W")
        );

        PlayerState player = createPlayerWithTiles(handTiles);
        Tile winningTile = new Tile("7W");

        WinValidationResult result = winValidationService.validateWin(player, winningTile, roomConfig);

        assertTrue(result.isValid());
        assertEquals("Seven Pairs", result.getWinType());
        assertEquals(2, result.getBaseFan());
    }

    @Test
    @DisplayName("Should validate all pungs winning hand")
    void shouldValidateAllPungs() {
        // Create all pungs: 111W 222W 333W 444W 55W
        List<Tile> handTiles = Arrays.asList(
            new Tile("1W"), new Tile("1W"), new Tile("1W"),
            new Tile("2W"), new Tile("2W"), new Tile("2W"),
            new Tile("3W"), new Tile("3W"), new Tile("3W"),
            new Tile("4W"), new Tile("4W"), new Tile("4W"),
            new Tile("5W"), new Tile("5W")
        );

        PlayerState player = createPlayerWithTiles(handTiles);
        Tile winningTile = new Tile("5W");

        WinValidationResult result = winValidationService.validateWin(player, winningTile, roomConfig);

        assertTrue(result.isValid());
        assertEquals("All Pungs", result.getWinType());
        assertEquals(3, result.getBaseFan());
    }

    @Test
    @DisplayName("Should validate edge wait winning condition")
    void shouldValidateEdgeWait() {
        // Create hand with edge wait: 111W 222W 333W 444W 12W (waiting for 3W)
        List<Tile> handTiles = Arrays.asList(
            new Tile("1W"), new Tile("1W"), new Tile("1W"),
            new Tile("2W"), new Tile("2W"), new Tile("2W"),
            new Tile("3W"), new Tile("3W"), new Tile("3W"),
            new Tile("4W"), new Tile("4W"), new Tile("4W"),
            new Tile("1W"), new Tile("2W")
        );

        PlayerState player = createPlayerWithTiles(handTiles);
        Tile winningTile = new Tile("3W"); // Completes 123W sequence

        WinValidationResult result = winValidationService.validateWin(player, winningTile, roomConfig);

        assertTrue(result.isValid());
        assertTrue(result.isEdgeWait());
        assertEquals(2, result.getBaseFan()); // Edge wait bonus
    }

    @Test
    @DisplayName("Should validate pair wait winning condition")
    void shouldValidatePairWait() {
        // Create hand with pair wait: 111W 222W 333W 444W 5W (waiting for 5W)
        List<Tile> handTiles = Arrays.asList(
            new Tile("1W"), new Tile("1W"), new Tile("1W"),
            new Tile("2W"), new Tile("2W"), new Tile("2W"),
            new Tile("3W"), new Tile("3W"), new Tile("3W"),
            new Tile("4W"), new Tile("4W"), new Tile("4W"),
            new Tile("5W")
        );

        PlayerState player = createPlayerWithTiles(handTiles);
        Tile winningTile = new Tile("5W"); // Completes pair

        WinValidationResult result = winValidationService.validateWin(player, winningTile, roomConfig);

        assertTrue(result.isValid());
        assertTrue(result.isPairWait());
        assertEquals(2, result.getBaseFan()); // Pair wait bonus
    }

    @Test
    @DisplayName("Should reject invalid winning hand - wrong tile count")
    void shouldRejectInvalidTileCount() {
        // Create hand with wrong tile count
        List<Tile> handTiles = Arrays.asList(
            new Tile("1W"), new Tile("1W"), new Tile("1W"),
            new Tile("2W"), new Tile("2W")
        );

        PlayerState player = createPlayerWithTiles(handTiles);
        Tile winningTile = new Tile("2W");

        WinValidationResult result = winValidationService.validateWin(player, winningTile, roomConfig);

        assertFalse(result.isValid());
        assertEquals("Invalid tile count", result.getErrorMessage());
    }

    @Test
    @DisplayName("Should reject invalid winning hand - no valid combinations")
    void shouldRejectNoValidCombinations() {
        // Create hand with no valid winning combinations
        List<Tile> handTiles = Arrays.asList(
            new Tile("1W"), new Tile("2W"), new Tile("3W"),
            new Tile("4W"), new Tile("5W"), new Tile("6W"),
            new Tile("7W"), new Tile("8W"), new Tile("9W"),
            new Tile("1T"), new Tile("2T"), new Tile("3T"),
            new Tile("4T")
        );

        PlayerState player = createPlayerWithTiles(handTiles);
        Tile winningTile = new Tile("5T");

        WinValidationResult result = winValidationService.validateWin(player, winningTile, roomConfig);

        assertFalse(result.isValid());
        assertEquals("No valid winning combinations", result.getErrorMessage());
    }

    @Test
    @DisplayName("Should handle winning with melds")
    void shouldHandleWinningWithMelds() {
        // Create hand with existing melds
        List<Tile> handTiles = Arrays.asList(
            new Tile("4W"), new Tile("4W"), new Tile("4W"),
            new Tile("5W"), new Tile("5W")
        );

        PlayerState player = createPlayerWithTiles(handTiles);
        
        // Add existing melds
        MeldSet peng1 = new MeldSet(MeldSet.MeldType.PENG, Arrays.asList(
            new Tile("1W"), new Tile("1W"), new Tile("1W")
        ));
        MeldSet peng2 = new MeldSet(MeldSet.MeldType.PENG, Arrays.asList(
            new Tile("2W"), new Tile("2W"), new Tile("2W")
        ));
        MeldSet peng3 = new MeldSet(MeldSet.MeldType.PENG, Arrays.asList(
            new Tile("3W"), new Tile("3W"), new Tile("3W")
        ));
        
        player.addMeld(peng1);
        player.addMeld(peng2);
        player.addMeld(peng3);

        Tile winningTile = new Tile("5W");

        WinValidationResult result = winValidationService.validateWin(player, winningTile, roomConfig);

        assertTrue(result.isValid());
        assertEquals("All Pungs", result.getWinType());
        assertEquals(3, result.getBaseFan());
    }

    @Test
    @DisplayName("Should validate self-draw bonus")
    void shouldValidateSelfDrawBonus() {
        List<Tile> handTiles = Arrays.asList(
            new Tile("1W"), new Tile("1W"), new Tile("1W"),
            new Tile("2W"), new Tile("2W"), new Tile("2W"),
            new Tile("3W"), new Tile("3W"), new Tile("3W"),
            new Tile("4W"), new Tile("4W"), new Tile("4W"),
            new Tile("5W"), new Tile("5W")
        );

        PlayerState player = createPlayerWithTiles(handTiles);
        Tile winningTile = new Tile("5W");

        WinValidationResult result = winValidationService.validateWin(player, winningTile, roomConfig, true);

        assertTrue(result.isValid());
        assertTrue(result.isSelfDraw());
        assertEquals(2, result.getBaseFan()); // Self-draw bonus
    }

    @Test
    @DisplayName("Should validate dealer bonus")
    void shouldValidateDealerBonus() {
        List<Tile> handTiles = Arrays.asList(
            new Tile("1W"), new Tile("1W"), new Tile("1W"),
            new Tile("2W"), new Tile("2W"), new Tile("2W"),
            new Tile("3W"), new Tile("3W"), new Tile("3W"),
            new Tile("4W"), new Tile("4W"), new Tile("4W"),
            new Tile("5W"), new Tile("5W")
        );

        PlayerState player = createPlayerWithTiles(handTiles);
        player.setDealer(true);
        Tile winningTile = new Tile("5W");

        WinValidationResult result = winValidationService.validateWin(player, winningTile, roomConfig);

        assertTrue(result.isValid());
        assertTrue(result.isDealerWin());
        assertEquals(2, result.getBaseFan()); // Dealer bonus
    }

    @Test
    @DisplayName("Should handle disabled winning types")
    void shouldHandleDisabledWinningTypes() {
        // Disable seven pairs
        huTypes.setSevenPairs(false);
        roomConfig.setHuTypes(huTypes);

        List<Tile> handTiles = Arrays.asList(
            new Tile("1W"), new Tile("1W"),
            new Tile("2W"), new Tile("2W"),
            new Tile("3W"), new Tile("3W"),
            new Tile("4W"), new Tile("4W"),
            new Tile("5W"), new Tile("5W"),
            new Tile("6W"), new Tile("6W"),
            new Tile("7W"), new Tile("7W")
        );

        PlayerState player = createPlayerWithTiles(handTiles);
        Tile winningTile = new Tile("7W");

        WinValidationResult result = winValidationService.validateWin(player, winningTile, roomConfig);

        assertFalse(result.isValid());
        assertEquals("Seven pairs not allowed", result.getErrorMessage());
    }

    @Test
    @DisplayName("Should calculate multiple bonuses correctly")
    void shouldCalculateMultipleBonuses() {
        List<Tile> handTiles = Arrays.asList(
            new Tile("1W"), new Tile("1W"), new Tile("1W"),
            new Tile("2W"), new Tile("2W"), new Tile("2W"),
            new Tile("3W"), new Tile("3W"), new Tile("3W"),
            new Tile("4W"), new Tile("4W"), new Tile("4W"),
            new Tile("5W"), new Tile("5W")
        );

        PlayerState player = createPlayerWithTiles(handTiles);
        player.setDealer(true);
        Tile winningTile = new Tile("5W");

        // Self-draw + dealer + all pungs
        WinValidationResult result = winValidationService.validateWin(player, winningTile, roomConfig, true);

        assertTrue(result.isValid());
        assertTrue(result.isSelfDraw());
        assertTrue(result.isDealerWin());
        assertEquals("All Pungs", result.getWinType());
        assertEquals(6, result.getBaseFan()); // 3 (all pungs) + 1 (self-draw) + 1 (dealer) + 1 (base)
    }

    @Test
    @DisplayName("Should validate winning combinations details")
    void shouldValidateWinningCombinationsDetails() {
        List<Tile> handTiles = Arrays.asList(
            new Tile("1W"), new Tile("1W"), new Tile("1W"),
            new Tile("2W"), new Tile("2W"), new Tile("2W"),
            new Tile("3W"), new Tile("3W"), new Tile("3W"),
            new Tile("4W"), new Tile("4W"), new Tile("4W"),
            new Tile("5W"), new Tile("5W")
        );

        PlayerState player = createPlayerWithTiles(handTiles);
        Tile winningTile = new Tile("5W");

        WinValidationResult result = winValidationService.validateWin(player, winningTile, roomConfig);

        assertTrue(result.isValid());
        
        List<List<Tile>> combinations = result.getWinningCombinations();
        assertEquals(5, combinations.size()); // 4 pungs + 1 pair
        
        // Verify each combination
        for (List<Tile> combination : combinations) {
            if (combination.size() == 3) {
                // Should be a pung (3 identical tiles)
                assertTrue(combination.get(0).equals(combination.get(1)));
                assertTrue(combination.get(1).equals(combination.get(2)));
            } else if (combination.size() == 2) {
                // Should be a pair (2 identical tiles)
                assertTrue(combination.get(0).equals(combination.get(1)));
            }
        }
    }

    @Test
    @DisplayName("Should handle edge cases with mixed suits")
    void shouldHandleEdgeCasesWithMixedSuits() {
        // Test with mixed suits but still valid winning hand
        List<Tile> handTiles = Arrays.asList(
            new Tile("1W"), new Tile("1W"), new Tile("1W"),
            new Tile("2T"), new Tile("2T"), new Tile("2T"),
            new Tile("3G"), new Tile("3G"), new Tile("3G"),
            new Tile("4W"), new Tile("4W"), new Tile("4W"),
            new Tile("5T"), new Tile("5T")
        );

        PlayerState player = createPlayerWithTiles(handTiles);
        Tile winningTile = new Tile("5T");

        WinValidationResult result = winValidationService.validateWin(player, winningTile, roomConfig);

        assertTrue(result.isValid());
        assertEquals("All Pungs", result.getWinType());
    }

    // Helper method to create player with specific tiles
    private PlayerState createPlayerWithTiles(List<Tile> tiles) {
        PlayerState player = new PlayerState("testPlayer", 0);
        for (Tile tile : tiles) {
            player.addTile(tile);
        }
        return player;
    }
}