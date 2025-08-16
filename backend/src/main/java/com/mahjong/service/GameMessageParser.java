package com.mahjong.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mahjong.model.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Service for parsing and converting game messages to appropriate action objects
 */
@Service
public class GameMessageParser {
    
    private static final Logger logger = LoggerFactory.getLogger(GameMessageParser.class);
    
    private final ObjectMapper objectMapper;
    
    @Autowired
    public GameMessageParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    /**
     * Parse player action from game message
     */
    public PlayerAction parsePlayerAction(GameMessage message) {
        try {
            String command = message.getCommand();
            Object data = message.getData();
            
            // Pass action doesn't need data
            if (data == null && !"pass".equalsIgnoreCase(command)) {
                throw new IllegalArgumentException("Action data is required");
            }
            
            switch (command.toLowerCase()) {
                case "play":
                case "discard":
                    return parsePlayAction(data);
                    
                case "peng":
                    return parsePengAction(data);
                    
                case "gang":
                    return parseGangAction(data);
                    
                case "chi":
                    return parseChiAction(data);
                    
                case "hu":
                case "win":
                    return parseHuAction(data);
                    
                case "pass":
                    return new PassAction();
                    
                default:
                    throw new IllegalArgumentException("Unknown action command: " + command);
            }
            
        } catch (Exception e) {
            logger.error("Failed to parse player action from message: {}", message, e);
            throw new IllegalArgumentException("Invalid action format: " + e.getMessage(), e);
        }
    }
    
    /**
     * Parse play/discard action
     */
    private PlayerAction parsePlayAction(Object data) {
        try {
            PlayAction playDto;
            if (data instanceof Map) {
                playDto = objectMapper.convertValue(data, PlayAction.class);
            } else if (data instanceof String) {
                // Simple string tile format
                playDto = new PlayAction((String) data);
            } else {
                playDto = objectMapper.convertValue(data, PlayAction.class);
            }
            
            if (playDto.getTile() == null || playDto.getTile().trim().isEmpty()) {
                throw new IllegalArgumentException("Tile is required for play action");
            }
            
            return new DiscardAction(playDto.getTile());
            
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid play action format: " + e.getMessage(), e);
        }
    }
    
    /**
     * Parse Peng action
     */
    private PlayerAction parsePengAction(Object data) {
        try {
            PengActionDto pengDto = objectMapper.convertValue(data, PengActionDto.class);
            
            if (pengDto.getTile() == null || pengDto.getTile().trim().isEmpty()) {
                throw new IllegalArgumentException("Tile is required for Peng action");
            }
            
            if (pengDto.getClaimedFrom() == null || pengDto.getClaimedFrom().trim().isEmpty()) {
                throw new IllegalArgumentException("ClaimedFrom is required for Peng action");
            }
            
            return new PengAction(pengDto.getTile(), pengDto.getClaimedFrom());
            
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid Peng action format: " + e.getMessage(), e);
        }
    }
    
    /**
     * Parse Gang action
     */
    private PlayerAction parseGangAction(Object data) {
        try {
            GangActionDto gangDto = objectMapper.convertValue(data, GangActionDto.class);
            
            if (gangDto.getTile() == null || gangDto.getTile().trim().isEmpty()) {
                throw new IllegalArgumentException("Tile is required for Gang action");
            }
            
            if (gangDto.getGangType() == null) {
                throw new IllegalArgumentException("Gang type is required for Gang action");
            }
            
            // Convert DTO gang type to service gang type
            GangAction.GangType serviceGangType;
            switch (gangDto.getGangType()) {
                case MING:
                    serviceGangType = GangAction.GangType.MING;
                    break;
                case AN:
                    serviceGangType = GangAction.GangType.AN;
                    break;
                case BU:
                    serviceGangType = GangAction.GangType.BU;
                    break;
                default:
                    throw new IllegalArgumentException("Unknown gang type: " + gangDto.getGangType());
            }
            
            return new GangAction(gangDto.getTile(), serviceGangType, gangDto.getClaimedFrom());
            
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid Gang action format: " + e.getMessage(), e);
        }
    }
    
    /**
     * Parse Chi action
     */
    private PlayerAction parseChiAction(Object data) {
        try {
            // Chi action parsing would be implemented here
            // For now, throw unsupported since Chi is not commonly used in 3-player Mahjong
            throw new UnsupportedOperationException("Chi action is not supported in 3-player Mahjong");
            
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid Chi action format: " + e.getMessage(), e);
        }
    }
    
    /**
     * Parse Hu (Win) action
     */
    private PlayerAction parseHuAction(Object data) {
        try {
            HuActionDto huDto = objectMapper.convertValue(data, HuActionDto.class);
            
            if (huDto.getWinningTile() == null || huDto.getWinningTile().trim().isEmpty()) {
                throw new IllegalArgumentException("Winning tile is required for Hu action");
            }
            
            return new HuAction(huDto.getWinningTile(), huDto.isSelfDraw());
            
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid Hu action format: " + e.getMessage(), e);
        }
    }
    
    /**
     * Convert action result to response data
     */
    public Object convertActionResultToResponseData(ActionResult result) {
        try {
            return Map.of(
                    "success", result.isSuccess(),
                    "message", result.getMessage() != null ? result.getMessage() : "",
                    "data", result.getData() != null ? result.getData() : Map.of(),
                    "timestamp", System.currentTimeMillis()
            );
            
        } catch (Exception e) {
            logger.error("Failed to convert action result to response data", e);
            return Map.of(
                    "success", false,
                    "message", "Failed to process action result",
                    "timestamp", System.currentTimeMillis()
            );
        }
    }
    
    /**
     * Validate message format
     */
    public void validateMessage(GameMessage message) {
        if (message == null) {
            throw new IllegalArgumentException("Message cannot be null");
        }
        
        if (message.getCommand() == null || message.getCommand().trim().isEmpty()) {
            throw new IllegalArgumentException("Message command is required");
        }
        
        if (message.getType() == null) {
            throw new IllegalArgumentException("Message type is required");
        }
        
        // Additional validation based on message type
        switch (message.getType()) {
            case REQUEST:
                if (message.getRequestId() == null || message.getRequestId().trim().isEmpty()) {
                    throw new IllegalArgumentException("Request ID is required for request messages");
                }
                break;
                
            case RESPONSE:
                if (message.getRequestId() == null || message.getRequestId().trim().isEmpty()) {
                    throw new IllegalArgumentException("Request ID is required for response messages");
                }
                break;
                
            case ERROR:
                if (message.getError() == null || message.getError().trim().isEmpty()) {
                    throw new IllegalArgumentException("Error message is required for error messages");
                }
                break;
        }
    }
}