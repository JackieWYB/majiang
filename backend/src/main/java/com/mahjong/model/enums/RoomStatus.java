package com.mahjong.model.enums;

/**
 * Room status enumeration
 */
public enum RoomStatus {
    WAITING,    // Waiting for players to join
    READY,      // All players joined, ready to start
    PLAYING,    // Game in progress
    FINISHED,   // Game completed
    DISSOLVED   // Room dissolved
}