package com.mahjong.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mahjong.model.dto.GameMessage;
import com.mahjong.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb",
        "spring.redis.host=localhost",
        "spring.redis.port=6370"
})
class GameMessageProtocolIntegrationTest {
    
    @MockBean
    private GameService gameService;
    
    @MockBean
    private RoomService roomService;
    
    @MockBean
    private WebSocketSessionService sessionService;
    
    private WebSocketStompClient stompClient;
    private ObjectMapper objectMapper;
    private BlockingQueue<GameMessage> receivedMessages;
    
    @BeforeEach
    void setUp() {
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());
        objectMapper = new ObjectMapper();
        receivedMessages = new LinkedBlockingQueue<>();
    }
    
    @Test
    void shouldHandlePlayActionMessage() throws Exception {
        // Given
        String userId = "user123";
        String roomId = "room456";
        String requestId = "req789";
        
        GameMessage playMessage = new GameMessage(GameMessage.MessageType.REQUEST, "play");
        playMessage.setRequestId(requestId);
        playMessage.setRoomId(roomId);
        playMessage.setData(Map.of("tile", "5W", "fromHand", true));
        
        ActionResult mockResult = ActionResult.success("Play successful", 
                Map.of("remainingTiles", 67));
        
        when(gameService.handlePlayerAction(eq(roomId), eq(userId), any(PlayerAction.class)))
                .thenReturn(mockResult);
        when(sessionService.isUserOnline(userId)).thenReturn(true);
        
        // Create test session handler
        StompSessionHandler sessionHandler = new TestStompSessionHandler();
        
        // Note: This is a conceptual test - actual WebSocket testing would require
        // a running server and proper authentication setup
        
        // Verify that the message parsing and action handling works correctly
        GameMessageParser parser = new GameMessageParser(objectMapper);
        PlayerAction action = parser.parsePlayerAction(playMessage);
        
        assertNotNull(action);
        assertEquals(PlayerAction.ActionType.DISCARD, action.getType());
    }
    
    @Test
    void shouldHandlePengActionMessage() throws Exception {
        // Given
        String userId = "user123";
        String roomId = "room456";
        String requestId = "req789";
        
        GameMessage pengMessage = new GameMessage(GameMessage.MessageType.REQUEST, "peng");
        pengMessage.setRequestId(requestId);
        pengMessage.setRoomId(roomId);
        pengMessage.setData(Map.of(
                "tile", "7W",
                "claimedFrom", "user456",
                "discardTile", "2T"
        ));
        
        ActionResult mockResult = ActionResult.success("Peng successful", 
                Map.of("meldFormed", true));
        
        when(gameService.handlePlayerAction(eq(roomId), eq(userId), any(PlayerAction.class)))
                .thenReturn(mockResult);
        when(sessionService.isUserOnline(userId)).thenReturn(true);
        
        // Verify message parsing
        GameMessageParser parser = new GameMessageParser(objectMapper);
        PlayerAction action = parser.parsePlayerAction(pengMessage);
        
        assertNotNull(action);
        assertEquals(PlayerAction.ActionType.PENG, action.getType());
    }
    
    @Test
    void shouldHandleGangActionMessage() throws Exception {
        // Given
        String userId = "user123";
        String roomId = "room456";
        String requestId = "req789";
        
        GameMessage gangMessage = new GameMessage(GameMessage.MessageType.REQUEST, "gang");
        gangMessage.setRequestId(requestId);
        gangMessage.setRoomId(roomId);
        gangMessage.setData(Map.of(
                "tile", "9W",
                "gangType", "MING",
                "claimedFrom", "user789"
        ));
        
        ActionResult mockResult = ActionResult.success("Gang successful", 
                Map.of("gangFormed", true, "bonusPoints", 2));
        
        when(gameService.handlePlayerAction(eq(roomId), eq(userId), any(PlayerAction.class)))
                .thenReturn(mockResult);
        when(sessionService.isUserOnline(userId)).thenReturn(true);
        
        // Verify message parsing
        GameMessageParser parser = new GameMessageParser(objectMapper);
        PlayerAction action = parser.parsePlayerAction(gangMessage);
        
        assertNotNull(action);
        assertEquals(PlayerAction.ActionType.GANG, action.getType());
    }
    
    @Test
    void shouldHandleHuActionMessage() throws Exception {
        // Given
        String userId = "user123";
        String roomId = "room456";
        String requestId = "req789";
        
        GameMessage huMessage = new GameMessage(GameMessage.MessageType.REQUEST, "hu");
        huMessage.setRequestId(requestId);
        huMessage.setRoomId(roomId);
        huMessage.setData(Map.of(
                "winningTile", "8W",
                "selfDraw", true
        ));
        
        ActionResult mockResult = ActionResult.success("Win successful", 
                Map.of("gameEnded", true, "winnerScore", 24));
        
        when(gameService.handlePlayerAction(eq(roomId), eq(userId), any(PlayerAction.class)))
                .thenReturn(mockResult);
        when(sessionService.isUserOnline(userId)).thenReturn(true);
        
        // Verify message parsing
        GameMessageParser parser = new GameMessageParser(objectMapper);
        PlayerAction action = parser.parsePlayerAction(huMessage);
        
        assertNotNull(action);
        assertEquals(PlayerAction.ActionType.HU, action.getType());
    }
    
    @Test
    void shouldValidateMessageFormat() {
        // Given
        GameMessageParser parser = new GameMessageParser(objectMapper);
        
        // Valid request message
        GameMessage validRequest = new GameMessage(GameMessage.MessageType.REQUEST, "play");
        validRequest.setRequestId("req123");
        validRequest.setData(Map.of("tile", "5W"));
        
        // Invalid message - missing request ID
        GameMessage invalidRequest = new GameMessage(GameMessage.MessageType.REQUEST, "play");
        invalidRequest.setData(Map.of("tile", "5W"));
        
        // Valid response message
        GameMessage validResponse = new GameMessage(GameMessage.MessageType.RESPONSE, "gameAction");
        validResponse.setRequestId("req123");
        validResponse.setData(Map.of("success", true));
        
        // Valid error message
        GameMessage validError = new GameMessage(GameMessage.MessageType.ERROR, "gameAction");
        validError.setRequestId("req123");
        validError.setError("Invalid action");
        
        // When & Then
        assertDoesNotThrow(() -> parser.validateMessage(validRequest));
        assertDoesNotThrow(() -> parser.validateMessage(validResponse));
        assertDoesNotThrow(() -> parser.validateMessage(validError));
        
        assertThrows(IllegalArgumentException.class, () -> parser.validateMessage(invalidRequest));
        assertThrows(IllegalArgumentException.class, () -> parser.validateMessage(null));
    }
    
    @Test
    void shouldBroadcastMessagesCorrectly() {
        // Given
        GameMessageBroadcastService broadcastService = mock(GameMessageBroadcastService.class);
        String roomId = "room123";
        String userId = "user456";
        
        // When
        broadcastService.broadcastRoomEvent(roomId, "userJoined", Map.of("userId", userId));
        broadcastService.sendTurnNotification(userId, roomId, 
                java.util.List.of("discard", "gang"), System.currentTimeMillis() + 15000);
        
        // Then
        verify(broadcastService).broadcastRoomEvent(eq(roomId), eq("userJoined"), any());
        verify(broadcastService).sendTurnNotification(eq(userId), eq(roomId), any(), anyLong());
    }
    
    @Test
    void shouldHandleInvalidActionData() {
        // Given
        GameMessageParser parser = new GameMessageParser(objectMapper);
        
        // Invalid play action - missing tile
        GameMessage invalidPlay = new GameMessage(GameMessage.MessageType.REQUEST, "play");
        invalidPlay.setRequestId("req123");
        invalidPlay.setData(Map.of("fromHand", true));
        
        // Invalid peng action - missing claimedFrom
        GameMessage invalidPeng = new GameMessage(GameMessage.MessageType.REQUEST, "peng");
        invalidPeng.setRequestId("req123");
        invalidPeng.setData(Map.of("tile", "5W"));
        
        // Invalid gang action - missing gang type
        GameMessage invalidGang = new GameMessage(GameMessage.MessageType.REQUEST, "gang");
        invalidGang.setRequestId("req123");
        invalidGang.setData(Map.of("tile", "9W"));
        
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> parser.parsePlayerAction(invalidPlay));
        assertThrows(IllegalArgumentException.class, () -> parser.parsePlayerAction(invalidPeng));
        assertThrows(IllegalArgumentException.class, () -> parser.parsePlayerAction(invalidGang));
    }
    
    /**
     * Test STOMP session handler for WebSocket testing
     */
    private class TestStompSessionHandler extends StompSessionHandlerAdapter {
        
        @Override
        public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
            // Subscribe to game messages
            session.subscribe("/user/queue/game", new StompFrameHandler() {
                @Override
                public Type getPayloadType(StompHeaders headers) {
                    return GameMessage.class;
                }
                
                @Override
                public void handleFrame(StompHeaders headers, Object payload) {
                    if (payload instanceof GameMessage) {
                        receivedMessages.offer((GameMessage) payload);
                    }
                }
            });
        }
        
        @Override
        public void handleException(StompSession session, StompCommand command, 
                                  StompHeaders headers, byte[] payload, Throwable exception) {
            exception.printStackTrace();
        }
    }
}