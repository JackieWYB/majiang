package com.mahjong.controller;

import com.mahjong.model.dto.ApiResponse;
import com.mahjong.model.dto.GameHistoryResponse;
import com.mahjong.model.dto.GameReplayResponse;
import com.mahjong.model.dto.UserStatisticsResponse;
import com.mahjong.model.entity.GameRecord;
import com.mahjong.model.entity.User;
import com.mahjong.service.GameHistoryService;
import com.mahjong.service.GameReplayService;
import com.mahjong.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * REST controller for game history and replay functionality
 */
@RestController
@RequestMapping("/api/game-records")
public class GameRecordController {
    
    private static final Logger logger = LoggerFactory.getLogger(GameRecordController.class);
    
    @Autowired
    private GameHistoryService gameHistoryService;
    
    @Autowired
    private GameReplayService gameReplayService;
    
    @Autowired
    private UserService userService;
    
    /**
     * Get user's game history with pagination
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<Page<GameHistoryResponse>>> getUserGameHistory(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        
        try {
            // Verify user access (users can only access their own history unless admin)
            if (!canAccessUserData(userId, authentication)) {
                return ResponseEntity.status(403)
                        .body(ApiResponse.error("Access denied", "FORBIDDEN"));
            }
            
            Pageable pageable = PageRequest.of(page, size);
            Page<GameRecord> gameRecords = gameHistoryService.getUserGameHistory(userId, pageable);
            
            Page<GameHistoryResponse> response = gameRecords.map(this::convertToGameHistoryResponse);
            
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (Exception e) {
            logger.error("Error retrieving user game history for user: {}", userId, e);
            return ResponseEntity.status(500)
                    .body(ApiResponse.error("Failed to retrieve game history", "INTERNAL_ERROR"));
        }
    }
    
    /**
     * Get room's game history with pagination
     */
    @GetMapping("/room/{roomId}")
    public ResponseEntity<ApiResponse<Page<GameHistoryResponse>>> getRoomGameHistory(
            @PathVariable String roomId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        
        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<GameRecord> gameRecords = gameHistoryService.getRoomGameHistory(roomId, pageable);
            
            Page<GameHistoryResponse> response = gameRecords.map(this::convertToGameHistoryResponse);
            
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (Exception e) {
            logger.error("Error retrieving room game history for room: {}", roomId, e);
            return ResponseEntity.status(500)
                    .body(ApiResponse.error("Failed to retrieve room game history", "INTERNAL_ERROR"));
        }
    }
    
    /**
     * Get specific game record details
     */
    @GetMapping("/{gameId}")
    public ResponseEntity<ApiResponse<GameHistoryResponse>> getGameRecord(
            @PathVariable String gameId,
            Authentication authentication) {
        
        try {
            Optional<GameRecord> gameRecordOpt = gameHistoryService.getGameRecordForReplay(gameId);
            
            if (gameRecordOpt.isEmpty()) {
                return ResponseEntity.status(404)
                        .body(ApiResponse.error("Game record not found", "NOT_FOUND"));
            }
            
            GameRecord gameRecord = gameRecordOpt.get();
            GameHistoryResponse response = convertToGameHistoryResponse(gameRecord);
            
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (Exception e) {
            logger.error("Error retrieving game record: {}", gameId, e);
            return ResponseEntity.status(500)
                    .body(ApiResponse.error("Failed to retrieve game record", "INTERNAL_ERROR"));
        }
    }
    
    /**
     * Get game replay data
     */
    @GetMapping("/{gameId}/replay")
    public ResponseEntity<ApiResponse<GameReplayResponse>> getGameReplay(
            @PathVariable String gameId,
            @RequestParam(defaultValue = "false") boolean stepByStep,
            Authentication authentication) {
        
        try {
            GameReplayResponse response = new GameReplayResponse(gameId, null, false);
            
            if (stepByStep) {
                // Get step-by-step replay
                List<GameReplayService.ReplayStep> steps = gameReplayService.getStepByStepReplay(gameId);
                
                if (steps.isEmpty()) {
                    response.setSuccessful(false);
                    response.setErrorMessage("No replay data available for this game");
                } else {
                    response.setSuccessful(true);
                    response.setTotalSteps(steps.size());
                    
                    // Convert to DTOs
                    List<GameReplayResponse.ReplayStepDto> stepDtos = steps.stream()
                            .map(step -> new GameReplayResponse.ReplayStepDto(
                                    step.getStepNumber(),
                                    step.getAction(),
                                    step.getGameSnapshot(),
                                    step.getDescription()
                            ))
                            .collect(Collectors.toList());
                    
                    response.setSteps(stepDtos);
                    response.setRoomId(steps.get(0).getGameSnapshot().getRoomId());
                }
            } else {
                // Get full game reconstruction
                GameReplayService.ReplayResult replayResult = gameReplayService.reconstructGame(gameId);
                
                response.setSuccessful(replayResult.isSuccessful());
                response.setErrorMessage(replayResult.getErrorMessage());
                response.setMetadata(replayResult.getMetadata());
                
                if (replayResult.isSuccessful()) {
                    response.setRoomId(replayResult.getGameState().getRoomId());
                    response.setTotalSteps(replayResult.getActions().size());
                }
            }
            
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (Exception e) {
            logger.error("Error retrieving game replay: {}", gameId, e);
            return ResponseEntity.status(500)
                    .body(ApiResponse.error("Failed to retrieve game replay", "INTERNAL_ERROR"));
        }
    }
    
    /**
     * Get user statistics
     */
    @GetMapping("/statistics/user/{userId}")
    public ResponseEntity<ApiResponse<UserStatisticsResponse>> getUserStatistics(
            @PathVariable Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            Authentication authentication) {
        
        try {
            // Verify user access
            if (!canAccessUserData(userId, authentication)) {
                return ResponseEntity.status(403)
                        .body(ApiResponse.error("Access denied", "FORBIDDEN"));
            }
            
            Map<String, Object> stats;
            if (startDate != null && endDate != null) {
                stats = gameHistoryService.getUserStatisticsForDateRange(userId, startDate, endDate);
            } else {
                stats = gameHistoryService.getEnhancedUserStatistics(userId);
            }
            
            UserStatisticsResponse response = convertToUserStatisticsResponse(userId, stats);
            
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (Exception e) {
            logger.error("Error retrieving user statistics for user: {}", userId, e);
            return ResponseEntity.status(500)
                    .body(ApiResponse.error("Failed to retrieve user statistics", "INTERNAL_ERROR"));
        }
    }
    
    /**
     * Get system-wide statistics
     */
    @GetMapping("/statistics/system")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSystemStatistics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            Authentication authentication) {
        
        try {
            // Default to last 30 days if no dates provided
            if (startDate == null) {
                startDate = LocalDateTime.now().minusDays(30);
            }
            if (endDate == null) {
                endDate = LocalDateTime.now();
            }
            
            Map<String, Object> stats = gameHistoryService.getSystemStatistics(startDate, endDate);
            
            return ResponseEntity.ok(ApiResponse.success(stats));
            
        } catch (Exception e) {
            logger.error("Error retrieving system statistics", e);
            return ResponseEntity.status(500)
                    .body(ApiResponse.error("Failed to retrieve system statistics", "INTERNAL_ERROR"));
        }
    }
    
    /**
     * Get leaderboard
     */
    @GetMapping("/leaderboard")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getLeaderboard(
            @RequestParam(defaultValue = "score") String type,
            @RequestParam(defaultValue = "10") int limit,
            Authentication authentication) {
        
        try {
            if (limit > 100) {
                limit = 100; // Cap at 100 entries
            }
            
            List<Map<String, Object>> leaderboard = gameHistoryService.getLeaderboard(type, limit);
            
            return ResponseEntity.ok(ApiResponse.success(leaderboard));
            
        } catch (Exception e) {
            logger.error("Error retrieving leaderboard", e);
            return ResponseEntity.status(500)
                    .body(ApiResponse.error("Failed to retrieve leaderboard", "INTERNAL_ERROR"));
        }
    }
    
    /**
     * Get games with replay data
     */
    @GetMapping("/with-replay")
    public ResponseEntity<ApiResponse<Page<GameHistoryResponse>>> getGamesWithReplayData(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        
        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<GameRecord> gameRecords = gameHistoryService.getGamesWithReplayData(pageable);
            
            Page<GameHistoryResponse> response = gameRecords.map(this::convertToGameHistoryResponse);
            
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (Exception e) {
            logger.error("Error retrieving games with replay data", e);
            return ResponseEntity.status(500)
                    .body(ApiResponse.error("Failed to retrieve games with replay data", "INTERNAL_ERROR"));
        }
    }
    
    /**
     * Check if game has replay data
     */
    @GetMapping("/{gameId}/has-replay")
    public ResponseEntity<ApiResponse<Map<String, Object>>> hasReplayData(
            @PathVariable String gameId,
            Authentication authentication) {
        
        try {
            boolean hasReplay = gameHistoryService.hasReplayData(gameId);
            int actionCount = gameHistoryService.getGameActionCount(gameId);
            
            Map<String, Object> result = Map.of(
                    "gameId", gameId,
                    "hasReplayData", hasReplay,
                    "actionCount", actionCount
            );
            
            return ResponseEntity.ok(ApiResponse.success(result));
            
        } catch (Exception e) {
            logger.error("Error checking replay data for game: {}", gameId, e);
            return ResponseEntity.status(500)
                    .body(ApiResponse.error("Failed to check replay data", "INTERNAL_ERROR"));
        }
    }
    
    /**
     * Convert GameRecord to GameHistoryResponse
     */
    private GameHistoryResponse convertToGameHistoryResponse(GameRecord gameRecord) {
        GameHistoryResponse response = new GameHistoryResponse();
        response.setGameId(gameRecord.getId());
        response.setRoomId(gameRecord.getRoomId());
        response.setRoundIndex(gameRecord.getRoundIndex());
        response.setResult(gameRecord.getResult());
        response.setWinnerId(gameRecord.getWinnerId());
        response.setDurationSeconds(gameRecord.getDurationSeconds());
        response.setFormattedDuration(gameRecord.getFormattedDuration());
        response.setWinningTile(gameRecord.getWinningTile());
        response.setWinningType(gameRecord.getWinningType());
        response.setFinalScore(gameRecord.getFinalScore());
        response.setDealerUserId(gameRecord.getDealerUserId());
        response.setCreatedAt(gameRecord.getCreatedAt());
        response.setHasReplayData(gameRecord.getGameActions() != null && !gameRecord.getGameActions().trim().isEmpty());
        
        // Set winner and dealer nicknames if available
        if (gameRecord.getWinner() != null) {
            response.setWinnerNickname(gameRecord.getWinner().getNickname());
        }
        if (gameRecord.getDealer() != null) {
            response.setDealerNickname(gameRecord.getDealer().getNickname());
        }
        
        // Convert player records
        if (gameRecord.getPlayerRecords() != null && !gameRecord.getPlayerRecords().isEmpty()) {
            List<GameHistoryResponse.PlayerResultDto> playerResults = gameRecord.getPlayerRecords().stream()
                    .map(pr -> {
                        GameHistoryResponse.PlayerResultDto dto = new GameHistoryResponse.PlayerResultDto();
                        dto.setUserId(pr.getUserId());
                        dto.setSeatIndex(pr.getSeatIndex());
                        dto.setResult(pr.getResult());
                        dto.setScore(pr.getScore());
                        dto.setBaseScore(pr.getBaseScore());
                        dto.setGangScore(pr.getGangScore());
                        dto.setMultiplier(pr.getMultiplier());
                        dto.setIsDealer(pr.getIsDealer());
                        dto.setIsSelfDraw(pr.getIsSelfDraw());
                        
                        // Set user info if available
                        if (pr.getUser() != null) {
                            dto.setNickname(pr.getUser().getNickname());
                            dto.setAvatar(pr.getUser().getAvatar());
                        }
                        
                        return dto;
                    })
                    .collect(Collectors.toList());
            
            response.setPlayerResults(playerResults);
        }
        
        return response;
    }
    
    /**
     * Convert statistics map to UserStatisticsResponse
     */
    private UserStatisticsResponse convertToUserStatisticsResponse(Long userId, Map<String, Object> stats) {
        UserStatisticsResponse response = new UserStatisticsResponse();
        response.setUserId(userId);
        response.setTotalGames(getLongValue(stats, "totalGames"));
        response.setWins(getLongValue(stats, "wins"));
        response.setLosses(getLongValue(stats, "losses"));
        response.setDraws(getLongValue(stats, "draws"));
        response.setWinRate(getDoubleValue(stats, "winRate"));
        response.setTotalScore(getLongValue(stats, "totalScore"));
        response.setAverageScore(getDoubleValue(stats, "averageScore"));
        response.setDealerGames(getLongValue(stats, "dealerGames"));
        response.setDealerWins(getLongValue(stats, "dealerWins"));
        response.setDealerWinRate(getDoubleValue(stats, "dealerWinRate"));
        response.setSelfDrawWins(getLongValue(stats, "selfDrawWins"));
        response.setLongestWinStreak(getIntegerValue(stats, "longestWinStreak"));
        response.setCurrentWinStreak(getIntegerValue(stats, "currentWinStreak"));
        
        if (stats.containsKey("startDate")) {
            response.setStartDate((LocalDateTime) stats.get("startDate"));
        }
        if (stats.containsKey("endDate")) {
            response.setEndDate((LocalDateTime) stats.get("endDate"));
        }
        
        return response;
    }
    
    /**
     * Helper method to safely get Long value from map
     */
    private Long getLongValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return 0L;
    }
    
    /**
     * Helper method to safely get Double value from map
     */
    private Double getDoubleValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return 0.0;
    }
    
    /**
     * Helper method to safely get Integer value from map
     */
    private Integer getIntegerValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return 0;
    }
    
    /**
     * Check if user can access data (own data or admin)
     */
    private boolean canAccessUserData(Long userId, Authentication authentication) {
        if (authentication == null) {
            return false;
        }
        
        try {
            String currentUserOpenId = authentication.getName();
            Optional<User> currentUserOpt = userService.findByOpenId(currentUserOpenId);
            
            if (currentUserOpt.isEmpty()) {
                return false;
            }
            
            User currentUser = currentUserOpt.get();
            
            // Users can access their own data
            if (currentUser.getId().equals(userId)) {
                return true;
            }
            
            // TODO: Add admin role check when implemented
            // return currentUser.hasRole("ADMIN");
            
            return false;
            
        } catch (Exception e) {
            logger.error("Error checking user access", e);
            return false;
        }
    }
}