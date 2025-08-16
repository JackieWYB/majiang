package com.mahjong.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mahjong.model.dto.GameMessage;
import com.mahjong.model.dto.PlayAction;
import com.mahjong.model.dto.PengAction;
import com.mahjong.model.entity.Room;
import com.mahjong.model.entity.User;
import com.mahjong.service.GameService;
import com.mahjong.service.RoomService;
import com.mahjong.service.UserService;
import com.mahjong.service.JwtTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.*;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for WebSocket communication flows
 * Tests real-time message handling and connection management
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class WebSocketIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private UserService userService;

    @Autowired
    private RoomService roomService;

    @Autowired
    private GameService gameService;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private ObjectMapper objectMapper;

    private User testUser1, testUser2, testUser3;
    private String token1, token2, token3;
    private Room testRoom;

    @BeforeEach
    void setUp() {
        // Create test users
        testUser1 = createTestUser("ws_user1", "WebSocket User 1");
        testUser2 = createTestUser("ws_user2", "WebSocket User 2");
        testUser3 = createTestUser("ws_user3", "WebSocket User 3");

        // Generate JWT tokens
        token1 = jwtTokenService.generateToken(testUser1.getId().toString());
        token2 = jwtTokenService.generateToken(testUser2.getId().toString());
        token3 = jwtTokenService.generateToken(testUser3.getId().toString());

        // Create test room
        testRoom = roomService.createRoom(testUser1.getId().toString(), null);
        roomService.joinRoom(testRoom.getId(), testUser2.getId().toString());
        roomService.joinRoom(testRoom.getId(), testUser3.getId().toString());
    }

    @Test
    void testWebSocketConnection_Success() throws Exception {
        CountDownLatch connectionLatch = new CountDownLatch(1);
        AtomicReference<WebSocketSession> sessionRef = new AtomicReference<>();

        WebSocketHandler handler = new WebSocketHandler() {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) {
                sessionRef.set(session);
                connectionLatch.countDown();
            }

            @Override
            public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) {}

            @Override
            public void handleTransportError(WebSocketSession session, Throwable exception) {}

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) {}

            @Override
            public boolean supportsPartialMessages() {
                return false;
            }
        };

        StandardWebSocketClient client = new StandardWebSocketClient();
        URI uri = URI.create("ws://localhost:" + port + "/game?token=" + token1);
        
        WebSocketSession session = client.doHandshake(handler, null, uri).get(5, TimeUnit.SECONDS);
        
        assertTrue(connectionLatch.await(5, TimeUnit.SECONDS));
        assertNotNull(sessionRef.get());
        assertTrue(session.isOpen());
        
        session.close();
    }

    @Test
    void testWebSocketConnection_InvalidToken() throws Exception {
        CountDownLatch errorLatch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        WebSocketHandler handler = new WebSocketHandler() {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) {}

            @Override
            public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) {}

            @Override
            public void handleTransportError(WebSocketSession session, Throwable exception) {
                errorRef.set(exception);
                errorLatch.countDown();
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) {
                if (closeStatus.getCode() == 1008) { // Policy violation
                    errorLatch.countDown();
                }
            }

            @Override
            public boolean supportsPartialMessages() {
                return false;
            }
        };

        StandardWebSocketClient client = new StandardWebSocketClient();
        URI uri = URI.create("ws://localhost:" + port + "/game?token=invalid_token");
        
        try {
            client.doHandshake(handler, null, uri).get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            // Expected - connection should fail
        }

        assertTrue(errorLatch.await(5, TimeUnit.SECONDS));
    }

    @Test
    void testGameMessageFlow() throws Exception {
        CountDownLatch messageLatch = new CountDownLatch(2); // Expecting 2 messages
        AtomicReference<GameMessage> receivedMessage = new AtomicReference<>();

        WebSocketHandler handler = new WebSocketHandler() {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                // Join room after connection
                GameMessage joinMessage = new GameMessage();
                joinMessage.setType("REQ");
                joinMessage.setCmd("JOIN_ROOM");
                joinMessage.setRoomId(testRoom.getId());
                joinMessage.setTimestamp(System.currentTimeMillis());

                String messageJson = objectMapper.writeValueAsString(joinMessage);
                session.sendMessage(new TextMessage(messageJson));
            }

            @Override
            public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
                if (message instanceof TextMessage) {
                    String payload = ((TextMessage) message).getPayload();
                    GameMessage gameMessage = objectMapper.readValue(payload, GameMessage.class);
                    receivedMessage.set(gameMessage);
                    messageLatch.countDown();
                }
            }

            @Override
            public void handleTransportError(WebSocketSession session, Throwable exception) {}

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) {}

            @Override
            public boolean supportsPartialMessages() {
                return false;
            }
        };

        StandardWebSocketClient client = new StandardWebSocketClient();
        URI uri = URI.create("ws://localhost:" + port + "/game?token=" + token1);
        
        WebSocketSession session = client.doHandshake(handler, null, uri).get(5, TimeUnit.SECONDS);
        
        assertTrue(messageLatch.await(10, TimeUnit.SECONDS));
        assertNotNull(receivedMessage.get());
        
        session.close();
    }

    @Test
    void testMultipleClientConnections() throws Exception {
        CountDownLatch connectionLatch = new CountDownLatch(3);
        
        WebSocketHandler handler = new WebSocketHandler() {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) {
                connectionLatch.countDown();
            }

            @Override
            public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) {}

            @Override
            public void handleTransportError(WebSocketSession session, Throwable exception) {}

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) {}

            @Override
            public boolean supportsPartialMessages() {
                return false;
            }
        };

        StandardWebSocketClient client = new StandardWebSocketClient();
        
        // Connect all three users
        WebSocketSession session1 = client.doHandshake(handler, null, 
            URI.create("ws://localhost:" + port + "/game?token=" + token1)).get(5, TimeUnit.SECONDS);
        WebSocketSession session2 = client.doHandshake(handler, null, 
            URI.create("ws://localhost:" + port + "/game?token=" + token2)).get(5, TimeUnit.SECONDS);
        WebSocketSession session3 = client.doHandshake(handler, null, 
            URI.create("ws://localhost:" + port + "/game?token=" + token3)).get(5, TimeUnit.SECONDS);
        
        assertTrue(connectionLatch.await(10, TimeUnit.SECONDS));
        
        assertTrue(session1.isOpen());
        assertTrue(session2.isOpen());
        assertTrue(session3.isOpen());
        
        session1.close();
        session2.close();
        session3.close();
    }

    @Test
    void testGameActionBroadcast() throws Exception {
        // Start the game first
        gameService.startGame(testRoom.getId());

        CountDownLatch broadcastLatch = new CountDownLatch(2); // Expecting broadcast to 2 other players
        AtomicReference<GameMessage> broadcastMessage = new AtomicReference<>();

        WebSocketHandler player2Handler = new WebSocketHandler() {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) {}

            @Override
            public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
                if (message instanceof TextMessage) {
                    String payload = ((TextMessage) message).getPayload();
                    GameMessage gameMessage = objectMapper.readValue(payload, GameMessage.class);
                    
                    if ("PLAYER_ACTION".equals(gameMessage.getCmd())) {
                        broadcastMessage.set(gameMessage);
                        broadcastLatch.countDown();
                    }
                }
            }

            @Override
            public void handleTransportError(WebSocketSession session, Throwable exception) {}

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) {}

            @Override
            public boolean supportsPartialMessages() {
                return false;
            }
        };

        WebSocketHandler player1Handler = new WebSocketHandler() {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                // Send a play action
                PlayAction playAction = new PlayAction("1W");
                GameMessage actionMessage = new GameMessage();
                actionMessage.setType("REQ");
                actionMessage.setCmd("PLAY");
                actionMessage.setRoomId(testRoom.getId());
                actionMessage.setData(playAction);
                actionMessage.setTimestamp(System.currentTimeMillis());

                String messageJson = objectMapper.writeValueAsString(actionMessage);
                session.sendMessage(new TextMessage(messageJson));
            }

            @Override
            public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) {}

            @Override
            public void handleTransportError(WebSocketSession session, Throwable exception) {}

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) {}

            @Override
            public boolean supportsPartialMessages() {
                return false;
            }
        };

        StandardWebSocketClient client = new StandardWebSocketClient();
        
        // Connect players
        WebSocketSession session1 = client.doHandshake(player1Handler, null, 
            URI.create("ws://localhost:" + port + "/game?token=" + token1)).get(5, TimeUnit.SECONDS);
        WebSocketSession session2 = client.doHandshake(player2Handler, null, 
            URI.create("ws://localhost:" + port + "/game?token=" + token2)).get(5, TimeUnit.SECONDS);
        WebSocketSession session3 = client.doHandshake(player2Handler, null, 
            URI.create("ws://localhost:" + port + "/game?token=" + token3)).get(5, TimeUnit.SECONDS);
        
        assertTrue(broadcastLatch.await(15, TimeUnit.SECONDS));
        assertNotNull(broadcastMessage.get());
        assertEquals("PLAYER_ACTION", broadcastMessage.get().getCmd());
        
        session1.close();
        session2.close();
        session3.close();
    }

    @Test
    void testHeartbeatMechanism() throws Exception {
        CountDownLatch heartbeatLatch = new CountDownLatch(1);
        AtomicReference<GameMessage> heartbeatResponse = new AtomicReference<>();

        WebSocketHandler handler = new WebSocketHandler() {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                // Send heartbeat
                GameMessage heartbeat = new GameMessage();
                heartbeat.setType("REQ");
                heartbeat.setCmd("HEARTBEAT");
                heartbeat.setTimestamp(System.currentTimeMillis());

                String messageJson = objectMapper.writeValueAsString(heartbeat);
                session.sendMessage(new TextMessage(messageJson));
            }

            @Override
            public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
                if (message instanceof TextMessage) {
                    String payload = ((TextMessage) message).getPayload();
                    GameMessage gameMessage = objectMapper.readValue(payload, GameMessage.class);
                    
                    if ("HEARTBEAT".equals(gameMessage.getCmd()) && "RESP".equals(gameMessage.getType())) {
                        heartbeatResponse.set(gameMessage);
                        heartbeatLatch.countDown();
                    }
                }
            }

            @Override
            public void handleTransportError(WebSocketSession session, Throwable exception) {}

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) {}

            @Override
            public boolean supportsPartialMessages() {
                return false;
            }
        };

        StandardWebSocketClient client = new StandardWebSocketClient();
        URI uri = URI.create("ws://localhost:" + port + "/game?token=" + token1);
        
        WebSocketSession session = client.doHandshake(handler, null, uri).get(5, TimeUnit.SECONDS);
        
        assertTrue(heartbeatLatch.await(10, TimeUnit.SECONDS));
        assertNotNull(heartbeatResponse.get());
        assertEquals("RESP", heartbeatResponse.get().getType());
        
        session.close();
    }

    @Test
    void testConnectionRecovery() throws Exception {
        CountDownLatch reconnectionLatch = new CountDownLatch(1);
        AtomicReference<GameMessage> gameSnapshot = new AtomicReference<>();

        WebSocketHandler handler = new WebSocketHandler() {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                // Request game state after reconnection
                GameMessage stateRequest = new GameMessage();
                stateRequest.setType("REQ");
                stateRequest.setCmd("GET_GAME_STATE");
                stateRequest.setRoomId(testRoom.getId());
                stateRequest.setTimestamp(System.currentTimeMillis());

                String messageJson = objectMapper.writeValueAsString(stateRequest);
                session.sendMessage(new TextMessage(messageJson));
            }

            @Override
            public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
                if (message instanceof TextMessage) {
                    String payload = ((TextMessage) message).getPayload();
                    GameMessage gameMessage = objectMapper.readValue(payload, GameMessage.class);
                    
                    if ("GAME_SNAPSHOT".equals(gameMessage.getCmd())) {
                        gameSnapshot.set(gameMessage);
                        reconnectionLatch.countDown();
                    }
                }
            }

            @Override
            public void handleTransportError(WebSocketSession session, Throwable exception) {}

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) {}

            @Override
            public boolean supportsPartialMessages() {
                return false;
            }
        };

        // Start game first
        gameService.startGame(testRoom.getId());

        StandardWebSocketClient client = new StandardWebSocketClient();
        URI uri = URI.create("ws://localhost:" + port + "/game?token=" + token1);
        
        WebSocketSession session = client.doHandshake(handler, null, uri).get(5, TimeUnit.SECONDS);
        
        assertTrue(reconnectionLatch.await(10, TimeUnit.SECONDS));
        assertNotNull(gameSnapshot.get());
        assertNotNull(gameSnapshot.get().getData());
        
        session.close();
    }

    @Test
    void testConcurrentMessageHandling() throws Exception {
        CountDownLatch messageLatch = new CountDownLatch(10);
        
        WebSocketHandler handler = new WebSocketHandler() {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                // Send multiple messages rapidly
                for (int i = 0; i < 10; i++) {
                    GameMessage message = new GameMessage();
                    message.setType("REQ");
                    message.setCmd("TEST_MESSAGE_" + i);
                    message.setTimestamp(System.currentTimeMillis());

                    String messageJson = objectMapper.writeValueAsString(message);
                    session.sendMessage(new TextMessage(messageJson));
                }
            }

            @Override
            public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) {
                messageLatch.countDown();
            }

            @Override
            public void handleTransportError(WebSocketSession session, Throwable exception) {}

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) {}

            @Override
            public boolean supportsPartialMessages() {
                return false;
            }
        };

        StandardWebSocketClient client = new StandardWebSocketClient();
        URI uri = URI.create("ws://localhost:" + port + "/game?token=" + token1);
        
        WebSocketSession session = client.doHandshake(handler, null, uri).get(5, TimeUnit.SECONDS);
        
        assertTrue(messageLatch.await(15, TimeUnit.SECONDS));
        
        session.close();
    }

    // Helper methods

    private User createTestUser(String openId, String nickname) {
        User user = new User();
        user.setOpenId(openId);
        user.setNickname(nickname);
        user.setAvatar("avatar.jpg");
        user.setCoins(1000);
        user.setRoomCards(10);
        return userService.createUser(user);
    }
}