package com.mahjong.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Base class for all game actions that can be logged and replayed
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "actionType"
)
@JsonSubTypes({
    @JsonSubTypes.Type(value = GameAction.DiscardActionDto.class, name = "DISCARD"),
    @JsonSubTypes.Type(value = GameAction.PengActionDto.class, name = "PENG"),
    @JsonSubTypes.Type(value = GameAction.GangActionDto.class, name = "GANG"),
    @JsonSubTypes.Type(value = GameAction.ChiActionDto.class, name = "CHI"),
    @JsonSubTypes.Type(value = GameAction.HuActionDto.class, name = "HU"),
    @JsonSubTypes.Type(value = GameAction.PassActionDto.class, name = "PASS"),
    @JsonSubTypes.Type(value = GameAction.DrawActionDto.class, name = "DRAW"),
    @JsonSubTypes.Type(value = GameAction.GameStartActionDto.class, name = "GAME_START"),
    @JsonSubTypes.Type(value = GameAction.GameEndActionDto.class, name = "GAME_END")
})
public abstract class GameAction {
    
    public enum ActionType {
        DISCARD, PENG, GANG, CHI, HU, PASS, DRAW, GAME_START, GAME_END
    }
    
    @JsonProperty("sequenceNumber")
    private Long sequenceNumber;
    
    @JsonProperty("timestamp")
    private LocalDateTime timestamp;
    
    @JsonProperty("userId")
    private Long userId;
    
    @JsonProperty("seatIndex")
    private Integer seatIndex;
    
    @JsonProperty("actionType")
    private ActionType actionType;
    
    protected GameAction() {
        // Default constructor for Jackson deserialization
    }
    
    protected GameAction(ActionType actionType, Long userId, Integer seatIndex) {
        this.actionType = actionType;
        this.userId = userId;
        this.seatIndex = seatIndex;
        this.timestamp = LocalDateTime.now();
    }
    
    // Getters and Setters
    public Long getSequenceNumber() {
        return sequenceNumber;
    }
    
    public void setSequenceNumber(Long sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }
    
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
    
    public Long getUserId() {
        return userId;
    }
    
    public void setUserId(Long userId) {
        this.userId = userId;
    }
    
    public Integer getSeatIndex() {
        return seatIndex;
    }
    
    public void setSeatIndex(Integer seatIndex) {
        this.seatIndex = seatIndex;
    }
    
    public ActionType getActionType() {
        return actionType;
    }
    
    public void setActionType(ActionType actionType) {
        this.actionType = actionType;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GameAction that = (GameAction) o;
        return Objects.equals(sequenceNumber, that.sequenceNumber);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(sequenceNumber);
    }
    
    // Concrete action implementations
    
    public static class DiscardActionDto extends GameAction {
        @JsonProperty("tile")
        private String tile;
        
        @JsonProperty("fromHand")
        private boolean fromHand = true;
        
        public DiscardActionDto() {
            super();
            setActionType(ActionType.DISCARD);
        }
        
        public DiscardActionDto(Long userId, Integer seatIndex, String tile, boolean fromHand) {
            super(ActionType.DISCARD, userId, seatIndex);
            this.tile = tile;
            this.fromHand = fromHand;
        }
        
        public String getTile() { return tile; }
        public void setTile(String tile) { this.tile = tile; }
        public boolean isFromHand() { return fromHand; }
        public void setFromHand(boolean fromHand) { this.fromHand = fromHand; }
    }
    
    public static class PengActionDto extends GameAction {
        @JsonProperty("tile")
        private String tile;
        
        @JsonProperty("claimedFromUserId")
        private Long claimedFromUserId;
        
        public PengActionDto() {
            super();
            setActionType(ActionType.PENG);
        }
        
        public PengActionDto(Long userId, Integer seatIndex, String tile, Long claimedFromUserId) {
            super(ActionType.PENG, userId, seatIndex);
            this.tile = tile;
            this.claimedFromUserId = claimedFromUserId;
        }
        
        public String getTile() { return tile; }
        public void setTile(String tile) { this.tile = tile; }
        public Long getClaimedFromUserId() { return claimedFromUserId; }
        public void setClaimedFromUserId(Long claimedFromUserId) { this.claimedFromUserId = claimedFromUserId; }
    }
    
    public static class GangActionDto extends GameAction {
        public enum GangType {
            MING,    // Exposed Gang (claimed from discard)
            AN,      // Concealed Gang (from hand)
            BU       // Upgrade Gang (upgrade Peng to Gang)
        }
        
        @JsonProperty("tile")
        private String tile;
        
        @JsonProperty("gangType")
        private GangType gangType;
        
        @JsonProperty("claimedFromUserId")
        private Long claimedFromUserId;
        
        public GangActionDto() {
            super();
            setActionType(ActionType.GANG);
        }
        
        public GangActionDto(Long userId, Integer seatIndex, String tile, GangType gangType, Long claimedFromUserId) {
            super(ActionType.GANG, userId, seatIndex);
            this.tile = tile;
            this.gangType = gangType;
            this.claimedFromUserId = claimedFromUserId;
        }
        
        public String getTile() { return tile; }
        public void setTile(String tile) { this.tile = tile; }
        public GangType getGangType() { return gangType; }
        public void setGangType(GangType gangType) { this.gangType = gangType; }
        public Long getClaimedFromUserId() { return claimedFromUserId; }
        public void setClaimedFromUserId(Long claimedFromUserId) { this.claimedFromUserId = claimedFromUserId; }
    }
    
    public static class ChiActionDto extends GameAction {
        @JsonProperty("tile")
        private String tile;
        
        @JsonProperty("sequence")
        private String sequence;
        
        @JsonProperty("claimedFromUserId")
        private Long claimedFromUserId;
        
        public ChiActionDto() {
            super();
            setActionType(ActionType.CHI);
        }
        
        public ChiActionDto(Long userId, Integer seatIndex, String tile, String sequence, Long claimedFromUserId) {
            super(ActionType.CHI, userId, seatIndex);
            this.tile = tile;
            this.sequence = sequence;
            this.claimedFromUserId = claimedFromUserId;
        }
        
        public String getTile() { return tile; }
        public void setTile(String tile) { this.tile = tile; }
        public String getSequence() { return sequence; }
        public void setSequence(String sequence) { this.sequence = sequence; }
        public Long getClaimedFromUserId() { return claimedFromUserId; }
        public void setClaimedFromUserId(Long claimedFromUserId) { this.claimedFromUserId = claimedFromUserId; }
    }
    
    public static class HuActionDto extends GameAction {
        @JsonProperty("winningTile")
        private String winningTile;
        
        @JsonProperty("selfDraw")
        private boolean selfDraw;
        
        @JsonProperty("winningType")
        private String winningType;
        
        public HuActionDto() {
            super();
            setActionType(ActionType.HU);
        }
        
        public HuActionDto(Long userId, Integer seatIndex, String winningTile, boolean selfDraw, String winningType) {
            super(ActionType.HU, userId, seatIndex);
            this.winningTile = winningTile;
            this.selfDraw = selfDraw;
            this.winningType = winningType;
        }
        
        public String getWinningTile() { return winningTile; }
        public void setWinningTile(String winningTile) { this.winningTile = winningTile; }
        public boolean isSelfDraw() { return selfDraw; }
        public void setSelfDraw(boolean selfDraw) { this.selfDraw = selfDraw; }
        public String getWinningType() { return winningType; }
        public void setWinningType(String winningType) { this.winningType = winningType; }
    }
    
    public static class PassActionDto extends GameAction {
        public PassActionDto() {
            super();
            setActionType(ActionType.PASS);
        }
        
        public PassActionDto(Long userId, Integer seatIndex) {
            super(ActionType.PASS, userId, seatIndex);
        }
    }
    
    public static class DrawActionDto extends GameAction {
        @JsonProperty("tile")
        private String tile;
        
        public DrawActionDto() {
            super();
            setActionType(ActionType.DRAW);
        }
        
        public DrawActionDto(Long userId, Integer seatIndex, String tile) {
            super(ActionType.DRAW, userId, seatIndex);
            this.tile = tile;
        }
        
        public String getTile() { return tile; }
        public void setTile(String tile) { this.tile = tile; }
    }
    
    public static class GameStartActionDto extends GameAction {
        @JsonProperty("dealerUserId")
        private Long dealerUserId;
        
        @JsonProperty("randomSeed")
        private String randomSeed;
        
        public GameStartActionDto() {
            super();
            setActionType(ActionType.GAME_START);
        }
        
        public GameStartActionDto(Long dealerUserId, String randomSeed) {
            super(ActionType.GAME_START, null, null);
            this.dealerUserId = dealerUserId;
            this.randomSeed = randomSeed;
        }
        
        public Long getDealerUserId() { return dealerUserId; }
        public void setDealerUserId(Long dealerUserId) { this.dealerUserId = dealerUserId; }
        public String getRandomSeed() { return randomSeed; }
        public void setRandomSeed(String randomSeed) { this.randomSeed = randomSeed; }
    }
    
    public static class GameEndActionDto extends GameAction {
        @JsonProperty("winnerId")
        private Long winnerId;
        
        @JsonProperty("gameResult")
        private String gameResult;
        
        public GameEndActionDto() {
            super();
            setActionType(ActionType.GAME_END);
        }
        
        public GameEndActionDto(Long winnerId, String gameResult) {
            super(ActionType.GAME_END, null, null);
            this.winnerId = winnerId;
            this.gameResult = gameResult;
        }
        
        public Long getWinnerId() { return winnerId; }
        public void setWinnerId(Long winnerId) { this.winnerId = winnerId; }
        public String getGameResult() { return gameResult; }
        public void setGameResult(String gameResult) { this.gameResult = gameResult; }
    }
}