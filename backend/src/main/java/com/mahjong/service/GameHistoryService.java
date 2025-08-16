package com.mahjong.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mahjong.model.dto.GameAction;
import com.mahjong.model.entity.GamePlayerRecord;
import com.mahjong.model.entity.GameRecord;
import com.mahjong.model.enums.GameResult;
import com.mahjong.model.game.GameState;
import com.mahjong.model.game.PlayerState;
import com.mahjong.repository.GamePlayerRecordRepository;
import com.mahjong.repository.GameRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service for managing game history, action logging, and replay functionality
 */
@Service
@Transactional
public class GameHistoryService {
    
    private static final Logger logger = LoggerFactory.getLogger(GameHistoryService.class);
    
    @Autowired
    private GameRecordRepository gameRecordRepository;
    
    @Autowired
    private GamePlayerRecordRepository gamePlayerRecordRepository;
    
    private final ObjectMapper objectMapper;
    
    // In-memory storage for active game actions (before persistence)
    private final Map<String, List<GameAction>> activeGameActions = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> gameSequenceCounters = new ConcurrentHashMap<>();
    
    public GameHistoryService() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }
    
    /**
     * Initialize action logging for a new game
     */
    public void initializeGameActionLogging(String gameId) {
        activeGameActions.put(gameId, Collections.synchronizedList(new ArrayList<>()));
        gameSequenceCounters.put(gameId, new AtomicLong(0));
        logger.info("Initialized action logging for game: {}", gameId);
    }
    
    /**
     * Log a game action with automatic sequence numbering
     */
    public void logGameAction(String gameId, GameAction action) {
        if (!activeGameActions.containsKey(gameId)) {
            logger.warn("Attempting to log action for uninitialized game: {}", gameId);
            initializeGameActionLogging(gameId);
        }
        
        AtomicLong sequenceCounter = gameSequenceCounters.get(gameId);
        action.setSequenceNumber(sequenceCounter.incrementAndGet());
        action.setTimestamp(LocalDateTime.now());
        
        List<GameAction> actions = activeGameActions.get(gameId);
        actions.add(action);
        
        logger.debug("Logged action {} for game {}: {}", 
                action.getSequenceNumber(), gameId, action.getActionType());
    }
    
    /**
     * Get current action sequence for a game
     */
    public List<GameAction> getGameActions(String gameId) {
        return activeGameActions.getOrDefault(gameId, new ArrayList<>());
    }
    
    /**
     * Save complete game record with action history
     */
    public GameRecord saveGameRecord(String gameId, String roomId, Integer roundIndex, 
                                   GameState finalGameState, GameResult result, 
                                   Long winnerId, Integer durationSeconds) {
        try {
            // Create game record
            GameRecord gameRecord = new GameRecord();
            gameRecord.setId(gameId);
            gameRecord.setRoomId(roomId);
            gameRecord.setRoundIndex(roundIndex);
            gameRecord.setResult(result);
            gameRecord.setWinnerId(winnerId);
            gameRecord.setDurationSeconds(durationSeconds);
            gameRecord.setDealerUserId(Long.parseLong(finalGameState.getDealerUserId()));
            gameRecord.setRandomSeed(String.valueOf(finalGameState.getRandomSeed()));
            
            // Serialize game actions
            List<GameAction> actions = activeGameActions.get(gameId);
            if (actions != null && !actions.isEmpty()) {
                String actionsJson = objectMapper.writeValueAsString(actions);
                gameRecord.setGameActions(actionsJson);
            }
            
            // Serialize final hands
            Map<String, Object> finalHands = new HashMap<>();
            for (PlayerState playerState : finalGameState.getPlayers()) {
                Map<String, Object> playerData = new HashMap<>();
                playerData.put("handTiles", playerState.getHandTiles());
                playerData.put("melds", playerState.getMelds());
                playerData.put("discardedTiles", new ArrayList<>());
                finalHands.put(playerState.getUserId().toString(), playerData);
            }
            gameRecord.setFinalHands(objectMapper.writeValueAsString(finalHands));
            
            // Save game record
            gameRecord = gameRecordRepository.save(gameRecord);
            
            // Save player records
            for (PlayerState playerState : finalGameState.getPlayers()) {
                GamePlayerRecord playerRecord = new GamePlayerRecord();
                playerRecord.setGameRecordId(gameId);
                playerRecord.setUserId(Long.parseLong(playerState.getUserId()));
                playerRecord.setSeatIndex(playerState.getSeatIndex());
                
                // Determine player result
                if (Objects.equals(Long.parseLong(playerState.getUserId()), winnerId)) {
                    playerRecord.setResult(GameResult.WIN);
                } else if (result == GameResult.DRAW) {
                    playerRecord.setResult(GameResult.DRAW);
                } else {
                    playerRecord.setResult(GameResult.LOSE);
                }
                
                playerRecord.setScore(playerState.getScore());
                playerRecord.setIsDealer(Objects.equals(playerState.getUserId(), finalGameState.getDealerUserId()));
                
                // Serialize player data
                playerRecord.setFinalHand(objectMapper.writeValueAsString(playerState.getHandTiles()));
                playerRecord.setMelds(objectMapper.writeValueAsString(playerState.getMelds()));
                
                gamePlayerRecordRepository.save(playerRecord);
            }
            
            // Clean up in-memory data
            activeGameActions.remove(gameId);
            gameSequenceCounters.remove(gameId);
            
            logger.info("Saved game record for game: {} with {} actions", gameId, 
                    actions != null ? actions.size() : 0);
            
            return gameRecord;
            
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize game data for game: {}", gameId, e);
            throw new RuntimeException("Failed to save game record", e);
        }
    }
    
    /**
     * Get game history for a user with pagination
     */
    @Transactional(readOnly = true)
    public Page<GameRecord> getUserGameHistory(Long userId, Pageable pageable) {
        return gameRecordRepository.findByPlayerUserId(userId, pageable);
    }
    
    /**
     * Get game history for a room with pagination
     */
    @Transactional(readOnly = true)
    public Page<GameRecord> getRoomGameHistory(String roomId, Pageable pageable) {
        return gameRecordRepository.findByRoomIdOrderByCreatedAtDesc(roomId, pageable);
    }
    
    /**
     * Get game record by ID for replay
     */
    @Transactional(readOnly = true)
    public Optional<GameRecord> getGameRecordForReplay(String gameId) {
        return gameRecordRepository.findById(gameId);
    }
    
    /**
     * Reconstruct game actions from saved record for replay
     */
    @Transactional(readOnly = true)
    public List<GameAction> reconstructGameActions(String gameId) {
        Optional<GameRecord> gameRecordOpt = gameRecordRepository.findById(gameId);
        if (gameRecordOpt.isEmpty()) {
            logger.warn("Game record not found for replay: {}", gameId);
            return new ArrayList<>();
        }
        
        GameRecord gameRecord = gameRecordOpt.get();
        String actionsJson = gameRecord.getGameActions();
        
        if (actionsJson == null || actionsJson.trim().isEmpty()) {
            logger.warn("No action data found for game: {}", gameId);
            return new ArrayList<>();
        }
        
        try {
            TypeReference<List<GameAction>> typeRef = new TypeReference<List<GameAction>>() {};
            List<GameAction> actions = objectMapper.readValue(actionsJson, typeRef);
            
            // Sort by sequence number to ensure correct order
            actions.sort(Comparator.comparing(GameAction::getSequenceNumber));
            
            logger.info("Reconstructed {} actions for game replay: {}", actions.size(), gameId);
            return actions;
            
        } catch (JsonProcessingException e) {
            logger.error("Failed to deserialize game actions for game: {}", gameId, e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Get user statistics
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getUserStatistics(Long userId) {
        Object[] stats = gamePlayerRecordRepository.getPlayerStatistics(userId);
        
        Map<String, Object> statistics = new HashMap<>();
        if (stats != null && stats.length >= 6) {
            statistics.put("totalGames", ((Number) stats[0]).longValue());
            statistics.put("wins", ((Number) stats[1]).longValue());
            statistics.put("losses", ((Number) stats[2]).longValue());
            statistics.put("draws", ((Number) stats[3]).longValue());
            statistics.put("totalScore", ((Number) stats[4]).longValue());
            statistics.put("averageScore", ((Number) stats[5]).doubleValue());
            
            long totalGames = ((Number) stats[0]).longValue();
            long wins = ((Number) stats[1]).longValue();
            statistics.put("winRate", totalGames > 0 ? (double) wins / totalGames : 0.0);
        } else {
            // Default values for new users
            statistics.put("totalGames", 0L);
            statistics.put("wins", 0L);
            statistics.put("losses", 0L);
            statistics.put("draws", 0L);
            statistics.put("totalScore", 0L);
            statistics.put("averageScore", 0.0);
            statistics.put("winRate", 0.0);
        }
        
        return statistics;
    }
    
    /**
     * Get user statistics for a date range
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getUserStatisticsForDateRange(Long userId, LocalDateTime startDate, LocalDateTime endDate) {
        Object[] stats = gamePlayerRecordRepository.getPlayerStatisticsForDateRange(userId, startDate, endDate);
        
        Map<String, Object> statistics = new HashMap<>();
        if (stats != null && stats.length >= 6) {
            statistics.put("totalGames", ((Number) stats[0]).longValue());
            statistics.put("wins", ((Number) stats[1]).longValue());
            statistics.put("losses", ((Number) stats[2]).longValue());
            statistics.put("draws", ((Number) stats[3]).longValue());
            statistics.put("totalScore", ((Number) stats[4]).longValue());
            statistics.put("averageScore", ((Number) stats[5]).doubleValue());
            
            long totalGames = ((Number) stats[0]).longValue();
            long wins = ((Number) stats[1]).longValue();
            statistics.put("winRate", totalGames > 0 ? (double) wins / totalGames : 0.0);
        } else {
            statistics.put("totalGames", 0L);
            statistics.put("wins", 0L);
            statistics.put("losses", 0L);
            statistics.put("draws", 0L);
            statistics.put("totalScore", 0L);
            statistics.put("averageScore", 0.0);
            statistics.put("winRate", 0.0);
        }
        
        statistics.put("startDate", startDate);
        statistics.put("endDate", endDate);
        
        return statistics;
    }
    
    /**
     * Get recent games with replay data
     */
    @Transactional(readOnly = true)
    public Page<GameRecord> getGamesWithReplayData(Pageable pageable) {
        return gameRecordRepository.findGamesWithReplayData(pageable);
    }
    
    /**
     * Clean up old game records (for maintenance)
     */
    @Transactional
    public void cleanupOldRecords(LocalDateTime cutoffDate) {
        logger.info("Cleaning up game records older than: {}", cutoffDate);
        gameRecordRepository.deleteOldRecords(cutoffDate);
    }
    
    /**
     * Get system-wide game statistics
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getSystemStatistics(LocalDateTime startDate, LocalDateTime endDate) {
        Object[] stats = gameRecordRepository.getGameStatistics(startDate, endDate);
        
        Map<String, Object> systemStats = new HashMap<>();
        if (stats != null && stats.length >= 4) {
            systemStats.put("totalGames", ((Number) stats[0]).longValue());
            systemStats.put("averageDuration", ((Number) stats[1]).doubleValue());
            systemStats.put("totalWins", ((Number) stats[2]).longValue());
            systemStats.put("totalDraws", ((Number) stats[3]).longValue());
        }
        
        systemStats.put("startDate", startDate);
        systemStats.put("endDate", endDate);
        
        return systemStats;
    }
    
    /**
     * Get enhanced user statistics with additional metrics
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getEnhancedUserStatistics(Long userId) {
        Map<String, Object> basicStats = getUserStatistics(userId);
        
        // Get dealer statistics
        Object[] dealerStats = gamePlayerRecordRepository.getDealerStatistics(userId);
        if (dealerStats != null && dealerStats.length >= 3) {
            basicStats.put("dealerGames", ((Number) dealerStats[0]).longValue());
            basicStats.put("dealerWins", ((Number) dealerStats[1]).longValue());
            basicStats.put("dealerAverageScore", ((Number) dealerStats[2]).doubleValue());
            
            long dealerGames = ((Number) dealerStats[0]).longValue();
            long dealerWins = ((Number) dealerStats[1]).longValue();
            basicStats.put("dealerWinRate", dealerGames > 0 ? (double) dealerWins / dealerGames : 0.0);
        } else {
            basicStats.put("dealerGames", 0L);
            basicStats.put("dealerWins", 0L);
            basicStats.put("dealerAverageScore", 0.0);
            basicStats.put("dealerWinRate", 0.0);
        }
        
        // Get self-draw statistics
        Object[] selfDrawStats = gamePlayerRecordRepository.getSelfDrawStatistics(userId);
        if (selfDrawStats != null && selfDrawStats.length >= 2) {
            basicStats.put("selfDrawGames", ((Number) selfDrawStats[0]).longValue());
            basicStats.put("selfDrawWins", ((Number) selfDrawStats[1]).longValue());
        } else {
            basicStats.put("selfDrawGames", 0L);
            basicStats.put("selfDrawWins", 0L);
        }
        
        // Calculate win streaks
        Map<String, Integer> streakStats = calculateWinStreaks(userId);
        basicStats.putAll(streakStats);
        
        return basicStats;
    }
    
    /**
     * Calculate win streaks for a user
     */
    private Map<String, Integer> calculateWinStreaks(Long userId) {
        // Get recent games ordered by date
        List<GamePlayerRecord> recentGames = gamePlayerRecordRepository
                .getRecentPerformance(userId, org.springframework.data.domain.PageRequest.of(0, 100))
                .getContent();
        
        int currentStreak = 0;
        int longestWinStreak = 0;
        int longestLoseStreak = 0;
        int tempWinStreak = 0;
        int tempLoseStreak = 0;
        
        for (GamePlayerRecord record : recentGames) {
            if (record.getResult() == GameResult.WIN) {
                tempWinStreak++;
                tempLoseStreak = 0;
                if (currentStreak >= 0) {
                    currentStreak++;
                } else {
                    currentStreak = 1;
                }
            } else if (record.getResult() == GameResult.LOSE) {
                tempLoseStreak++;
                tempWinStreak = 0;
                if (currentStreak <= 0) {
                    currentStreak--;
                } else {
                    currentStreak = -1;
                }
            } else {
                // Draw breaks streaks
                tempWinStreak = 0;
                tempLoseStreak = 0;
                currentStreak = 0;
            }
            
            longestWinStreak = Math.max(longestWinStreak, tempWinStreak);
            longestLoseStreak = Math.max(longestLoseStreak, tempLoseStreak);
        }
        
        Map<String, Integer> streaks = new HashMap<>();
        streaks.put("currentWinStreak", Math.max(0, currentStreak));
        streaks.put("currentLoseStreak", Math.abs(Math.min(0, currentStreak)));
        streaks.put("longestWinStreak", longestWinStreak);
        streaks.put("longestLoseStreak", longestLoseStreak);
        
        return streaks;
    }
    
    /**
     * Get leaderboard data
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getLeaderboard(String type, int limit) {
        List<Map<String, Object>> leaderboard = new ArrayList<>();
        
        org.springframework.data.domain.Pageable pageable = 
                org.springframework.data.domain.PageRequest.of(0, limit);
        
        if ("score".equals(type)) {
            List<Object[]> topScorers = gamePlayerRecordRepository.findTopPerformersByScore(pageable).getContent();
            for (Object[] row : topScorers) {
                Map<String, Object> entry = new HashMap<>();
                entry.put("userId", row[0]);
                entry.put("totalScore", row[1]);
                leaderboard.add(entry);
            }
        } else if ("winRate".equals(type)) {
            List<Object[]> topWinners = gamePlayerRecordRepository.findTopPerformersByWinRate(10L, pageable).getContent();
            for (Object[] row : topWinners) {
                Map<String, Object> entry = new HashMap<>();
                entry.put("userId", row[0]);
                entry.put("winRate", row[1]);
                entry.put("totalGames", row[2]);
                leaderboard.add(entry);
            }
        }
        
        return leaderboard;
    }
    
    /**
     * Get game action count for a specific game
     */
    @Transactional(readOnly = true)
    public int getGameActionCount(String gameId) {
        Optional<GameRecord> gameRecordOpt = gameRecordRepository.findById(gameId);
        if (gameRecordOpt.isEmpty()) {
            return 0;
        }
        
        GameRecord gameRecord = gameRecordOpt.get();
        String actionsJson = gameRecord.getGameActions();
        
        if (actionsJson == null || actionsJson.trim().isEmpty()) {
            return 0;
        }
        
        try {
            TypeReference<List<GameAction>> typeRef = new TypeReference<List<GameAction>>() {};
            List<GameAction> actions = objectMapper.readValue(actionsJson, typeRef);
            return actions.size();
        } catch (JsonProcessingException e) {
            logger.error("Failed to parse game actions for count: {}", gameId, e);
            return 0;
        }
    }
    
    /**
     * Check if game has replay data
     */
    @Transactional(readOnly = true)
    public boolean hasReplayData(String gameId) {
        Optional<GameRecord> gameRecordOpt = gameRecordRepository.findById(gameId);
        if (gameRecordOpt.isEmpty()) {
            return false;
        }
        
        GameRecord gameRecord = gameRecordOpt.get();
        return gameRecord.getGameActions() != null && !gameRecord.getGameActions().trim().isEmpty();
    }
}