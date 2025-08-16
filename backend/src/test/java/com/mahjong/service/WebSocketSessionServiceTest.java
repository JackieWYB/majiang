package com.mahjong.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.WebSocketSession;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

/**
 * Unit tests for WebSocketSessionService
 */
@ExtendWith(MockitoExtension.class)
class WebSocketSessionServiceTest {
    
    @Mock
    private WebSocketSession mockSession1;
    
    @Mock
    private WebSocketSession mockSession2;
    
    private WebSocketSessionService sessionService;
    
    @BeforeEach
    void setUp() {
        sessionService = new WebSocketSessionService();
        
        lenient().when(mockSession1.getId()).thenReturn("session1");
        lenient().when(mockSession1.isOpen()).thenReturn(true);
        
        lenient().when(mockSession2.getId()).thenReturn("session2");
        lenient().when(mockSession2.isOpen()).thenReturn(true);
    }
    
    @Test
    void shouldRegisterUserSession() {
        String userId = "user123";
        
        sessionService.registerSession(userId, mockSession1);
        
        assertEquals(mockSession1, sessionService.getSession(userId));
        assertTrue(sessionService.isUserOnline(userId));
        assertEquals(1, sessionService.getActiveSessionCount());
    }
    
    @Test
    void shouldReplaceExistingSession() throws Exception {
        String userId = "user123";
        
        // Register first session
        sessionService.registerSession(userId, mockSession1);
        assertEquals(mockSession1, sessionService.getSession(userId));
        
        // Register second session for same user
        sessionService.registerSession(userId, mockSession2);
        
        // Should close first session and use second
        verify(mockSession1).close();
        assertEquals(mockSession2, sessionService.getSession(userId));
        assertEquals(1, sessionService.getActiveSessionCount());
    }
    
    @Test
    void shouldUnregisterSession() {
        String userId = "user123";
        
        sessionService.registerSession(userId, mockSession1);
        assertTrue(sessionService.isUserOnline(userId));
        
        sessionService.unregisterSession("session1");
        
        assertFalse(sessionService.isUserOnline(userId));
        assertNull(sessionService.getSession(userId));
        assertEquals(0, sessionService.getActiveSessionCount());
    }
    
    @Test
    void shouldAddUserToRoom() {
        String userId = "user123";
        String roomId = "room456";
        
        sessionService.addUserToRoom(userId, roomId);
        
        Set<String> roomUsers = sessionService.getRoomUsers(roomId);
        assertTrue(roomUsers.contains(userId));
        assertEquals(1, sessionService.getActiveRoomCount());
    }
    
    @Test
    void shouldRemoveUserFromRoom() {
        String userId = "user123";
        String roomId = "room456";
        
        sessionService.addUserToRoom(userId, roomId);
        assertTrue(sessionService.getRoomUsers(roomId).contains(userId));
        
        sessionService.removeUserFromRoom(userId, roomId);
        
        assertFalse(sessionService.getRoomUsers(roomId).contains(userId));
        assertEquals(0, sessionService.getActiveRoomCount());
    }
    
    @Test
    void shouldHandleMultipleUsersInRoom() {
        String user1 = "user1";
        String user2 = "user2";
        String roomId = "room123";
        
        sessionService.addUserToRoom(user1, roomId);
        sessionService.addUserToRoom(user2, roomId);
        
        Set<String> roomUsers = sessionService.getRoomUsers(roomId);
        assertEquals(2, roomUsers.size());
        assertTrue(roomUsers.contains(user1));
        assertTrue(roomUsers.contains(user2));
        
        sessionService.removeUserFromRoom(user1, roomId);
        
        roomUsers = sessionService.getRoomUsers(roomId);
        assertEquals(1, roomUsers.size());
        assertTrue(roomUsers.contains(user2));
        assertFalse(roomUsers.contains(user1));
    }
    
    @Test
    void shouldGetUserIdFromSessionId() {
        String userId = "user123";
        
        sessionService.registerSession(userId, mockSession1);
        
        assertEquals(userId, sessionService.getUserId("session1"));
        assertNull(sessionService.getUserId("nonexistent"));
    }
    
    @Test
    void shouldCleanupInactiveSessions() {
        String user1 = "user1";
        String user2 = "user2";
        String roomId = "room123";
        
        // Register sessions
        sessionService.registerSession(user1, mockSession1);
        sessionService.registerSession(user2, mockSession2);
        sessionService.addUserToRoom(user1, roomId);
        sessionService.addUserToRoom(user2, roomId);
        
        assertEquals(2, sessionService.getActiveSessionCount());
        assertEquals(2, sessionService.getRoomUsers(roomId).size());
        
        // Mock session1 as closed
        when(mockSession1.isOpen()).thenReturn(false);
        
        sessionService.cleanupInactiveSessions();
        
        // Should remove inactive session and user from rooms
        assertEquals(1, sessionService.getActiveSessionCount());
        assertFalse(sessionService.isUserOnline(user1));
        assertTrue(sessionService.isUserOnline(user2));
        
        Set<String> roomUsers = sessionService.getRoomUsers(roomId);
        assertEquals(1, roomUsers.size());
        assertFalse(roomUsers.contains(user1));
        assertTrue(roomUsers.contains(user2));
    }
    
    @Test
    void shouldHandleUnregisterWithRoomCleanup() {
        String userId = "user123";
        String roomId = "room456";
        
        sessionService.registerSession(userId, mockSession1);
        sessionService.addUserToRoom(userId, roomId);
        
        assertEquals(1, sessionService.getActiveRoomCount());
        assertTrue(sessionService.getRoomUsers(roomId).contains(userId));
        
        sessionService.unregisterSession("session1");
        
        // Should remove user from all rooms
        Set<String> roomUsers = sessionService.getRoomUsers(roomId);
        assertFalse(roomUsers.contains(userId));
        // Room should still exist but be empty
        assertTrue(roomUsers.isEmpty());
    }
    
    @Test
    void shouldReturnEmptySetForNonexistentRoom() {
        Set<String> users = sessionService.getRoomUsers("nonexistent");
        assertNotNull(users);
        assertTrue(users.isEmpty());
    }
    
    @Test
    void shouldHandleNullSessionId() {
        assertNull(sessionService.getUserId(null));
        
        // Should not throw exception
        sessionService.unregisterSession(null);
        assertEquals(0, sessionService.getActiveSessionCount());
    }
}