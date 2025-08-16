import { LocalCacheManager } from './LocalCacheManager';

// Game State Manager interfaces and types
export interface GameState {
    roomId: string;
    gameId: string;
    players: PlayerState[];
    currentPlayerIndex: number;
    turnStartTime: number;
    turnDeadline: number;
    phase: GamePhase;
    discardPile: string[];
    remainingTiles: number;
    lastAction?: GameAction;
    availableActions: ActionType[];
    dealerIndex: number;
    roundIndex: number;
    version: number; // For conflict resolution
}

export interface PlayerState {
    userId: string;
    nickname: string;
    avatar: string;
    handTiles: string[];
    melds: MeldSet[];
    seatIndex: number;
    isDealer: boolean;
    score: number;
    status: PlayerStatus;
    isConnected: boolean;
    lastActionTime: number;
}

export interface MeldSet {
    type: 'PENG' | 'GANG' | 'CHI';
    tiles: string[];
    isConcealed: boolean;
}

export interface GameAction {
    type: ActionType;
    playerId: string;
    tile?: string;
    tiles?: string[];
    timestamp: number;
    sequence: number;
}

export interface GameSnapshot {
    state: GameState;
    timestamp: number;
    checksum: string;
}

export interface UserPreferences {
    soundEnabled: boolean;
    musicEnabled: boolean;
    autoPlay: boolean;
    showHints: boolean;
    animationSpeed: number;
    tileTheme: string;
    backgroundTheme: string;
    language: string;
    notifications: boolean;
    vibration: boolean;
    autoReconnect: boolean;
    dataUsageOptimization: boolean;
}

export interface ValidationResult {
    isValid: boolean;
    errors: string[];
    warnings: string[];
}

export interface ConflictResolution {
    localState: GameState;
    serverState: GameState;
    resolution: 'accept_server' | 'merge' | 'reject';
    mergedState?: GameState;
}

export enum GamePhase {
    WAITING = 'WAITING',
    DEALING = 'DEALING',
    PLAYING = 'PLAYING',
    SETTLEMENT = 'SETTLEMENT',
    FINISHED = 'FINISHED'
}

export enum PlayerStatus {
    ACTIVE = 'ACTIVE',
    DISCONNECTED = 'DISCONNECTED',
    TRUSTEE = 'TRUSTEE',
    SPECTATING = 'SPECTATING'
}

export enum ActionType {
    DISCARD = 'DISCARD',
    PENG = 'PENG',
    GANG = 'GANG',
    CHI = 'CHI',
    HU = 'HU',
    PASS = 'PASS',
    DRAW = 'DRAW'
}

/**
 * Game State Manager for client-side game state tracking
 * Handles local state management, validation, prediction, and synchronization
 */
export class GameStateManager {
    private static _instance: GameStateManager;
    private _currentState: GameState | null = null;
    private _localPlayerIndex: number = -1;
    private _localUserId: string = '';
    private _stateHistory: GameState[] = [];
    private _maxHistorySize: number = 50;
    private _predictedActions: GameAction[] = [];
    private _userPreferences: UserPreferences;
    private _lastSyncTime: number = 0;
    private _syncInterval: number = 30000; // 30 seconds
    private _validationRules: Map<ActionType, (action: GameAction, state: GameState) => ValidationResult> = new Map();
    private _stateChangeListeners: ((state: GameState) => void)[] = [];
    private _conflictListeners: ((conflict: ConflictResolution) => void)[] = [];
    private _cacheManager: LocalCacheManager;

    public static get instance(): GameStateManager {
        if (!this._instance) {
            this._instance = new GameStateManager();
        }
        return this._instance;
    }

    private constructor() {
        this._cacheManager = LocalCacheManager.instance;
        this._userPreferences = this.loadUserPreferences();
        this.initializeValidationRules();
    }

    /**
     * Initialize the game state manager with user ID
     */
    public initialize(userId: string): void {
        this._localUserId = userId;
        this.loadCachedState();
    }

    /**
     * Update game state from server snapshot with conflict resolution
     */
    public updateGameState(snapshot: GameState): void {
        const previousState = this._currentState;
        
        if (this._currentState && this._currentState.version > snapshot.version) {
            // Local state is newer, handle conflict
            this.handleStateConflict(this._currentState, snapshot);
            return;
        }

        // Apply predicted actions to server state
        const stateWithPredictions = this.applyPredictedActions(snapshot);
        
        // Update state
        this._currentState = stateWithPredictions;
        this.updateLocalPlayerIndex();
        this.addToHistory(stateWithPredictions);
        this.validateLocalState();
        this.cacheState();
        this._lastSyncTime = Date.now();

        // Notify listeners
        this.notifyStateChange(stateWithPredictions);

        console.log('Game state updated:', {
            roomId: stateWithPredictions.roomId,
            phase: stateWithPredictions.phase,
            currentPlayer: stateWithPredictions.currentPlayerIndex,
            version: stateWithPredictions.version
        });
    }

    /**
     * Predict and apply local action optimistically
     */
    public predictAction(action: GameAction): ValidationResult {
        if (!this._currentState) {
            return { isValid: false, errors: ['No game state available'], warnings: [] };
        }

        // Validate action
        const validation = this.validateAction(action, this._currentState);
        if (!validation.isValid) {
            return validation;
        }

        // Create predicted state
        const predictedState = this.applyActionToState(action, { ...this._currentState });
        if (predictedState) {
            // Store predicted action
            this._predictedActions.push(action);
            
            // Update local state optimistically
            this._currentState = predictedState;
            this.notifyStateChange(predictedState);
            
            console.log('Action predicted:', action.type, action.tile);
        }

        return validation;
    }

    /**
     * Confirm predicted action was accepted by server
     */
    public confirmAction(action: GameAction): void {
        this._predictedActions = this._predictedActions.filter(
            predicted => predicted.sequence !== action.sequence
        );
        console.log('Action confirmed:', action.type, action.tile);
    }

    /**
     * Reject predicted action and rollback
     */
    public rejectAction(action: GameAction, reason: string): void {
        this._predictedActions = this._predictedActions.filter(
            predicted => predicted.sequence !== action.sequence
        );
        
        // Rollback to last confirmed state
        if (this._stateHistory.length > 0) {
            this._currentState = { ...this._stateHistory[this._stateHistory.length - 1] };
            this.notifyStateChange(this._currentState);
        }
        
        console.warn('Action rejected:', action.type, reason);
    }

    /**
     * Validate action against current state
     */
    public validateAction(action: GameAction, state: GameState): ValidationResult {
        const validator = this._validationRules.get(action.type);
        if (!validator) {
            return { isValid: false, errors: [`No validator for action type: ${action.type}`], warnings: [] };
        }

        return validator(action, state);
    }

    /**
     * Get current game state
     */
    public getCurrentState(): GameState | null {
        return this._currentState ? { ...this._currentState } : null;
    }

    /**
     * Get local player state
     */
    public getLocalPlayer(): PlayerState | null {
        if (!this._currentState || this._localPlayerIndex === -1) {
            return null;
        }
        const player = this._currentState.players[this._localPlayerIndex];
        return { 
            ...player, 
            handTiles: [...player.handTiles],
            melds: player.melds.map(m => ({ ...m, tiles: [...m.tiles] }))
        };
    }

    /**
     * Get player by user ID
     */
    public getPlayer(userId: string): PlayerState | null {
        if (!this._currentState) {
            return null;
        }
        
        const player = this._currentState.players.find(p => p.userId === userId);
        return player ? { 
            ...player, 
            handTiles: [...player.handTiles],
            melds: player.melds.map(m => ({ ...m, tiles: [...m.tiles] }))
        } : null;
    }

    /**
     * Check if it's local player's turn
     */
    public isLocalPlayerTurn(): boolean {
        if (!this._currentState || this._localPlayerIndex === -1) {
            return false;
        }
        return this._currentState.currentPlayerIndex === this._localPlayerIndex;
    }

    /**
     * Get available actions for local player
     */
    public getAvailableActions(): ActionType[] {
        if (!this._currentState || !this.isLocalPlayerTurn()) {
            return [];
        }
        return [...this._currentState.availableActions];
    }

    /**
     * Get time remaining for current turn
     */
    public getTurnTimeRemaining(): number {
        if (!this._currentState) {
            return 0;
        }
        return Math.max(0, this._currentState.turnDeadline - Date.now());
    }

    /**
     * Set local player index
     */
    public setLocalPlayerIndex(index: number): void {
        this._localPlayerIndex = index;
    }

    /**
     * Clear game state
     */
    public clearState(): void {
        this._currentState = null;
        this._localPlayerIndex = -1;
        this._stateHistory = [];
        this._predictedActions = [];
        this.clearCachedState();
    }

    /**
     * Get user preferences
     */
    public getUserPreferences(): UserPreferences {
        return { ...this._userPreferences };
    }

    /**
     * Update user preferences
     */
    public updateUserPreferences(preferences: Partial<UserPreferences>): void {
        this._userPreferences = { ...this._userPreferences, ...preferences };
        this.saveUserPreferences();
    }

    /**
     * Add state change listener
     */
    public addStateChangeListener(listener: (state: GameState) => void): void {
        this._stateChangeListeners.push(listener);
    }

    /**
     * Remove state change listener
     */
    public removeStateChangeListener(listener: (state: GameState) => void): void {
        const index = this._stateChangeListeners.indexOf(listener);
        if (index > -1) {
            this._stateChangeListeners.splice(index, 1);
        }
    }

    /**
     * Add conflict resolution listener
     */
    public addConflictListener(listener: (conflict: ConflictResolution) => void): void {
        this._conflictListeners.push(listener);
    }

    /**
     * Remove conflict resolution listener
     */
    public removeConflictListener(listener: (conflict: ConflictResolution) => void): void {
        const index = this._conflictListeners.indexOf(listener);
        if (index > -1) {
            this._conflictListeners.splice(index, 1);
        }
    }

    /**
     * Get state history
     */
    public getStateHistory(): GameState[] {
        return [...this._stateHistory];
    }

    /**
     * Force synchronization with server
     */
    public requestSync(): void {
        // This would typically trigger a request to the server for the latest state
        console.log('Sync requested');
        // Emit event for external sync handling
        this.notifyStateChange(this._currentState!);
    }

    /**
     * Get state validation errors
     */
    public getValidationErrors(): string[] {
        if (!this._currentState) {
            return ['No game state available'];
        }

        const errors: string[] = [];
        
        // Check player count
        if (this._currentState.players.length !== 3) {
            errors.push('Invalid player count');
        }

        // Check current player index
        if (this._currentState.currentPlayerIndex < 0 || 
            this._currentState.currentPlayerIndex >= this._currentState.players.length) {
            errors.push('Invalid current player index');
        }

        // Check tile counts
        const totalHandTiles = this._currentState.players.reduce((sum, p) => sum + p.handTiles.length, 0);
        const totalMeldTiles = this._currentState.players.reduce((sum, p) => 
            sum + p.melds.reduce((meldSum, m) => meldSum + m.tiles.length, 0), 0);
        const totalTiles = totalHandTiles + totalMeldTiles + this._currentState.discardPile.length + this._currentState.remainingTiles;
        
        if (totalTiles !== 108) { // Standard 3-player mahjong tile count
            errors.push(`Invalid total tile count: ${totalTiles}`);
        }

        return errors;
    }

    /**
     * Get performance metrics
     */
    public getPerformanceMetrics(): {
        stateUpdates: number;
        predictedActions: number;
        validationErrors: number;
        syncTime: number;
        memoryUsage: number;
    } {
        return {
            stateUpdates: this._stateHistory.length,
            predictedActions: this._predictedActions.length,
            validationErrors: this.getValidationErrors().length,
            syncTime: Date.now() - this._lastSyncTime,
            memoryUsage: this.calculateMemoryUsage()
        };
    }

    /**
     * Export state for debugging
     */
    public exportState(): {
        currentState: GameState | null;
        stateHistory: GameState[];
        predictedActions: GameAction[];
        userPreferences: UserPreferences;
        localPlayerIndex: number;
        lastSyncTime: number;
    } {
        return {
            currentState: this._currentState ? { ...this._currentState } : null,
            stateHistory: [...this._stateHistory],
            predictedActions: [...this._predictedActions],
            userPreferences: { ...this._userPreferences },
            localPlayerIndex: this._localPlayerIndex,
            lastSyncTime: this._lastSyncTime
        };
    }

    /**
     * Import state for debugging/testing
     */
    public importState(exportedState: {
        currentState: GameState | null;
        stateHistory: GameState[];
        predictedActions: GameAction[];
        userPreferences: UserPreferences;
        localPlayerIndex: number;
        lastSyncTime: number;
    }): void {
        this._currentState = exportedState.currentState;
        this._stateHistory = [...exportedState.stateHistory];
        this._predictedActions = [...exportedState.predictedActions];
        this._userPreferences = { ...exportedState.userPreferences };
        this._localPlayerIndex = exportedState.localPlayerIndex;
        this._lastSyncTime = exportedState.lastSyncTime;
        
        // Update cache
        this.saveUserPreferences();
        this.cacheState();
        
        if (this._currentState) {
            this.notifyStateChange(this._currentState);
        }
    }

    /**
     * Get cache statistics
     */
    public getCacheStats(): {
        cacheStats: any;
        userPreferencesCached: boolean;
        gameStateCached: boolean;
        cacheSize: number;
    } {
        return {
            cacheStats: this._cacheManager.getStats(),
            userPreferencesCached: this._cacheManager.has('user_preferences'),
            gameStateCached: this._cacheManager.has('cached_game_state'),
            cacheSize: this._cacheManager.size()
        };
    }

    /**
     * Clear all cached data
     */
    public clearAllCache(): void {
        this._cacheManager.clear();
        this.clearCachedState();
    }

    /**
     * Preload commonly used data
     */
    public preloadData(): void {
        // Preload user preferences if not already cached
        if (!this._cacheManager.has('user_preferences')) {
            this.loadUserPreferences();
        }

        // Cache common game data
        const commonGameData = {
            tileTypes: ['WAN', 'TIAO', 'TONG'],
            actionTypes: Object.values(ActionType),
            gamePhases: Object.values(GamePhase),
            playerStatuses: Object.values(PlayerStatus)
        };
        
        this._cacheManager.set('common_game_data', commonGameData, 24 * 60 * 60 * 1000); // 24 hours
    }

    /**
     * Get cached common game data
     */
    public getCommonGameData(): any {
        return this._cacheManager.get('common_game_data');
    }

    private initializeValidationRules(): void {
        // Discard validation
        this._validationRules.set(ActionType.DISCARD, (action, state) => {
            const player = state.players.find(p => p.userId === action.playerId);
            if (!player) {
                return { isValid: false, errors: ['Player not found'], warnings: [] };
            }

            if (!action.tile) {
                return { isValid: false, errors: ['No tile specified for discard'], warnings: [] };
            }

            if (!player.handTiles.includes(action.tile)) {
                return { isValid: false, errors: ['Tile not in hand'], warnings: [] };
            }

            return { isValid: true, errors: [], warnings: [] };
        });

        // Peng validation
        this._validationRules.set(ActionType.PENG, (action, state) => {
            const player = state.players.find(p => p.userId === action.playerId);
            if (!player) {
                return { isValid: false, errors: ['Player not found'], warnings: [] };
            }

            if (!action.tile) {
                return { isValid: false, errors: ['No tile specified for peng'], warnings: [] };
            }

            const tileCount = player.handTiles.filter(t => t === action.tile).length;
            if (tileCount < 2) {
                return { isValid: false, errors: ['Not enough tiles for peng'], warnings: [] };
            }

            return { isValid: true, errors: [], warnings: [] };
        });

        // Add more validation rules for other actions...
    }

    private applyPredictedActions(serverState: GameState): GameState {
        let state = { ...serverState };
        
        // Apply any remaining predicted actions that haven't been confirmed
        for (const action of this._predictedActions) {
            const newState = this.applyActionToState(action, state);
            if (newState) {
                state = newState;
            }
        }
        
        return state;
    }

    private applyActionToState(action: GameAction, state: GameState): GameState | null {
        const newState = { ...state };
        const player = newState.players.find(p => p.userId === action.playerId);
        
        if (!player) {
            return null;
        }

        switch (action.type) {
            case ActionType.DISCARD:
                if (action.tile && player.handTiles.includes(action.tile)) {
                    player.handTiles = player.handTiles.filter((t, i) => i !== player.handTiles.indexOf(action.tile!));
                    newState.discardPile.push(action.tile);
                    newState.lastAction = action;
                }
                break;
                
            case ActionType.PENG:
                if (action.tile) {
                    // Remove two tiles from hand
                    const tileIndices = player.handTiles
                        .map((t, i) => t === action.tile ? i : -1)
                        .filter(i => i !== -1)
                        .slice(0, 2);
                    
                    if (tileIndices.length === 2) {
                        player.handTiles = player.handTiles.filter((_, i) => !tileIndices.includes(i));
                        player.melds.push({
                            type: 'PENG',
                            tiles: [action.tile, action.tile, action.tile],
                            isConcealed: false
                        });
                        newState.lastAction = action;
                    }
                }
                break;
                
            // Add more action applications...
        }

        newState.version++;
        return newState;
    }

    private handleStateConflict(localState: GameState, serverState: GameState): void {
        const conflict: ConflictResolution = {
            localState,
            serverState,
            resolution: 'accept_server' // Default to server authority
        };

        // Notify conflict listeners
        this._conflictListeners.forEach(listener => {
            try {
                listener(conflict);
            } catch (error) {
                console.error('Error in conflict listener:', error);
            }
        });

        // Apply resolution
        switch (conflict.resolution) {
            case 'accept_server':
                this._currentState = serverState;
                this._predictedActions = []; // Clear predictions
                break;
            case 'merge':
                if (conflict.mergedState) {
                    this._currentState = conflict.mergedState;
                }
                break;
            case 'reject':
                // Keep local state
                break;
        }

        console.warn('State conflict resolved:', conflict.resolution);
    }

    private updateLocalPlayerIndex(): void {
        if (!this._currentState || !this._localUserId) {
            return;
        }

        const index = this._currentState.players.findIndex(p => p.userId === this._localUserId);
        if (index !== -1) {
            this._localPlayerIndex = index;
        }
    }

    private addToHistory(state: GameState): void {
        this._stateHistory.push({ ...state });
        
        // Limit history size
        if (this._stateHistory.length > this._maxHistorySize) {
            this._stateHistory.shift();
        }
    }

    private validateLocalState(): void {
        if (!this._currentState) {
            return;
        }

        // Validate state consistency
        const errors: string[] = [];
        
        // Check player count
        if (this._currentState.players.length !== 3) {
            errors.push('Invalid player count');
        }

        // Check current player index
        if (this._currentState.currentPlayerIndex < 0 || 
            this._currentState.currentPlayerIndex >= this._currentState.players.length) {
            errors.push('Invalid current player index');
        }

        // Check tile counts
        const totalHandTiles = this._currentState.players.reduce((sum, p) => sum + p.handTiles.length, 0);
        const totalMeldTiles = this._currentState.players.reduce((sum, p) => 
            sum + p.melds.reduce((meldSum, m) => meldSum + m.tiles.length, 0), 0);
        const totalTiles = totalHandTiles + totalMeldTiles + this._currentState.discardPile.length + this._currentState.remainingTiles;
        
        if (totalTiles !== 108) { // Standard 3-player mahjong tile count
            errors.push(`Invalid total tile count: ${totalTiles}`);
        }

        if (errors.length > 0) {
            console.warn('State validation errors:', errors);
        }
    }

    private notifyStateChange(state: GameState): void {
        this._stateChangeListeners.forEach(listener => {
            try {
                listener(state);
            } catch (error) {
                console.error('Error in state change listener:', error);
            }
        });
    }

    private loadUserPreferences(): UserPreferences {
        try {
            // Try to load from cache manager first
            const cached = this._cacheManager.get<UserPreferences>('user_preferences');
            if (cached) {
                return cached;
            }

            // Fallback to direct storage
            const stored = this.getStoredData('user_preferences');
            if (stored) {
                const preferences = JSON.parse(stored);
                // Cache for future use
                this._cacheManager.set('user_preferences', preferences, 7 * 24 * 60 * 60 * 1000); // 7 days
                return preferences;
            }
        } catch (error) {
            console.warn('Failed to load user preferences:', error);
        }

        // Default preferences
        const defaultPreferences: UserPreferences = {
            soundEnabled: true,
            musicEnabled: true,
            autoPlay: false,
            showHints: true,
            animationSpeed: 1.0,
            tileTheme: 'classic',
            backgroundTheme: 'green',
            language: 'zh-CN',
            notifications: true,
            vibration: true,
            autoReconnect: true,
            dataUsageOptimization: false
        };

        // Cache default preferences
        this._cacheManager.set('user_preferences', defaultPreferences, 7 * 24 * 60 * 60 * 1000);
        return defaultPreferences;
    }

    private saveUserPreferences(): void {
        try {
            // Save to cache manager
            this._cacheManager.set('user_preferences', this._userPreferences, 7 * 24 * 60 * 60 * 1000); // 7 days
            
            // Also save to direct storage as backup
            this.setStoredData('user_preferences', JSON.stringify(this._userPreferences));
        } catch (error) {
            console.error('Failed to save user preferences:', error);
        }
    }

    private loadCachedState(): void {
        try {
            // Try cache manager first
            const cached = this._cacheManager.get<{ state: GameState; timestamp: number }>('cached_game_state');
            if (cached && Date.now() - cached.timestamp < 300000) { // 5 minutes
                this._currentState = cached.state;
                this.updateLocalPlayerIndex();
                console.log('Loaded cached game state from cache manager');
                return;
            }

            // Fallback to direct storage
            const stored = this.getStoredData('cached_game_state');
            if (stored) {
                const cachedData = JSON.parse(stored);
                // Only load if not too old (5 minutes)
                if (Date.now() - cachedData.timestamp < 300000) {
                    this._currentState = cachedData.state;
                    this.updateLocalPlayerIndex();
                    
                    // Cache in cache manager for future use
                    this._cacheManager.set('cached_game_state', cachedData, 300000); // 5 minutes
                    console.log('Loaded cached game state from storage');
                }
            }
        } catch (error) {
            console.warn('Failed to load cached state:', error);
        }
    }

    private cacheState(): void {
        if (!this._currentState) {
            return;
        }

        try {
            const cached = {
                state: this._currentState,
                timestamp: Date.now()
            };
            
            // Cache in cache manager
            this._cacheManager.set('cached_game_state', cached, 300000); // 5 minutes
            
            // Also save to direct storage as backup
            this.setStoredData('cached_game_state', JSON.stringify(cached));
        } catch (error) {
            console.error('Failed to cache state:', error);
        }
    }

    private clearCachedState(): void {
        try {
            // Clear from cache manager
            this._cacheManager.delete('cached_game_state');
            
            // Clear from direct storage
            this.removeStoredData('cached_game_state');
        } catch (error) {
            console.error('Failed to clear cached state:', error);
        }
    }

    private getStoredData(key: string): string | null {
        if (typeof wx !== 'undefined') {
            // WeChat Mini Program
            try {
                return wx.getStorageSync(key) || null;
            } catch (error) {
                return null;
            }
        } else if (typeof localStorage !== 'undefined') {
            // Web/simulator
            return localStorage.getItem(key);
        }
        return null;
    }

    private setStoredData(key: string, value: string): void {
        if (typeof wx !== 'undefined') {
            // WeChat Mini Program
            wx.setStorageSync(key, value);
        } else if (typeof localStorage !== 'undefined') {
            // Web/simulator
            localStorage.setItem(key, value);
        }
    }

    private removeStoredData(key: string): void {
        if (typeof wx !== 'undefined') {
            // WeChat Mini Program
            wx.removeStorageSync(key);
        } else if (typeof localStorage !== 'undefined') {
            // Web/simulator
            localStorage.removeItem(key);
        }
    }

    private calculateMemoryUsage(): number {
        // Rough estimation of memory usage in bytes
        const stateSize = this._currentState ? JSON.stringify(this._currentState).length * 2 : 0;
        const historySize = this._stateHistory.reduce((sum, state) => sum + JSON.stringify(state).length * 2, 0);
        const actionsSize = this._predictedActions.reduce((sum, action) => sum + JSON.stringify(action).length * 2, 0);
        const preferencesSize = JSON.stringify(this._userPreferences).length * 2;
        
        return stateSize + historySize + actionsSize + preferencesSize;
    }
}