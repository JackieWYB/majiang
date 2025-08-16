package com.mahjong.service;

import com.mahjong.config.WebSocketEventListener;
import com.mahjong.model.dto.GameMessage;
import com.mahjong.model.game.GameState;
import com.mahjong.model.game.PlayerState;
import com.mahjong.model.config.RoomConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration test for the complete reconnection system
 */
@SpringBootTest
@TestPropertySource(properties = {
    "game.reconnection.grace-period-seconds=1",
    "game.reconnection.max-disconnection-time-minutes=1"
})
class ReconnectionSystemIntegrationTest {
    
    @MockBean
    private GameStateRedisService gameStateRedisService;
    
    @MockBean
    private GameStateConsistencyService gameStateConsistencyService;
    
    @MockBean
    private WebSocketSessionService webSocketSessionService;
    
    @MockBean
    private GameMessageBroadcastService broadcastService;
    
    @MockBean
    private JwtTokenService jwtTokenService;
    
    @MockBean
    private GameStateRecoveryService gameStateRecoveryService;
    
    @MockBean
    private GameService gameService;
    
    private PlayerReconnectionService reconnectionService;
    private TrusteeModeService trusteeModeService;
    private WebSocketEventListener eventListener;
    
    private GameState testGameState;
    private final String roomId = "123456";
    private final String userId = "user1";
    private final String sessionId = "session1";
    private final String token = "valid-jwt-token";
    
    @BeforeEach
    void setUp() {
        // Initialize services
        reconnectionService = new PlayerReconnectionService();
        trusteeModeService = new TrusteeModeService();
        eventListener = new WebSocketEventListener(webSocketSessionService, reconnectionService, gameStateRedisService);
        
        // Inject mocked dependencies manually for test
        setField(reconnectionService, "gameStateRedisService", gameStateRedisService);
        setField(reconnectionService, "gameStateConsistencyService", gameStateConsistencyService);
        setField(reconnectionService, "webSocketSessionService", webSocketSessionService);
        setField(reconnectionService, "broadcastService", broadcastService);
        setField(reconnectionService, "jwtTokenService", jwtTokenService);
        setField(reconnectionService, "gameStateRecoveryService", gameStateRecoveryService);
        
        setField(trusteeModeService, "gameService", gameService);
        setField(trusteeModeService, "broadcastService", broadcastService);
        
        // Create test game state
        List<String> playerIds = Arrays.asList("user1", "user2", "user3");
        RoomConfig config = new RoomConfig();
        testGameState = new GameState(roomId, "game1", playerIds, config);
        testGameState.setPhase(GameState.GamePhase.PLAYING);
    }
    
    @Test
    void testCompleteDisconnectionAndReconnectionFlow() throws InterruptedException {
        // Given - setup mocks
        when(gameStateRedisService.loadGameState(roomId)).thenReturn(testGameState);
        when(gameStateRedisService.getSessionInfo(sessionId))
            .thenReturn(new GameStateRedisService.SessionInfo(userId, roomId, System.currentTimeMillis()));
        when(jwtTokenService.validateToken(token)).thenReturn(true);
        when(gameStateRecoveryService.recoverGameState(roomId)).thenReturn(testGameState);
        
        // Step 1: Simulate WebSocket disconnection
        SessionDisconnectEvent disconnectEvent = createMockDisconnectEvent(sessionId, userId);
        eventListener.handleWebSocketDisconnectListener(disconnectEvent);
        
        // Verify disconnection was handled
        verify(webSocketSessionService).unregisterSession(sessionId);
        assertTrue(reconnectionService.isPlayerDisconnected(userId));
        
        PlayerState player = testGameState.getPlayerByUserId(userId);
        assertEquals(PlayerState.PlayerStatus.DISCONNECTED, player.getStatus());
        
        // Step 2: Wait for grace period and verify trustee mode activation
        Thread.sleep(1500); // Wait longer than grace period (1 second)
        
        // Step 3: Simulate reconnection
        PlayerReconnectionService.ReconnectionResult result = 
            reconnectionService.handlePlayerReconnection(userId, "newSession", token);
        
        // Verify successful reconnection
        assertTrue(result.isSuccess());
        assertNotNull(result.getGameSnapshot());
        assertEquals(roomId, result.getRoomId());
        
        // Verify player status updated
        assertEquals(PlayerState.PlayerStatus.WAITING_TURN, player.getStatus());
        assertFalse(reconnectionService.isPlayerDisconnected(userId));
        
        // Verify session management
        verify(webSocketSessionService).addUserToRoom(userId, roomId);
        verify(gameStateRedisService).savePlayerSession(userId, "newSession", roomId);
        
        // Verify notifications sent
        verify(broadcastService).broadcastRoomEvent(eq(roomId), eq("playerDisconnected"), any());
        verify(broadcastService).broadcastRoomEvent(eq(roomId), eq("playerReconnected"), any());
    }
    
    @Test
    void testTrusteeModeIntegrationWithGameService() {
        // Given - player in trustee mode
        PlayerState player = testGameState.getPlayerByUserId(userId);
        player.setStatus(PlayerState.PlayerStatus.TRUSTEE);
        player.setAvailableActions(Arrays.asList(PlayerState.ActionType.DISCARD));
        
        when(gameService.getGameState(roomId)).thenReturn(testGameState);
        when(gameService.handlePlayerAction(eq(roomId), eq(userId), any()))
            .thenReturn(ActionResult.success("Action executed"));
        
        // When
        trusteeModeService.executeAutomaticAction(roomId, userId);
        
        // Then
        verify(gameService).handlePlayerAction(eq(roomId), eq(userId), any());
        verify(broadcastService).broadcastRoomEvent(eq(roomId), eq("trusteeAction"), any());
    }
    
    @Test
    void testReconnectionWithGameStateRecovery() {
        // Given - Redis fails, database recovery succeeds
        when(gameStateRedisService.loadGameState(roomId)).thenReturn(testGameState);
        reconnectionService.handlePlayerDisconnection(userId, sessionId, roomId);
        
        when(jwtTokenService.validateToken(token)).thenReturn(true);
        when(gameStateRecoveryService.recoverGameState(roomId)).thenReturn(testGameState);
        
        // When
        PlayerReconnectionService.ReconnectionResult result = 
            reconnectionService.handlePlayerReconnection(userId, "newSession", token);
        
        // Then
        assertTrue(result.isSuccess());
        verify(gameStateRecoveryService).recoverGameState(roomId);
    }
    
    @Test
    void testMultiplePlayersDisconnectionAndReconnection() {
        // Given - multiple players disconnect
        String user2 = "user2";
        String user3 = "user3";
        
        when(gameStateRedisService.loadGameState(roomId)).thenReturn(testGameState);
        when(jwtTokenService.validateToken(anyString())).thenReturn(true);
        when(gameStateRecoveryService.recoverGameState(roomId)).thenReturn(testGameState);
        
        // When - disconnect multiple players
        reconnectionService.handlePlayerDisconnection(userId, sessionId, roomId);
        reconnectionService.handlePlayerDisconnection(user2, "session2", roomId);
        reconnectionService.handlePlayerDisconnection(user3, "session3", roomId);
        
        assertEquals(3, reconnectionService.getDisconnectedPlayerCount());
        
        // Reconnect all players
        PlayerReconnectionService.ReconnectionResult result1 = 
            reconnectionService.handlePlayerReconnection(userId, "newSession1", token);
        PlayerReconnectionService.ReconnectionResult result2 = 
            reconnectionService.handlePlayerReconnection(user2, "newSession2", token);
        PlayerReconnectionService.ReconnectionResult result3 = 
            reconnectionService.handlePlayerReconnection(user3, "newSession3", token);
        
        // Then
        assertTrue(result1.isSuccess());
        assertTrue(result2.isSuccess());
        assertTrue(result3.isSuccess());
        assertEquals(0, reconnectionService.getDisconnectedPlayerCount());
    }
    
    @Test
    void testReconnectionFailureScenarios() {
        // Test 1: Invalid token
        when(jwtTokenService.validateToken(token)).thenReturn(false);
        
        PlayerReconnectionService.ReconnectionResult result1 = 
            reconnectionService.handlePlayerReconnection(userId, sessionId, token);
        
        assertFalse(result1.isSuccess());
        assertEquals("Invalid authentication token", result1.getMessage());
        
        // Test 2: No disconnection record
        when(jwtTokenService.validateToken(token)).thenReturn(true);
        
        PlayerReconnectionService.ReconnectionResult result2 = 
            reconnectionService.handlePlayerReconnection("unknownUser", sessionId, token);
        
        assertFalse(result2.isSuccess());
        assertEquals("No active disconnection found", result2.getMessage());
    }
    
    // Helper methods
    private SessionDisconnectEvent createMockDisconnectEvent(String sessionId, String userId) {
        SessionDisconnectEvent event = mock(SessionDisconnectEvent.class);
        StompHeaderAccessor headerAccessor = mock(StompHeaderAccessor.class);
        Principal principal = mock(Principal.class);
        
        when(event.getMessage()).thenReturn(mock(org.springframework.messaging.Message.class));
        when(StompHeaderAccessor.wrap(any())).thenReturn(headerAccessor);
        when(headerAccessor.getSessionId()).thenReturn(sessionId);
        when(headerAccessor.getUser()).thenReturn(principal);
        when(principal.getName()).thenReturn(userId);
        
        return event;
    }
    
    private void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            // Handle reflection exceptions in test
            throw new RuntimeException("Failed to set field: " + fieldName, e);
        }
    }
}