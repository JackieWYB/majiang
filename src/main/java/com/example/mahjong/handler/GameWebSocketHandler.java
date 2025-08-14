package com.example.mahjong.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(GameWebSocketHandler.class);
    private final CopyOnWriteArrayList<WebSocketSession> sessions = new CopyOnWriteArrayList<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.add(session);
        logger.info("New WebSocket connection established: {}", session.getId());
        // Here you would typically authenticate the user via the session handshake attributes
        // and associate the session with a user/player.

        // Send a welcome message
        session.sendMessage(new TextMessage("Welcome to the Mahjong Game Server!"));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        logger.info("Received message from {}: {}", session.getId(), payload);

        // For now, just echo the message back
        // In the future, this will parse the JSON command and delegate to a game service
        session.sendMessage(new TextMessage("Echo from server: " + payload));

        // Example of handling a heartbeat
        if ("{\"cmd\":\"Heartbeat\"}".equals(payload)) {
            logger.info("Received heartbeat from {}", session.getId());
            // Optionally send a heartbeat response
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session);
        logger.info("WebSocket connection closed: {} with status {}", session.getId(), status);
        // Here you would handle player disconnection from a room/game.
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        logger.error("WebSocket transport error for session {}: {}", session.getId(), exception.getMessage());
    }
}
