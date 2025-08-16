package com.mahjong.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request DTO for joining a room
 */
public class JoinRoomRequest {
    
    @NotBlank(message = "Room ID is required")
    @Pattern(regexp = "\\d{6}", message = "Room ID must be 6 digits")
    private String roomId;
    
    public JoinRoomRequest() {}
    
    public JoinRoomRequest(String roomId) {
        this.roomId = roomId;
    }
    
    public String getRoomId() {
        return roomId;
    }
    
    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }
    
    @Override
    public String toString() {
        return "JoinRoomRequest{" +
                "roomId='" + roomId + '\'' +
                '}';
    }
}