package com.mahjong.service;

import com.mahjong.model.game.PlayerState;

/**
 * Base class for player actions
 */
public abstract class PlayerAction {
    
    public enum ActionType {
        DISCARD,
        PENG,
        GANG,
        CHI,
        HU,
        PASS;
        
        public PlayerState.ActionType toPlayerActionType() {
            switch (this) {
                case DISCARD: return PlayerState.ActionType.DISCARD;
                case PENG: return PlayerState.ActionType.PENG;
                case GANG: return PlayerState.ActionType.GANG;
                case CHI: return PlayerState.ActionType.CHI;
                case HU: return PlayerState.ActionType.HU;
                case PASS: return PlayerState.ActionType.PASS;
                default: throw new IllegalArgumentException("Unknown action type: " + this);
            }
        }
    }
    
    private final ActionType type;
    private final long timestamp;
    
    protected PlayerAction(ActionType type) {
        this.type = type;
        this.timestamp = System.currentTimeMillis();
    }
    
    public ActionType getType() {
        return type;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    @Override
    public String toString() {
        return String.format("%s{type=%s, timestamp=%d}", 
                getClass().getSimpleName(), type, timestamp);
    }
}

/**
 * Discard action
 */
class DiscardAction extends PlayerAction {
    private final String tile;
    
    public DiscardAction(String tile) {
        super(ActionType.DISCARD);
        this.tile = tile;
    }
    
    public String getTile() {
        return tile;
    }
    
    @Override
    public String toString() {
        return String.format("DiscardAction{tile='%s', timestamp=%d}", tile, getTimestamp());
    }
}

/**
 * Peng action
 */
class PengAction extends PlayerAction {
    private final String tile;
    private final String claimedFrom;
    
    public PengAction(String tile, String claimedFrom) {
        super(ActionType.PENG);
        this.tile = tile;
        this.claimedFrom = claimedFrom;
    }
    
    public String getTile() {
        return tile;
    }
    
    public String getClaimedFrom() {
        return claimedFrom;
    }
    
    @Override
    public String toString() {
        return String.format("PengAction{tile='%s', claimedFrom='%s', timestamp=%d}", 
                tile, claimedFrom, getTimestamp());
    }
}

/**
 * Gang action
 */
class GangAction extends PlayerAction {
    
    public enum GangType {
        MING,    // Exposed Gang (claimed from discard)
        AN,      // Concealed Gang (from hand)
        BU       // Upgrade Gang (upgrade Peng to Gang)
    }
    
    private final String tile;
    private final GangType gangType;
    private final String claimedFrom; // null for concealed gang
    
    public GangAction(String tile, GangType gangType, String claimedFrom) {
        super(ActionType.GANG);
        this.tile = tile;
        this.gangType = gangType;
        this.claimedFrom = claimedFrom;
    }
    
    public String getTile() {
        return tile;
    }
    
    public GangType getGangType() {
        return gangType;
    }
    
    public String getClaimedFrom() {
        return claimedFrom;
    }
    
    @Override
    public String toString() {
        return String.format("GangAction{tile='%s', type=%s, claimedFrom='%s', timestamp=%d}", 
                tile, gangType, claimedFrom, getTimestamp());
    }
}

/**
 * Chi action
 */
class ChiAction extends PlayerAction {
    private final String tile;
    private final String sequence; // e.g., "123" for tiles 1,2,3
    private final String claimedFrom;
    
    public ChiAction(String tile, String sequence, String claimedFrom) {
        super(ActionType.CHI);
        this.tile = tile;
        this.sequence = sequence;
        this.claimedFrom = claimedFrom;
    }
    
    public String getTile() {
        return tile;
    }
    
    public String getSequence() {
        return sequence;
    }
    
    public String getClaimedFrom() {
        return claimedFrom;
    }
    
    @Override
    public String toString() {
        return String.format("ChiAction{tile='%s', sequence='%s', claimedFrom='%s', timestamp=%d}", 
                tile, sequence, claimedFrom, getTimestamp());
    }
}

/**
 * Hu (Win) action
 */
class HuAction extends PlayerAction {
    private final String winningTile;
    private final boolean selfDraw; // true if won by drawing, false if won by claiming
    
    public HuAction(String winningTile, boolean selfDraw) {
        super(ActionType.HU);
        this.winningTile = winningTile;
        this.selfDraw = selfDraw;
    }
    
    public String getWinningTile() {
        return winningTile;
    }
    
    public boolean isSelfDraw() {
        return selfDraw;
    }
    
    @Override
    public String toString() {
        return String.format("HuAction{winningTile='%s', selfDraw=%s, timestamp=%d}", 
                winningTile, selfDraw, getTimestamp());
    }
}

/**
 * Pass action
 */
class PassAction extends PlayerAction {
    
    public PassAction() {
        super(ActionType.PASS);
    }
    
    @Override
    public String toString() {
        return String.format("PassAction{timestamp=%d}", getTimestamp());
    }
}