package com.mahjong.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mahjong.model.dto.GameAction;
import com.mahjong.model.entity.GameRecord;
import com.mahjong.model.enums.GameResult;
import com.mahjong.service.GameReplayService.ReplayResult;
import com.mahjong.service.GameReplayService.ReplayStep;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for GameReplayService
 */
@ExtendWith(MockitoExtension.class)
class GameReplayServiceTest {
    
    @Mock
    private GameHistoryService gameHistoryService;
    
    @InjectMocks
    private GameReplayService gameReplayService;
    
    private ObjectMapper objectMapper;
    
    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }
    
    @Test
    void testReconstructGameSuccess() {
        // Given
        String gameId = "test-game-123";
        GameRecord gameRecord = createTestGameRecord(gameId);
        List<GameAction> actions = createTestGameActions();
        
        when(gameHistoryService.getGameRecordForReplay(gameId)).thenReturn(Optional.of(gameRecord));
        when(gameHistoryService.reconstructGameActions(gameId)).thenReturn(actions);
        
        // When
        ReplayResult result = gameReplayService.reconstructGame(gameId);
        
        // Then
        assertTrue(result.isSuccessful());
        assertNull(result.getErrorMessage());
        assertNotNull(result.getGameState());
        assertNotNull(result.getActions());
        assertNotNull(result.getMetadata());
        
        assertEquals(actions.size(), result.getActions().size());
        assertEquals(gameId, result.getGameState().getGameId());
        assertEquals("123456", result.getGameState().getRoomId());
        
        verify(gameHistoryService).getGameRecordForReplay(gameId);
        verify(gameHistoryService).reconstructGameActions(gameId);
    }
    
    @Test
    void testReconstructGameWithNonExistentRecord() {
        // Given
        String gameId = "non-existent-game";
        
        when(gameHistoryService.getGameRecordForReplay(gameId)).thenReturn(Optional.empty());
        
        // When
        ReplayResult result = gameReplayService.reconstructGame(gameId);
        
        // Then
        assertFalse(result.isSuccessful());
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("Game record not found"));
        assertNull(result.getGameState());
        assertNull(result.getActions());
        
        verify(gameHistoryService).getGameRecordForReplay(gameId);
        verify(gameHistoryService, never()).reconstructGameActions(anyString());
    }
    
    @Test
    void testReconstructGameWithEmptyActions() {
        // Given
        String gameId = "test-game-123";
        GameRecord gameRecord = createTestGameRecord(gameId);
        List<GameAction> emptyActions = new ArrayList<>();
        
        when(gameHistoryService.getGameRecordForReplay(gameId)).thenReturn(Optional.of(gameRecord));
        when(gameHistoryService.reconstructGameActions(gameId)).thenReturn(emptyActions);
        
        // When
        ReplayResult result = gameReplayService.reconstructGame(gameId);
        
        // Then
        assertFalse(result.isSuccessful());
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("No action data found"));
        
        verify(gameHistoryService).getGameRecordForReplay(gameId);
        verify(gameHistoryService).reconstructGameActions(gameId);
    }
    
    @Test
    void testReconstructGameWithoutGameStartAction() {
        // Given
        String gameId = "test-game-123";
        GameRecord gameRecord = createTestGameRecord(gameId);
        
        // Create actions without game start action
        List<GameAction> actions = Arrays.asList(
                createDiscardAction(1001L, 0, "5W"),
                createPengAction(1002L, 1, "5W", 1001L)
        );
        
        when(gameHistoryService.getGameRecordForReplay(gameId)).thenReturn(Optional.of(gameRecord));
        when(gameHistoryService.reconstructGameActions(gameId)).thenReturn(actions);
        
        // When
        ReplayResult result = gameReplayService.reconstructGame(gameId);
        
        // Then
        assertFalse(result.isSuccessful());
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("Game start action not found"));
    }
    
    @Test
    void testGetStepByStepReplaySuccess() {
        // Given
        String gameId = "test-game-123";
        GameRecord gameRecord = createTestGameRecord(gameId);
        List<GameAction> actions = createTestGameActions();
        
        when(gameHistoryService.getGameRecordForReplay(gameId)).thenReturn(Optional.of(gameRecord));
        when(gameHistoryService.reconstructGameActions(gameId)).thenReturn(actions);
        
        // When
        List<ReplayStep> steps = gameReplayService.getStepByStepReplay(gameId);
        
        // Then
        assertNotNull(steps);
        assertFalse(steps.isEmpty());
        
        // Should have initial step + action steps (excluding game end)
        int expectedSteps = 1 + (int) actions.stream()
                .filter(action -> !(action instanceof GameAction.GameStartActionDto) && 
                                !(action instanceof GameAction.GameEndActionDto))
                .count();
        assertEquals(expectedSteps, steps.size());
        
        // Verify first step is game start
        ReplayStep firstStep = steps.get(0);
        assertEquals(0, firstStep.getStepNumber());
        assertNotNull(firstStep.getGameSnapshot());
        assertEquals("Game started", firstStep.getDescription());
        
        // Verify subsequent steps have increasing step numbers
        for (int i = 1; i < steps.size(); i++) {
            assertEquals(i, steps.get(i).getStepNumber());
            assertNotNull(steps.get(i).getAction());
            assertNotNull(steps.get(i).getGameSnapshot());
            assertNotNull(steps.get(i).getDescription());
        }
    }
    
    @Test
    void testGetStepByStepReplayWithFailedReconstruction() {
        // Given
        String gameId = "test-game-123";
        
        when(gameHistoryService.getGameRecordForReplay(gameId)).thenReturn(Optional.empty());
        
        // When
        List<ReplayStep> steps = gameReplayService.getStepByStepReplay(gameId);
        
        // Then
        assertNotNull(steps);
        assertTrue(steps.isEmpty());
    }
    
    @Test
    void testActionDescriptions() {
        // Given
        String gameId = "test-game-123";
        GameRecord gameRecord = createTestGameRecord(gameId);
        
        // Create actions with different types
        List<GameAction> actions = Arrays.asList(
                createGameStartAction(1001L, "seed123"),
                createDiscardAction(1001L, 0, "5W"),
                createDrawAction(1002L, 1, "7W"),
                createPengAction(1003L, 2, "5W", 1001L),
                createGangAction(1001L, 0, "8W", GameAction.GangActionDto.GangType.AN, null),
                createHuAction(1002L, 1, "9W", true, "Basic Win"),
                createPassAction(1003L, 2)
        );
        
        when(gameHistoryService.getGameRecordForReplay(gameId)).thenReturn(Optional.of(gameRecord));
        when(gameHistoryService.reconstructGameActions(gameId)).thenReturn(actions);
        
        // When
        List<ReplayStep> steps = gameReplayService.getStepByStepReplay(gameId);
        
        // Then
        assertNotNull(steps);
        assertTrue(steps.size() > 1);
        
        // Verify action descriptions
        boolean foundDiscard = false, foundDraw = false, foundPeng = false, 
                foundGang = false, foundHu = false, foundPass = false;
        
        for (ReplayStep step : steps) {
            String description = step.getDescription();
            if (description.contains("discarded")) foundDiscard = true;
            if (description.contains("drew")) foundDraw = true;
            if (description.contains("Peng")) foundPeng = true;
            if (description.contains("Gang")) foundGang = true;
            if (description.contains("won")) foundHu = true;
            if (description.contains("passed")) foundPass = true;
        }
        
        assertTrue(foundDiscard, "Should have discard description");
        assertTrue(foundDraw, "Should have draw description");
        assertTrue(foundPeng, "Should have peng description");
        assertTrue(foundGang, "Should have gang description");
        assertTrue(foundHu, "Should have hu description");
        assertTrue(foundPass, "Should have pass description");
    }
    
    @Test
    void testReplayMetadata() {
        // Given
        String gameId = "test-game-123";
        GameRecord gameRecord = createTestGameRecord(gameId);
        List<GameAction> actions = createTestGameActions();
        
        when(gameHistoryService.getGameRecordForReplay(gameId)).thenReturn(Optional.of(gameRecord));
        when(gameHistoryService.reconstructGameActions(gameId)).thenReturn(actions);
        
        // When
        ReplayResult result = gameReplayService.reconstructGame(gameId);
        
        // Then
        assertTrue(result.isSuccessful());
        Map<String, Object> metadata = result.getMetadata();
        
        assertNotNull(metadata);
        assertEquals(gameId, metadata.get("gameId"));
        assertEquals("123456", metadata.get("roomId"));
        assertEquals(1, metadata.get("roundIndex"));
        assertEquals(GameResult.WIN, metadata.get("result"));
        assertEquals(1001L, metadata.get("winnerId"));
        assertEquals(300, metadata.get("durationSeconds"));
        assertEquals(actions.size(), metadata.get("totalActions"));
        assertEquals("test-seed-123", metadata.get("randomSeed"));
        assertNotNull(metadata.get("createdAt"));
    }
    
    /**
     * Helper method to create test game record
     */
    private GameRecord createTestGameRecord(String gameId) {
        GameRecord gameRecord = new GameRecord();
        gameRecord.setId(gameId);
        gameRecord.setRoomId("123456");
        gameRecord.setRoundIndex(1);
        gameRecord.setResult(GameResult.WIN);
        gameRecord.setWinnerId(1001L);
        gameRecord.setDurationSeconds(300);
        gameRecord.setDealerUserId(1001L);
        gameRecord.setRandomSeed("test-seed-123");
        gameRecord.setCreatedAt(LocalDateTime.now());
        
        // Create final hands JSON
        Map<String, Object> finalHands = new HashMap<>();
        finalHands.put("1001", Map.of("handTiles", Arrays.asList("1W", "2W", "3W")));
        finalHands.put("1002", Map.of("handTiles", Arrays.asList("4W", "5W", "6W")));
        finalHands.put("1003", Map.of("handTiles", Arrays.asList("7W", "8W", "9W")));
        
        try {
            gameRecord.setFinalHands(objectMapper.writeValueAsString(finalHands));
        } catch (Exception e) {
            // Ignore for test
        }
        
        return gameRecord;
    }
    
    /**
     * Helper method to create test game actions
     */
    private List<GameAction> createTestGameActions() {
        List<GameAction> actions = Arrays.asList(
                createGameStartAction(1001L, "test-seed-123"),
                createDiscardAction(1001L, 0, "5W"),
                createPengAction(1002L, 1, "5W", 1001L),
                createDrawAction(1003L, 2, "7W"),
                createHuAction(1002L, 1, "9W", false, "Basic Win"),
                createGameEndAction(1002L, "WIN")
        );
        
        // Set sequence numbers
        for (int i = 0; i < actions.size(); i++) {
            actions.get(i).setSequenceNumber((long) (i + 1));
            actions.get(i).setTimestamp(LocalDateTime.now().plusSeconds(i));
        }
        
        return actions;
    }
    
    /**
     * Helper methods to create specific action types
     */
    private GameAction.GameStartActionDto createGameStartAction(Long dealerUserId, String randomSeed) {
        return new GameAction.GameStartActionDto(dealerUserId, randomSeed);
    }
    
    private GameAction.DiscardActionDto createDiscardAction(Long userId, Integer seatIndex, String tile) {
        return new GameAction.DiscardActionDto(userId, seatIndex, tile, true);
    }
    
    private GameAction.DrawActionDto createDrawAction(Long userId, Integer seatIndex, String tile) {
        return new GameAction.DrawActionDto(userId, seatIndex, tile);
    }
    
    private GameAction.PengActionDto createPengAction(Long userId, Integer seatIndex, String tile, Long claimedFromUserId) {
        return new GameAction.PengActionDto(userId, seatIndex, tile, claimedFromUserId);
    }
    
    private GameAction.GangActionDto createGangAction(Long userId, Integer seatIndex, String tile, 
                                                     GameAction.GangActionDto.GangType gangType, Long claimedFromUserId) {
        return new GameAction.GangActionDto(userId, seatIndex, tile, gangType, claimedFromUserId);
    }
    
    private GameAction.HuActionDto createHuAction(Long userId, Integer seatIndex, String winningTile, 
                                                 boolean selfDraw, String winningType) {
        return new GameAction.HuActionDto(userId, seatIndex, winningTile, selfDraw, winningType);
    }
    
    private GameAction.PassActionDto createPassAction(Long userId, Integer seatIndex) {
        return new GameAction.PassActionDto(userId, seatIndex);
    }
    
    private GameAction.GameEndActionDto createGameEndAction(Long winnerId, String gameResult) {
        return new GameAction.GameEndActionDto(winnerId, gameResult);
    }
}