package com.mahjong.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mahjong.model.dto.GameMessage;
import com.mahjong.model.dto.GameSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GameMessageBroadcastServiceTest {
    
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    
    @Mock
    private WebSocketSessionService sessionService;
    
    private GameMessageBroadcastService broadcastService;
    private ObjectMapper objectMapper;
    
    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        broadcastService = new GameMessageBroadcastService(messagingTemplate, sessionService, objectMapper);
    }
    
    @Test
    void shouldSendMessageToOnlineUser() {
        // Given
        String userId = "user123";
        GameMessage message = GameMessage.event("test", "room123", Map.of("data", "value"));
        
        when(sessionService.isUserOnline(userId)).thenReturn(true);
        
        // When
        broadcastService.sendToUser(userId, message);
        
        // Then
        verify(messagingTemplate).convertAndSendToUser(userId, "/queue/game", message);
        verify(sessionService).isUserOnline(userId);
    }
    
    @Test
    void shouldSkipMessageToOfflineUser() {
        // Given
        String userId = "user123";
        GameMessage message = GameMessage.event("test", "room123", Map.of("data", "value"));
        
        when(sessionService.isUserOnline(userId)).thenReturn(false);
        
        // When
        broadcastService.sendToUser(userId, message);
        
        // Then
        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any());
        verify(sessionService).isUserOnline(userId);
    }
    
    @Test
    void shouldBroadcastToAllUsersInRoom() {
        // Given
        String roomId = "room123";
        GameMessage message = GameMessage.event("test", roomId, Map.of("data", "value"));
        Set<String> roomUsers = Set.of("user1", "user2", "user3");
        
        when(sessionService.getRoomUsers(roomId)).thenReturn(roomUsers);
        when(sessionService.isUserOnline(anyString())).thenReturn(true);
        
        // When
        broadcastService.broadcastToRoom(roomId, message);
        
        // Then
        verify(messagingTemplate, times(3)).convertAndSendToUser(anyString(), eq("/queue/game"), eq(message));
        verify(sessionService).getRoomUsers(roomId);
    }
    
    @Test
    void shouldBroadcastToRoomExcludingUser() {
        // Given
        String roomId = "room123";
        String excludeUserId = "user2";
        GameMessage message = GameMessage.event("test", roomId, Map.of("data", "value"));
        Set<String> roomUsers = Set.of("user1", "user2", "user3");
        
        when(sessionService.getRoomUsers(roomId)).thenReturn(roomUsers);
        when(sessionService.isUserOnline(anyString())).thenReturn(true);
        
        // When
        broadcastService.broadcastToRoom(roomId, message, excludeUserId);
        
        // Then
        verify(messagingTemplate, times(2)).convertAndSendToUser(anyString(), eq("/queue/game"), eq(message));
        verify(messagingTemplate, never()).convertAndSendToUser(eq(excludeUserId), anyString(), any());
        verify(sessionService).getRoomUsers(roomId);
    }
    
    @Test
    void shouldSkipBroadcastToEmptyRoom() {
        // Given
        String roomId = "room123";
        GameMessage message = GameMessage.event("test", roomId, Map.of("data", "value"));
        
        when(sessionService.getRoomUsers(roomId)).thenReturn(Set.of());
        
        // When
        broadcastService.broadcastToRoom(roomId, message);
        
        // Then
        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any());
        verify(sessionService).getRoomUsers(roomId);
    }
    
    @Test
    void shouldBroadcastToSpecificUsers() {
        // Given
        List<String> userIds = List.of("user1", "user2", "user3");
        GameMessage message = GameMessage.event("test", "room123", Map.of("data", "value"));
        
        when(sessionService.isUserOnline(anyString())).thenReturn(true);
        
        // When
        broadcastService.broadcastToUsers(userIds, message);
        
        // Then
        verify(messagingTemplate, times(3)).convertAndSendToUser(anyString(), eq("/queue/game"), eq(message));
    }
    
    @Test
    void shouldSendGameSnapshot() {
        // Given
        String userId = "user123";
        GameSnapshot snapshot = new GameSnapshot();
        snapshot.setRoomId("room123");
        snapshot.setGameId("game456");
        
        when(sessionService.isUserOnline(userId)).thenReturn(true);
        
        // When
        broadcastService.sendGameSnapshot(userId, snapshot);
        
        // Then
        verify(messagingTemplate).convertAndSendToUser(eq(userId), eq("/queue/game"), any(GameMessage.class));
    }
    
    @Test
    void shouldBroadcastGameSnapshot() {
        // Given
        String roomId = "room123";
        GameSnapshot snapshot = new GameSnapshot();
        snapshot.setRoomId(roomId);
        snapshot.setGameId("game456");
        Set<String> roomUsers = Set.of("user1", "user2");
        
        when(sessionService.getRoomUsers(roomId)).thenReturn(roomUsers);
        when(sessionService.isUserOnline(anyString())).thenReturn(true);
        
        // When
        broadcastService.broadcastGameSnapshot(roomId, snapshot);
        
        // Then
        verify(messagingTemplate, times(2)).convertAndSendToUser(anyString(), eq("/queue/game"), any(GameMessage.class));
    }
    
    @Test
    void shouldBroadcastPersonalizedSnapshots() {
        // Given
        String roomId = "room123";
        Set<String> roomUsers = Set.of("user1", "user2");
        
        GameSnapshot snapshot1 = new GameSnapshot();
        snapshot1.setRoomId(roomId);
        GameSnapshot snapshot2 = new GameSnapshot();
        snapshot2.setRoomId(roomId);
        
        Map<String, GameSnapshot> userSnapshots = Map.of(
                "user1", snapshot1,
                "user2", snapshot2
        );
        
        when(sessionService.getRoomUsers(roomId)).thenReturn(roomUsers);
        when(sessionService.isUserOnline(anyString())).thenReturn(true);
        
        // When
        broadcastService.broadcastPersonalizedSnapshots(roomId, userSnapshots);
        
        // Then
        verify(messagingTemplate, times(2)).convertAndSendToUser(anyString(), eq("/queue/game"), any(GameMessage.class));
    }
    
    @Test
    void shouldSendActionResult() {
        // Given
        String userId = "user123";
        String requestId = "req456";
        ActionResult result = ActionResult.success("Success", Map.of("score", 100));
        
        when(sessionService.isUserOnline(userId)).thenReturn(true);
        
        // When
        broadcastService.sendActionResult(userId, requestId, result);
        
        // Then
        verify(messagingTemplate).convertAndSendToUser(eq(userId), eq("/queue/game"), any(GameMessage.class));
    }
    
    @Test
    void shouldBroadcastActionEvent() {
        // Given
        String roomId = "room123";
        String actingUserId = "user1";
        String actionType = "discard";
        Object actionData = Map.of("tile", "5W");
        Set<String> roomUsers = Set.of("user1", "user2", "user3");
        
        when(sessionService.getRoomUsers(roomId)).thenReturn(roomUsers);
        when(sessionService.isUserOnline(anyString())).thenReturn(true);
        
        // When
        broadcastService.broadcastActionEvent(roomId, actingUserId, actionType, actionData);
        
        // Then
        // Should broadcast to 2 users (excluding the acting user)
        verify(messagingTemplate, times(2)).convertAndSendToUser(anyString(), eq("/queue/game"), any(GameMessage.class));
        verify(messagingTemplate, never()).convertAndSendToUser(eq(actingUserId), anyString(), any());
    }
    
    @Test
    void shouldSendErrorMessage() {
        // Given
        String userId = "user123";
        String command = "play";
        String requestId = "req456";
        String error = "Invalid tile";
        
        when(sessionService.isUserOnline(userId)).thenReturn(true);
        
        // When
        broadcastService.sendError(userId, command, requestId, error);
        
        // Then
        verify(messagingTemplate).convertAndSendToUser(eq(userId), eq("/queue/game"), any(GameMessage.class));
    }
    
    @Test
    void shouldBroadcastRoomEvent() {
        // Given
        String roomId = "room123";
        String eventType = "userJoined";
        Object eventData = Map.of("userId", "user456");
        Set<String> roomUsers = Set.of("user1", "user2");
        
        when(sessionService.getRoomUsers(roomId)).thenReturn(roomUsers);
        when(sessionService.isUserOnline(anyString())).thenReturn(true);
        
        // When
        broadcastService.broadcastRoomEvent(roomId, eventType, eventData);
        
        // Then
        verify(messagingTemplate, times(2)).convertAndSendToUser(anyString(), eq("/queue/game"), any(GameMessage.class));
    }
    
    @Test
    void shouldSendTurnNotification() {
        // Given
        String userId = "user123";
        String roomId = "room456";
        List<String> availableActions = List.of("discard", "gang", "hu");
        long turnDeadline = System.currentTimeMillis() + 15000;
        
        when(sessionService.isUserOnline(userId)).thenReturn(true);
        
        // When
        broadcastService.sendTurnNotification(userId, roomId, availableActions, turnDeadline);
        
        // Then
        verify(messagingTemplate).convertAndSendToUser(eq(userId), eq("/queue/game"), any(GameMessage.class));
    }
    
    @Test
    void shouldBroadcastTurnChange() {
        // Given
        String roomId = "room123";
        String currentUserId = "user2";
        int currentPlayerIndex = 1;
        long turnDeadline = System.currentTimeMillis() + 15000;
        Set<String> roomUsers = Set.of("user1", "user2", "user3");
        
        when(sessionService.getRoomUsers(roomId)).thenReturn(roomUsers);
        when(sessionService.isUserOnline(anyString())).thenReturn(true);
        
        // When
        broadcastService.broadcastTurnChange(roomId, currentUserId, currentPlayerIndex, turnDeadline);
        
        // Then
        verify(messagingTemplate, times(3)).convertAndSendToUser(anyString(), eq("/queue/game"), any(GameMessage.class));
    }
    
    @Test
    void shouldBroadcastGameEnd() {
        // Given
        String roomId = "room123";
        Object settlementResult = Map.of("winner", "user1", "scores", List.of(100, -50, -50));
        Set<String> roomUsers = Set.of("user1", "user2", "user3");
        
        when(sessionService.getRoomUsers(roomId)).thenReturn(roomUsers);
        when(sessionService.isUserOnline(anyString())).thenReturn(true);
        
        // When
        broadcastService.broadcastGameEnd(roomId, settlementResult);
        
        // Then
        verify(messagingTemplate, times(3)).convertAndSendToUser(anyString(), eq("/queue/game"), any(GameMessage.class));
    }
    
    @Test
    void shouldSendHeartbeat() {
        // Given
        String userId = "user123";
        String requestId = "req456";
        
        when(sessionService.isUserOnline(userId)).thenReturn(true);
        
        // When
        broadcastService.sendHeartbeat(userId, requestId);
        
        // Then
        verify(messagingTemplate).convertAndSendToUser(eq(userId), eq("/queue/game"), any(GameMessage.class));
    }
    
    @Test
    void shouldHandleExceptionInSendToUser() {
        // Given
        String userId = "user123";
        GameMessage message = GameMessage.event("test", "room123", Map.of("data", "value"));
        
        when(sessionService.isUserOnline(userId)).thenReturn(true);
        doThrow(new RuntimeException("Connection error")).when(messagingTemplate)
                .convertAndSendToUser(anyString(), anyString(), any());
        
        // When & Then
        // Should not throw exception, just log error
        broadcastService.sendToUser(userId, message);
        
        verify(messagingTemplate).convertAndSendToUser(userId, "/queue/game", message);
    }
}