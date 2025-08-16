package com.mahjong.model.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for creating a room
 */
public class CreateRoomRequest {
    
    @NotNull(message = "Rule ID is required")
    private Long ruleId;
    
    public CreateRoomRequest() {}
    
    public CreateRoomRequest(Long ruleId) {
        this.ruleId = ruleId;
    }
    
    public Long getRuleId() {
        return ruleId;
    }
    
    public void setRuleId(Long ruleId) {
        this.ruleId = ruleId;
    }
    
    @Override
    public String toString() {
        return "CreateRoomRequest{" +
                "ruleId=" + ruleId +
                '}';
    }
}