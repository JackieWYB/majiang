package com.mahjong.model.entity;

import com.mahjong.model.enums.GameResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GamePlayerRecordTest {
    
    private GamePlayerRecord playerRecord;
    
    @BeforeEach
    void setUp() {
        playerRecord = new GamePlayerRecord("game-123", 1L, 0, GameResult.WIN);
    }
    
    @Test
    void testGamePlayerRecordCreation() {
        assertNotNull(playerRecord);
        assertEquals("game-123", playerRecord.getGameRecordId());
        assertEquals(1L, playerRecord.getUserId());
        assertEquals(0, playerRecord.getSeatIndex());
        assertEquals(GameResult.WIN, playerRecord.getResult());
        assertEquals(0, playerRecord.getScore());
        assertEquals(0, playerRecord.getBaseScore());
        assertEquals(0, playerRecord.getGangScore());
        assertEquals(1.0, playerRecord.getMultiplier());
        assertFalse(playerRecord.getIsDealer());
        assertFalse(playerRecord.getIsSelfDraw());
    }
    
    @Test
    void testDefaultConstructor() {
        GamePlayerRecord defaultRecord = new GamePlayerRecord();
        assertNotNull(defaultRecord);
        assertNull(defaultRecord.getGameRecordId());
        assertNull(defaultRecord.getUserId());
        assertNull(defaultRecord.getSeatIndex());
        assertNull(defaultRecord.getResult());
        assertEquals(0, defaultRecord.getScore());
        assertEquals(0, defaultRecord.getBaseScore());
        assertEquals(0, defaultRecord.getGangScore());
        assertEquals(1.0, defaultRecord.getMultiplier());
        assertFalse(defaultRecord.getIsDealer());
        assertFalse(defaultRecord.getIsSelfDraw());
    }
    
    @Test
    void testResultChecks() {
        assertTrue(playerRecord.isWinner());
        assertFalse(playerRecord.isLoser());
        assertFalse(playerRecord.isDraw());
        
        playerRecord.setResult(GameResult.LOSE);
        assertFalse(playerRecord.isWinner());
        assertTrue(playerRecord.isLoser());
        assertFalse(playerRecord.isDraw());
        
        playerRecord.setResult(GameResult.DRAW);
        assertFalse(playerRecord.isWinner());
        assertFalse(playerRecord.isLoser());
        assertTrue(playerRecord.isDraw());
    }
    
    @Test
    void testDealerAndSelfDraw() {
        assertFalse(playerRecord.isDealer());
        assertFalse(playerRecord.isSelfDraw());
        
        playerRecord.setIsDealer(true);
        assertTrue(playerRecord.isDealer());
        assertTrue(playerRecord.getIsDealer());
        
        playerRecord.setIsSelfDraw(true);
        assertTrue(playerRecord.isSelfDraw());
        assertTrue(playerRecord.getIsSelfDraw());
        
        playerRecord.setIsDealer(false);
        assertFalse(playerRecord.isDealer());
        assertFalse(playerRecord.getIsDealer());
        
        playerRecord.setIsSelfDraw(false);
        assertFalse(playerRecord.isSelfDraw());
        assertFalse(playerRecord.getIsSelfDraw());
    }
    
    @Test
    void testScoreCalculations() {
        playerRecord.setBaseScore(8);
        playerRecord.setGangScore(2);
        playerRecord.setMultiplier(2.0);
        
        assertEquals(10, playerRecord.getTotalScore()); // baseScore + gangScore
        assertEquals(20, playerRecord.getFinalScore()); // totalScore * multiplier
        
        playerRecord.addGangScore(3);
        assertEquals(5, playerRecord.getGangScore());
        assertEquals(13, playerRecord.getTotalScore());
        assertEquals(26, playerRecord.getFinalScore());
        
        // Test adding null and negative gang score
        playerRecord.addGangScore(null);
        assertEquals(5, playerRecord.getGangScore());
        
        playerRecord.addGangScore(-2);
        assertEquals(5, playerRecord.getGangScore()); // Should not change for negative values
        
        playerRecord.addGangScore(0);
        assertEquals(5, playerRecord.getGangScore()); // Should not change for zero
    }
    
    @Test
    void testEqualsAndHashCode() {
        GamePlayerRecord record1 = new GamePlayerRecord("game-123", 1L, 0, GameResult.WIN);
        GamePlayerRecord record2 = new GamePlayerRecord("game-123", 1L, 1, GameResult.LOSE); // Same game and user, different seat and result
        GamePlayerRecord record3 = new GamePlayerRecord("game-123", 2L, 0, GameResult.WIN); // Same game, different user
        GamePlayerRecord record4 = new GamePlayerRecord("game-456", 1L, 0, GameResult.WIN); // Different game, same user
        
        assertEquals(record1, record2); // Same game and user
        assertEquals(record1.hashCode(), record2.hashCode());
        
        assertNotEquals(record1, record3); // Different user
        assertNotEquals(record1.hashCode(), record3.hashCode());
        
        assertNotEquals(record1, record4); // Different game
        assertNotEquals(record1.hashCode(), record4.hashCode());
    }
    
    @Test
    void testToString() {
        playerRecord.setId(1L);
        playerRecord.setScore(16);
        playerRecord.setIsDealer(true);
        playerRecord.setIsSelfDraw(true);
        
        String toString = playerRecord.toString();
        assertTrue(toString.contains("id=1"));
        assertTrue(toString.contains("gameRecordId='game-123'"));
        assertTrue(toString.contains("userId=1"));
        assertTrue(toString.contains("seatIndex=0"));
        assertTrue(toString.contains("result=WIN"));
        assertTrue(toString.contains("score=16"));
        assertTrue(toString.contains("isDealer=true"));
        assertTrue(toString.contains("isSelfDraw=true"));
    }
    
    @Test
    void testSettersAndGetters() {
        playerRecord.setId(123L);
        assertEquals(123L, playerRecord.getId());
        
        playerRecord.setGameRecordId("game-456");
        assertEquals("game-456", playerRecord.getGameRecordId());
        
        playerRecord.setUserId(2L);
        assertEquals(2L, playerRecord.getUserId());
        
        playerRecord.setSeatIndex(1);
        assertEquals(1, playerRecord.getSeatIndex());
        
        playerRecord.setScore(24);
        assertEquals(24, playerRecord.getScore());
        
        playerRecord.setBaseScore(12);
        assertEquals(12, playerRecord.getBaseScore());
        
        playerRecord.setGangScore(4);
        assertEquals(4, playerRecord.getGangScore());
        
        playerRecord.setMultiplier(1.5);
        assertEquals(1.5, playerRecord.getMultiplier());
        
        playerRecord.setWinningHand("[\"1W\",\"2W\",\"3W\"]");
        assertEquals("[\"1W\",\"2W\",\"3W\"]", playerRecord.getWinningHand());
        
        playerRecord.setFinalHand("[\"4W\",\"5W\",\"6W\"]");
        assertEquals("[\"4W\",\"5W\",\"6W\"]", playerRecord.getFinalHand());
        
        playerRecord.setMelds("[{\"type\":\"peng\",\"tiles\":[\"7W\",\"7W\",\"7W\"]}]");
        assertEquals("[{\"type\":\"peng\",\"tiles\":[\"7W\",\"7W\",\"7W\"]}]", playerRecord.getMelds());
    }
    
    @Test
    void testRelationships() {
        GameRecord gameRecord = new GameRecord("game-123", "123456", 1, GameResult.WIN);
        User user = new User("user_id", "TestUser");
        
        playerRecord.setGameRecord(gameRecord);
        playerRecord.setUser(user);
        
        assertEquals(gameRecord, playerRecord.getGameRecord());
        assertEquals(user, playerRecord.getUser());
    }
    
    @Test
    void testComplexScenario() {
        // Set up a winning dealer with self-draw
        playerRecord.setResult(GameResult.WIN);
        playerRecord.setIsDealer(true);
        playerRecord.setIsSelfDraw(true);
        playerRecord.setBaseScore(4);
        playerRecord.setGangScore(2);
        playerRecord.setMultiplier(3.0); // Dealer multiplier + self-draw bonus
        playerRecord.setScore(18); // Final calculated score
        
        playerRecord.setWinningHand("[\"1W\",\"1W\",\"2W\",\"2W\",\"3W\",\"3W\",\"4W\",\"4W\",\"5W\",\"5W\",\"6W\",\"6W\",\"7W\",\"7W\"]");
        playerRecord.setFinalHand("[\"1W\",\"1W\",\"2W\",\"2W\",\"3W\",\"3W\",\"4W\",\"4W\",\"5W\",\"5W\",\"6W\",\"6W\",\"7W\",\"7W\"]");
        playerRecord.setMelds("[{\"type\":\"gang\",\"tiles\":[\"8W\",\"8W\",\"8W\",\"8W\"]}]");
        
        // Verify all conditions
        assertTrue(playerRecord.isWinner());
        assertTrue(playerRecord.isDealer());
        assertTrue(playerRecord.isSelfDraw());
        assertEquals(6, playerRecord.getTotalScore()); // 4 + 2
        assertEquals(18, playerRecord.getFinalScore()); // 6 * 3.0
        
        assertNotNull(playerRecord.getWinningHand());
        assertNotNull(playerRecord.getFinalHand());
        assertNotNull(playerRecord.getMelds());
        
        // Add more gang score
        playerRecord.addGangScore(1);
        assertEquals(3, playerRecord.getGangScore());
        assertEquals(7, playerRecord.getTotalScore());
        assertEquals(21, playerRecord.getFinalScore());
    }
    
    @Test
    void testLosingPlayerScenario() {
        GamePlayerRecord loser = new GamePlayerRecord("game-123", 2L, 1, GameResult.LOSE);
        loser.setIsDealer(false);
        loser.setIsSelfDraw(false);
        loser.setBaseScore(-8);
        loser.setGangScore(0);
        loser.setMultiplier(1.0);
        loser.setScore(-8);
        
        loser.setFinalHand("[\"1W\",\"2W\",\"3W\",\"4W\",\"5W\",\"6W\",\"7W\",\"8W\",\"9W\",\"1W\",\"2W\",\"3W\",\"4W\"]");
        
        assertTrue(loser.isLoser());
        assertFalse(loser.isWinner());
        assertFalse(loser.isDealer());
        assertFalse(loser.isSelfDraw());
        assertEquals(-8, loser.getTotalScore());
        assertEquals(-8, loser.getFinalScore());
        
        assertNull(loser.getWinningHand()); // Loser has no winning hand
        assertNotNull(loser.getFinalHand());
    }
}