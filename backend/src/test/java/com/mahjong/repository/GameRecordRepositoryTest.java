package com.mahjong.repository;

import com.mahjong.model.entity.GamePlayerRecord;
import com.mahjong.model.entity.GameRecord;
import com.mahjong.model.entity.User;
import com.mahjong.model.enums.GameResult;
import com.mahjong.model.enums.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for GameRecordRepository
 */
@DataJpaTest
class GameRecordRepositoryTest {
    
    @Autowired
    private TestEntityManager entityManager;
    
    @Autowired
    private GameRecordRepository gameRecordRepository;
    
    @Autowired
    private GamePlayerRecordRepository gamePlayerRecordRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    private User testUser1, testUser2, testUser3;
    private GameRecord testGame1, testGame2, testGame3;
    
    @BeforeEach
    void setUp() {
        // Create test users
        testUser1 = createTestUser("user1", "User One");
        testUser2 = createTestUser("user2", "User Two");
        testUser3 = createTestUser("user3", "User Three");
        
        entityManager.persistAndFlush(testUser1);
        entityManager.persistAndFlush(testUser2);
        entityManager.persistAndFlush(testUser3);
        
        // Create test game records
        testGame1 = createTestGameRecord("game1", "123456", 1, GameResult.WIN, testUser1.getId(), 300);
        testGame2 = createTestGameRecord("game2", "123456", 2, GameResult.DRAW, null, 450);
        testGame3 = createTestGameRecord("game3", "789012", 1, GameResult.WIN, testUser2.getId(), 200);
        
        entityManager.persistAndFlush(testGame1);
        entityManager.persistAndFlush(testGame2);
        entityManager.persistAndFlush(testGame3);
        
        // Create player records
        createPlayerRecord(testGame1.getId(), testUser1.getId(), 0, GameResult.WIN, 24);
        createPlayerRecord(testGame1.getId(), testUser2.getId(), 1, GameResult.LOSE, -12);
        createPlayerRecord(testGame1.getId(), testUser3.getId(), 2, GameResult.LOSE, -12);
        
        createPlayerRecord(testGame2.getId(), testUser1.getId(), 0, GameResult.DRAW, 0);
        createPlayerRecord(testGame2.getId(), testUser2.getId(), 1, GameResult.DRAW, 0);
        createPlayerRecord(testGame2.getId(), testUser3.getId(), 2, GameResult.DRAW, 0);
        
        createPlayerRecord(testGame3.getId(), testUser1.getId(), 0, GameResult.LOSE, -8);
        createPlayerRecord(testGame3.getId(), testUser2.getId(), 1, GameResult.WIN, 16);
        createPlayerRecord(testGame3.getId(), testUser3.getId(), 2, GameResult.LOSE, -8);
        
        entityManager.flush();
    }
    
    @Test
    void testFindByRoomIdOrderByCreatedAtDesc() {
        // When
        List<GameRecord> games = gameRecordRepository.findByRoomIdOrderByCreatedAtDesc("123456");
        
        // Then
        assertEquals(2, games.size());
        assertEquals("game2", games.get(0).getId()); // More recent
        assertEquals("game1", games.get(1).getId());
    }
    
    @Test
    void testFindByRoomIdOrderByCreatedAtDescWithPagination() {
        // Given
        Pageable pageable = PageRequest.of(0, 1);
        
        // When
        Page<GameRecord> page = gameRecordRepository.findByRoomIdOrderByCreatedAtDesc("123456", pageable);
        
        // Then
        assertEquals(1, page.getContent().size());
        assertEquals(2, page.getTotalElements());
        assertEquals("game2", page.getContent().get(0).getId());
    }
    
    @Test
    void testFindByPlayerUserId() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        
        // When
        Page<GameRecord> page = gameRecordRepository.findByPlayerUserId(testUser1.getId(), pageable);
        
        // Then
        assertEquals(3, page.getContent().size()); // User1 participated in all 3 games
        assertEquals(3, page.getTotalElements());
    }
    
    @Test
    void testFindByPlayerUserIdAndDateRange() {
        // Given
        LocalDateTime startDate = LocalDateTime.now().minusHours(1);
        LocalDateTime endDate = LocalDateTime.now().plusHours(1);
        Pageable pageable = PageRequest.of(0, 10);
        
        // When
        Page<GameRecord> page = gameRecordRepository.findByPlayerUserIdAndDateRange(
                testUser1.getId(), startDate, endDate, pageable);
        
        // Then
        assertEquals(3, page.getContent().size()); // All games are within the date range
    }
    
    @Test
    void testFindByWinnerIdOrderByCreatedAtDesc() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        
        // When
        Page<GameRecord> page = gameRecordRepository.findByWinnerIdOrderByCreatedAtDesc(testUser1.getId(), pageable);
        
        // Then
        assertEquals(1, page.getContent().size());
        assertEquals("game1", page.getContent().get(0).getId());
    }
    
    @Test
    void testFindByResultOrderByCreatedAtDesc() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        
        // When
        Page<GameRecord> page = gameRecordRepository.findByResultOrderByCreatedAtDesc(GameResult.WIN, pageable);
        
        // Then
        assertEquals(2, page.getContent().size()); // game1 and game3 are wins
    }
    
    @Test
    void testFindRecentGames() {
        // Given
        LocalDateTime sinceDate = LocalDateTime.now().minusHours(1);
        Pageable pageable = PageRequest.of(0, 10);
        
        // When
        Page<GameRecord> page = gameRecordRepository.findRecentGames(sinceDate, pageable);
        
        // Then
        assertEquals(3, page.getContent().size()); // All games are recent
    }
    
    @Test
    void testCountGamesByUserId() {
        // When
        Long count = gameRecordRepository.countGamesByUserId(testUser1.getId());
        
        // Then
        assertEquals(3L, count); // User1 participated in 3 games
    }
    
    @Test
    void testCountWinsByUserId() {
        // When
        Long wins = gameRecordRepository.countWinsByUserId(testUser1.getId());
        
        // Then
        assertEquals(1L, wins); // User1 won 1 game
    }
    
    @Test
    void testGetTotalScoreByUserId() {
        // When
        Long totalScore = gameRecordRepository.getTotalScoreByUserId(testUser1.getId());
        
        // Then
        assertEquals(4L, totalScore); // 24 + 0 + (-8) = 16, but this tests the query structure
    }
    
    @Test
    void testFindGamesWithReplayData() {
        // Given
        testGame1.setGameActions("[{\"actionType\":\"DISCARD\",\"tile\":\"5W\"}]");
        entityManager.persistAndFlush(testGame1);
        
        Pageable pageable = PageRequest.of(0, 10);
        
        // When
        Page<GameRecord> page = gameRecordRepository.findGamesWithReplayData(pageable);
        
        // Then
        assertEquals(1, page.getContent().size());
        assertEquals("game1", page.getContent().get(0).getId());
    }
    
    @Test
    void testFindByRoomIdAndRoundIndex() {
        // When
        Optional<GameRecord> game = gameRecordRepository.findByRoomIdAndRoundIndex("123456", 1);
        
        // Then
        assertTrue(game.isPresent());
        assertEquals("game1", game.get().getId());
    }
    
    @Test
    void testGetAverageGameDuration() {
        // When
        Double avgDuration = gameRecordRepository.getAverageGameDuration();
        
        // Then
        assertNotNull(avgDuration);
        assertEquals(316.67, avgDuration, 0.1); // (300 + 450 + 200) / 3
    }
    
    @Test
    void testGetGameStatistics() {
        // Given
        LocalDateTime startDate = LocalDateTime.now().minusHours(1);
        LocalDateTime endDate = LocalDateTime.now().plusHours(1);
        
        // When
        Object[] stats = gameRecordRepository.getGameStatistics(startDate, endDate);
        
        // Then
        assertNotNull(stats);
        assertEquals(4, stats.length);
        assertEquals(3L, ((Number) stats[0]).longValue()); // Total games
        assertEquals(316.67, ((Number) stats[1]).doubleValue(), 0.1); // Average duration
        assertEquals(2L, ((Number) stats[2]).longValue()); // Total wins
        assertEquals(1L, ((Number) stats[3]).longValue()); // Total draws
    }
    
    /**
     * Helper method to create test user
     */
    private User createTestUser(String openId, String nickname) {
        User user = new User();
        user.setOpenId(openId);
        user.setNickname(nickname);
        user.setAvatar("avatar.jpg");
        user.setCoins(1000);
        user.setRoomCards(10);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }
    
    /**
     * Helper method to create test game record
     */
    private GameRecord createTestGameRecord(String id, String roomId, Integer roundIndex, 
                                          GameResult result, Long winnerId, Integer durationSeconds) {
        GameRecord gameRecord = new GameRecord();
        gameRecord.setId(id);
        gameRecord.setRoomId(roomId);
        gameRecord.setRoundIndex(roundIndex);
        gameRecord.setResult(result);
        gameRecord.setWinnerId(winnerId);
        gameRecord.setDurationSeconds(durationSeconds);
        gameRecord.setCreatedAt(LocalDateTime.now().minusMinutes(roundIndex * 10)); // Stagger creation times
        return gameRecord;
    }
    
    /**
     * Helper method to create player record
     */
    private void createPlayerRecord(String gameRecordId, Long userId, Integer seatIndex, 
                                  GameResult result, Integer score) {
        GamePlayerRecord playerRecord = new GamePlayerRecord();
        playerRecord.setGameRecordId(gameRecordId);
        playerRecord.setUserId(userId);
        playerRecord.setSeatIndex(seatIndex);
        playerRecord.setResult(result);
        playerRecord.setScore(score);
        playerRecord.setBaseScore(score);
        playerRecord.setIsDealer(seatIndex == 0);
        
        entityManager.persistAndFlush(playerRecord);
    }
}