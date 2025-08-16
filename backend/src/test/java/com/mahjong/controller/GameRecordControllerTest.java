package com.mahjong.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mahjong.model.dto.GameAction;
import com.mahjong.model.dto.GameHistoryResponse;
import com.mahjong.model.dto.GameReplayResponse;
import com.mahjong.model.dto.UserStatisticsResponse;
import com.mahjong.model.entity.GameRecord;
import com.mahjong.model.entity.User;
import com.mahjong.model.enums.GameResult;
import com.mahjong.service.GameHistoryService;
import com.mahjong.service.GameReplayService;
import com.mahjong.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for GameRecordController
 */
@WebMvcTest(GameRecordController.class)
class GameRecordControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @MockBean
    private GameHistoryService gameHistoryService;
    
    @MockBean
    private GameReplayService gameReplayService;
    
    @MockBean
    private UserService userService;
    
    private ObjectMapper objectMapper;
    
    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }
    
    @Test
    @WithMockUser(username = "test-openid")
    void testGetUserGameHistory() throws Exception {
        // Given
        Long userId = 1001L;
        User mockUser = new User();
        mockUser.setId(userId);
        mockUser.setOpenId("test-openid");
        
        List<GameRecord> gameRecords = Arrays.asList(
                createTestGameRecord("game1", "room1", GameResult.WIN),
                createTestGameRecord("game2", "room2", GameResult.LOSE)
        );
        Page<GameRecord> page = new PageImpl<>(gameRecords, PageRequest.of(0, 20), 2);
        
        when(userService.findByOpenId("test-openid")).thenReturn(Optional.of(mockUser));
        when(gameHistoryService.getUserGameHistory(eq(userId), any(Pageable.class))).thenReturn(page);
        
        // When & Then
        mockMvc.perform(get("/api/game-records/user/{userId}", userId)
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.totalElements").value(2));
        
        verify(gameHistoryService).getUserGameHistory(eq(userId), any(Pageable.class));
    }
    
    @Test
    @WithMockUser(username = "other-openid")
    void testGetUserGameHistoryAccessDenied() throws Exception {
        // Given
        Long userId = 1001L;
        User mockUser = new User();
        mockUser.setId(2001L); // Different user
        mockUser.setOpenId("other-openid");
        
        when(userService.findByOpenId("other-openid")).thenReturn(Optional.of(mockUser));
        
        // When & Then
        mockMvc.perform(get("/api/game-records/user/{userId}", userId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        
        verify(gameHistoryService, never()).getUserGameHistory(any(), any());
    }
    
    @Test
    @WithMockUser
    void testGetRoomGameHistory() throws Exception {
        // Given
        String roomId = "123456";
        List<GameRecord> gameRecords = Arrays.asList(
                createTestGameRecord("game1", roomId, GameResult.WIN),
                createTestGameRecord("game2", roomId, GameResult.DRAW)
        );
        Page<GameRecord> page = new PageImpl<>(gameRecords, PageRequest.of(0, 20), 2);
        
        when(gameHistoryService.getRoomGameHistory(eq(roomId), any(Pageable.class))).thenReturn(page);
        
        // When & Then
        mockMvc.perform(get("/api/game-records/room/{roomId}", roomId)
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.content[0].roomId").value(roomId));
        
        verify(gameHistoryService).getRoomGameHistory(eq(roomId), any(Pageable.class));
    }
    
    @Test
    @WithMockUser
    void testGetGameRecord() throws Exception {
        // Given
        String gameId = "test-game-123";
        GameRecord gameRecord = createTestGameRecord(gameId, "room1", GameResult.WIN);
        
        when(gameHistoryService.getGameRecordForReplay(gameId)).thenReturn(Optional.of(gameRecord));
        
        // When & Then
        mockMvc.perform(get("/api/game-records/{gameId}", gameId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.gameId").value(gameId))
                .andExpect(jsonPath("$.data.roomId").value("room1"))
                .andExpect(jsonPath("$.data.result").value("WIN"));
        
        verify(gameHistoryService).getGameRecordForReplay(gameId);
    }
    
    @Test
    @WithMockUser
    void testGetGameRecordNotFound() throws Exception {
        // Given
        String gameId = "non-existent-game";
        
        when(gameHistoryService.getGameRecordForReplay(gameId)).thenReturn(Optional.empty());
        
        // When & Then
        mockMvc.perform(get("/api/game-records/{gameId}", gameId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
        
        verify(gameHistoryService).getGameRecordForReplay(gameId);
    }
    
    @Test
    @WithMockUser
    void testGetGameReplayStepByStep() throws Exception {
        // Given
        String gameId = "test-game-123";
        List<GameReplayService.ReplayStep> steps = createTestReplaySteps();
        
        when(gameReplayService.getStepByStepReplay(gameId)).thenReturn(steps);
        
        // When & Then
        mockMvc.perform(get("/api/game-records/{gameId}/replay", gameId)
                        .param("stepByStep", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.successful").value(true))
                .andExpect(jsonPath("$.data.totalSteps").value(steps.size()))
                .andExpect(jsonPath("$.data.steps").isArray())
                .andExpect(jsonPath("$.data.steps.length()").value(steps.size()));
        
        verify(gameReplayService).getStepByStepReplay(gameId);
    }
    
    @Test
    @WithMockUser
    void testGetGameReplayFullReconstruction() throws Exception {
        // Given
        String gameId = "test-game-123";
        GameReplayService.ReplayResult replayResult = createTestReplayResult(gameId, true);
        
        when(gameReplayService.reconstructGame(gameId)).thenReturn(replayResult);
        
        // When & Then
        mockMvc.perform(get("/api/game-records/{gameId}/replay", gameId)
                        .param("stepByStep", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.successful").value(true))
                .andExpect(jsonPath("$.data.gameId").value(gameId))
                .andExpect(jsonPath("$.data.metadata").exists());
        
        verify(gameReplayService).reconstructGame(gameId);
    }
    
    @Test
    @WithMockUser(username = "test-openid")
    void testGetUserStatistics() throws Exception {
        // Given
        Long userId = 1001L;
        User mockUser = new User();
        mockUser.setId(userId);
        mockUser.setOpenId("test-openid");
        
        Map<String, Object> stats = createTestUserStatistics();
        
        when(userService.findByOpenId("test-openid")).thenReturn(Optional.of(mockUser));
        when(gameHistoryService.getEnhancedUserStatistics(userId)).thenReturn(stats);
        
        // When & Then
        mockMvc.perform(get("/api/game-records/statistics/user/{userId}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value(userId))
                .andExpect(jsonPath("$.data.totalGames").value(10))
                .andExpect(jsonPath("$.data.wins").value(6))
                .andExpect(jsonPath("$.data.winRate").value(0.6));
        
        verify(gameHistoryService).getEnhancedUserStatistics(userId);
    }
    
    @Test
    @WithMockUser(username = "test-openid")
    void testGetUserStatisticsWithDateRange() throws Exception {
        // Given
        Long userId = 1001L;
        User mockUser = new User();
        mockUser.setId(userId);
        mockUser.setOpenId("test-openid");
        
        LocalDateTime startDate = LocalDateTime.now().minusDays(30);
        LocalDateTime endDate = LocalDateTime.now();
        Map<String, Object> stats = createTestUserStatistics();
        stats.put("startDate", startDate);
        stats.put("endDate", endDate);
        
        when(userService.findByOpenId("test-openid")).thenReturn(Optional.of(mockUser));
        when(gameHistoryService.getUserStatisticsForDateRange(userId, startDate, endDate)).thenReturn(stats);
        
        // When & Then
        mockMvc.perform(get("/api/game-records/statistics/user/{userId}", userId)
                        .param("startDate", startDate.toString())
                        .param("endDate", endDate.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.startDate").exists())
                .andExpect(jsonPath("$.data.endDate").exists());
        
        verify(gameHistoryService).getUserStatisticsForDateRange(userId, startDate, endDate);
    }
    
    @Test
    @WithMockUser
    void testGetSystemStatistics() throws Exception {
        // Given
        Map<String, Object> stats = Map.of(
                "totalGames", 100L,
                "averageDuration", 450.5,
                "totalWins", 60L,
                "totalDraws", 10L
        );
        
        when(gameHistoryService.getSystemStatistics(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(stats);
        
        // When & Then
        mockMvc.perform(get("/api/game-records/statistics/system"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalGames").value(100))
                .andExpect(jsonPath("$.data.averageDuration").value(450.5))
                .andExpect(jsonPath("$.data.totalWins").value(60))
                .andExpect(jsonPath("$.data.totalDraws").value(10));
        
        verify(gameHistoryService).getSystemStatistics(any(LocalDateTime.class), any(LocalDateTime.class));
    }
    
    @Test
    @WithMockUser
    void testGetLeaderboard() throws Exception {
        // Given
        List<Map<String, Object>> leaderboard = Arrays.asList(
                Map.of("userId", 1001L, "totalScore", 500L),
                Map.of("userId", 1002L, "totalScore", 450L),
                Map.of("userId", 1003L, "totalScore", 400L)
        );
        
        when(gameHistoryService.getLeaderboard("score", 10)).thenReturn(leaderboard);
        
        // When & Then
        mockMvc.perform(get("/api/game-records/leaderboard")
                        .param("type", "score")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].userId").value(1001))
                .andExpect(jsonPath("$.data[0].totalScore").value(500));
        
        verify(gameHistoryService).getLeaderboard("score", 10);
    }
    
    @Test
    @WithMockUser
    void testGetGamesWithReplayData() throws Exception {
        // Given
        List<GameRecord> gameRecords = Arrays.asList(
                createTestGameRecord("game1", "room1", GameResult.WIN),
                createTestGameRecord("game2", "room2", GameResult.LOSE)
        );
        Page<GameRecord> page = new PageImpl<>(gameRecords, PageRequest.of(0, 20), 2);
        
        when(gameHistoryService.getGamesWithReplayData(any(Pageable.class))).thenReturn(page);
        
        // When & Then
        mockMvc.perform(get("/api/game-records/with-replay")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content.length()").value(2));
        
        verify(gameHistoryService).getGamesWithReplayData(any(Pageable.class));
    }
    
    @Test
    @WithMockUser
    void testHasReplayData() throws Exception {
        // Given
        String gameId = "test-game-123";
        
        when(gameHistoryService.hasReplayData(gameId)).thenReturn(true);
        when(gameHistoryService.getGameActionCount(gameId)).thenReturn(25);
        
        // When & Then
        mockMvc.perform(get("/api/game-records/{gameId}/has-replay", gameId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.gameId").value(gameId))
                .andExpect(jsonPath("$.data.hasReplayData").value(true))
                .andExpect(jsonPath("$.data.actionCount").value(25));
        
        verify(gameHistoryService).hasReplayData(gameId);
        verify(gameHistoryService).getGameActionCount(gameId);
    }
    
    /**
     * Helper method to create test game record
     */
    private GameRecord createTestGameRecord(String gameId, String roomId, GameResult result) {
        GameRecord gameRecord = new GameRecord();
        gameRecord.setId(gameId);
        gameRecord.setRoomId(roomId);
        gameRecord.setRoundIndex(1);
        gameRecord.setResult(result);
        gameRecord.setWinnerId(1001L);
        gameRecord.setDurationSeconds(300);
        gameRecord.setFinalScore(24);
        gameRecord.setDealerUserId(1001L);
        gameRecord.setCreatedAt(LocalDateTime.now());
        gameRecord.setGameActions("[{\"actionType\":\"GAME_START\"}]"); // Mock JSON
        
        return gameRecord;
    }
    
    /**
     * Helper method to create test replay steps
     */
    private List<GameReplayService.ReplayStep> createTestReplaySteps() {
        // This would normally be created by the service, but for testing we'll mock it
        return Arrays.asList(
                // Mock replay steps - in real implementation these would be proper ReplayStep objects
        );
    }
    
    /**
     * Helper method to create test replay result
     */
    private GameReplayService.ReplayResult createTestReplayResult(String gameId, boolean successful) {
        if (successful) {
            Map<String, Object> metadata = Map.of(
                    "gameId", gameId,
                    "roomId", "room1",
                    "totalActions", 10
            );
            // In real implementation, this would include actual GameState and actions
            return new GameReplayService.ReplayResult(null, new ArrayList<>(), metadata);
        } else {
            return new GameReplayService.ReplayResult("Test error message");
        }
    }
    
    /**
     * Helper method to create test user statistics
     */
    private Map<String, Object> createTestUserStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalGames", 10L);
        stats.put("wins", 6L);
        stats.put("losses", 3L);
        stats.put("draws", 1L);
        stats.put("winRate", 0.6);
        stats.put("totalScore", 150L);
        stats.put("averageScore", 15.0);
        stats.put("dealerGames", 3L);
        stats.put("dealerWins", 2L);
        stats.put("dealerWinRate", 0.67);
        stats.put("selfDrawWins", 2L);
        stats.put("longestWinStreak", 4);
        stats.put("currentWinStreak", 2);
        
        return stats;
    }
}