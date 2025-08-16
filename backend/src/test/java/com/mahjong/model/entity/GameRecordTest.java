package com.mahjong.model.entity;

import com.mahjong.model.enums.GameResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GameRecordTest {
    
    private GameRecord gameRecord;
    
    @BeforeEach
    void setUp() {
        gameRecord = new GameRecord("game-123", "123456", 1, GameResult.WIN);
    }
    
    @Test
    void testGameRecordCreation() {
        assertNotNull(gameRecord);
        assertEquals("game-123", gameRecord.getId());
        assertEquals("123456", gameRecord.getRoomId());
        assertEquals(1, gameRecord.getRoundIndex());
        assertEquals(GameResult.WIN, gameRecord.getResult());
        assertEquals(0, gameRecord.getBaseScore());
        assertEquals(1.0, gameRecord.getMultiplier());
        assertEquals(0, gameRecord.getFinalScore());
        assertTrue(gameRecord.getPlayerRecords().isEmpty());
    }
    
    @Test
    void testDefaultConstructor() {
        GameRecord defaultRecord = new GameRecord();
        assertNotNull(defaultRecord);
        assertNull(defaultRecord.getId());
        assertNull(defaultRecord.getRoomId());
        assertNull(defaultRecord.getRoundIndex());
        assertNull(defaultRecord.getResult());
        assertEquals(0, defaultRecord.getBaseScore());
        assertEquals(1.0, defaultRecord.getMultiplier());
        assertEquals(0, defaultRecord.getFinalScore());
    }
    
    @Test
    void testHasWinner() {
        assertFalse(gameRecord.hasWinner()); // No winner set
        
        gameRecord.setWinnerId(1L);
        assertTrue(gameRecord.hasWinner());
        
        gameRecord.setResult(GameResult.DRAW);
        assertFalse(gameRecord.hasWinner()); // Draw result
        
        gameRecord.setResult(GameResult.WIN);
        assertTrue(gameRecord.hasWinner());
    }
    
    @Test
    void testIsDraw() {
        assertFalse(gameRecord.isDraw());
        
        gameRecord.setResult(GameResult.DRAW);
        assertTrue(gameRecord.isDraw());
        
        gameRecord.setResult(GameResult.LOSE);
        assertFalse(gameRecord.isDraw());
    }
    
    @Test
    void testIsWinner() {
        assertFalse(gameRecord.isWinner(1L)); // No winner set
        
        gameRecord.setWinnerId(1L);
        assertTrue(gameRecord.isWinner(1L));
        assertFalse(gameRecord.isWinner(2L));
        assertFalse(gameRecord.isWinner(null));
    }
    
    @Test
    void testIsDealer() {
        assertFalse(gameRecord.isDealer(1L)); // No dealer set
        
        gameRecord.setDealerUserId(1L);
        assertTrue(gameRecord.isDealer(1L));
        assertFalse(gameRecord.isDealer(2L));
        assertFalse(gameRecord.isDealer(null));
    }
    
    @Test
    void testPlayerRecordManagement() {
        assertEquals(0, gameRecord.getTotalPlayers());
        
        GamePlayerRecord player1 = new GamePlayerRecord("game-123", 1L, 0, GameResult.WIN);
        GamePlayerRecord player2 = new GamePlayerRecord("game-123", 2L, 1, GameResult.LOSE);
        
        gameRecord.addPlayerRecord(player1);
        assertEquals(1, gameRecord.getTotalPlayers());
        assertEquals(gameRecord, player1.getGameRecord());
        
        gameRecord.addPlayerRecord(player2);
        assertEquals(2, gameRecord.getTotalPlayers());
        
        // Test getting player record
        GamePlayerRecord retrieved = gameRecord.getPlayerRecord(1L);
        assertNotNull(retrieved);
        assertEquals(1L, retrieved.getUserId());
        assertEquals(GameResult.WIN, retrieved.getResult());
        
        assertNull(gameRecord.getPlayerRecord(3L));
    }
    
    @Test
    void testFormattedDuration() {
        assertEquals("0:00", gameRecord.getFormattedDuration());
        
        gameRecord.setDurationSeconds(65);
        assertEquals("1:05", gameRecord.getFormattedDuration());
        
        gameRecord.setDurationSeconds(3661);
        assertEquals("61:01", gameRecord.getFormattedDuration());
        
        gameRecord.setDurationSeconds(30);
        assertEquals("0:30", gameRecord.getFormattedDuration());
        
        gameRecord.setDurationSeconds(null);
        assertEquals("0:00", gameRecord.getFormattedDuration());
    }
    
    @Test
    void testEqualsAndHashCode() {
        GameRecord record1 = new GameRecord("game-123", "123456", 1, GameResult.WIN);
        GameRecord record2 = new GameRecord("game-123", "654321", 2, GameResult.LOSE);
        GameRecord record3 = new GameRecord("game-456", "123456", 1, GameResult.WIN);
        
        assertEquals(record1, record2); // Same ID
        assertEquals(record1.hashCode(), record2.hashCode());
        
        assertNotEquals(record1, record3); // Different ID
        assertNotEquals(record1.hashCode(), record3.hashCode());
    }
    
    @Test
    void testToString() {
        gameRecord.setWinnerId(1L);
        gameRecord.setFinalScore(24);
        gameRecord.setDurationSeconds(120);
        
        String toString = gameRecord.toString();
        assertTrue(toString.contains("id='game-123'"));
        assertTrue(toString.contains("roomId='123456'"));
        assertTrue(toString.contains("roundIndex=1"));
        assertTrue(toString.contains("result=WIN"));
        assertTrue(toString.contains("winnerId=1"));
        assertTrue(toString.contains("finalScore=24"));
        assertTrue(toString.contains("durationSeconds=120"));
    }
    
    @Test
    void testSettersAndGetters() {
        gameRecord.setWinnerId(2L);
        assertEquals(2L, gameRecord.getWinnerId());
        
        gameRecord.setDurationSeconds(300);
        assertEquals(300, gameRecord.getDurationSeconds());
        
        gameRecord.setWinningTile("1W");
        assertEquals("1W", gameRecord.getWinningTile());
        
        gameRecord.setWinningType("Seven Pairs");
        assertEquals("Seven Pairs", gameRecord.getWinningType());
        
        gameRecord.setBaseScore(4);
        assertEquals(4, gameRecord.getBaseScore());
        
        gameRecord.setMultiplier(2.0);
        assertEquals(2.0, gameRecord.getMultiplier());
        
        gameRecord.setFinalScore(16);
        assertEquals(16, gameRecord.getFinalScore());
        
        gameRecord.setRandomSeed("seed123");
        assertEquals("seed123", gameRecord.getRandomSeed());
        
        gameRecord.setGameActions("[{\"action\":\"draw\"}]");
        assertEquals("[{\"action\":\"draw\"}]", gameRecord.getGameActions());
        
        gameRecord.setFinalHands("{\"player1\":[\"1W\",\"2W\"]}");
        assertEquals("{\"player1\":[\"1W\",\"2W\"]}", gameRecord.getFinalHands());
    }
    
    @Test
    void testRelationships() {
        Room room = new Room("123456", 1L, 1L);
        User winner = new User("winner_id", "Winner");
        User dealer = new User("dealer_id", "Dealer");
        
        gameRecord.setRoom(room);
        gameRecord.setWinner(winner);
        gameRecord.setDealer(dealer);
        
        assertEquals(room, gameRecord.getRoom());
        assertEquals(winner, gameRecord.getWinner());
        assertEquals(dealer, gameRecord.getDealer());
    }
    
    @Test
    void testComplexScenario() {
        // Set up a complete game record
        gameRecord.setWinnerId(1L);
        gameRecord.setDealerUserId(2L);
        gameRecord.setDurationSeconds(180);
        gameRecord.setWinningTile("5W");
        gameRecord.setWinningType("All Pungs");
        gameRecord.setBaseScore(8);
        gameRecord.setMultiplier(2.0);
        gameRecord.setFinalScore(16);
        
        // Add player records
        GamePlayerRecord winner = new GamePlayerRecord("game-123", 1L, 0, GameResult.WIN);
        winner.setScore(16);
        winner.setIsDealer(false);
        winner.setIsSelfDraw(true);
        
        GamePlayerRecord dealer = new GamePlayerRecord("game-123", 2L, 1, GameResult.LOSE);
        dealer.setScore(-8);
        dealer.setIsDealer(true);
        dealer.setIsSelfDraw(false);
        
        GamePlayerRecord player3 = new GamePlayerRecord("game-123", 3L, 2, GameResult.LOSE);
        player3.setScore(-8);
        player3.setIsDealer(false);
        player3.setIsSelfDraw(false);
        
        gameRecord.addPlayerRecord(winner);
        gameRecord.addPlayerRecord(dealer);
        gameRecord.addPlayerRecord(player3);
        
        // Verify the complete setup
        assertTrue(gameRecord.hasWinner());
        assertTrue(gameRecord.isWinner(1L));
        assertTrue(gameRecord.isDealer(2L));
        assertEquals(3, gameRecord.getTotalPlayers());
        assertEquals("3:00", gameRecord.getFormattedDuration());
        
        GamePlayerRecord winnerRecord = gameRecord.getPlayerRecord(1L);
        assertTrue(winnerRecord.isWinner());
        assertTrue(winnerRecord.isSelfDraw());
        assertFalse(winnerRecord.isDealer());
        
        GamePlayerRecord dealerRecord = gameRecord.getPlayerRecord(2L);
        assertTrue(dealerRecord.isLoser());
        assertFalse(dealerRecord.isSelfDraw());
        assertTrue(dealerRecord.isDealer());
    }
}