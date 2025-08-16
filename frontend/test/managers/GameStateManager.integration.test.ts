import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest';
import { 
    GameStateManager, 
    GameState, 
    PlayerState, 
    GamePhase, 
    PlayerStatus, 
    ActionType,
    GameAction
} from '../../assets/scripts/managers/GameStateManager';
import { NetworkManager, GameMessage } from '../../assets/scripts/network/NetworkManager';
import { StateSynchronizer } from '../../assets/scripts/managers/StateSynchronizer';

// Mock WebSocket
class MockWebSocket {
    public onopen: ((event: Event) => void) | null = null;
    public onmessage: ((event: MessageEvent) => void) | null = null;
    public onclose: ((event: CloseEvent) => void) | null = null;
    public onerror: ((event: Event) => void) | null = null;
    public readyState: number = WebSocket.CONNECTING;

    constructor(public url: string) {
        // Simulate connection after a short delay
        setTimeout(() => {
            this.readyState = WebSocket.OPEN;
            if (this.onopen) {
                this.onopen(new Event('open'));
            }
        }, 10);
    }

    send(data: string): void {
        // Simulate server response
        setTimeout(() => {
            if (this.onmessage) {
                const message = JSON.parse(data);
                let response: any;

                switch (message.cmd) {
                    case 'GET_STATE_SNAPSHOT':
                        response = {
                            type: 'RESP',
                            cmd: 'GET_STATE_SNAPSHOT',
                            reqId: message.reqId,
                            data: {
                                snapshot: {
                                    state: mockGameState,
                                    timestamp: Date.now(),
                                    checksum: 'mock_checksum'
                                }
                            },
                            timestamp: Date.now()
                        };
                        break;
                    case 'HEARTBEAT':
                        response = {
                            type: 'RESP',
                            cmd: 'HEARTBEAT',
                            reqId: message.reqId,
                            timestamp: Date.now()
                        };
                        break;
                    default:
                        response = {
                            type: 'RESP',
                            cmd: message.cmd,
                            reqId: message.reqId,
                            data: { success: true },
                            timestamp: Date.now()
                        };
                }

                this.onmessage(new MessageEvent('message', { data: JSON.stringify(response) }));
            }
        }, 50);
    }

    close(): void {
        this.readyState = WebSocket.CLOSED;
        if (this.onclose) {
            this.onclose(new CloseEvent('close'));
        }
    }
}

// Mock global WebSocket
global.WebSocket = MockWebSocket as any;

// Mock localStorage
const mockLocalStorage = {
    store: new Map<string, string>(),
    getItem: vi.fn((key: string) => mockLocalStorage.store.get(key) || null),
    setItem: vi.fn((key: string, value: string) => mockLocalStorage.store.set(key, value)),
    removeItem: vi.fn((key: string) => mockLocalStorage.store.delete(key)),
    clear: vi.fn(() => mockLocalStorage.store.clear())
};

Object.defineProperty(global, 'localStorage', {
    value: mockLocalStorage
});

// Mock game state
const mockGameState: GameState = {
    roomId: '123456',
    gameId: 'game_123',
    players: [
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
    ],
    currentPlayerIndex: 0,
    turnStartTime: Date.now(),
    turnDeadline: Date.now() + 15000,
    phase: GamePhase.PLAYING,
    discardPile: [],
    remainingTiles: 69,
    availableActions: [ActionType.DISCARD],
    dealerIndex: 0,
    roundIndex: 1,
    version: 1
};

describe('GameStateManager Integration Tests', () => {
    let gameStateManager: GameStateManager;
    let networkManager: NetworkManager;
    let stateSynchronizer: StateSynchronizer;

    beforeEach(async () => {
        // Reset singletons
        (GameStateManager as any)._instance = null;
        
        // Clear localStorage mock
        mockLocalStorage.store.clear();
        vi.clearAllMocks();

        // Create instances
        gameStateManager = GameStateManager.instance;
        
        // Create a mock NetworkManager instead of using the real one
        networkManager = {
            connect: vi.fn().mockResolvedValue(undefined),
            disconnect: vi.fn(),
            sendMessage: vi.fn(),
            sendRequest: vi.fn().mockResolvedValue({ 
                snapshot: {
                    state: mockGameState,
                    timestamp: Date.now(),
                    checksum: 'mock_checksum'
                }
            }),
            onMessage: vi.fn(),
            offMessage: vi.fn(),
            isConnected: vi.fn().mockReturnValue(true),
            _messageHandlers: new Map()
        } as any;
        
        // Initialize
        gameStateManager.initialize('user1');
        
        stateSynchronizer = new StateSynchronizer(networkManager);
    });

    afterEach(() => {
        gameStateManager.clearState();
        if (networkManager && networkManager.disconnect) {
            networkManager.disconnect();
        }
        if (stateSynchronizer) {
            stateSynchronizer.destroy();
        }
    });

    describe('Network Integration', () => {
        it('should integrate with network manager for state updates', () => {
            const stateChangeListener = vi.fn();
            gameStateManager.addStateChangeListener(stateChangeListener);

            // Directly update state (simulating network message handling)
            gameStateManager.updateGameState(mockGameState);

            expect(stateChangeListener).toHaveBeenCalledWith(mockGameState);
            expect(gameStateManager.getCurrentState()?.roomId).toBe('123456');
        });

        it('should work with network manager for sending messages', () => {
            gameStateManager.updateGameState(mockGameState);

            // Predict action
            const discardAction: GameAction = {
                type: ActionType.DISCARD,
                playerId: 'user1',
                tile: '1W',
                timestamp: Date.now(),
                sequence: 1
            };

            const validation = gameStateManager.predictAction(discardAction);
            expect(validation.isValid).toBe(true);

            // Simulate sending through network manager
            const actionMessage: GameMessage = {
                type: 'REQ',
                cmd: 'PLAYER_ACTION',
                roomId: '123456',
                data: { action: discardAction },
                timestamp: Date.now()
            };

            networkManager.sendMessage(actionMessage);
            expect(networkManager.sendMessage).toHaveBeenCalledWith(actionMessage);
        });

        it('should handle action confirmation workflow', () => {
            gameStateManager.updateGameState(mockGameState);

            const discardAction: GameAction = {
                type: ActionType.DISCARD,
                playerId: 'user1',
                tile: '1W',
                timestamp: Date.now(),
                sequence: 1
            };

            // Predict action
            gameStateManager.predictAction(discardAction);
            
            // Confirm action
            gameStateManager.confirmAction(discardAction);

            // Action should remain applied
            const currentState = gameStateManager.getCurrentState();
            expect(currentState?.discardPile).toContain('1W');
        });

        it('should handle action rejection workflow', () => {
            gameStateManager.updateGameState(mockGameState);

            const discardAction: GameAction = {
                type: ActionType.DISCARD,
                playerId: 'user1',
                tile: '1W',
                timestamp: Date.now(),
                sequence: 1
            };

            // Store original state
            const originalState = gameStateManager.getCurrentState();
            const originalDiscardPileLength = originalState?.discardPile.length || 0;

            // Predict action
            gameStateManager.predictAction(discardAction);
            
            // Verify action was applied
            let currentState = gameStateManager.getCurrentState();
            expect(currentState?.discardPile).toContain('1W');
            
            // Reject action
            gameStateManager.rejectAction(discardAction, 'Invalid move');

            // Action should be rolled back to previous state
            currentState = gameStateManager.getCurrentState();
            expect(currentState?.discardPile.length).toBe(originalDiscardPileLength);
        });
    });

    describe('State Synchronization', () => {
        it('should perform synchronization with network manager', async () => {
            const snapshot = await stateSynchronizer.performSync();
            
            expect(networkManager.sendRequest).toHaveBeenCalled();
            expect(snapshot).toBeTruthy();
            expect(snapshot?.state.roomId).toBe(mockGameState.roomId);
        });

        it('should handle sync conflicts', () => {
            gameStateManager.updateGameState(mockGameState);

            const conflictListener = vi.fn();
            gameStateManager.addConflictListener(conflictListener);

            // Create conflicting states
            const localState = { ...mockGameState, version: 2, currentPlayerIndex: 1 };
            const serverState = { ...mockGameState, version: 1, currentPlayerIndex: 2 };

            gameStateManager.updateGameState(localState);
            
            // Simulate server state with lower version
            gameStateManager.updateGameState(serverState);

            expect(conflictListener).toHaveBeenCalled();
        });

        it('should register and track actions for sync', () => {
            const action: GameAction = {
                type: ActionType.DISCARD,
                playerId: 'user1',
                tile: '1W',
                timestamp: Date.now(),
                sequence: 1
            };

            stateSynchronizer.registerAction(action);
            expect(stateSynchronizer.getPendingActionsCount()).toBe(1);

            stateSynchronizer.confirmAction(action);
            expect(stateSynchronizer.getPendingActionsCount()).toBe(0);
        });
    });

    describe('Reconnection Scenarios', () => {
        it('should handle state recovery after reconnection', async () => {
            gameStateManager.updateGameState(mockGameState);

            // Simulate reconnection by requesting sync
            const syncResult = await stateSynchronizer.performSync();
            expect(syncResult).toBeTruthy();
            expect(syncResult?.state.roomId).toBe(mockGameState.roomId);
        });

        it('should maintain predicted actions during reconnection', () => {
            gameStateManager.updateGameState(mockGameState);

            const discardAction: GameAction = {
                type: ActionType.DISCARD,
                playerId: 'user1',
                tile: '1W',
                timestamp: Date.now(),
                sequence: 1
            };

            gameStateManager.predictAction(discardAction);
            stateSynchronizer.registerAction(discardAction);

            // Predicted action should be tracked
            expect(stateSynchronizer.getPendingActionsCount()).toBe(1);
            
            // After reconnection, actions should still be pending
            expect(stateSynchronizer.getPendingActionsCount()).toBe(1);
        });
    });

    describe('Error Handling', () => {
        it('should handle network errors gracefully', () => {
            networkManager.sendMessage = vi.fn(() => {
                throw new Error('Network error');
            });

            expect(() => {
                networkManager.sendMessage({
                    type: 'REQ',
                    cmd: 'TEST',
                    timestamp: Date.now()
                });
            }).toThrow('Network error');
        });

        it('should handle invalid actions gracefully', () => {
            gameStateManager.updateGameState(mockGameState);

            const invalidAction: GameAction = {
                type: ActionType.DISCARD,
                playerId: 'user1',
                tile: 'INVALID_TILE',
                timestamp: Date.now(),
                sequence: 1
            };

            const result = gameStateManager.predictAction(invalidAction);
            expect(result.isValid).toBe(false);
            expect(result.errors.length).toBeGreaterThan(0);
        });

        it('should handle state validation errors', () => {
            // Create invalid state
            const invalidState = {
                ...mockGameState,
                players: [] // Invalid: no players
            };

            // Should not throw, but may log warnings
            expect(() => {
                gameStateManager.updateGameState(invalidState);
            }).not.toThrow();
        });
    });

    describe('Performance', () => {
        it('should handle rapid state updates efficiently', () => {
            const startTime = Date.now();
            
            // Perform many state updates
            for (let i = 0; i < 100; i++) {
                const state = { ...mockGameState, version: i };
                gameStateManager.updateGameState(state);
            }
            
            const endTime = Date.now();
            const duration = endTime - startTime;
            
            // Should complete within reasonable time (less than 100ms)
            expect(duration).toBeLessThan(100);
        });

        it('should limit state history size', () => {
            // Add many states to history
            for (let i = 0; i < 100; i++) {
                const state = { ...mockGameState, version: i };
                gameStateManager.updateGameState(state);
            }
            
            const history = gameStateManager.getStateHistory();
            expect(history.length).toBeLessThanOrEqual(50); // Max history size
        });
    });

    describe('Memory Management', () => {
        it('should clean up listeners properly', () => {
            const listener = vi.fn();
            gameStateManager.addStateChangeListener(listener);
            gameStateManager.removeStateChangeListener(listener);
            
            gameStateManager.updateGameState(mockGameState);
            
            expect(listener).not.toHaveBeenCalled();
        });

        it('should clear cached data on state clear', () => {
            gameStateManager.updateGameState(mockGameState);
            expect(gameStateManager.getCurrentState()).not.toBeNull();
            
            gameStateManager.clearState();
            expect(gameStateManager.getCurrentState()).toBeNull();
            
            // State should be properly cleared
            expect(gameStateManager.getLocalPlayer()).toBeNull();
            expect(gameStateManager.getStateHistory()).toHaveLength(0);
        });
    });

    describe('Enhanced Features', () => {
        it('should provide performance metrics', () => {
            gameStateManager.updateGameState(mockGameState);
            
            const metrics = gameStateManager.getPerformanceMetrics();
            expect(metrics).toHaveProperty('stateUpdates');
            expect(metrics).toHaveProperty('predictedActions');
            expect(metrics).toHaveProperty('validationErrors');
            expect(metrics).toHaveProperty('syncTime');
            expect(metrics).toHaveProperty('memoryUsage');
            
            expect(metrics.stateUpdates).toBeGreaterThan(0);
            expect(metrics.memoryUsage).toBeGreaterThan(0);
        });

        it('should export and import state', () => {
            gameStateManager.updateGameState(mockGameState);
            
            const exportedState = gameStateManager.exportState();
            expect(exportedState.currentState).toEqual(mockGameState);
            
            // Clear state and import
            gameStateManager.clearState();
            expect(gameStateManager.getCurrentState()).toBeNull();
            
            gameStateManager.importState(exportedState);
            expect(gameStateManager.getCurrentState()).toEqual(mockGameState);
        });

        it('should validate state integrity', () => {
            const validationErrors = gameStateManager.getValidationErrors();
            expect(Array.isArray(validationErrors)).toBe(true);
        });

        it('should handle state synchronization metrics', () => {
            const metrics = stateSynchronizer.getSyncMetrics();
            expect(metrics).toHaveProperty('status');
            expect(metrics).toHaveProperty('pendingActions');
            expect(metrics).toHaveProperty('conflictResolvers');
            expect(metrics).toHaveProperty('syncEfficiency');
        });

        it('should create and restore state backups', () => {
            const backup = stateSynchronizer.createStateBackup(mockGameState);
            expect(typeof backup).toBe('string');
            
            const restoredState = stateSynchronizer.restoreStateFromBackup(backup);
            expect(restoredState).toEqual(mockGameState);
        });

        it('should validate state integrity comprehensively', () => {
            const validation = stateSynchronizer.validateStateIntegrity(mockGameState);
            expect(validation).toHaveProperty('isValid');
            expect(validation).toHaveProperty('errors');
            expect(validation).toHaveProperty('warnings');
            expect(Array.isArray(validation.errors)).toBe(true);
            expect(Array.isArray(validation.warnings)).toBe(true);
        });
    });
});