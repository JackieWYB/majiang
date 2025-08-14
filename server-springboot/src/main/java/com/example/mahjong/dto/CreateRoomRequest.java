package com.example.mahjong.dto;

// Using a flexible Map to represent the JSON config from the client
import java.util.Map;

public class CreateRoomRequest {
    private String ruleName;
    private Map<String, Object> config;

    public String getRuleName() {
        return ruleName;
    }

    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    public Map<String, Object> getConfig() {
        return config;
    }

    public void setConfig(Map<String, Object> config) {
        this.config = config;
    }
}
