package com.mahjong.model.entity;

import com.mahjong.model.enums.RoomStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class RoomTest {
    
    private Room room;
    
    @BeforeEach
    void setUp() {
        room = new Room("123456", 1L, 1L);
    }
    
    @Test
    void testRoomCreation() {
        assertNotNull(room);
        assertEquals("123456", room.getId());
        assertEquals(1L, room.getOwnerId());
        assertEquals(1L, room.getRuleId());
        assertEquals(RoomStatus.WAITING, room.getStatus());
        assertEquals(0, room.getRoundIndex());
        assertEquals(8, room.getMaxRounds());
        assertNotNull(room.getLastActivityAt());
        assertTrue(room.getPlayers().isEmpty());
    }
    
    @Test
    void testDefaultConstructor() {
        Room defaultRoom = new Room();
        assertNotNull(defaultRoom);
        assertNull(defaultRoom.getId());
        assertNull(defaultRoom.getOwnerId());
        assertEquals(RoomStatus.WAITING, defaultRoom.getStatus());
        assertEquals(0, defaultRoom.getRoundIndex());
        assertEquals(8, defaultRoom.getMaxRounds());
        assertNotNull(defaultRoom.getLastActivityAt());
    }
    
    @Test
    void testRoomStatusUpdate() {
        LocalDateTime beforeUpdate = room.getLastActivityAt();
        
        // Wait a bit to ensure time difference
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        room.setStatus(RoomStatus.PLAYING);
        assertEquals(RoomStatus.PLAYING, room.getStatus());
        assertTrue(room.getLastActivityAt().isAfter(beforeUpdate));
    }
    
    @Test
    void testPlayerManagement() {
        assertTrue(room.isEmpty());
        assertFalse(room.isFull());
        
        RoomPlayer player1 = new RoomPlayer("123456", 1L, 0);
        RoomPlayer player2 = new RoomPlayer("123456", 2L, 1);
        RoomPlayer player3 = new RoomPlayer("123456", 3L, 2);
        
        room.addPlayer(player1);
        assertEquals(1, room.getPlayers().size());
        assertFalse(room.isEmpty());
        assertFalse(room.isFull());
        assertTrue(room.hasPlayer(1L));
        assertFalse(room.hasPlayer(4L));
        
        room.addPlayer(player2);
        room.addPlayer(player3);
        assertEquals(3, room.getPlayers().size());
        assertTrue(room.isFull());
        
        // Try to add a 4th player - should not be added
        RoomPlayer player4 = new RoomPlayer("123456", 4L, 0);
        room.addPlayer(player4);
        assertEquals(3, room.getPlayers().size());
        
        // Test getting player
        RoomPlayer retrievedPlayer = room.getPlayer(2L);
        assertNotNull(retrievedPlayer);
        assertEquals(2L, retrievedPlayer.getUserId());
        
        assertNull(room.getPlayer(5L));
        
        // Test removing player
        room.removePlayer(2L);
        assertEquals(2, room.getPlayers().size());
        assertFalse(room.hasPlayer(2L));
    }
    
    @Test
    void testCanStart() {
        assertFalse(room.canStart());
        
        // Add 2 players - still can't start
        room.addPlayer(new RoomPlayer("123456", 1L, 0));
        room.addPlayer(new RoomPlayer("123456", 2L, 1));
        assertFalse(room.canStart());
        
        // Add 3rd player - now can start
        room.addPlayer(new RoomPlayer("123456", 3L, 2));
        assertTrue(room.canStart());
        
        // Change status - can't start anymore
        room.setStatus(RoomStatus.PLAYING);
        assertFalse(room.canStart());
    }
    
    @Test
    void testIsOwner() {
        assertTrue(room.isOwner(1L));
        assertFalse(room.isOwner(2L));
        assertFalse(room.isOwner(null));
    }
    
    @Test
    void testIsActive() {
        assertTrue(room.isActive());
        
        room.setStatus(RoomStatus.READY);
        assertTrue(room.isActive());
        
        room.setStatus(RoomStatus.PLAYING);
        assertTrue(room.isActive());
        
        room.setStatus(RoomStatus.FINISHED);
        assertFalse(room.isActive());
        
        room.setStatus(RoomStatus.DISSOLVED);
        assertFalse(room.isActive());
    }
    
    @Test
    void testIsInactive() {
        assertFalse(room.isInactive());
        
        // Set last activity to 31 minutes ago
        room.setLastActivityAt(LocalDateTime.now().minusMinutes(31));
        assertTrue(room.isInactive());
        
        // Set last activity to 29 minutes ago
        room.setLastActivityAt(LocalDateTime.now().minusMinutes(29));
        assertFalse(room.isInactive());
    }
    
    @Test
    void testRoundManagement() {
        assertEquals(0, room.getRoundIndex());
        assertFalse(room.isLastRound());
        
        room.nextRound();
        assertEquals(1, room.getRoundIndex());
        
        // Set to last round
        room.setRoundIndex(8);
        assertTrue(room.isLastRound());
        
        room.setRoundIndex(7);
        assertFalse(room.isLastRound());
        
        room.nextRound();
        assertTrue(room.isLastRound());
    }
    
    @Test
    void testTransferOwnership() {
        assertEquals(1L, room.getOwnerId());
        
        // Add players
        room.addPlayer(new RoomPlayer("123456", 1L, 0));
        room.addPlayer(new RoomPlayer("123456", 2L, 1));
        
        // Transfer to existing player
        room.transferOwnership(2L);
        assertEquals(2L, room.getOwnerId());
        
        // Try to transfer to non-existing player - should not change
        room.transferOwnership(3L);
        assertEquals(2L, room.getOwnerId());
    }
    
    @Test
    void testUpdateActivity() {
        LocalDateTime beforeUpdate = room.getLastActivityAt();
        
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        room.updateActivity();
        assertTrue(room.getLastActivityAt().isAfter(beforeUpdate));
    }
    
    @Test
    void testEqualsAndHashCode() {
        Room room1 = new Room("123456", 1L, 1L);
        Room room2 = new Room("123456", 2L, 2L);
        Room room3 = new Room("654321", 1L, 1L);
        
        assertEquals(room1, room2); // Same ID
        assertEquals(room1.hashCode(), room2.hashCode());
        
        assertNotEquals(room1, room3); // Different ID
        assertNotEquals(room1.hashCode(), room3.hashCode());
    }
    
    @Test
    void testToString() {
        room.addPlayer(new RoomPlayer("123456", 1L, 0));
        room.addPlayer(new RoomPlayer("123456", 2L, 1));
        room.setRoundIndex(3);
        room.setMaxRounds(10);
        
        String toString = room.toString();
        assertTrue(toString.contains("id='123456'"));
        assertTrue(toString.contains("ownerId=1"));
        assertTrue(toString.contains("status=WAITING"));
        assertTrue(toString.contains("playerCount=2"));
        assertTrue(toString.contains("roundIndex=3"));
        assertTrue(toString.contains("maxRounds=10"));
    }
    
    @Test
    void testSettersAndGetters() {
        room.setCurrentDealerUserId(2L);
        assertEquals(2L, room.getCurrentDealerUserId());
        
        room.setCurrentGameId("game-123");
        assertEquals("game-123", room.getCurrentGameId());
        
        room.setMaxRounds(16);
        assertEquals(16, room.getMaxRounds());
        
        LocalDateTime now = LocalDateTime.now();
        room.setCreatedAt(now);
        assertEquals(now, room.getCreatedAt());
        
        room.setUpdatedAt(now);
        assertEquals(now, room.getUpdatedAt());
    }
}