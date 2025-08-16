import { GameState, GameAction, GameSnapshot, ConflictResolution } from './GameStateManager';
import { NetworkManager, GameMessage } from '../network/NetworkManager';
import { LocalCacheManager } from './LocalCacheManager';

export interface SyncConfig {
    syncInterval: number;
    maxRetries: number;
    retryDelay: number;
    checksumValidation: boolean;
    conflictResolutionStrategy: 'server_wins' | 'client_wins' | 'merge' | 'manual';
}

export interface SyncStatus {
    lastSyncTime: number;
    syncInProgress: boolean;
    pendingSyncs: number;
    syncErrors: number;
    lastError?: string;
}

export interface StateDiff {
    version: number;
    changes: StateChange[];
    checksum: string;
}

export interface StateChange {
    path: string;
    oldValue: any;
    newValue: any;
    timestamp: number;
}

/**
 * State Synchronizer for handling complex client-server state synchronization
 * Manages conflict resolution, delta synchronization, and consistency checks
 */
export class StateSynchronizer {
    private _config: SyncConfig;
    private _status: SyncStatus;
    private _networkManager: NetworkManager;
    private _cacheManager: LocalCacheManager;
    private _syncTimer: number = 0;
    private _pendingActions: Map<string, GameAction> = new Map();
    private _lastKnownServerState: GameState | null = null;
    private _conflictResolvers: Map<string, (conflict: ConflictResolution) => ConflictResolution> = new Map();

    constructor(networkManager: NetworkManager, config?: Partial<SyncConfig>) {
        this._networkManager = networkManager;
        this._cacheManager = LocalCacheManager.instance;
        this._config = {
            syncInterval: 30000, // 30 seconds
            maxRetries: 3,
            retryDelay: 1000,
            checksumValidation: true,
            conflictResolutionStrategy: 'server_wins',
            ...config
        };

        this._status = {
            lastSyncTime: 0,
            syncInProgress: false,
            pendingSyncs: 0,
            syncErrors: 0
        };

        this.initializeNetworkHandlers();
        this.loadCachedSyncData();
    }

    /**
     * Start automatic synchronization
     */
    public startSync(): void {
        this.stopSync(); // Clear any existing timer
        
        this._syncTimer = setInterval(() => {
            this.performSync();
        }, this._config.syncInterval) as any;

        console.log('State synchronization started');
    }

    /**
     * Stop automatic synchronization
     */
    public stopSync(): void {
        if (this._syncTimer) {
            clearInterval(this._syncTimer);
            this._syncTimer = 0;
        }
        console.log('State synchronization stopped');
    }

    /**
     * Perform immediate synchronization
     */
    public async performSync(): Promise<GameSnapshot | null> {
        if (this._status.syncInProgress) {
            console.log('Sync already in progress, skipping');
            return null;
        }

        this._status.syncInProgress = true;
        this._status.pendingSyncs++;

        try {
            const snapshot = await this.requestServerSnapshot();
            if (snapshot) {
                this._status.lastSyncTime = Date.now();
                this._lastKnownServerState = snapshot.state;
                console.log('Sync completed successfully');
            }
            return snapshot;
        } catch (error) {
            this._status.syncErrors++;
            this._status.lastError = error instanceof Error ? error.message : 'Unknown sync error';
            console.error('Sync failed:', error);
            return null;
        } finally {
            this._status.syncInProgress = false;
            this._status.pendingSyncs--;
        }
    }

    /**
     * Register action for synchronization tracking
     */
    public registerAction(action: GameAction): void {
        const actionId = `${action.type}_${action.sequence}`;
        this._pendingActions.set(actionId, action);
        
        // Auto-remove after timeout
        setTimeout(() => {
            this._pendingActions.delete(actionId);
        }, 30000); // 30 seconds timeout
    }

    /**
     * Confirm action was processed by server
     */
    public confirmAction(action: GameAction): void {
        const actionId = `${action.type}_${action.sequence}`;
        this._pendingActions.delete(actionId);
        console.log('Action confirmed:', actionId);
    }

    /**
     * Handle state conflict between client and server
     */
    public resolveConflict(localState: GameState, serverState: GameState): ConflictResolution {
        const conflict: ConflictResolution = {
            localState,
            serverState,
            resolution: this._config.conflictResolutionStrategy
        };

        // Check for custom conflict resolver
        const resolver = this._conflictResolvers.get(localState.roomId);
        if (resolver) {
            return resolver(conflict);
        }

        // Apply default resolution strategy
        switch (this._config.conflictResolutionStrategy) {
            case 'server_wins':
                conflict.resolution = 'accept_server';
                break;
                
            case 'client_wins':
                conflict.resolution = 'reject';
                break;
                
            case 'merge':
                conflict.mergedState = this.mergeStates(localState, serverState);
                conflict.resolution = 'merge';
                break;
                
            case 'manual':
                // Let the application handle it
                break;
        }

        return conflict;
    }

    /**
     * Register custom conflict resolver for a room
     */
    public registerConflictResolver(
        roomId: string, 
        resolver: (conflict: ConflictResolution) => ConflictResolution
    ): void {
        this._conflictResolvers.set(roomId, resolver);
    }

    /**
     * Calculate state difference
     */
    public calculateDiff(oldState: GameState, newState: GameState): StateDiff {
        const changes: StateChange[] = [];
        
        // Compare basic properties
        this.compareProperty('currentPlayerIndex', oldState, newState, changes);
        this.compareProperty('phase', oldState, newState, changes);
        this.compareProperty('turnStartTime', oldState, newState, changes);
        this.compareProperty('turnDeadline', oldState, newState, changes);
        this.compareProperty('remainingTiles', oldState, newState, changes);
        
        // Compare arrays
        this.compareArray('discardPile', oldState.discardPile, newState.discardPile, changes);
        this.compareArray('availableActions', oldState.availableActions, newState.availableActions, changes);
        
        // Compare players
        this.comparePlayers(oldState.players, newState.players, changes);

        return {
            version: newState.version,
            changes,
            checksum: this.calculateChecksum(newState)
        };
    }

    /**
     * Apply state difference
     */
    public applyDiff(baseState: GameState, diff: StateDiff): GameState {
        const newState = { ...baseState };
        
        for (const change of diff.changes) {
            this.applyChange(newState, change);
        }
        
        newState.version = diff.version;
        return newState;
    }

    /**
     * Validate state checksum
     */
    public validateChecksum(state: GameState, expectedChecksum: string): boolean {
        if (!this._config.checksumValidation) {
            return true;
        }
        
        const actualChecksum = this.calculateChecksum(state);
        return actualChecksum === expectedChecksum;
    }

    /**
     * Get synchronization status
     */
    public getSyncStatus(): SyncStatus {
        return { ...this._status };
    }

    /**
     * Get pending actions count
     */
    public getPendingActionsCount(): number {
        return this._pendingActions.size;
    }

    /**
     * Force resync with server
     */
    public async forceResync(): Promise<void> {
        this._pendingActions.clear();
        await this.performSync();
    }

    private initializeNetworkHandlers(): void {
        // Handle state sync responses
        this._networkManager.onMessage('STATE_SYNC', (message: GameMessage) => {
            if (message.data && message.data.snapshot) {
                this.handleServerSnapshot(message.data.snapshot);
            }
        });

        // Handle action confirmations
        this._networkManager.onMessage('ACTION_CONFIRMED', (message: GameMessage) => {
            if (message.data && message.data.action) {
                this.confirmAction(message.data.action);
            }
        });

        // Handle action rejections
        this._networkManager.onMessage('ACTION_REJECTED', (message: GameMessage) => {
            if (message.data && message.data.action) {
                const actionId = `${message.data.action.type}_${message.data.action.sequence}`;
                this._pendingActions.delete(actionId);
                console.warn('Action rejected by server:', actionId, message.data.reason);
            }
        });
    }

    private async requestServerSnapshot(): Promise<GameSnapshot | null> {
        try {
            const response = await this._networkManager.sendRequest({
                type: 'REQ',
                cmd: 'GET_STATE_SNAPSHOT',
                timestamp: Date.now()
            });

            if (response && response.snapshot) {
                return response.snapshot;
            }
            return null;
        } catch (error) {
            console.error('Failed to request server snapshot:', error);
            throw error;
        }
    }

    private handleServerSnapshot(snapshot: GameSnapshot): void {
        // Validate checksum if enabled
        if (this._config.checksumValidation && !this.validateChecksum(snapshot.state, snapshot.checksum)) {
            console.error('Server snapshot checksum validation failed');
            return;
        }

        this._lastKnownServerState = snapshot.state;
        console.log('Received server snapshot:', snapshot.state.version);
    }

    private mergeStates(localState: GameState, serverState: GameState): GameState {
        // Simple merge strategy - prefer server for most fields, keep local predictions
        const mergedState: GameState = {
            ...serverState, // Start with server state
            version: Math.max(localState.version, serverState.version)
        };

        // Apply any pending local actions that aren't reflected in server state
        for (const action of this._pendingActions.values()) {
            // This would apply the action to the merged state
            // Implementation depends on specific action types
        }

        return mergedState;
    }

    private compareProperty(
        property: keyof GameState, 
        oldState: GameState, 
        newState: GameState, 
        changes: StateChange[]
    ): void {
        if (oldState[property] !== newState[property]) {
            changes.push({
                path: property,
                oldValue: oldState[property],
                newValue: newState[property],
                timestamp: Date.now()
            });
        }
    }

    private compareArray(
        property: string,
        oldArray: any[],
        newArray: any[],
        changes: StateChange[]
    ): void {
        if (JSON.stringify(oldArray) !== JSON.stringify(newArray)) {
            changes.push({
                path: property,
                oldValue: oldArray,
                newValue: newArray,
                timestamp: Date.now()
            });
        }
    }

    private comparePlayers(
        oldPlayers: any[],
        newPlayers: any[],
        changes: StateChange[]
    ): void {
        for (let i = 0; i < Math.max(oldPlayers.length, newPlayers.length); i++) {
            const oldPlayer = oldPlayers[i];
            const newPlayer = newPlayers[i];
            
            if (JSON.stringify(oldPlayer) !== JSON.stringify(newPlayer)) {
                changes.push({
                    path: `players[${i}]`,
                    oldValue: oldPlayer,
                    newValue: newPlayer,
                    timestamp: Date.now()
                });
            }
        }
    }

    private applyChange(state: any, change: StateChange): void {
        const pathParts = change.path.split(/[\[\].]/).filter(part => part);
        let current = state;
        
        // Navigate to the parent of the target property
        for (let i = 0; i < pathParts.length - 1; i++) {
            const part = pathParts[i];
            if (!(part in current)) {
                current[part] = {};
            }
            current = current[part];
        }
        
        // Set the final property
        const finalPart = pathParts[pathParts.length - 1];
        current[finalPart] = change.newValue;
    }

    private calculateChecksum(state: GameState): string {
        // Simple checksum calculation - in production, use a proper hash function
        const stateString = JSON.stringify(state, Object.keys(state).sort());
        let hash = 0;
        
        for (let i = 0; i < stateString.length; i++) {
            const char = stateString.charCodeAt(i);
            hash = ((hash << 5) - hash) + char;
            hash = hash & hash; // Convert to 32-bit integer
        }
        
        return hash.toString(36);
    }

    /**
     * Get detailed sync metrics
     */
    public getSyncMetrics(): {
        status: SyncStatus;
        pendingActions: number;
        conflictResolvers: number;
        lastKnownServerVersion: number;
        syncEfficiency: number;
    } {
        return {
            status: { ...this._status },
            pendingActions: this._pendingActions.size,
            conflictResolvers: this._conflictResolvers.size,
            lastKnownServerVersion: this._lastKnownServerState?.version || 0,
            syncEfficiency: this._status.syncErrors > 0 ? 
                (this._status.lastSyncTime > 0 ? 1 - (this._status.syncErrors / 10) : 0) : 1
        };
    }

    /**
     * Reset sync statistics
     */
    public resetSyncStats(): void {
        this._status.syncErrors = 0;
        this._status.lastError = undefined;
        this._status.pendingSyncs = 0;
    }

    /**
     * Validate state integrity
     */
    public validateStateIntegrity(state: GameState): {
        isValid: boolean;
        errors: string[];
        warnings: string[];
    } {
        const errors: string[] = [];
        const warnings: string[] = [];

        // Basic validation
        if (!state.roomId) {
            errors.push('Missing room ID');
        }

        if (!state.gameId) {
            errors.push('Missing game ID');
        }

        if (state.players.length !== 3) {
            errors.push('Invalid player count');
        }

        if (state.currentPlayerIndex < 0 || state.currentPlayerIndex >= state.players.length) {
            errors.push('Invalid current player index');
        }

        // Tile count validation
        const totalHandTiles = state.players.reduce((sum, p) => sum + p.handTiles.length, 0);
        const totalMeldTiles = state.players.reduce((sum, p) => 
            sum + p.melds.reduce((meldSum, m) => meldSum + m.tiles.length, 0), 0);
        const totalTiles = totalHandTiles + totalMeldTiles + state.discardPile.length + state.remainingTiles;
        
        if (totalTiles !== 108) {
            errors.push(`Invalid total tile count: ${totalTiles}`);
        }

        // Time validation
        if (state.turnDeadline <= state.turnStartTime) {
            warnings.push('Turn deadline is before start time');
        }

        if (state.turnDeadline < Date.now() - 60000) {
            warnings.push('Turn deadline is far in the past');
        }

        return {
            isValid: errors.length === 0,
            errors,
            warnings
        };
    }

    /**
     * Create state backup
     */
    public createStateBackup(state: GameState): string {
        const backup = {
            state,
            timestamp: Date.now(),
            version: state.version,
            checksum: this.calculateChecksum(state)
        };
        return JSON.stringify(backup);
    }

    /**
     * Restore state from backup
     */
    public restoreStateFromBackup(backupData: string): GameState | null {
        try {
            const backup = JSON.parse(backupData);
            
            // Validate backup
            if (!backup.state || !backup.checksum) {
                console.error('Invalid backup data');
                return null;
            }

            // Verify checksum
            if (this.calculateChecksum(backup.state) !== backup.checksum) {
                console.error('Backup checksum validation failed');
                return null;
            }

            return backup.state;
        } catch (error) {
            console.error('Failed to restore state from backup:', error);
            return null;
        }
    }

    private loadCachedSyncData(): void {
        // Load cached sync status
        const cachedStatus = this._cacheManager.get<SyncStatus>('sync_status');
        if (cachedStatus) {
            this._status = { ...this._status, ...cachedStatus };
        }

        // Load cached server state
        const cachedServerState = this._cacheManager.get<GameState>('last_server_state');
        if (cachedServerState) {
            this._lastKnownServerState = cachedServerState;
        }
    }

    private cacheSyncData(): void {
        // Cache sync status
        this._cacheManager.set('sync_status', this._status, 60000); // 1 minute TTL

        // Cache last known server state
        if (this._lastKnownServerState) {
            this._cacheManager.set('last_server_state', this._lastKnownServerState, 300000); // 5 minutes TTL
        }
    }

    /**
     * Cleanup resources
     */
    public destroy(): void {
        this.stopSync();
        this._pendingActions.clear();
        this._conflictResolvers.clear();
        this.cacheSyncData();
    }
}