package com.mahjong.service;

/**
 * Result of a player action
 */
public class ActionResult {
    private final boolean success;
    private final String message;
    private final Object data;
    
    private ActionResult(boolean success, String message, Object data) {
        this.success = success;
        this.message = message;
        this.data = data;
    }
    
    public static ActionResult success(String message) {
        return new ActionResult(true, message, null);
    }
    
    public static ActionResult success(String message, Object data) {
        return new ActionResult(true, message, data);
    }
    
    public static ActionResult failure(String message) {
        return new ActionResult(false, message, null);
    }
    
    public static ActionResult failure(String message, Object data) {
        return new ActionResult(false, message, data);
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public boolean isFailure() {
        return !success;
    }
    
    public String getMessage() {
        return message;
    }
    
    public Object getData() {
        return data;
    }
    
    @Override
    public String toString() {
        return String.format("ActionResult{success=%s, message='%s', data=%s}", 
                success, message, data);
    }
}

/**
 * Result of action validation
 */
class ActionValidationResult {
    private final boolean valid;
    private final String errorMessage;
    
    private ActionValidationResult(boolean valid, String errorMessage) {
        this.valid = valid;
        this.errorMessage = errorMessage;
    }
    
    public static ActionValidationResult valid() {
        return new ActionValidationResult(true, null);
    }
    
    public static ActionValidationResult invalid(String errorMessage) {
        return new ActionValidationResult(false, errorMessage);
    }
    
    public boolean isValid() {
        return valid;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    @Override
    public String toString() {
        return String.format("ActionValidationResult{valid=%s, errorMessage='%s'}", 
                valid, errorMessage);
    }
}

/**
 * Pending action for claim scenarios
 */
class PendingAction {
    private final String userId;
    private final com.mahjong.model.game.Tile claimedTile;
    private final String claimedFrom;
    private final java.util.List<com.mahjong.model.game.PlayerState.ActionType> availableActions;
    private final long deadline;
    
    public PendingAction(String userId, 
                        com.mahjong.model.game.Tile claimedTile, 
                        String claimedFrom,
                        java.util.List<com.mahjong.model.game.PlayerState.ActionType> availableActions,
                        long deadline) {
        this.userId = userId;
        this.claimedTile = claimedTile;
        this.claimedFrom = claimedFrom;
        this.availableActions = new java.util.ArrayList<>(availableActions);
        this.deadline = deadline;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public com.mahjong.model.game.Tile getClaimedTile() {
        return claimedTile;
    }
    
    public String getClaimedFrom() {
        return claimedFrom;
    }
    
    public java.util.List<com.mahjong.model.game.PlayerState.ActionType> getAvailableActions() {
        return new java.util.ArrayList<>(availableActions);
    }
    
    public long getDeadline() {
        return deadline;
    }
    
    public boolean isExpired() {
        return System.currentTimeMillis() > deadline;
    }
    
    @Override
    public String toString() {
        return String.format("PendingAction{userId='%s', tile=%s, claimedFrom='%s', actions=%s, deadline=%d}", 
                userId, claimedTile, claimedFrom, availableActions, deadline);
    }
}