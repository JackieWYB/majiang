package com.mahjong.model.entity;

import com.mahjong.model.enums.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UserTest {
    
    private User user;
    
    @BeforeEach
    void setUp() {
        user = new User("test_open_id", "TestUser");
    }
    
    @Test
    void testUserCreation() {
        assertNotNull(user);
        assertEquals("test_open_id", user.getOpenId());
        assertEquals("TestUser", user.getNickname());
        assertEquals(UserStatus.ACTIVE, user.getStatus());
        assertEquals(0, user.getCoins());
        assertEquals(0, user.getRoomCards());
    }
    
    @Test
    void testDefaultConstructor() {
        User defaultUser = new User();
        assertNotNull(defaultUser);
        assertNull(defaultUser.getOpenId());
        assertNull(defaultUser.getNickname());
        assertEquals(UserStatus.ACTIVE, defaultUser.getStatus());
        assertEquals(0, defaultUser.getCoins());
        assertEquals(0, defaultUser.getRoomCards());
    }
    
    @Test
    void testIsActive() {
        assertTrue(user.isActive());
        assertFalse(user.isBanned());
        
        user.setStatus(UserStatus.BANNED);
        assertFalse(user.isActive());
        assertTrue(user.isBanned());
        
        user.setStatus(UserStatus.INACTIVE);
        assertFalse(user.isActive());
        assertFalse(user.isBanned());
    }
    
    @Test
    void testAddCoins() {
        assertEquals(0, user.getCoins());
        
        user.addCoins(100);
        assertEquals(100, user.getCoins());
        
        user.addCoins(50);
        assertEquals(150, user.getCoins());
        
        // Test null and negative values
        user.addCoins(null);
        assertEquals(150, user.getCoins());
        
        user.addCoins(-10);
        assertEquals(150, user.getCoins());
        
        user.addCoins(0);
        assertEquals(150, user.getCoins());
    }
    
    @Test
    void testDeductCoins() {
        user.setCoins(100);
        
        assertTrue(user.deductCoins(30));
        assertEquals(70, user.getCoins());
        
        assertTrue(user.deductCoins(70));
        assertEquals(0, user.getCoins());
        
        // Test insufficient coins
        assertFalse(user.deductCoins(10));
        assertEquals(0, user.getCoins());
        
        // Test null and negative values
        assertFalse(user.deductCoins(null));
        assertFalse(user.deductCoins(-10));
        assertEquals(0, user.getCoins());
    }
    
    @Test
    void testAddRoomCards() {
        assertEquals(0, user.getRoomCards());
        
        user.addRoomCards(5);
        assertEquals(5, user.getRoomCards());
        
        user.addRoomCards(3);
        assertEquals(8, user.getRoomCards());
        
        // Test null and negative values
        user.addRoomCards(null);
        assertEquals(8, user.getRoomCards());
        
        user.addRoomCards(-2);
        assertEquals(8, user.getRoomCards());
        
        user.addRoomCards(0);
        assertEquals(8, user.getRoomCards());
    }
    
    @Test
    void testDeductRoomCards() {
        user.setRoomCards(10);
        
        assertTrue(user.deductRoomCards(3));
        assertEquals(7, user.getRoomCards());
        
        assertTrue(user.deductRoomCards(7));
        assertEquals(0, user.getRoomCards());
        
        // Test insufficient room cards
        assertFalse(user.deductRoomCards(1));
        assertEquals(0, user.getRoomCards());
        
        // Test null and negative values
        assertFalse(user.deductRoomCards(null));
        assertFalse(user.deductRoomCards(-5));
        assertEquals(0, user.getRoomCards());
    }
    
    @Test
    void testEqualsAndHashCode() {
        User user1 = new User("open_id_1", "User1");
        user1.setId(1L);
        
        User user2 = new User("open_id_1", "User1");
        user2.setId(1L);
        
        User user3 = new User("open_id_2", "User2");
        user3.setId(2L);
        
        assertEquals(user1, user2);
        assertEquals(user1.hashCode(), user2.hashCode());
        
        assertNotEquals(user1, user3);
        assertNotEquals(user1.hashCode(), user3.hashCode());
        
        // Test with null id
        User user4 = new User("open_id_3", "User3");
        User user5 = new User("open_id_3", "User3");
        
        assertEquals(user4, user5);
        assertEquals(user4.hashCode(), user5.hashCode());
    }
    
    @Test
    void testToString() {
        user.setId(1L);
        user.setCoins(100);
        user.setRoomCards(5);
        
        String toString = user.toString();
        assertTrue(toString.contains("id=1"));
        assertTrue(toString.contains("openId='test_open_id'"));
        assertTrue(toString.contains("nickname='TestUser'"));
        assertTrue(toString.contains("status=ACTIVE"));
        assertTrue(toString.contains("coins=100"));
        assertTrue(toString.contains("roomCards=5"));
    }
    
    @Test
    void testSettersAndGetters() {
        user.setId(123L);
        assertEquals(123L, user.getId());
        
        user.setUnionId("union_123");
        assertEquals("union_123", user.getUnionId());
        
        user.setAvatar("http://example.com/avatar.jpg");
        assertEquals("http://example.com/avatar.jpg", user.getAvatar());
        
        user.setStatus(UserStatus.BANNED);
        assertEquals(UserStatus.BANNED, user.getStatus());
    }
}