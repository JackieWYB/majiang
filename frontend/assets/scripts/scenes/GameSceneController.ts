import { _decorator, Component, Node, Button, Label, Prefab, instantiate, Layout, Vec3, tween, Color } from 'cc';
import { BaseUIController } from '../ui/BaseUIController';
import { GameStateManager, GameState, GamePhase } from '../managers/GameStateManager';
import { NetworkManager } from '../network/NetworkManager';
import { SceneManager } from '../managers/SceneManager';
const { ccclass, property } = _decorator;

interface TileData {
    suit: string; // WAN, TIAO, TONG
    rank: number; // 1-9
    id: string;
}

interface PlayerDisplayInfo {
    seatIndex: number;
    userId: string;
    nickname: string;
    avatar: string;
    handTileCount: number;
    melds: any[];
    isDealer: boolean;
    isCurrentPlayer: boolean;
}

/**
 * Game Scene Controller with 3-player layout and tile rendering
 */
@ccclass('GameSceneController')
export class GameSceneController extends BaseUIController {
    // Game board areas
    @property(Node)
    private gameBoard: Node = null!;

    @property(Node)
    private centerArea: Node = null!;

    // Player areas (3-player layout)
    @property(Node)
    private bottomPlayerArea: Node = null!; // Local player

    @property(Node)
    private leftPlayerArea: Node = null!;

    @property(Node)
    private rightPlayerArea: Node = null!;

    // Hand and discard areas
    @property(Node)
    private playerHandArea: Node = null!;

    @property(Node)
    private playerMeldArea: Node = null!;

    @property(Node)
    private discardArea: Node = null!;

    // Action buttons
    @property(Node)
    private actionButtonsArea: Node = null!;

    @property(Button)
    private discardButton: Button = null!;

    @property(Button)
    private pengButton: Button = null!;

    @property(Button)
    private gangButton: Button = null!;

    @property(Button)
    private huButton: Button = null!;

    @property(Button)
    private passButton: Button = null!;

    // UI elements
    @property(Label)
    private roomIdLabel: Label = null!;

    @property(Label)
    private remainingTilesLabel: Label = null!;

    @property(Label)
    private currentPlayerLabel: Label = null!;

    @property(Label)
    private turnTimerLabel: Label = null!;

    @property(Node)
    private waitingIndicator: Node = null!;

    // Prefabs
    @property(Prefab)
    private tilePrefab: Prefab = null!;

    @property(Prefab)
    private meldPrefab: Prefab = null!;

    // Game state
    private _currentRoomId: string = '';
    private _localPlayerSeat: number = 0;
    private _selectedTileIndex: number = -1;
    private _availableActions: string[] = [];
    private _turnTimer: number = 0;
    private _turnTimerHandle: number = 0;

    protected setupComponents(): void {
        this.loadCurrentRoom();
        this.initializeGameBoard();
        this.hideActionButtons();
        this.showWaitingIndicator(true);
        this.setupPlayerAreas();
    }

    protected bindEvents(): void {
        // Bind action buttons
        if (this.discardButton) {
            this.discardButton.node.on(Button.EventType.CLICK, () => this.onDiscardClick(), this);
        }
        if (this.pengButton) {
            this.pengButton.node.on(Button.EventType.CLICK, () => this.onActionClick('PENG'), this);
        }
        if (this.gangButton) {
            this.gangButton.node.on(Button.EventType.CLICK, () => this.onActionClick('GANG'), this);
        }
        if (this.huButton) {
            this.huButton.node.on(Button.EventType.CLICK, () => this.onActionClick('HU'), this);
        }
        if (this.passButton) {
            this.passButton.node.on(Button.EventType.CLICK, () => this.onActionClick('PASS'), this);
        }

        // Register network message handlers
        NetworkManager.instance.onMessage('GameSnapshot', this.onGameSnapshot.bind(this));
        NetworkManager.instance.onMessage('PlayerAction', this.onPlayerAction.bind(this));
        NetworkManager.instance.onMessage('GameSettlement', this.onGameSettlement.bind(this));
        NetworkManager.instance.onMessage('AvailableActions', this.onAvailableActions.bind(this));
        NetworkManager.instance.onMessage('TurnTimer', this.onTurnTimer.bind(this));
        NetworkManager.instance.onMessage('GameStarted', this.onGameStarted.bind(this));
    }

    protected unbindEvents(): void {
        // Unbind action buttons
        if (this.discardButton) {
            this.discardButton.node.off(Button.EventType.CLICK);
        }
        if (this.pengButton) {
            this.pengButton.node.off(Button.EventType.CLICK);
        }
        if (this.gangButton) {
            this.gangButton.node.off(Button.EventType.CLICK);
        }
        if (this.huButton) {
            this.huButton.node.off(Button.EventType.CLICK);
        }
        if (this.passButton) {
            this.passButton.node.off(Button.EventType.CLICK);
        }

        // Unregister network message handlers
        NetworkManager.instance.offMessage('GameSnapshot', this.onGameSnapshot);
        NetworkManager.instance.offMessage('PlayerAction', this.onPlayerAction);
        NetworkManager.instance.offMessage('GameSettlement', this.onGameSettlement);
        NetworkManager.instance.offMessage('AvailableActions', this.onAvailableActions);
        NetworkManager.instance.offMessage('TurnTimer', this.onTurnTimer);
        NetworkManager.instance.offMessage('GameStarted', this.onGameStarted);

        // Clear timer
        if (this._turnTimerHandle > 0) {
            clearInterval(this._turnTimerHandle);
        }
    }

    private loadCurrentRoom(): void {
        // Get current room ID from storage
        if (typeof wx !== 'undefined') {
            this._currentRoomId = wx.getStorageSync('current_room_id') || '';
        } else {
            this._currentRoomId = localStorage.getItem('current_room_id') || '';
        }

        if (this._currentRoomId) {
            // Request game snapshot
            NetworkManager.instance.sendMessage({
                type: 'REQ',
                cmd: 'GetGameSnapshot',
                roomId: this._currentRoomId,
                timestamp: Date.now()
            });
        }
    }

    private initializeGameBoard(): void {
        // Initialize game board layout for 3-player Mahjong
        console.log('Initializing 3-player game board...');
        
        // Set room ID display
        if (this.roomIdLabel) {
            this.roomIdLabel.string = `房间: ${this._currentRoomId}`;
        }
        
        // Initialize tile areas
        this.clearAllTileAreas();
    }

    private setupPlayerAreas(): void {
        // Setup 3-player layout
        // Bottom: Local player (seat 0)
        // Left: Player 1
        // Right: Player 2
        
        if (this.bottomPlayerArea) {
            this.setupPlayerAreaLayout(this.bottomPlayerArea, 0);
        }
        if (this.leftPlayerArea) {
            this.setupPlayerAreaLayout(this.leftPlayerArea, 1);
        }
        if (this.rightPlayerArea) {
            this.setupPlayerAreaLayout(this.rightPlayerArea, 2);
        }
    }

    private setupPlayerAreaLayout(playerArea: Node, seatIndex: number): void {
        // Setup individual player area with name, avatar, meld area, etc.
        const nameLabel = playerArea.getChildByName('NameLabel')?.getComponent(Label);
        const avatarNode = playerArea.getChildByName('Avatar');
        const meldArea = playerArea.getChildByName('MeldArea');
        const dealerIndicator = playerArea.getChildByName('DealerIndicator');
        const turnIndicator = playerArea.getChildByName('TurnIndicator');

        // Initially hide indicators
        if (dealerIndicator) dealerIndicator.active = false;
        if (turnIndicator) turnIndicator.active = false;
        
        // Setup meld area layout
        if (meldArea) {
            const layout = meldArea.getComponent(Layout);
            if (layout) {
                layout.type = Layout.Type.HORIZONTAL;
                layout.spacingX = 5;
            }
        }
    }

    private onGameSnapshot(message: any): void {
        const gameState: GameState = message.data;
        GameStateManager.instance.updateGameState(gameState);
        this.updateGameUI(gameState);
        this.showWaitingIndicator(false);
    }

    private onGameStarted(message: any): void {
        const gameData = message.data;
        console.log('Game started:', gameData);
        this._localPlayerSeat = gameData.localPlayerSeat || 0;
        this.showWaitingIndicator(false);
    }

    private onPlayerAction(message: any): void {
        const action = message.data;
        console.log('Player action:', action);
        this.handlePlayerAction(action);
    }

    private onGameSettlement(message: any): void {
        const settlement = message.data;
        console.log('Game settlement:', settlement);
        this.showSettlement(settlement);
    }

    private onAvailableActions(message: any): void {
        const actions = message.data.actions;
        this._availableActions = actions;
        this.showActionButtons(actions);
    }

    private onTurnTimer(message: any): void {
        const timerData = message.data;
        this._turnTimer = timerData.remainingTime;
        this.updateTurnTimer();
    }

    private onDiscardClick(): void {
        if (this._selectedTileIndex < 0) {
            this.showMessage('请先选择要打出的牌');
            return;
        }

        const localPlayer = GameStateManager.instance.getLocalPlayer();
        if (!localPlayer || this._selectedTileIndex >= localPlayer.handTiles.length) {
            console.warn('Invalid tile selection');
            return;
        }

        const selectedTile = localPlayer.handTiles[this._selectedTileIndex];
        this.sendPlayerAction('DISCARD', { tile: selectedTile });
    }

    private onActionClick(actionType: string): void {
        if (!this._availableActions.includes(actionType)) {
            console.warn('Action not available:', actionType);
            return;
        }

        let actionData: any = { action: actionType };

        // Add specific data for different actions
        switch (actionType) {
            case 'PENG':
            case 'GANG':
                // These would need the target tile from the last discard
                const gameState = GameStateManager.instance.getCurrentState();
                if (gameState && gameState.discardPile.length > 0) {
                    actionData.targetTile = gameState.discardPile[gameState.discardPile.length - 1];
                }
                break;
            case 'HU':
                // Hu action might need additional validation data
                break;
        }

        this.sendPlayerAction(actionType, actionData);
        this.hideActionButtons();
    }

    private sendPlayerAction(actionType: string, data: any): void {
        NetworkManager.instance.sendMessage({
            type: 'REQ',
            cmd: 'PlayerAction',
            roomId: this._currentRoomId,
            data: {
                ...data,
                action: actionType
            },
            timestamp: Date.now()
        });
    }

    private updateGameUI(gameState: GameState): void {
        // Update game UI based on current state
        this.updatePlayerInfo(gameState);
        this.updatePlayerHands(gameState);
        this.updatePlayerMelds(gameState);
        this.updateDiscardPile(gameState);
        this.updateGameInfo(gameState);
        this.updatePlayerIndicators(gameState);
    }

    private updatePlayerInfo(gameState: GameState): void {
        // Update player information in each area
        gameState.players.forEach((player, index) => {
            const playerArea = this.getPlayerAreaBySeat(index);
            if (playerArea) {
                const nameLabel = playerArea.getChildByName('NameLabel')?.getComponent(Label);
                const avatarNode = playerArea.getChildByName('Avatar');
                const handCountLabel = playerArea.getChildByName('HandCountLabel')?.getComponent(Label);

                if (nameLabel) {
                    nameLabel.string = player.nickname || `玩家${index + 1}`;
                }
                if (handCountLabel) {
                    handCountLabel.string = `${player.handTiles?.length || 0}张`;
                }
                // Load avatar if needed
                if (avatarNode && player.avatar) {
                    this.loadPlayerAvatar(avatarNode, player.avatar);
                }
            }
        });
    }

    private updatePlayerHands(gameState: GameState): void {
        // Update local player's hand tiles display
        const localPlayer = gameState.players[this._localPlayerSeat];
        if (localPlayer && this.playerHandArea && localPlayer.handTiles) {
            this.renderHandTiles(localPlayer.handTiles);
        }
    }

    private updatePlayerMelds(gameState: GameState): void {
        // Update all players' meld displays
        gameState.players.forEach((player, index) => {
            const playerArea = this.getPlayerAreaBySeat(index);
            if (playerArea && player.melds) {
                const meldArea = playerArea.getChildByName('MeldArea');
                if (meldArea) {
                    this.renderPlayerMelds(meldArea, player.melds);
                }
            }
        });
    }

    private updateDiscardPile(gameState: GameState): void {
        // Update discard pile display
        if (this.discardArea && gameState.discardPile) {
            this.renderDiscardPile(gameState.discardPile);
        }
    }

    private updateGameInfo(gameState: GameState): void {
        // Update game information labels
        if (this.remainingTilesLabel) {
            this.remainingTilesLabel.string = `剩余: ${gameState.remainingTiles || 0}张`;
        }
        
        if (this.currentPlayerLabel) {
            const currentPlayer = gameState.players[gameState.currentPlayerIndex];
            this.currentPlayerLabel.string = `当前: ${currentPlayer?.nickname || ''}`;
        }
    }

    private updatePlayerIndicators(gameState: GameState): void {
        // Update dealer and turn indicators
        gameState.players.forEach((player, index) => {
            const playerArea = this.getPlayerAreaBySeat(index);
            if (playerArea) {
                const dealerIndicator = playerArea.getChildByName('DealerIndicator');
                const turnIndicator = playerArea.getChildByName('TurnIndicator');

                if (dealerIndicator) {
                    dealerIndicator.active = player.isDealer || false;
                }
                if (turnIndicator) {
                    turnIndicator.active = index === gameState.currentPlayerIndex;
                }
            }
        });
    }

    private renderHandTiles(handTiles: TileData[]): void {
        if (!this.playerHandArea || !this.tilePrefab) return;

        // Clear existing tiles
        this.playerHandArea.removeAllChildren();

        // Create tile nodes
        handTiles.forEach((tile, index) => {
            const tileNode = instantiate(this.tilePrefab);
            this.setupTileNode(tileNode, tile, index);
            this.playerHandArea.addChild(tileNode);

            // Add click handler for tile selection
            const button = tileNode.getComponent(Button);
            if (button) {
                button.node.on(Button.EventType.CLICK, () => {
                    this.selectTile(index);
                });
            }
        });

        // Update layout
        const layout = this.playerHandArea.getComponent(Layout);
        if (layout) {
            layout.updateLayout();
        }
    }

    private renderPlayerMelds(meldArea: Node, melds: any[]): void {
        if (!meldArea || !this.meldPrefab) return;

        // Clear existing melds
        meldArea.removeAllChildren();

        // Create meld nodes
        melds.forEach(meld => {
            const meldNode = instantiate(this.meldPrefab);
            this.setupMeldNode(meldNode, meld);
            meldArea.addChild(meldNode);
        });

        // Update layout
        const layout = meldArea.getComponent(Layout);
        if (layout) {
            layout.updateLayout();
        }
    }

    private renderDiscardPile(discardPile: TileData[]): void {
        if (!this.discardArea || !this.tilePrefab) return;

        // Clear existing tiles
        this.discardArea.removeAllChildren();

        // Show recent discarded tiles (limit to prevent overflow)
        const recentTiles = discardPile.slice(-20);
        recentTiles.forEach(tile => {
            const tileNode = instantiate(this.tilePrefab);
            this.setupTileNode(tileNode, tile, -1, true); // -1 index for discard tiles
            this.discardArea.addChild(tileNode);
        });

        // Update layout
        const layout = this.discardArea.getComponent(Layout);
        if (layout) {
            layout.updateLayout();
        }
    }

    private setupTileNode(tileNode: Node, tile: TileData, index: number, isDiscard: boolean = false): void {
        // Setup tile visual representation
        const tileLabel = tileNode.getChildByName('TileLabel')?.getComponent(Label);
        const tileSprite = tileNode.getComponent('Sprite'); // Assuming tile has sprite component

        if (tileLabel) {
            tileLabel.string = this.getTileDisplayText(tile);
        }

        // Set tile color based on suit
        if (tileSprite) {
            // This would set appropriate tile texture/color
            // Implementation depends on your tile assets
        }

        // Scale down discard tiles
        if (isDiscard) {
            tileNode.setScale(0.8, 0.8, 1);
        }

        // Store tile data for reference
        tileNode['tileData'] = tile;
        tileNode['tileIndex'] = index;
    }

    private setupMeldNode(meldNode: Node, meld: any): void {
        // Setup meld display (Peng, Gang, Chi)
        const meldTypeLabel = meldNode.getChildByName('TypeLabel')?.getComponent(Label);
        const tilesArea = meldNode.getChildByName('TilesArea');

        if (meldTypeLabel) {
            meldTypeLabel.string = meld.type; // PENG, GANG, CHI
        }

        if (tilesArea && meld.tiles && this.tilePrefab) {
            // Clear existing tiles
            tilesArea.removeAllChildren();

            // Add tiles to meld
            meld.tiles.forEach((tile: TileData) => {
                const tileNode = instantiate(this.tilePrefab);
                this.setupTileNode(tileNode, tile, -1);
                tileNode.setScale(0.7, 0.7, 1); // Smaller for melds
                tilesArea.addChild(tileNode);
            });
        }
    }

    private getTileDisplayText(tile: TileData): string {
        // Convert tile data to display text
        const suitMap: {[key: string]: string} = {
            'WAN': '万',
            'TIAO': '条',
            'TONG': '筒'
        };
        
        return `${tile.rank}${suitMap[tile.suit] || tile.suit}`;
    }

    private selectTile(index: number): void {
        // Handle tile selection
        this._selectedTileIndex = index;
        
        // Update visual selection
        this.updateTileSelection();
        
        // Show discard button if it's player's turn
        const gameState = GameStateManager.instance.getCurrentState();
        if (gameState && gameState.currentPlayerIndex === this._localPlayerSeat) {
            if (this.discardButton) {
                this.discardButton.node.active = true;
            }
        }
    }

    private updateTileSelection(): void {
        // Update visual selection of tiles
        if (!this.playerHandArea) return;

        this.playerHandArea.children.forEach((tileNode, index) => {
            const isSelected = index === this._selectedTileIndex;
            
            // Move selected tile up slightly
            const targetY = isSelected ? 20 : 0;
            tween(tileNode)
                .to(0.2, { position: new Vec3(tileNode.position.x, targetY, 0) })
                .start();
        });
    }

    private getPlayerAreaBySeat(seatIndex: number): Node | null {
        switch (seatIndex) {
            case 0: return this.bottomPlayerArea;
            case 1: return this.leftPlayerArea;
            case 2: return this.rightPlayerArea;
            default: return null;
        }
    }

    private loadPlayerAvatar(avatarNode: Node, avatarUrl: string): void {
        // Load and display player avatar
        console.log('Loading avatar for player:', avatarUrl);
        // Implementation would depend on your asset loading system
    }

    private clearAllTileAreas(): void {
        // Clear all tile display areas
        if (this.playerHandArea) {
            this.playerHandArea.removeAllChildren();
        }
        if (this.discardArea) {
            this.discardArea.removeAllChildren();
        }
        if (this.playerMeldArea) {
            this.playerMeldArea.removeAllChildren();
        }
    }

    private showActionButtons(actions: string[]): void {
        this.hideActionButtons();

        // Show available action buttons
        actions.forEach(action => {
            switch (action) {
                case 'PENG':
                    if (this.pengButton) {
                        this.pengButton.node.active = true;
                        this.animateButtonAppear(this.pengButton.node);
                    }
                    break;
                case 'GANG':
                    if (this.gangButton) {
                        this.gangButton.node.active = true;
                        this.animateButtonAppear(this.gangButton.node);
                    }
                    break;
                case 'HU':
                    if (this.huButton) {
                        this.huButton.node.active = true;
                        this.animateButtonAppear(this.huButton.node);
                    }
                    break;
            }
        });

        // Always show pass button when actions are available
        if (actions.length > 0 && this.passButton) {
            this.passButton.node.active = true;
            this.animateButtonAppear(this.passButton.node);
        }

        // Start action timeout timer
        this.startActionTimer();
    }

    private hideActionButtons(): void {
        if (this.discardButton) this.discardButton.node.active = false;
        if (this.pengButton) this.pengButton.node.active = false;
        if (this.gangButton) this.gangButton.node.active = false;
        if (this.huButton) this.huButton.node.active = false;
        if (this.passButton) this.passButton.node.active = false;
        
        this._selectedTileIndex = -1;
        this.updateTileSelection();
    }

    private animateButtonAppear(buttonNode: Node): void {
        // Animate button appearance
        buttonNode.setScale(0, 0, 1);
        tween(buttonNode)
            .to(0.3, { scale: new Vec3(1, 1, 1) })
            .start();
    }

    private startActionTimer(): void {
        // Start countdown for action timeout (usually 2 seconds for claims)
        let timeLeft = 2;
        
        const timer = setInterval(() => {
            timeLeft--;
            if (timeLeft <= 0) {
                clearInterval(timer);
                this.hideActionButtons();
            }
        }, 1000);
    }

    private updateTurnTimer(): void {
        if (this.turnTimerLabel) {
            this.turnTimerLabel.string = `${this._turnTimer}s`;
            
            // Change color when time is running out
            if (this._turnTimer <= 5) {
                this.turnTimerLabel.node.color = new Color(255, 0, 0); // Red
            } else {
                this.turnTimerLabel.node.color = new Color(255, 255, 255); // White
            }
        }
    }

    private showWaitingIndicator(show: boolean): void {
        if (this.waitingIndicator) {
            this.waitingIndicator.active = show;
        }
    }

    private handlePlayerAction(action: any): void {
        // Handle other players' actions
        console.log('Handling player action:', action);
        // Update UI based on action (animations, tile movements, etc.)
    }

    private handlePlayerAction(action: any): void {
        // Handle other players' actions with animations
        console.log('Handling player action:', action);
        
        switch (action.type) {
            case 'DISCARD':
                this.animateTileDiscard(action);
                break;
            case 'PENG':
            case 'GANG':
                this.animateMeldAction(action);
                break;
            case 'HU':
                this.animateWinAction(action);
                break;
        }
    }

    private animateTileDiscard(action: any): void {
        // Animate tile being discarded
        console.log('Animating discard:', action.tile);
        // Implementation would create tile animation from player to discard pile
    }

    private animateMeldAction(action: any): void {
        // Animate meld formation (Peng/Gang)
        console.log('Animating meld:', action.type, action.tiles);
        // Implementation would animate tiles moving to meld area
    }

    private animateWinAction(action: any): void {
        // Animate winning hand reveal
        console.log('Animating win:', action);
        // Implementation would highlight winning tiles
    }

    private showSettlement(settlement: any): void {
        // Show settlement results and navigate to settlement scene
        console.log('Showing settlement:', settlement);
        
        // Store settlement data for settlement scene
        if (typeof wx !== 'undefined') {
            wx.setStorageSync('settlement_data', settlement);
        } else {
            localStorage.setItem('settlement_data', JSON.stringify(settlement));
        }

        // Show settlement animation/popup first
        this.showSettlementPopup(settlement);

        // Navigate to settlement scene after delay
        setTimeout(() => {
            SceneManager.instance.loadScene('SettlementScene');
        }, 3000);
    }

    private showSettlementPopup(settlement: any): void {
        // Show quick settlement popup before scene transition
        console.log('Settlement popup:', settlement);
        // Implementation would show popup with basic results
    }

    private showMessage(message: string): void {
        console.log('Game message:', message);
        // Implementation would show toast or temporary message
    }
}