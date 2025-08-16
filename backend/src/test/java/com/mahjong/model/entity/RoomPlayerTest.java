package com.mahjong.model.entity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class RoomPlayerTest {
    
    private RoomPlayer roomPlayer;
    
    @BeforeEach
    void setUp() {
        roomPlayer = new RoomPlayer("123456", 1L, 0);
    }
    
    @Test
    void testRoomPlayerCreation() {
        assertNotNull(roomPlayer);
        assertEquals("123456", roomPlayer.getRoomId());
        assertEquals(1L, roomPlayer.getUserId());
        assertEquals(0, roomPlayer.getSeatIndex());
        assertEquals(0, roomPlayer.getTotalScore());
        assertFalse(roomPlayer.getIsReady());
        assertTrue(roomPlayer.getIsOnline());
        assertNotNull(roomPlayer.getLastSeenAt());
    }
    
    @Test
    void testDefaultConstructor() {
        RoomPlayer defaultPlayer = new RoomPlayer();
        assertNotNull(defaultPlayer);
        assertNull(defaultPlayer.getRoomId());
        assertNull(defaultPlayer.getUserId());
        assertNull(defaultPlayer.getSeatIndex());
        assertEquals(0, defaultPlayer.getTotalScore());
        assertFalse(defaultPlayer.getIsReady());
        assertTrue(defaultPlayer.getIsOnline());
        assertNotNull(defaultPlayer.getLastSeenAt());
    }
    
    @Test
    void testReadyStatus() {
        assertFalse(roomPlayer.isReady());
        
        roomPlayer.setReady();
        assertTrue(roomPlayer.isReady());
        assertTrue(roomPlayer.getIsReady());
        
        roomPlayer.setNotReady();
        assertFalse(roomPlayer.isReady());
        assertFalse(roomPlayer.getIsReady());
    }
    
    @Test
    void testOnlineStatus() {
        assertTrue(roomPlayer.isOnline());
        
        LocalDateTime beforeOffline = roomPlayer.getLastSeenAt();
        roomPlayer.goOffline();
        assertFalse(roomPlayer.isOnline());
        assertFalse(roomPlayer.getIsOnline());
        // Last seen should not change when going offline
        assertEquals(beforeOffline, roomPlayer.getLastSeenAt());
        
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        roomPlayer.goOnline();
        assertTrue(roomPlayer.isOnline());
        assertTrue(roomPlayer.getIsOnline());
        assertTrue(roomPlayer.getLastSeenAt().isAfter(beforeOffline));
    }
    
    @Test
    void testSetOnlineStatus() {
        LocalDateTime beforeUpdate = roomPlayer.getLastSeenAt();
        
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        roomPlayer.setIsOnline(true);
        assertTrue(roomPlayer.getIsOnline());
        assertTrue(roomPlayer.getLastSeenAt().isAfter(beforeUpdate));
        
        LocalDateTime afterOnline = roomPlayer.getLastSeenAt();
        roomPlayer.setIsOnline(false);
        assertFalse(roomPlayer.getIsOnline());
        // Last seen should not change when setting to false
        assertEquals(afterOnline, roomPlayer.getLastSeenAt());
    }
    
    @Test
    void testUpdateLastSeen() {
        LocalDateTime beforeUpdate = roomPlayer.getLastSeenAt();
        
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        roomPlayer.updateLastSeen();
        assertTrue(roomPlayer.getLastSeenAt().isAfter(beforeUpdate));
    }
    
    @Test
    void testScoreManagement() {
        assertEquals(0, roomPlayer.getTotalScore());
        
        roomPlayer.addScore(10);
        assertEquals(10, roomPlayer.getTotalScore());
        
        roomPlayer.addScore(-5);
        assertEquals(5, roomPlayer.getTotalScore());
        
        roomPlayer.addScore(null);
        assertEquals(5, roomPlayer.getTotalScore());
        
        roomPlayer.resetScore();
        assertEquals(0, roomPlayer.getTotalScore());
    }
    
    @Test
    void testIsDisconnected() {
        assertFalse(roomPlayer.isDisconnected());
        
        // Player is online - not disconnected
        roomPlayer.setIsOnline(true);
        assertFalse(roomPlayer.isDisconnected());
        
        // Player goes offline but recently seen - not disconnected
        roomPlayer.setIsOnline(false);
        roomPlayer.setLastSeenAt(LocalDateTime.now().minusSeconds(20));
        assertFalse(roomPlayer.isDisconnected());
        
        // Player offline and not seen for more than 30 seconds - disconnected
        roomPlayer.setLastSeenAt(LocalDateTime.now().minusSeconds(35));
        assertTrue(roomPlayer.isDisconnected());
        
        // Player comes back online - not disconnected
        roomPlayer.setIsOnline(true);
        assertFalse(roomPlayer.isDisconnected());
    }
    
    @Test
    void testEqualsAndHashCode() {
        RoomPlayer player1 = new RoomPlayer("123456", 1L, 0);
        RoomPlayer player2 = new RoomPlayer("123456", 1L, 1); // Same room and user, different seat
        RoomPlayer player3 = new RoomPlayer("123456", 2L, 0); // Same room, different user
        RoomPlayer player4 = new RoomPlayer("654321", 1L, 0); // Different room, same user
        
        assertEquals(player1, player2); // Same room and user
        assertEquals(player1.hashCode(), player2.hashCode());
        
        assertNotEquals(player1, player3); // Different user
        assertNotEquals(player1.hashCode(), player3.hashCode());
        
        assertNotEquals(player1, player4); // Different room
        assertNotEquals(player1.hashCode(), player4.hashCode());
    }
    
    @Test
    void testToString() {
        roomPlayer.setId(1L);
        roomPlayer.setTotalScore(50);
        roomPlayer.setIsReady(true);
        
        String toString = roomPlayer.toString();
        assertTrue(toString.contains("id=1"));
        assertTrue(toString.contains("roomId='123456'"));
        assertTrue(toString.contains("userId=1"));
        assertTrue(toString.contains("seatIndex=0"));
        assertTrue(toString.contains("totalScore=50"));
        assertTrue(toString.contains("isReady=true"));
        assertTrue(toString.contains("isOnline=true"));
    }
    
    @Test
    void testSettersAndGetters() {
        roomPlayer.setId(123L);
        assertEquals(123L, roomPlayer.getId());
        
        roomPlayer.setRoomId("654321");
        assertEquals("654321", roomPlayer.getRoomId());
        
        roomPlayer.setUserId(2L);
        assertEquals(2L, roomPlayer.getUserId());
        
        roomPlayer.setSeatIndex(2);
        assertEquals(2, roomPlayer.getSeatIndex());
        
        roomPlayer.setTotalScore(100);
        assertEquals(100, roomPlayer.getTotalScore());
        
        roomPlayer.setIsReady(true);
        assertTrue(roomPlayer.getIsReady());
        
        LocalDateTime now = LocalDateTime.now();
        roomPlayer.setJoinedAt(now);
        assertEquals(now, roomPlayer.getJoinedAt());
        
        roomPlayer.setLastSeenAt(now);
        assertEquals(now, roomPlayer.getLastSeenAt());
    }
    
    @Test
    void testRelationships() {
        Room room = new Room("123456", 1L, 1L);
        User user = new User("open_id", "TestUser");
        
        roomPlayer.setRoom(room);
        roomPlayer.setUser(user);
        
        assertEquals(room, roomPlayer.getRoom());
        assertEquals(user, roomPlayer.getUser());
    }
}