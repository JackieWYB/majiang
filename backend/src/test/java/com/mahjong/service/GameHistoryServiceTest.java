package com.mahjong.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mahjong.model.dto.GameAction;
import com.mahjong.model.entity.GamePlayerRecord;
import com.mahjong.model.entity.GameRecord;
import com.mahjong.model.enums.GameResult;
import com.mahjong.model.game.GameState;
import com.mahjong.model.game.PlayerState;
import com.mahjong.model.game.Tile;
import com.mahjong.repository.GamePlayerRecordRepository;
import com.mahjong.repository.GameRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for GameHistoryService
 */
@ExtendWith(MockitoExtension.class)
class GameHistoryServiceTest {
    
    @Mock
    private GameRecordRepository gameRecordRepository;
    
    @Mock
    private GamePlayerRecordRepository gamePlayerRecordRepository;
    
    @InjectMocks
    private GameHistoryService gameHistoryService;
    
    private ObjectMapper objectMapper;
    
    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }
    
    @Test
    void testInitializeGameActionLogging() {
        // Given
        String gameId = "test-game-123";
        
        // When
        gameHistoryService.initializeGameActionLogging(gameId);
        
        // Then
        List<GameAction> actions = gameHistoryService.getGameActions(gameId);
        assertNotNull(actions);
        assertTrue(actions.isEmpty());
    }
    
    @Test
    void testLogGameAction() {
        // Given
        String gameId = "test-game-123";
        gameHistoryService.initializeGameActionLogging(gameId);
        
        GameAction.DiscardActionDto discardAction = new GameAction.DiscardActionDto(1001L, 0, "5W", true);
        
        // When
        gameHistoryService.logGameAction(gameId, discardAction);
        
        // Then
        List<GameAction> actions = gameHistoryService.getGameActions(gameId);
        assertEquals(1, actions.size());
        
        GameAction loggedAction = actions.get(0);
        assertEquals(1L, loggedAction.getSequenceNumber());
        assertEquals(GameAction.ActionType.DISCARD, loggedAction.getActionType());
        assertEquals(1001L, loggedAction.getUserId());
        assertNotNull(loggedAction.getTimestamp());
    }
    
    @Test
    void testLogMultipleGameActions() {
        // Given
        String gameId = "test-game-123";
        gameHistoryService.initializeGameActionLogging(gameId);
        
        GameAction.DiscardActionDto discardAction = new GameAction.DiscardActionDto(1001L, 0, "5W", true);
        GameAction.DrawActionDto drawAction = new GameAction.DrawActionDto(1002L, 1, "7W");
        GameAction.PengActionDto pengAction = new GameAction.PengActionDto(1003L, 2, "5W", 1001L);
        
        // When
        gameHistoryService.logGameAction(gameId, discardAction);
        gameHistoryService.logGameAction(gameId, drawAction);
        gameHistoryService.logGameAction(gameId, pengAction);
        
        // Then
        List<GameAction> actions = gameHistoryService.getGameActions(gameId);
        assertEquals(3, actions.size());
        
        // Verify sequence numbers
        assertEquals(1L, actions.get(0).getSequenceNumber());
        assertEquals(2L, actions.get(1).getSequenceNumber());
        assertEquals(3L, actions.get(2).getSequenceNumber());
        
        // Verify action types
        assertEquals(GameAction.ActionType.DISCARD, actions.get(0).getActionType());
        assertEquals(GameAction.ActionType.DRAW, actions.get(1).getActionType());
        assertEquals(GameAction.ActionType.PENG, actions.get(2).getActionType());
    }
    
    @Test
    void testSaveGameRecord() throws Exception {
        // Given
        String gameId = "test-game-123";
        String roomId = "123456";
        Integer roundIndex = 1;
        GameResult result = GameResult.WIN;
        Long winnerId = 1001L;
        Integer durationSeconds = 300;
        
        // Initialize and log some actions
        gameHistoryService.initializeGameActionLogging(gameId);
        GameAction.GameStartActionDto startAction = new GameAction.GameStartActionDto(1001L, "seed123");
        GameAction.DiscardActionDto discardAction = new GameAction.DiscardActionDto(1001L, 0, "5W", true);
        gameHistoryService.logGameAction(gameId, startAction);
        gameHistoryService.logGameAction(gameId, discardAction);
        
        // Create game state
        GameState gameState = createTestGameState(gameId, roomId);
        
        // Mock repository save
        GameRecord savedRecord = new GameRecord(gameId, roomId, roundIndex, result);
        when(gameRecordRepository.save(any(GameRecord.class))).thenReturn(savedRecord);
        when(gamePlayerRecordRepository.save(any(GamePlayerRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        // When
        GameRecord result_record = gameHistoryService.saveGameRecord(
                gameId, roomId, roundIndex, gameState, result, winnerId, durationSeconds);
        
        // Then
        assertNotNull(result_record);
        assertEquals(gameId, result_record.getId());
        assertEquals(roomId, result_record.getRoomId());
        assertEquals(result, result_record.getResult());
        
        // Verify repository interactions
        verify(gameRecordRepository).save(any(GameRecord.class));
        verify(gamePlayerRecordRepository, times(3)).save(any(GamePlayerRecord.class));
        
        // Verify actions are cleared from memory
        List<GameAction> actionsAfterSave = gameHistoryService.getGameActions(gameId);
        assertTrue(actionsAfterSave.isEmpty());
    }
    
    @Test
    void testGetUserGameHistory() {
        // Given
        Long userId = 1001L;
        Pageable pageable = PageRequest.of(0, 10);
        
        List<GameRecord> gameRecords = Arrays.asList(
                new GameRecord("game1", "room1", 1, GameResult.WIN),
                new GameRecord("game2", "room2", 1, GameResult.LOSE)
        );
        Page<GameRecord> page = new PageImpl<>(gameRecords, pageable, 2);
        
        when(gameRecordRepository.findByPlayerUserId(userId, pageable)).thenReturn(page);
        
        // When
        Page<GameRecord> result = gameHistoryService.getUserGameHistory(userId, pageable);
        
        // Then
        assertNotNull(result);
        assertEquals(2, result.getContent().size());
        assertEquals(2, result.getTotalElements());
        
        verify(gameRecordRepository).findByPlayerUserId(userId, pageable);
    }
    
    @Test
    void testGetRoomGameHistory() {
        // Given
        String roomId = "123456";
        Pageable pageable = PageRequest.of(0, 10);
        
        List<GameRecord> gameRecords = Arrays.asList(
                new GameRecord("game1", roomId, 1, GameResult.WIN),
                new GameRecord("game2", roomId, 2, GameResult.DRAW)
        );
        Page<GameRecord> page = new PageImpl<>(gameRecords, pageable, 2);
        
        when(gameRecordRepository.findByRoomIdOrderByCreatedAtDesc(roomId, pageable)).thenReturn(page);
        
        // When
        Page<GameRecord> result = gameHistoryService.getRoomGameHistory(roomId, pageable);
        
        // Then
        assertNotNull(result);
        assertEquals(2, result.getContent().size());
        assertEquals(roomId, result.getContent().get(0).getRoomId());
        
        verify(gameRecordRepository).findByRoomIdOrderByCreatedAtDesc(roomId, pageable);
    }
    
    @Test
    void testGetGameRecordForReplay() {
        // Given
        String gameId = "test-game-123";
        GameRecord gameRecord = new GameRecord(gameId, "room1", 1, GameResult.WIN);
        
        when(gameRecordRepository.findById(gameId)).thenReturn(Optional.of(gameRecord));
        
        // When
        Optional<GameRecord> result = gameHistoryService.getGameRecordForReplay(gameId);
        
        // Then
        assertTrue(result.isPresent());
        assertEquals(gameId, result.get().getId());
        
        verify(gameRecordRepository).findById(gameId);
    }
    
    @Test
    void testReconstructGameActions() throws Exception {
        // Given
        String gameId = "test-game-123";
        
        // Create test actions
        List<GameAction> originalActions = Arrays.asList(
                new GameAction.GameStartActionDto(1001L, "seed123"),
                new GameAction.DiscardActionDto(1001L, 0, "5W", true),
                new GameAction.PengActionDto(1002L, 1, "5W", 1001L)
        );
        
        // Set sequence numbers
        for (int i = 0; i < originalActions.size(); i++) {
            originalActions.get(i).setSequenceNumber((long) (i + 1));
        }
        
        String actionsJson = objectMapper.writeValueAsString(originalActions);
        
        GameRecord gameRecord = new GameRecord(gameId, "room1", 1, GameResult.WIN);
        gameRecord.setGameActions(actionsJson);
        
        when(gameRecordRepository.findById(gameId)).thenReturn(Optional.of(gameRecord));
        
        // When
        List<GameAction> reconstructedActions = gameHistoryService.reconstructGameActions(gameId);
        
        // Then
        assertNotNull(reconstructedActions);
        assertEquals(3, reconstructedActions.size());
        
        // Verify actions are in correct order
        assertEquals(1L, reconstructedActions.get(0).getSequenceNumber());
        assertEquals(2L, reconstructedActions.get(1).getSequenceNumber());
        assertEquals(3L, reconstructedActions.get(2).getSequenceNumber());
        
        // Verify action types (they should be properly deserialized)
        assertNotNull(reconstructedActions.get(0).getActionType());
        assertNotNull(reconstructedActions.get(1).getActionType());
        assertNotNull(reconstructedActions.get(2).getActionType());
    }
    
    @Test
    void testReconstructGameActionsWithEmptyData() {
        // Given
        String gameId = "test-game-123";
        GameRecord gameRecord = new GameRecord(gameId, "room1", 1, GameResult.WIN);
        gameRecord.setGameActions(null);
        
        when(gameRecordRepository.findById(gameId)).thenReturn(Optional.of(gameRecord));
        
        // When
        List<GameAction> reconstructedActions = gameHistoryService.reconstructGameActions(gameId);
        
        // Then
        assertNotNull(reconstructedActions);
        assertTrue(reconstructedActions.isEmpty());
    }
    
    @Test
    void testReconstructGameActionsWithNonExistentGame() {
        // Given
        String gameId = "non-existent-game";
        
        when(gameRecordRepository.findById(gameId)).thenReturn(Optional.empty());
        
        // When
        List<GameAction> reconstructedActions = gameHistoryService.reconstructGameActions(gameId);
        
        // Then
        assertNotNull(reconstructedActions);
        assertTrue(reconstructedActions.isEmpty());
    }
    
    @Test
    void testGetUserStatistics() {
        // Given
        Long userId = 1001L;
        Object[] stats = {10L, 6L, 3L, 1L, 150L, 15.0}; // totalGames, wins, losses, draws, totalScore, avgScore
        
        when(gamePlayerRecordRepository.getPlayerStatistics(userId)).thenReturn(stats);
        
        // When
        Map<String, Object> result = gameHistoryService.getUserStatistics(userId);
        
        // Then
        assertNotNull(result);
        assertEquals(10L, result.get("totalGames"));
        assertEquals(6L, result.get("wins"));
        assertEquals(3L, result.get("losses"));
        assertEquals(1L, result.get("draws"));
        assertEquals(150L, result.get("totalScore"));
        assertEquals(15.0, result.get("averageScore"));
        assertEquals(0.6, result.get("winRate"));
        
        verify(gamePlayerRecordRepository).getPlayerStatistics(userId);
    }
    
    @Test
    void testGetUserStatisticsForNewUser() {
        // Given
        Long userId = 1001L;
        
        when(gamePlayerRecordRepository.getPlayerStatistics(userId)).thenReturn(null);
        
        // When
        Map<String, Object> result = gameHistoryService.getUserStatistics(userId);
        
        // Then
        assertNotNull(result);
        assertEquals(0L, result.get("totalGames"));
        assertEquals(0L, result.get("wins"));
        assertEquals(0L, result.get("losses"));
        assertEquals(0L, result.get("draws"));
        assertEquals(0L, result.get("totalScore"));
        assertEquals(0.0, result.get("averageScore"));
        assertEquals(0.0, result.get("winRate"));
    }
    
    @Test
    void testGetUserStatisticsForDateRange() {
        // Given
        Long userId = 1001L;
        LocalDateTime startDate = LocalDateTime.now().minusDays(30);
        LocalDateTime endDate = LocalDateTime.now();
        Object[] stats = {5L, 3L, 2L, 0L, 75L, 15.0};
        
        when(gamePlayerRecordRepository.getPlayerStatisticsForDateRange(userId, startDate, endDate))
                .thenReturn(stats);
        
        // When
        Map<String, Object> result = gameHistoryService.getUserStatisticsForDateRange(userId, startDate, endDate);
        
        // Then
        assertNotNull(result);
        assertEquals(5L, result.get("totalGames"));
        assertEquals(3L, result.get("wins"));
        assertEquals(2L, result.get("losses"));
        assertEquals(0L, result.get("draws"));
        assertEquals(0.6, result.get("winRate"));
        assertEquals(startDate, result.get("startDate"));
        assertEquals(endDate, result.get("endDate"));
        
        verify(gamePlayerRecordRepository).getPlayerStatisticsForDateRange(userId, startDate, endDate);
    }
    
    @Test
    void testGetGamesWithReplayData() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        List<GameRecord> gameRecords = Arrays.asList(
                new GameRecord("game1", "room1", 1, GameResult.WIN),
                new GameRecord("game2", "room2", 1, GameResult.LOSE)
        );
        Page<GameRecord> page = new PageImpl<>(gameRecords, pageable, 2);
        
        when(gameRecordRepository.findGamesWithReplayData(pageable)).thenReturn(page);
        
        // When
        Page<GameRecord> result = gameHistoryService.getGamesWithReplayData(pageable);
        
        // Then
        assertNotNull(result);
        assertEquals(2, result.getContent().size());
        
        verify(gameRecordRepository).findGamesWithReplayData(pageable);
    }
    
    @Test
    void testGetSystemStatistics() {
        // Given
        LocalDateTime startDate = LocalDateTime.now().minusDays(30);
        LocalDateTime endDate = LocalDateTime.now();
        Object[] stats = {100L, 450.5, 60L, 10L}; // totalGames, avgDuration, totalWins, totalDraws
        
        when(gameRecordRepository.getGameStatistics(startDate, endDate)).thenReturn(stats);
        
        // When
        Map<String, Object> result = gameHistoryService.getSystemStatistics(startDate, endDate);
        
        // Then
        assertNotNull(result);
        assertEquals(100L, result.get("totalGames"));
        assertEquals(450.5, result.get("averageDuration"));
        assertEquals(60L, result.get("totalWins"));
        assertEquals(10L, result.get("totalDraws"));
        assertEquals(startDate, result.get("startDate"));
        assertEquals(endDate, result.get("endDate"));
        
        verify(gameRecordRepository).getGameStatistics(startDate, endDate);
    }
    
    @Test
    void testCleanupOldRecords() {
        // Given
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(90);
        
        // When
        gameHistoryService.cleanupOldRecords(cutoffDate);
        
        // Then
        verify(gameRecordRepository).deleteOldRecords(cutoffDate);
    }
    
    @Test
    void testGetEnhancedUserStatistics() {
        // Given
        Long userId = 1001L;
        Object[] basicStats = {10L, 6L, 3L, 1L, 150L, 15.0};
        Object[] dealerStats = {3L, 2L, 20.0};
        Object[] selfDrawStats = {5L, 2L};
        
        when(gamePlayerRecordRepository.getPlayerStatistics(userId)).thenReturn(basicStats);
        when(gamePlayerRecordRepository.getDealerStatistics(userId)).thenReturn(dealerStats);
        when(gamePlayerRecordRepository.getSelfDrawStatistics(userId)).thenReturn(selfDrawStats);
        when(gamePlayerRecordRepository.getRecentPerformance(eq(userId), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(new ArrayList<>()));
        
        // When
        Map<String, Object> result = gameHistoryService.getEnhancedUserStatistics(userId);
        
        // Then
        assertNotNull(result);
        assertEquals(10L, result.get("totalGames"));
        assertEquals(6L, result.get("wins"));
        assertEquals(0.6, result.get("winRate"));
        assertEquals(3L, result.get("dealerGames"));
        assertEquals(2L, result.get("dealerWins"));
        assertEquals(0.67, (Double) result.get("dealerWinRate"), 0.01);
        assertEquals(5L, result.get("selfDrawGames"));
        assertEquals(2L, result.get("selfDrawWins"));
        
        verify(gamePlayerRecordRepository).getPlayerStatistics(userId);
        verify(gamePlayerRecordRepository).getDealerStatistics(userId);
        verify(gamePlayerRecordRepository).getSelfDrawStatistics(userId);
    }
    
    @Test
    void testGetLeaderboardByScore() {
        // Given
        String type = "score";
        int limit = 10;
        List<Object[]> topScorers = Arrays.asList(
                new Object[]{1001L, 500L},
                new Object[]{1002L, 450L},
                new Object[]{1003L, 400L}
        );
        Page<Object[]> page = new PageImpl<>(topScorers);
        
        when(gamePlayerRecordRepository.findTopPerformersByScore(any(PageRequest.class))).thenReturn(page);
        
        // When
        List<Map<String, Object>> result = gameHistoryService.getLeaderboard(type, limit);
        
        // Then
        assertNotNull(result);
        assertEquals(3, result.size());
        assertEquals(1001L, result.get(0).get("userId"));
        assertEquals(500L, result.get(0).get("totalScore"));
        assertEquals(1002L, result.get(1).get("userId"));
        assertEquals(450L, result.get(1).get("totalScore"));
        
        verify(gamePlayerRecordRepository).findTopPerformersByScore(any(PageRequest.class));
    }
    
    @Test
    void testGetLeaderboardByWinRate() {
        // Given
        String type = "winRate";
        int limit = 10;
        List<Object[]> topWinners = Arrays.asList(
                new Object[]{1001L, 0.8, 20L},
                new Object[]{1002L, 0.75, 15L},
                new Object[]{1003L, 0.7, 25L}
        );
        Page<Object[]> page = new PageImpl<>(topWinners);
        
        when(gamePlayerRecordRepository.findTopPerformersByWinRate(eq(10L), any(PageRequest.class))).thenReturn(page);
        
        // When
        List<Map<String, Object>> result = gameHistoryService.getLeaderboard(type, limit);
        
        // Then
        assertNotNull(result);
        assertEquals(3, result.size());
        assertEquals(1001L, result.get(0).get("userId"));
        assertEquals(0.8, result.get(0).get("winRate"));
        assertEquals(20L, result.get(0).get("totalGames"));
        
        verify(gamePlayerRecordRepository).findTopPerformersByWinRate(eq(10L), any(PageRequest.class));
    }
    
    @Test
    void testGetGameActionCount() throws Exception {
        // Given
        String gameId = "test-game-123";
        List<GameAction> actions = Arrays.asList(
                new GameAction.GameStartActionDto(1001L, "seed123"),
                new GameAction.DiscardActionDto(1001L, 0, "5W", true),
                new GameAction.PengActionDto(1002L, 1, "5W", 1001L)
        );
        String actionsJson = objectMapper.writeValueAsString(actions);
        
        GameRecord gameRecord = new GameRecord(gameId, "room1", 1, GameResult.WIN);
        gameRecord.setGameActions(actionsJson);
        
        when(gameRecordRepository.findById(gameId)).thenReturn(Optional.of(gameRecord));
        
        // When
        int count = gameHistoryService.getGameActionCount(gameId);
        
        // Then
        assertEquals(3, count);
        verify(gameRecordRepository).findById(gameId);
    }
    
    @Test
    void testGetGameActionCountWithEmptyActions() {
        // Given
        String gameId = "test-game-123";
        GameRecord gameRecord = new GameRecord(gameId, "room1", 1, GameResult.WIN);
        gameRecord.setGameActions(null);
        
        when(gameRecordRepository.findById(gameId)).thenReturn(Optional.of(gameRecord));
        
        // When
        int count = gameHistoryService.getGameActionCount(gameId);
        
        // Then
        assertEquals(0, count);
        verify(gameRecordRepository).findById(gameId);
    }
    
    @Test
    void testGetGameActionCountWithNonExistentGame() {
        // Given
        String gameId = "non-existent-game";
        
        when(gameRecordRepository.findById(gameId)).thenReturn(Optional.empty());
        
        // When
        int count = gameHistoryService.getGameActionCount(gameId);
        
        // Then
        assertEquals(0, count);
        verify(gameRecordRepository).findById(gameId);
    }
    
    @Test
    void testHasReplayData() {
        // Given
        String gameId = "test-game-123";
        GameRecord gameRecord = new GameRecord(gameId, "room1", 1, GameResult.WIN);
        gameRecord.setGameActions("[{\"actionType\":\"GAME_START\"}]");
        
        when(gameRecordRepository.findById(gameId)).thenReturn(Optional.of(gameRecord));
        
        // When
        boolean hasReplay = gameHistoryService.hasReplayData(gameId);
        
        // Then
        assertTrue(hasReplay);
        verify(gameRecordRepository).findById(gameId);
    }
    
    @Test
    void testHasReplayDataWithEmptyActions() {
        // Given
        String gameId = "test-game-123";
        GameRecord gameRecord = new GameRecord(gameId, "room1", 1, GameResult.WIN);
        gameRecord.setGameActions("");
        
        when(gameRecordRepository.findById(gameId)).thenReturn(Optional.of(gameRecord));
        
        // When
        boolean hasReplay = gameHistoryService.hasReplayData(gameId);
        
        // Then
        assertFalse(hasReplay);
        verify(gameRecordRepository).findById(gameId);
    }
    
    @Test
    void testHasReplayDataWithNonExistentGame() {
        // Given
        String gameId = "non-existent-game";
        
        when(gameRecordRepository.findById(gameId)).thenReturn(Optional.empty());
        
        // When
        boolean hasReplay = gameHistoryService.hasReplayData(gameId);
        
        // Then
        assertFalse(hasReplay);
        verify(gameRecordRepository).findById(gameId);
    }
    
    /**
     * Helper method to create test game state
     */
    private GameState createTestGameState(String gameId, String roomId) {
        // Create a basic room config
        com.mahjong.model.config.RoomConfig config = new com.mahjong.model.config.RoomConfig();
        
        // Create player IDs
        List<String> playerIds = Arrays.asList("1001", "1002", "1003");
        
        // Create game state with proper constructor
        GameState gameState = new GameState(roomId, gameId, playerIds, config);
        
        // Set scores for players
        List<PlayerState> players = gameState.getPlayers();
        for (int i = 0; i < players.size(); i++) {
            PlayerState player = players.get(i);
            player.setScore(i * 10);
            
            // Add some test tiles
            player.addTile(new Tile(Tile.Suit.WAN, 1));
            player.addTile(new Tile(Tile.Suit.WAN, 2));
            player.addTile(new Tile(Tile.Suit.WAN, 3));
        }
        
        return gameState;
    }
}