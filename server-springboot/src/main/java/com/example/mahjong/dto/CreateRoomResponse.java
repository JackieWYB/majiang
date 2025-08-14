package com.example.mahjong.dto;

public class CreateRoomResponse {
    private Long roomId;

    public CreateRoomResponse(Long roomId) {
        this.roomId = roomId;
    }

    public Long getRoomId() {
        return roomId;
    }

    public void setRoomId(Long roomId) {
        this.roomId = roomId;
    }
}
