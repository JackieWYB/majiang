package com.mahjong.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mahjong.model.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class GameMessageParserTest {
    
    private GameMessageParser messageParser;
    private ObjectMapper objectMapper;
    
    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        messageParser = new GameMessageParser(objectMapper);
    }
    
    @Test
    void shouldParsePlayActionFromMap() {
        // Given
        Map<String, Object> actionData = Map.of("tile", "5W", "fromHand", true);
        GameMessage message = new GameMessage(GameMessage.MessageType.REQUEST, "play");
        message.setData(actionData);
        
        // When
        PlayerAction action = messageParser.parsePlayerAction(message);
        
        // Then
        assertNotNull(action);
        assertEquals(PlayerAction.ActionType.DISCARD, action.getType());
    }
    
    @Test
    void shouldParsePlayActionFromString() {
        // Given
        GameMessage message = new GameMessage(GameMessage.MessageType.REQUEST, "discard");
        message.setData("3T");
        
        // When
        PlayerAction action = messageParser.parsePlayerAction(message);
        
        // Then
        assertNotNull(action);
        assertEquals(PlayerAction.ActionType.DISCARD, action.getType());
    }
    
    @Test
    void shouldParsePengAction() {
        // Given
        Map<String, Object> actionData = Map.of(
                "tile", "7W",
                "claimedFrom", "user123",
                "discardTile", "2T"
        );
        GameMessage message = new GameMessage(GameMessage.MessageType.REQUEST, "peng");
        message.setData(actionData);
        
        // When
        PlayerAction action = messageParser.parsePlayerAction(message);
        
        // Then
        assertNotNull(action);
        assertEquals(PlayerAction.ActionType.PENG, action.getType());
    }
    
    @Test
    void shouldParseGangActionMing() {
        // Given
        Map<String, Object> actionData = Map.of(
                "tile", "9W",
                "gangType", "MING",
                "claimedFrom", "user456"
        );
        GameMessage message = new GameMessage(GameMessage.MessageType.REQUEST, "gang");
        message.setData(actionData);
        
        // When
        PlayerAction action = messageParser.parsePlayerAction(message);
        
        // Then
        assertNotNull(action);
        assertEquals(PlayerAction.ActionType.GANG, action.getType());
    }
    
    @Test
    void shouldParseGangActionAn() {
        // Given
        Map<String, Object> actionData = Map.of(
                "tile", "1T",
                "gangType", "AN"
        );
        GameMessage message = new GameMessage(GameMessage.MessageType.REQUEST, "gang");
        message.setData(actionData);
        
        // When
        PlayerAction action = messageParser.parsePlayerAction(message);
        
        // Then
        assertNotNull(action);
        assertEquals(PlayerAction.ActionType.GANG, action.getType());
    }
    
    @Test
    void shouldParseHuActionSelfDraw() {
        // Given
        Map<String, Object> actionData = Map.of(
                "winningTile", "8W",
                "selfDraw", true
        );
        GameMessage message = new GameMessage(GameMessage.MessageType.REQUEST, "hu");
        message.setData(actionData);
        
        // When
        PlayerAction action = messageParser.parsePlayerAction(message);
        
        // Then
        assertNotNull(action);
        assertEquals(PlayerAction.ActionType.HU, action.getType());
    }
    
    @Test
    void shouldParseHuActionClaimed() {
        // Given
        Map<String, Object> actionData = Map.of(
                "winningTile", "4T",
                "selfDraw", false,
                "claimedFrom", "user789"
        );
        GameMessage message = new GameMessage(GameMessage.MessageType.REQUEST, "win");
        message.setData(actionData);
        
        // When
        PlayerAction action = messageParser.parsePlayerAction(message);
        
        // Then
        assertNotNull(action);
        assertEquals(PlayerAction.ActionType.HU, action.getType());
    }
    
    @Test
    void shouldParsePassAction() {
        // Given
        GameMessage message = new GameMessage(GameMessage.MessageType.REQUEST, "pass");
        
        // When
        PlayerAction action = messageParser.parsePlayerAction(message);
        
        // Then
        assertNotNull(action);
        assertEquals(PlayerAction.ActionType.PASS, action.getType());
    }
    
    @Test
    void shouldThrowExceptionForUnknownCommand() {
        // Given
        GameMessage message = new GameMessage(GameMessage.MessageType.REQUEST, "unknown");
        message.setData(Map.of());
        
        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
                () -> messageParser.parsePlayerAction(message));
        assertTrue(exception.getMessage().contains("Unknown action command"));
    }
    
    @Test
    void shouldThrowExceptionForMissingTileInPlayAction() {
        // Given
        Map<String, Object> actionData = Map.of("fromHand", true);
        GameMessage message = new GameMessage(GameMessage.MessageType.REQUEST, "play");
        message.setData(actionData);
        
        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
                () -> messageParser.parsePlayerAction(message));
        assertTrue(exception.getMessage().contains("Tile is required"));
    }
    
    @Test
    void shouldThrowExceptionForMissingClaimedFromInPengAction() {
        // Given
        Map<String, Object> actionData = Map.of("tile", "5W");
        GameMessage message = new GameMessage(GameMessage.MessageType.REQUEST, "peng");
        message.setData(actionData);
        
        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
                () -> messageParser.parsePlayerAction(message));
        assertTrue(exception.getMessage().contains("ClaimedFrom is required"));
    }
    
    @Test
    void shouldThrowExceptionForMissingGangTypeInGangAction() {
        // Given
        Map<String, Object> actionData = Map.of("tile", "9T");
        GameMessage message = new GameMessage(GameMessage.MessageType.REQUEST, "gang");
        message.setData(actionData);
        
        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
                () -> messageParser.parsePlayerAction(message));
        assertTrue(exception.getMessage().contains("Gang type is required"));
    }
    
    @Test
    void shouldConvertActionResultToResponseData() {
        // Given
        ActionResult result = ActionResult.success("Action successful", Map.of("score", 100));
        
        // When
        Object responseData = messageParser.convertActionResultToResponseData(result);
        
        // Then
        assertInstanceOf(Map.class, responseData);
        @SuppressWarnings("unchecked")
        Map<String, Object> response = (Map<String, Object>) responseData;
        
        assertTrue((Boolean) response.get("success"));
        assertEquals("Action successful", response.get("message"));
        assertInstanceOf(Map.class, response.get("data"));
        assertNotNull(response.get("timestamp"));
    }
    
    @Test
    void shouldValidateValidRequestMessage() {
        // Given
        GameMessage message = new GameMessage(GameMessage.MessageType.REQUEST, "play");
        message.setRequestId("req123");
        message.setData(Map.of("tile", "5W"));
        
        // When & Then
        assertDoesNotThrow(() -> messageParser.validateMessage(message));
    }
    
    @Test
    void shouldValidateValidResponseMessage() {
        // Given
        GameMessage message = new GameMessage(GameMessage.MessageType.RESPONSE, "gameAction");
        message.setRequestId("req123");
        message.setData(Map.of("success", true));
        
        // When & Then
        assertDoesNotThrow(() -> messageParser.validateMessage(message));
    }
    
    @Test
    void shouldValidateValidErrorMessage() {
        // Given
        GameMessage message = new GameMessage(GameMessage.MessageType.ERROR, "gameAction");
        message.setRequestId("req123");
        message.setError("Invalid action");
        
        // When & Then
        assertDoesNotThrow(() -> messageParser.validateMessage(message));
    }
    
    @Test
    void shouldThrowExceptionForNullMessage() {
        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
                () -> messageParser.validateMessage(null));
        assertEquals("Message cannot be null", exception.getMessage());
    }
    
    @Test
    void shouldThrowExceptionForMissingCommand() {
        // Given
        GameMessage message = new GameMessage();
        message.setType(GameMessage.MessageType.REQUEST);
        
        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
                () -> messageParser.validateMessage(message));
        assertTrue(exception.getMessage().contains("command is required"));
    }
    
    @Test
    void shouldThrowExceptionForMissingRequestIdInRequest() {
        // Given
        GameMessage message = new GameMessage(GameMessage.MessageType.REQUEST, "play");
        
        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
                () -> messageParser.validateMessage(message));
        assertTrue(exception.getMessage().contains("Request ID is required"));
    }
    
    @Test
    void shouldThrowExceptionForMissingErrorInErrorMessage() {
        // Given
        GameMessage message = new GameMessage(GameMessage.MessageType.ERROR, "play");
        message.setRequestId("req123");
        
        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
                () -> messageParser.validateMessage(message));
        assertTrue(exception.getMessage().contains("Error message is required"));
    }
}