package com.mahjong.model.dto;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Response DTO for system health monitoring
 */
public class SystemHealthResponse {
    
    private String status;
    private LocalDateTime timestamp;
    private long activeRooms;
    private long activePlayers;
    private long totalUsers;
    private long onlineUsers;
    private double averageResponseTime;
    private long totalGamesPlayed;
    private long gamesPlayedToday;
    private Map<String, Object> systemMetrics;
    private Map<String, String> componentStatus;
    
    // Constructors
    public SystemHealthResponse() {
        this.timestamp = LocalDateTime.now();
    }
    
    public SystemHealthResponse(String status) {
        this();
        this.status = status;
    }
    
    // Getters and Setters
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
    
    public long getActiveRooms() {
        return activeRooms;
    }
    
    public void setActiveRooms(long activeRooms) {
        this.activeRooms = activeRooms;
    }
    
    public long getActivePlayers() {
        return activePlayers;
    }
    
    public void setActivePlayers(long activePlayers) {
        this.activePlayers = activePlayers;
    }
    
    public long getTotalUsers() {
        return totalUsers;
    }
    
    public void setTotalUsers(long totalUsers) {
        this.totalUsers = totalUsers;
    }
    
    public long getOnlineUsers() {
        return onlineUsers;
    }
    
    public void setOnlineUsers(long onlineUsers) {
        this.onlineUsers = onlineUsers;
    }
    
    public double getAverageResponseTime() {
        return averageResponseTime;
    }
    
    public void setAverageResponseTime(double averageResponseTime) {
        this.averageResponseTime = averageResponseTime;
    }
    
    public long getTotalGamesPlayed() {
        return totalGamesPlayed;
    }
    
    public void setTotalGamesPlayed(long totalGamesPlayed) {
        this.totalGamesPlayed = totalGamesPlayed;
    }
    
    public long getGamesPlayedToday() {
        return gamesPlayedToday;
    }
    
    public void setGamesPlayedToday(long gamesPlayedToday) {
        this.gamesPlayedToday = gamesPlayedToday;
    }
    
    public Map<String, Object> getSystemMetrics() {
        return systemMetrics;
    }
    
    public void setSystemMetrics(Map<String, Object> systemMetrics) {
        this.systemMetrics = systemMetrics;
    }
    
    public Map<String, String> getComponentStatus() {
        return componentStatus;
    }
    
    public void setComponentStatus(Map<String, String> componentStatus) {
        this.componentStatus = componentStatus;
    }
}