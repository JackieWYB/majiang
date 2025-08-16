package com.mahjong.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * WebSocket message structure for game communication
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GameMessage {
    
    public enum MessageType {
        EVENT,      // Server -> Client events
        REQUEST,    // Client -> Server requests
        RESPONSE,   // Server -> Client responses
        ERROR       // Error messages
    }
    
    @JsonProperty("type")
    private MessageType type;
    
    @JsonProperty("cmd")
    private String command;
    
    @JsonProperty("reqId")
    private String requestId;
    
    @JsonProperty("roomId")
    private String roomId;
    
    @JsonProperty("data")
    private Object data;
    
    @JsonProperty("timestamp")
    private Long timestamp;
    
    @JsonProperty("error")
    private String error;
    
    public GameMessage() {
        this.timestamp = System.currentTimeMillis();
    }
    
    public GameMessage(MessageType type, String command) {
        this();
        this.type = type;
        this.command = command;
    }
    
    public GameMessage(MessageType type, String command, Object data) {
        this(type, command);
        this.data = data;
    }
    
    public GameMessage(MessageType type, String command, String roomId, Object data) {
        this(type, command, data);
        this.roomId = roomId;
    }
    
    // Static factory methods for common message types
    public static GameMessage event(String command, String roomId, Object data) {
        return new GameMessage(MessageType.EVENT, command, roomId, data);
    }
    
    public static GameMessage response(String command, String requestId, Object data) {
        GameMessage message = new GameMessage(MessageType.RESPONSE, command, data);
        message.setRequestId(requestId);
        return message;
    }
    
    public static GameMessage error(String command, String requestId, String error) {
        GameMessage message = new GameMessage(MessageType.ERROR, command);
        message.setRequestId(requestId);
        message.setError(error);
        return message;
    }
    
    // Getters and setters
    public MessageType getType() {
        return type;
    }
    
    public void setType(MessageType type) {
        this.type = type;
    }
    
    public String getCommand() {
        return command;
    }
    
    public void setCommand(String command) {
        this.command = command;
    }
    
    public String getRequestId() {
        return requestId;
    }
    
    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
    
    public String getRoomId() {
        return roomId;
    }
    
    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }
    
    public Object getData() {
        return data;
    }
    
    public void setData(Object data) {
        this.data = data;
    }
    
    public Long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }
    
    public String getError() {
        return error;
    }
    
    public void setError(String error) {
        this.error = error;
    }
    
    @Override
    public String toString() {
        return String.format("GameMessage{type=%s, command='%s', roomId='%s', requestId='%s', error='%s'}", 
                type, command, roomId, requestId, error);
    }
}