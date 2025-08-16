import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { GameStateManager } from '../../assets/scripts/managers/GameStateManager';
import { NetworkManager } from '../../assets/scripts/network/NetworkManager';
import { SceneManager } from '../../assets/scripts/managers/SceneManager';
import { mockCc } from '../mocks/cocos';
import { mockWx } from '../mocks/wechat';

// Mock Cocos Creator and WeChat
global.cc = mockCc;
global.wx = mockWx;

/**
 * End-to-end tests for complete game scenarios
 * Tests the full game flow from login to game completion
 */
describe('Game Flow E2E Tests', () => {
    let gameStateManager: GameStateManager;
    let networkManager: NetworkManager;
    let sceneManager: SceneManager;

    beforeEach(() => {
        vi.clearAllMocks();
        
        // Reset singletons
        (GameStateManager as any)._instance = null;
        (NetworkManager as any)._instance = null;
        (SceneManager as any)._instance = null;

        gameStateManager = GameStateManager.instance;
        networkManager = new NetworkManager();
        sceneManager = SceneManager.instance;

        // Initialize managers
        gameStateManager.initialize('test_user_1');
        networkManager.onLoad();
        sceneManager.onLoad();
    });

    afterEach(() => {
        networkManager.disconnect();
        gameStateManager.clearState();
    });

    describe('Complete Game Flow', () => {
        it('should complete full game flow from login to settlement', async () => {
            // 1. Login Flow
            await simulateLogin();
            
            // 2. Lobby Flow
            await simulateLobbyNavigation();
            
            // 3. Room Creation/Joining
            const roomId = await simulateRoomCreation();
            
            // 4. Game Start
            await simulateGameStart(roomId);
            
            // 5. Gameplay
            await simulateGameplay(roomId);
            
            // 6. Game Settlement
            await simulateGameSettlement(roomId);
            
            // 7. Return to Lobby
            await simulateReturnToLobby();
            
            // Verify final state
            expect(sceneManager.getCurrentScene()).toBe('LobbyScene');
        });

        it('should handle reconnection during gameplay', async () => {
            // Setup game in progress
            await simulateLogin();
            const roomId = await simulateRoomCreation();
            await simulateGameStart(roomId);
            
            // Simulate disconnection
            networkManager.disconnect();
            expect(networkManager.isConnected()).toBe(false);
            
            // Simulate reconnection
            await networkManager.forceReconnect();
            expect(networkManager.isConnected()).toBe(true);
            
            // Verify game state is restored
            const gameState = gameStateManager.getCurrentState();
            expect(gameState).not.toBeNull();
            expect(gameState?.roomId).toBe(roomId);
        });

        it('should handle multiple rounds in a game', async () => {
            await simulateLogin();
            const roomId = await simulateRoomCreation();
            await simulateGameStart(roomId);
            
            // Play multiple rounds
            for (let round = 1; round <= 3; round++) {
                await simulateGameplay(roomId);
                await simulateGameSettlement(roomId);
                
                if (round < 3) {
                    await simulateNextRound(roomId);
                }
            }
            
            // Verify final game completion
            const gameState = gameStateManager.getCurrentState();
            expect(gameState?.phase).toBe('FINISHED');
        });
    });

    describe('Error Scenarios', () => {
        it('should handle network errors gracefully', async () => {
            await simulateLogin();
            
            // Simulate network error during room creation
            vi.spyOn(networkManager, 'sendRequest').mockRejectedValue(new Error('Network error'));
            
            // Attempt room creation
            try {
                await simulateRoomCreation();
            } catch (error) {
                expect(error.message).toBe('Network error');
            }
            
            // Verify user remains in lobby
            expect(sceneManager.getCurrentScene()).toBe('LobbyScene');
        });

        it('should handle invalid game actions', async () => {
            await simulateLogin();
            const roomId = await simulateRoomCreation();
            await simulateGameStart(roomId);
            
            // Attempt invalid action
            const invalidAction = {
                type: 'DISCARD',
                playerId: 'test_user_1',
                tile: 'INVALID_TILE',
                timestamp: Date.now(),
                sequence: 1
            };
            
            const result = gameStateManager.validateAction(invalidAction, gameStateManager.getCurrentState()!);
            expect(result.isValid).toBe(false);
            expect(result.errors).toContain('Tile not in hand');
        });

        it('should handle room full scenario', async () => {
            await simulateLogin();
            
            // Mock room full response
            vi.spyOn(networkManager, 'sendRequest').mockResolvedValue({
                error: 'ROOM_FULL',
                message: 'Room is full'
            });
            
            try {
                await simulateRoomJoining('123456');
            } catch (error) {
                expect(error.message).toBe('Room is full');
            }
        });
    });

    describe('Performance Scenarios', () => {
        it('should handle rapid user interactions', async () => {
            await simulateLogin();
            const roomId = await simulateRoomCreation();
            await simulateGameStart(roomId);
            
            // Simulate rapid tile clicks
            const startTime = Date.now();
            const clickPromises = [];
            
            for (let i = 0; i < 10; i++) {
                clickPromises.push(simulateTileClick('1W'));
            }
            
            await Promise.allSettled(clickPromises);
            const endTime = Date.now();
            
            // Should handle rapid clicks without crashing
            expect(endTime - startTime).toBeLessThan(1000);
        });

        it('should maintain performance with large game history', async () => {
            await simulateLogin();
            
            // Simulate many state updates
            for (let i = 0; i < 100; i++) {
                const mockState = createMockGameState();
                mockState.version = i;
                gameStateManager.updateGameState(mockState);
            }
            
            // Performance should remain good
            const startTime = Date.now();
            const currentState = gameStateManager.getCurrentState();
            const endTime = Date.now();
            
            expect(endTime - startTime).toBeLessThan(10);
            expect(currentState?.version).toBe(99);
        });
    });

    describe('Accessibility Scenarios', () => {
        it('should support keyboard navigation', async () => {
            await simulateLogin();
            
            // Simulate keyboard navigation in lobby
            const keyboardEvent = { keyCode: 9 }; // Tab key
            sceneManager.handleKeyboardInput(keyboardEvent);
            
            // Should not throw errors
            expect(true).toBe(true);
        });

        it('should provide audio feedback', async () => {
            await simulateLogin();
            const roomId = await simulateRoomCreation();
            await simulateGameStart(roomId);
            
            // Mock audio system
            const playAudioSpy = vi.spyOn(mockCc.audioEngine, 'playEffect');
            
            await simulateTileClick('1W');
            
            expect(playAudioSpy).toHaveBeenCalledWith('tile_click', false);
        });
    });

    // Helper functions for simulation

    async function simulateLogin(): Promise<void> {
        // Mock WeChat login
        mockWx.login.mockResolvedValue({ code: 'test_code' });
        mockWx.getUserInfo.mockResolvedValue({
            userInfo: {
                nickName: 'Test User',
                avatarUrl: 'test_avatar.jpg'
            }
        });

        // Simulate login process
        await networkManager.connect('test_token');
        sceneManager.loadScene('LobbyScene');
    }

    async function simulateLobbyNavigation(): Promise<void> {
        expect(sceneManager.getCurrentScene()).toBe('LobbyScene');
        
        // Mock room list response
        vi.spyOn(networkManager, 'sendRequest').mockResolvedValue({
            rooms: [
                { roomId: '123456', playerCount: 2, maxPlayers: 3, status: 'WAITING' }
            ]
        });
    }

    async function simulateRoomCreation(): Promise<string> {
        const roomId = '123456';
        
        vi.spyOn(networkManager, 'sendRequest').mockResolvedValue({
            roomId,
            ownerId: 'test_user_1',
            players: [
                { userId: 'test_user_1', nickname: 'Test User 1' },
                { userId: 'test_user_2', nickname: 'Test User 2' },
                { userId: 'test_user_3', nickname: 'Test User 3' }
            ]
        });
        
        // Simulate room creation
        const response = await networkManager.sendRequest({
            type: 'REQ',
            cmd: 'CREATE_ROOM',
            data: { config: {} },
            timestamp: Date.now()
        });
        
        sceneManager.loadScene('GameScene', { roomId });
        return response.roomId;
    }

    async function simulateRoomJoining(roomId: string): Promise<void> {
        const response = await networkManager.sendRequest({
            type: 'REQ',
            cmd: 'JOIN_ROOM',
            data: { roomId },
            timestamp: Date.now()
        });
        
        if (response.error) {
            throw new Error(response.message);
        }
        
        sceneManager.loadScene('GameScene', { roomId });
    }

    async function simulateGameStart(roomId: string): Promise<void> {
        const mockGameState = createMockGameState();
        mockGameState.roomId = roomId;
        mockGameState.phase = 'PLAYING';
        
        // Mock game start message
        vi.spyOn(networkManager, 'sendRequest').mockResolvedValue({
            success: true,
            gameState: mockGameState
        });
        
        gameStateManager.updateGameState(mockGameState);
    }

    async function simulateGameplay(roomId: string): Promise<void> {
        // Simulate several turns of gameplay
        for (let turn = 0; turn < 5; turn++) {
            await simulateTileClick('1W');
            await new Promise(resolve => setTimeout(resolve, 100));
        }
    }

    async function simulateTileClick(tile: string): Promise<void> {
        const action = {
            type: 'DISCARD',
            playerId: 'test_user_1',
            tile,
            timestamp: Date.now(),
            sequence: 1
        };
        
        const validation = gameStateManager.validateAction(action, gameStateManager.getCurrentState()!);
        if (validation.isValid) {
            gameStateManager.predictAction(action);
            
            await networkManager.sendMessage({
                type: 'REQ',
                cmd: 'PLAY',
                roomId: gameStateManager.getCurrentState()?.roomId,
                data: { tile },
                timestamp: Date.now()
            });
        }
    }

    async function simulateGameSettlement(roomId: string): Promise<void> {
        const mockGameState = gameStateManager.getCurrentState();
        if (mockGameState) {
            mockGameState.phase = 'SETTLEMENT';
            gameStateManager.updateGameState(mockGameState);
        }
        
        // Simulate settlement scene
        sceneManager.loadScene('SettlementScene', { 
            roomId,
            results: [
                { playerId: 'test_user_1', score: 100, isWinner: true },
                { playerId: 'test_user_2', score: -50, isWinner: false },
                { playerId: 'test_user_3', score: -50, isWinner: false }
            ]
        });
    }

    async function simulateNextRound(roomId: string): Promise<void> {
        const mockGameState = createMockGameState();
        mockGameState.roomId = roomId;
        mockGameState.phase = 'PLAYING';
        mockGameState.roundIndex += 1;
        
        gameStateManager.updateGameState(mockGameState);
        sceneManager.loadScene('GameScene', { roomId });
    }

    async function simulateReturnToLobby(): Promise<void> {
        sceneManager.loadScene('LobbyScene');
    }

    function createMockGameState(): any {
        return {
            roomId: '123456',
            gameId: 'game_123',
            players: [
                {
                    userId: 'test_user_1',
                    nickname: 'Test User 1',
                    handTiles: ['1W', '2W', '3W', '4W', '5W', '6W', '7W', '8W', '9W', '1T', '2T', '3T', '4T'],
                    melds: [],
                    seatIndex: 0,
                    isDealer: true,
                    score: 0,
                    status: 'ACTIVE',
                    isConnected: true
                },
                {
                    userId: 'test_user_2',
                    nickname: 'Test User 2',
                    handTiles: ['1W', '2W', '3W', '4W', '5W', '6W', '7W', '8W', '9W', '1T', '2T', '3T'],
                    melds: [],
                    seatIndex: 1,
                    isDealer: false,
                    score: 0,
                    status: 'ACTIVE',
                    isConnected: true
                },
                {
                    userId: 'test_user_3',
                    nickname: 'Test User 3',
                    handTiles: ['1W', '2W', '3W', '4W', '5W', '6W', '7W', '8W', '9W', '1T', '2T', '3T'],
                    melds: [],
                    seatIndex: 2,
                    isDealer: false,
                    score: 0,
                    status: 'ACTIVE',
                    isConnected: true
                }
            ],
            currentPlayerIndex: 0,
            turnStartTime: Date.now(),
            turnDeadline: Date.now() + 15000,
            phase: 'PLAYING',
            discardPile: [],
            remainingTiles: 69,
            availableActions: ['DISCARD'],
            dealerIndex: 0,
            roundIndex: 1,
            version: 1
        };
    }
});