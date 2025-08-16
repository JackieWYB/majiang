import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest';
import { 
    GameStateManager, 
    GameState, 
    PlayerState, 
    GamePhase, 
    PlayerStatus, 
    ActionType,
    GameAction,
    UserPreferences,
    ValidationResult,
    ConflictResolution
} from '../../assets/scripts/managers/GameStateManager';

// Mock localStorage for web environment
const mockLocalStorage = {
    store: new Map<string, string>(),
    getItem: vi.fn((key: string) => mockLocalStorage.store.get(key) || null),
    setItem: vi.fn((key: string, value: string) => {
        mockLocalStorage.store.set(key, value);
        return undefined;
    }),
    removeItem: vi.fn((key: string) => {
        mockLocalStorage.store.delete(key);
        return undefined;
    }),
    clear: vi.fn(() => mockLocalStorage.store.clear())
};

Object.defineProperty(global, 'localStorage', {
    value: mockLocalStorage,
    writable: true
});

describe('GameStateManager', () => {
    let gameStateManager: GameStateManager;
    let mockGameState: GameState;
    let mockPlayerStates: PlayerState[];

    beforeEach(() => {
        // Reset singleton instance
        (GameStateManager as any)._instance = null;
        gameStateManager = GameStateManager.instance;
        
        // Clear localStorage mock
        mockLocalStorage.store.clear();
        vi.clearAllMocks();

        // Create mock player states
        mockPlayerStates = [
            {
                userId: 'user1',
                nickname: 'Player 1',
                avatar: 'avatar1.jpg',
                handTiles: ['1W', '2W', '3W', '4W', '5W', '6W', '7W', '8W', '9W', '1T', '2T', '3T', '4T'],
                melds: [],
                seatIndex: 0,
                isDealer: true,
                score: 0,
                status: PlayerStatus.ACTIVE,
                isConnected: true,
                lastActionTime: Date.now()
            },
            {
                userId: 'user2',
                nickname: 'Player 2',
                avatar: 'avatar2.jpg',
                handTiles: ['1W', '2W', '3W', '4W', '5W', '6W', '7W', '8W', '9W', '1T', '2T', '3T'],
                melds: [],
                seatIndex: 1,
                isDealer: false,
                score: 0,
                status: PlayerStatus.ACTIVE,
                isConnected: true,
                lastActionTime: Date.now()
            },
            {
                userId: 'user3',
                nickname: 'Player 3',
                avatar: 'avatar3.jpg',
                handTiles: ['1W', '2W', '3W', '4W', '5W', '6W', '7W', '8W', '9W', '1T', '2T', '3T'],
                melds: [],
                seatIndex: 2,
                isDealer: false,
                score: 0,
                status: PlayerStatus.ACTIVE,
                isConnected: true,
                lastActionTime: Date.now()
            }
        ];

        // Create mock game state
        mockGameState = {
            roomId: '123456',
            gameId: 'game_123',
            players: mockPlayerStates,
            currentPlayerIndex: 0,
            turnStartTime: Date.now(),
            turnDeadline: Date.now() + 15000,
            phase: GamePhase.PLAYING,
            discardPile: [],
            remainingTiles: 69, // 108 - 39 (13*3 + 0 melds + 0 discarded)
            availableActions: [ActionType.DISCARD],
            dealerIndex: 0,
            roundIndex: 1,
            version: 1
        };

        gameStateManager.initialize('user1');
    });

    afterEach(() => {
        gameStateManager.clearState();
    });

    describe('Initialization', () => {
        it('should create singleton instance', () => {
            const instance1 = GameStateManager.instance;
            const instance2 = GameStateManager.instance;
            expect(instance1).toBe(instance2);
        });

        it('should initialize with user ID', () => {
            gameStateManager.initialize('testUser');
            expect(gameStateManager.getLocalPlayer()).toBeNull(); // No state yet
        });

        it('should load default user preferences', () => {
            const preferences = gameStateManager.getUserPreferences();
            expect(preferences).toEqual({
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
            });
        });
    });

    describe('State Management', () => {
        it('should update game state from server snapshot', () => {
            gameStateManager.updateGameState(mockGameState);
            
            const currentState = gameStateManager.getCurrentState();
            expect(currentState).toEqual(mockGameState);
        });

        it('should identify local player correctly', () => {
            gameStateManager.updateGameState(mockGameState);
            
            const localPlayer = gameStateManager.getLocalPlayer();
            expect(localPlayer?.userId).toBe('user1');
            expect(localPlayer?.seatIndex).toBe(0);
        });

        it('should detect local player turn', () => {
            gameStateManager.updateGameState(mockGameState);
            expect(gameStateManager.isLocalPlayerTurn()).toBe(true);

            // Change current player
            const newState = { ...mockGameState, currentPlayerIndex: 1 };
            gameStateManager.updateGameState(newState);
            expect(gameStateManager.isLocalPlayerTurn()).toBe(false);
        });

        it('should get available actions for local player', () => {
            gameStateManager.updateGameState(mockGameState);
            const actions = gameStateManager.getAvailableActions();
            expect(actions).toEqual([ActionType.DISCARD]);
        });

        it('should calculate turn time remaining', () => {
            const futureDeadline = Date.now() + 10000;
            const stateWithDeadline = { ...mockGameState, turnDeadline: futureDeadline };
            
            gameStateManager.updateGameState(stateWithDeadline);
            const timeRemaining = gameStateManager.getTurnTimeRemaining();
            
            expect(timeRemaining).toBeGreaterThan(9000);
            expect(timeRemaining).toBeLessThanOrEqual(10000);
        });

        it('should clear state properly', () => {
            gameStateManager.updateGameState(mockGameState);
            gameStateManager.clearState();
            
            expect(gameStateManager.getCurrentState()).toBeNull();
            expect(gameStateManager.getLocalPlayer()).toBeNull();
        });
    });

    describe('Action Validation', () => {
        beforeEach(() => {
            gameStateManager.updateGameState(mockGameState);
        });

        it('should validate discard action', () => {
            const discardAction: GameAction = {
                type: ActionType.DISCARD,
                playerId: 'user1',
                tile: '1W',
                timestamp: Date.now(),
                sequence: 1
            };

            const result = gameStateManager.validateAction(discardAction, mockGameState);
            expect(result.isValid).toBe(true);
            expect(result.errors).toHaveLength(0);
        });

        it('should reject invalid discard action', () => {
            const invalidAction: GameAction = {
                type: ActionType.DISCARD,
                playerId: 'user1',
                tile: 'INVALID',
                timestamp: Date.now(),
                sequence: 1
            };

            const result = gameStateManager.validateAction(invalidAction, mockGameState);
            expect(result.isValid).toBe(false);
            expect(result.errors).toContain('Tile not in hand');
        });

        it('should validate peng action', () => {
            // Add two more of the same tile to hand
            const stateWithPengTiles = {
                ...mockGameState,
                players: mockGameState.players.map(p => 
                    p.userId === 'user1' 
                        ? { ...p, handTiles: [...p.handTiles, '1W', '1W'] }
                        : p
                )
            };

            const pengAction: GameAction = {
                type: ActionType.PENG,
                playerId: 'user1',
                tile: '1W',
                timestamp: Date.now(),
                sequence: 1
            };

            const result = gameStateManager.validateAction(pengAction, stateWithPengTiles);
            expect(result.isValid).toBe(true);
        });

        it('should reject peng action without enough tiles', () => {
            const pengAction: GameAction = {
                type: ActionType.PENG,
                playerId: 'user1',
                tile: '5T', // Only one in hand
                timestamp: Date.now(),
                sequence: 1
            };

            const result = gameStateManager.validateAction(pengAction, mockGameState);
            expect(result.isValid).toBe(false);
            expect(result.errors).toContain('Not enough tiles for peng');
        });
    });

    describe('Action Prediction', () => {
        beforeEach(() => {
            gameStateManager.updateGameState(mockGameState);
        });

        it('should predict valid discard action', () => {
            const discardAction: GameAction = {
                type: ActionType.DISCARD,
                playerId: 'user1',
                tile: '1W',
                timestamp: Date.now(),
                sequence: 1
            };

            const result = gameStateManager.predictAction(discardAction);
            expect(result.isValid).toBe(true);

            // Check that state was updated optimistically
            const currentState = gameStateManager.getCurrentState();
            expect(currentState?.discardPile).toContain('1W');
            
            const localPlayer = gameStateManager.getLocalPlayer();
            expect(localPlayer?.handTiles).not.toContain('1W');
        });

        it('should confirm predicted action', () => {
            const discardAction: GameAction = {
                type: ActionType.DISCARD,
                playerId: 'user1',
                tile: '1W',
                timestamp: Date.now(),
                sequence: 1
            };

            gameStateManager.predictAction(discardAction);
            gameStateManager.confirmAction(discardAction);

            // Action should be removed from predicted actions
            // (This is internal state, so we test indirectly by ensuring no rollback occurs)
            const currentState = gameStateManager.getCurrentState();
            expect(currentState?.discardPile).toContain('1W');
        });

        it('should reject predicted action and rollback', () => {
            const originalState = gameStateManager.getCurrentState();
            
            const discardAction: GameAction = {
                type: ActionType.DISCARD,
                playerId: 'user1',
                tile: '1W',
                timestamp: Date.now(),
                sequence: 1
            };

            gameStateManager.predictAction(discardAction);
            gameStateManager.rejectAction(discardAction, 'Invalid move');

            // State should be rolled back
            const currentState = gameStateManager.getCurrentState();
            expect(currentState?.discardPile).toEqual(originalState?.discardPile);
        });
    });

    describe('State Synchronization', () => {
        it('should handle server state with higher version', () => {
            gameStateManager.updateGameState(mockGameState);
            
            const newerState = { ...mockGameState, version: 2, currentPlayerIndex: 1 };
            gameStateManager.updateGameState(newerState);
            
            const currentState = gameStateManager.getCurrentState();
            expect(currentState?.version).toBe(2);
            expect(currentState?.currentPlayerIndex).toBe(1);
        });

        it('should handle state conflict with lower server version', () => {
            const conflictListener = vi.fn();
            gameStateManager.addConflictListener(conflictListener);
            
            // Set local state with higher version
            const localState = { ...mockGameState, version: 2 };
            gameStateManager.updateGameState(localState);
            
            // Try to update with older server state
            const olderServerState = { ...mockGameState, version: 1, currentPlayerIndex: 1 };
            gameStateManager.updateGameState(olderServerState);
            
            expect(conflictListener).toHaveBeenCalled();
        });

        it('should apply predicted actions to server state', () => {
            gameStateManager.updateGameState(mockGameState);
            
            // Make a predicted action
            const discardAction: GameAction = {
                type: ActionType.DISCARD,
                playerId: 'user1',
                tile: '1W',
                timestamp: Date.now(),
                sequence: 1
            };
            gameStateManager.predictAction(discardAction);
            
            // Simulate server state update without the predicted action
            const serverState = { ...mockGameState, version: 2 };
            gameStateManager.updateGameState(serverState);
            
            // Predicted action should still be applied
            const currentState = gameStateManager.getCurrentState();
            expect(currentState?.discardPile).toContain('1W');
        });
    });

    describe('User Preferences', () => {
        it('should update user preferences', () => {
            const newPreferences: Partial<UserPreferences> = {
                soundEnabled: false,
                animationSpeed: 0.5
            };

            gameStateManager.updateUserPreferences(newPreferences);
            
            const preferences = gameStateManager.getUserPreferences();
            expect(preferences.soundEnabled).toBe(false);
            expect(preferences.animationSpeed).toBe(0.5);
            expect(preferences.musicEnabled).toBe(true); // Should remain unchanged
        });

        it('should maintain user preferences in memory', () => {
            const newPreferences: Partial<UserPreferences> = {
                tileTheme: 'modern',
                backgroundTheme: 'blue'
            };

            gameStateManager.updateUserPreferences(newPreferences);
            
            const preferences = gameStateManager.getUserPreferences();
            expect(preferences.tileTheme).toBe('modern');
            expect(preferences.backgroundTheme).toBe('blue');
        });

        it('should return copy of user preferences', () => {
            const preferences1 = gameStateManager.getUserPreferences();
            const preferences2 = gameStateManager.getUserPreferences();
            
            // Should be equal but not the same object
            expect(preferences1).toEqual(preferences2);
            expect(preferences1).not.toBe(preferences2);
            
            // Modifying one shouldn't affect the other
            preferences1.soundEnabled = !preferences1.soundEnabled;
            expect(preferences2.soundEnabled).not.toBe(preferences1.soundEnabled);
        });
    });

    describe('State Caching', () => {
        it('should maintain state in memory', () => {
            gameStateManager.updateGameState(mockGameState);
            
            const currentState = gameStateManager.getCurrentState();
            expect(currentState?.roomId).toBe(mockGameState.roomId);
            expect(currentState?.version).toBe(mockGameState.version);
        });

        it('should handle state updates correctly', () => {
            gameStateManager.updateGameState(mockGameState);
            
            const updatedState = { ...mockGameState, version: 2, currentPlayerIndex: 1 };
            gameStateManager.updateGameState(updatedState);
            
            const currentState = gameStateManager.getCurrentState();
            expect(currentState?.version).toBe(2);
            expect(currentState?.currentPlayerIndex).toBe(1);
        });

        it('should clear state properly', () => {
            gameStateManager.updateGameState(mockGameState);
            expect(gameStateManager.getCurrentState()).not.toBeNull();
            
            gameStateManager.clearState();
            expect(gameStateManager.getCurrentState()).toBeNull();
            expect(gameStateManager.getLocalPlayer()).toBeNull();
        });

        it('should maintain state history', () => {
            gameStateManager.updateGameState(mockGameState);
            
            const updatedState = { ...mockGameState, version: 2 };
            gameStateManager.updateGameState(updatedState);
            
            const history = gameStateManager.getStateHistory();
            expect(history.length).toBe(2);
            expect(history[0].version).toBe(1);
            expect(history[1].version).toBe(2);
        });
    });

    describe('Event Listeners', () => {
        it('should notify state change listeners', () => {
            const listener = vi.fn();
            gameStateManager.addStateChangeListener(listener);
            
            gameStateManager.updateGameState(mockGameState);
            
            expect(listener).toHaveBeenCalledWith(mockGameState);
        });

        it('should remove state change listeners', () => {
            const listener = vi.fn();
            gameStateManager.addStateChangeListener(listener);
            gameStateManager.removeStateChangeListener(listener);
            
            gameStateManager.updateGameState(mockGameState);
            
            expect(listener).not.toHaveBeenCalled();
        });

        it('should handle listener errors gracefully', () => {
            const errorListener = vi.fn(() => {
                throw new Error('Listener error');
            });
            const normalListener = vi.fn();
            
            gameStateManager.addStateChangeListener(errorListener);
            gameStateManager.addStateChangeListener(normalListener);
            
            // Should not throw
            expect(() => {
                gameStateManager.updateGameState(mockGameState);
            }).not.toThrow();
            
            expect(normalListener).toHaveBeenCalled();
        });
    });

    describe('State History', () => {
        it('should maintain state history', () => {
            gameStateManager.updateGameState(mockGameState);
            
            const updatedState = { ...mockGameState, version: 2, currentPlayerIndex: 1 };
            gameStateManager.updateGameState(updatedState);
            
            const history = gameStateManager.getStateHistory();
            expect(history).toHaveLength(2);
            expect(history[0].version).toBe(1);
            expect(history[1].version).toBe(2);
        });

        it('should limit history size', () => {
            // Update state many times
            for (let i = 1; i <= 60; i++) {
                const state = { ...mockGameState, version: i };
                gameStateManager.updateGameState(state);
            }
            
            const history = gameStateManager.getStateHistory();
            expect(history.length).toBeLessThanOrEqual(50); // Max history size
        });
    });

    describe('Player Queries', () => {
        beforeEach(() => {
            gameStateManager.updateGameState(mockGameState);
        });

        it('should get player by user ID', () => {
            const player = gameStateManager.getPlayer('user2');
            expect(player?.userId).toBe('user2');
            expect(player?.nickname).toBe('Player 2');
        });

        it('should return null for non-existent player', () => {
            const player = gameStateManager.getPlayer('nonexistent');
            expect(player).toBeNull();
        });

        it('should return copy of player state', () => {
            const player = gameStateManager.getPlayer('user1');
            const originalHandSize = player?.handTiles.length;
            
            // Modify returned player
            player?.handTiles.push('EXTRA');
            
            // Original should be unchanged
            const playerAgain = gameStateManager.getPlayer('user1');
            expect(playerAgain?.handTiles.length).toBe(originalHandSize);
        });
    });

    describe('State Validation', () => {
        it('should validate state consistency', () => {
            // Create invalid state with wrong tile count
            const invalidState = {
                ...mockGameState,
                remainingTiles: 200 // Invalid count
            };
            
            // Should log warning but not throw
            expect(() => {
                gameStateManager.updateGameState(invalidState);
            }).not.toThrow();
        });

        it('should validate player count', () => {
            const invalidState = {
                ...mockGameState,
                players: [mockPlayerStates[0]] // Only one player
            };
            
            expect(() => {
                gameStateManager.updateGameState(invalidState);
            }).not.toThrow();
        });

        it('should get validation errors', () => {
            gameStateManager.updateGameState(mockGameState);
            const errors = gameStateManager.getValidationErrors();
            expect(Array.isArray(errors)).toBe(true);
        });

        it('should detect invalid tile counts', () => {
            const invalidState = {
                ...mockGameState,
                remainingTiles: 200
            };
            
            gameStateManager.updateGameState(invalidState);
            const errors = gameStateManager.getValidationErrors();
            expect(errors.some(error => error.includes('Invalid total tile count'))).toBe(true);
        });
    });

    describe('Performance and Debugging', () => {
        beforeEach(() => {
            gameStateManager.updateGameState(mockGameState);
        });

        it('should provide performance metrics', () => {
            const metrics = gameStateManager.getPerformanceMetrics();
            
            expect(metrics).toHaveProperty('stateUpdates');
            expect(metrics).toHaveProperty('predictedActions');
            expect(metrics).toHaveProperty('validationErrors');
            expect(metrics).toHaveProperty('syncTime');
            expect(metrics).toHaveProperty('memoryUsage');
            
            expect(typeof metrics.stateUpdates).toBe('number');
            expect(typeof metrics.predictedActions).toBe('number');
            expect(typeof metrics.validationErrors).toBe('number');
            expect(typeof metrics.syncTime).toBe('number');
            expect(typeof metrics.memoryUsage).toBe('number');
        });

        it('should export complete state', () => {
            const exportedState = gameStateManager.exportState();
            
            expect(exportedState).toHaveProperty('currentState');
            expect(exportedState).toHaveProperty('stateHistory');
            expect(exportedState).toHaveProperty('predictedActions');
            expect(exportedState).toHaveProperty('userPreferences');
            expect(exportedState).toHaveProperty('localPlayerIndex');
            expect(exportedState).toHaveProperty('lastSyncTime');
            
            expect(exportedState.currentState).toEqual(mockGameState);
        });

        it('should import state correctly', () => {
            const originalState = gameStateManager.exportState();
            
            // Clear state
            gameStateManager.clearState();
            expect(gameStateManager.getCurrentState()).toBeNull();
            
            // Import state
            gameStateManager.importState(originalState);
            expect(gameStateManager.getCurrentState()).toEqual(mockGameState);
        });

        it('should calculate memory usage', () => {
            const metrics = gameStateManager.getPerformanceMetrics();
            expect(metrics.memoryUsage).toBeGreaterThan(0);
            
            // Add more state history
            for (let i = 0; i < 10; i++) {
                const state = { ...mockGameState, version: i + 2 };
                gameStateManager.updateGameState(state);
            }
            
            const newMetrics = gameStateManager.getPerformanceMetrics();
            expect(newMetrics.memoryUsage).toBeGreaterThan(metrics.memoryUsage);
        });

        it('should handle state import with listeners', () => {
            const listener = vi.fn();
            gameStateManager.addStateChangeListener(listener);
            
            const exportedState = gameStateManager.exportState();
            gameStateManager.clearState();
            
            gameStateManager.importState(exportedState);
            
            expect(listener).toHaveBeenCalledWith(mockGameState);
        });
    });

    describe('Enhanced Caching', () => {
        beforeEach(() => {
            gameStateManager.updateGameState(mockGameState);
        });

        it('should provide cache statistics', () => {
            const cacheStats = gameStateManager.getCacheStats();
            
            expect(cacheStats).toHaveProperty('cacheStats');
            expect(cacheStats).toHaveProperty('userPreferencesCached');
            expect(cacheStats).toHaveProperty('gameStateCached');
            expect(cacheStats).toHaveProperty('cacheSize');
            
            expect(typeof cacheStats.userPreferencesCached).toBe('boolean');
            expect(typeof cacheStats.gameStateCached).toBe('boolean');
            expect(typeof cacheStats.cacheSize).toBe('number');
        });

        it('should clear all cache', () => {
            gameStateManager.clearAllCache();
            
            const cacheStats = gameStateManager.getCacheStats();
            expect(cacheStats.cacheSize).toBe(0);
        });

        it('should preload common data', () => {
            gameStateManager.preloadData();
            
            const commonData = gameStateManager.getCommonGameData();
            expect(commonData).toHaveProperty('tileTypes');
            expect(commonData).toHaveProperty('actionTypes');
            expect(commonData).toHaveProperty('gamePhases');
            expect(commonData).toHaveProperty('playerStatuses');
        });

        it('should cache user preferences efficiently', () => {
            const newPreferences = {
                soundEnabled: false,
                language: 'en-US'
            };
            
            gameStateManager.updateUserPreferences(newPreferences);
            
            const cacheStats = gameStateManager.getCacheStats();
            expect(cacheStats.userPreferencesCached).toBe(true);
        });

        it('should handle cache integration with state management', () => {
            const initialCacheSize = gameStateManager.getCacheStats().cacheSize;
            
            // Update state multiple times
            for (let i = 0; i < 5; i++) {
                const state = { ...mockGameState, version: i + 2 };
                gameStateManager.updateGameState(state);
            }
            
            const finalCacheSize = gameStateManager.getCacheStats().cacheSize;
            expect(finalCacheSize).toBeGreaterThanOrEqual(initialCacheSize);
        });
    });
});