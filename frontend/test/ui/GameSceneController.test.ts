import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest';
import { GameSceneController } from '../../assets/scripts/scenes/GameSceneController';
import { GameStateManager, GameState, PlayerState, GamePhase, PlayerStatus, ActionType } from '../../assets/scripts/managers/GameStateManager';
import { NetworkManager } from '../../assets/scripts/network/NetworkManager';
import { mockCc } from '../mocks/cocos';

// Mock Cocos Creator
global.cc = mockCc;

describe('GameSceneController', () => {
    let gameSceneController: GameSceneController;
    let mockGameStateManager: GameStateManager;
    let mockNetworkManager: NetworkManager;
    let mockGameState: GameState;

    beforeEach(() => {
        // Reset mocks
        vi.clearAllMocks();
        
        // Create mock instances
        mockGameStateManager = {
            getCurrentState: vi.fn(),
            getLocalPlayer: vi.fn(),
            isLocalPlayerTurn: vi.fn(),
            getAvailableActions: vi.fn(),
            getTurnTimeRemaining: vi.fn(),
            predictAction: vi.fn(),
            addStateChangeListener: vi.fn(),
            removeStateChangeListener: vi.fn(),
            validateAction: vi.fn(),
            getPlayer: vi.fn()
        } as any;

        mockNetworkManager = {
            sendMessage: vi.fn(),
            isConnected: vi.fn(() => true),
            onMessage: vi.fn(),
            removeMessageHandler: vi.fn()
        } as any;

        // Create mock game state
        mockGameState = {
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

        // Create controller instance
        gameSceneController = new GameSceneController();
        
        // Inject dependencies
        (gameSceneController as any).gameStateManager = mockGameStateManager;
        (gameSceneController as any).networkManager = mockNetworkManager;
    });

    afterEach(() => {
        if (gameSceneController) {
            gameSceneController.onDestroy();
        }
    });

    describe('Initialization', () => {
        it('should initialize scene correctly', () => {
            mockGameStateManager.getCurrentState.mockReturnValue(mockGameState);
            mockGameStateManager.getLocalPlayer.mockReturnValue(mockGameState.players[0]);

            gameSceneController.onLoad();

            expect(mockGameStateManager.addStateChangeListener).toHaveBeenCalled();
            expect(mockNetworkManager.onMessage).toHaveBeenCalledWith('GAME_SNAPSHOT', expect.any(Function));
            expect(mockNetworkManager.onMessage).toHaveBeenCalledWith('PLAYER_ACTION', expect.any(Function));
        });

        it('should handle missing game state', () => {
            mockGameStateManager.getCurrentState.mockReturnValue(null);

            expect(() => {
                gameSceneController.onLoad();
            }).not.toThrow();
        });

        it('should setup UI components', () => {
            mockGameStateManager.getCurrentState.mockReturnValue(mockGameState);
            mockGameStateManager.getLocalPlayer.mockReturnValue(mockGameState.players[0]);

            gameSceneController.onLoad();
            gameSceneController.start();

            // Verify UI setup calls
            expect(mockCc.find).toHaveBeenCalledWith('HandTiles');
            expect(mockCc.find).toHaveBeenCalledWith('ActionButtons');
            expect(mockCc.find).toHaveBeenCalledWith('DiscardPile');
        });
    });

    describe('Game State Updates', () => {
        beforeEach(() => {
            mockGameStateManager.getCurrentState.mockReturnValue(mockGameState);
            mockGameStateManager.getLocalPlayer.mockReturnValue(mockGameState.players[0]);
            gameSceneController.onLoad();
        });

        it('should update UI when game state changes', () => {
            const updateUISpy = vi.spyOn(gameSceneController as any, 'updateUI');
            
            // Simulate state change
            const stateChangeCallback = mockGameStateManager.addStateChangeListener.mock.calls[0][0];
            stateChangeCallback(mockGameState);

            expect(updateUISpy).toHaveBeenCalledWith(mockGameState);
        });

        it('should render hand tiles correctly', () => {
            const renderHandTilesSpy = vi.spyOn(gameSceneController as any, 'renderHandTiles');
            
            (gameSceneController as any).updateUI(mockGameState);

            expect(renderHandTilesSpy).toHaveBeenCalledWith(mockGameState.players[0].handTiles);
        });

        it('should update action buttons based on available actions', () => {
            mockGameStateManager.getAvailableActions.mockReturnValue([ActionType.DISCARD, ActionType.PENG]);
            const updateActionButtonsSpy = vi.spyOn(gameSceneController as any, 'updateActionButtons');
            
            (gameSceneController as any).updateUI(mockGameState);

            expect(updateActionButtonsSpy).toHaveBeenCalledWith([ActionType.DISCARD, ActionType.PENG]);
        });

        it('should update turn timer', () => {
            mockGameStateManager.getTurnTimeRemaining.mockReturnValue(10000);
            const updateTurnTimerSpy = vi.spyOn(gameSceneController as any, 'updateTurnTimer');
            
            (gameSceneController as any).updateUI(mockGameState);

            expect(updateTurnTimerSpy).toHaveBeenCalledWith(10000);
        });

        it('should render other players correctly', () => {
            const renderOtherPlayersSpy = vi.spyOn(gameSceneController as any, 'renderOtherPlayers');
            
            (gameSceneController as any).updateUI(mockGameState);

            expect(renderOtherPlayersSpy).toHaveBeenCalledWith([
                mockGameState.players[1],
                mockGameState.players[2]
            ]);
        });
    });

    describe('Player Actions', () => {
        beforeEach(() => {
            mockGameStateManager.getCurrentState.mockReturnValue(mockGameState);
            mockGameStateManager.getLocalPlayer.mockReturnValue(mockGameState.players[0]);
            mockGameStateManager.isLocalPlayerTurn.mockReturnValue(true);
            gameSceneController.onLoad();
        });

        it('should handle tile discard', () => {
            const tileToDiscard = '1W';
            mockGameStateManager.validateAction.mockReturnValue({ isValid: true, errors: [] });
            mockGameStateManager.predictAction.mockReturnValue({ isValid: true });

            (gameSceneController as any).onTileClick(tileToDiscard);

            expect(mockGameStateManager.predictAction).toHaveBeenCalledWith({
                type: ActionType.DISCARD,
                playerId: 'user1',
                tile: tileToDiscard,
                timestamp: expect.any(Number),
                sequence: expect.any(Number)
            });

            expect(mockNetworkManager.sendMessage).toHaveBeenCalledWith({
                type: 'REQ',
                cmd: 'PLAY',
                roomId: '123456',
                data: { tile: tileToDiscard },
                timestamp: expect.any(Number)
            });
        });

        it('should prevent action when not player turn', () => {
            mockGameStateManager.isLocalPlayerTurn.mockReturnValue(false);

            (gameSceneController as any).onTileClick('1W');

            expect(mockNetworkManager.sendMessage).not.toHaveBeenCalled();
        });

        it('should handle peng action', () => {
            mockGameStateManager.getAvailableActions.mockReturnValue([ActionType.PENG]);
            mockGameStateManager.validateAction.mockReturnValue({ isValid: true, errors: [] });

            (gameSceneController as any).onPengButtonClick();

            expect(mockNetworkManager.sendMessage).toHaveBeenCalledWith({
                type: 'REQ',
                cmd: 'PENG',
                roomId: '123456',
                data: expect.objectContaining({
                    tile: expect.any(String),
                    fromPlayer: expect.any(String)
                }),
                timestamp: expect.any(Number)
            });
        });

        it('should handle gang action', () => {
            mockGameStateManager.getAvailableActions.mockReturnValue([ActionType.GANG]);
            mockGameStateManager.validateAction.mockReturnValue({ isValid: true, errors: [] });

            (gameSceneController as any).onGangButtonClick();

            expect(mockNetworkManager.sendMessage).toHaveBeenCalledWith({
                type: 'REQ',
                cmd: 'GANG',
                roomId: '123456',
                data: expect.objectContaining({
                    type: expect.any(String),
                    tile: expect.any(String)
                }),
                timestamp: expect.any(Number)
            });
        });

        it('should handle hu action', () => {
            mockGameStateManager.getAvailableActions.mockReturnValue([ActionType.HU]);
            mockGameStateManager.validateAction.mockReturnValue({ isValid: true, errors: [] });

            (gameSceneController as any).onHuButtonClick();

            expect(mockNetworkManager.sendMessage).toHaveBeenCalledWith({
                type: 'REQ',
                cmd: 'HU',
                roomId: '123456',
                data: expect.objectContaining({
                    tile: expect.any(String),
                    selfDraw: expect.any(Boolean)
                }),
                timestamp: expect.any(Number)
            });
        });

        it('should handle pass action', () => {
            mockGameStateManager.getAvailableActions.mockReturnValue([ActionType.PASS]);

            (gameSceneController as any).onPassButtonClick();

            expect(mockNetworkManager.sendMessage).toHaveBeenCalledWith({
                type: 'REQ',
                cmd: 'PASS',
                roomId: '123456',
                data: {},
                timestamp: expect.any(Number)
            });
        });

        it('should validate actions before sending', () => {
            mockGameStateManager.validateAction.mockReturnValue({ 
                isValid: false, 
                errors: ['Invalid action'] 
            });

            (gameSceneController as any).onTileClick('1W');

            expect(mockNetworkManager.sendMessage).not.toHaveBeenCalled();
        });
    });

    describe('Network Message Handling', () => {
        beforeEach(() => {
            mockGameStateManager.getCurrentState.mockReturnValue(mockGameState);
            mockGameStateManager.getLocalPlayer.mockReturnValue(mockGameState.players[0]);
            gameSceneController.onLoad();
        });

        it('should handle game snapshot message', () => {
            const gameSnapshotHandler = mockNetworkManager.onMessage.mock.calls
                .find(call => call[0] === 'GAME_SNAPSHOT')[1];

            const snapshotMessage = {
                type: 'EVENT',
                cmd: 'GAME_SNAPSHOT',
                data: mockGameState,
                timestamp: Date.now()
            };

            gameSnapshotHandler(snapshotMessage);

            expect(mockGameStateManager.getCurrentState).toHaveBeenCalled();
        });

        it('should handle player action message', () => {
            const playerActionHandler = mockNetworkManager.onMessage.mock.calls
                .find(call => call[0] === 'PLAYER_ACTION')[1];

            const actionMessage = {
                type: 'EVENT',
                cmd: 'PLAYER_ACTION',
                data: {
                    playerId: 'user2',
                    action: 'DISCARD',
                    tile: '5W'
                },
                timestamp: Date.now()
            };

            const playAnimationSpy = vi.spyOn(gameSceneController as any, 'playActionAnimation');
            playerActionHandler(actionMessage);

            expect(playAnimationSpy).toHaveBeenCalledWith('user2', 'DISCARD', '5W');
        });

        it('should handle turn change message', () => {
            const turnChangeHandler = mockNetworkManager.onMessage.mock.calls
                .find(call => call[0] === 'TURN_CHANGE')[1];

            const turnMessage = {
                type: 'EVENT',
                cmd: 'TURN_CHANGE',
                data: {
                    currentPlayerIndex: 1,
                    turnDeadline: Date.now() + 15000
                },
                timestamp: Date.now()
            };

            const updateTurnIndicatorSpy = vi.spyOn(gameSceneController as any, 'updateTurnIndicator');
            turnChangeHandler(turnMessage);

            expect(updateTurnIndicatorSpy).toHaveBeenCalledWith(1);
        });
    });

    describe('UI Animations', () => {
        beforeEach(() => {
            mockGameStateManager.getCurrentState.mockReturnValue(mockGameState);
            mockGameStateManager.getLocalPlayer.mockReturnValue(mockGameState.players[0]);
            gameSceneController.onLoad();
        });

        it('should play discard animation', () => {
            const mockTileNode = { runAction: vi.fn() };
            mockCc.find.mockReturnValue(mockTileNode);

            (gameSceneController as any).playDiscardAnimation('5W');

            expect(mockTileNode.runAction).toHaveBeenCalled();
        });

        it('should play peng animation', () => {
            const mockMeldArea = { addChild: vi.fn() };
            mockCc.find.mockReturnValue(mockMeldArea);

            (gameSceneController as any).playPengAnimation('user2', '5W');

            expect(mockMeldArea.addChild).toHaveBeenCalled();
        });

        it('should play winning animation', () => {
            const mockWinEffect = { active: false };
            mockCc.find.mockReturnValue(mockWinEffect);

            (gameSceneController as any).playWinAnimation('user1');

            expect(mockWinEffect.active).toBe(true);
        });

        it('should update turn indicator', () => {
            const mockIndicator = { position: { x: 0, y: 0 } };
            mockCc.find.mockReturnValue(mockIndicator);

            (gameSceneController as any).updateTurnIndicator(1);

            expect(mockIndicator.position).not.toEqual({ x: 0, y: 0 });
        });
    });

    describe('Error Handling', () => {
        beforeEach(() => {
            mockGameStateManager.getCurrentState.mockReturnValue(mockGameState);
            mockGameStateManager.getLocalPlayer.mockReturnValue(mockGameState.players[0]);
            gameSceneController.onLoad();
        });

        it('should handle network errors gracefully', () => {
            mockNetworkManager.sendMessage.mockImplementation(() => {
                throw new Error('Network error');
            });

            const showErrorSpy = vi.spyOn(gameSceneController as any, 'showError');

            expect(() => {
                (gameSceneController as any).onTileClick('1W');
            }).not.toThrow();

            expect(showErrorSpy).toHaveBeenCalledWith('Network error occurred');
        });

        it('should handle invalid game state', () => {
            mockGameStateManager.getCurrentState.mockReturnValue(null);

            expect(() => {
                (gameSceneController as any).updateUI(null);
            }).not.toThrow();
        });

        it('should handle missing UI components', () => {
            mockCc.find.mockReturnValue(null);

            expect(() => {
                (gameSceneController as any).renderHandTiles(['1W', '2W']);
            }).not.toThrow();
        });
    });

    describe('Performance', () => {
        beforeEach(() => {
            mockGameStateManager.getCurrentState.mockReturnValue(mockGameState);
            mockGameStateManager.getLocalPlayer.mockReturnValue(mockGameState.players[0]);
            gameSceneController.onLoad();
        });

        it('should throttle UI updates', () => {
            const updateUISpy = vi.spyOn(gameSceneController as any, 'updateUI');
            
            // Trigger multiple rapid updates
            for (let i = 0; i < 10; i++) {
                const stateChangeCallback = mockGameStateManager.addStateChangeListener.mock.calls[0][0];
                stateChangeCallback(mockGameState);
            }

            // Should be throttled to fewer calls
            expect(updateUISpy.mock.calls.length).toBeLessThan(10);
        });

        it('should cleanup resources on destroy', () => {
            gameSceneController.onDestroy();

            expect(mockGameStateManager.removeStateChangeListener).toHaveBeenCalled();
            expect(mockNetworkManager.removeMessageHandler).toHaveBeenCalledWith('GAME_SNAPSHOT');
            expect(mockNetworkManager.removeMessageHandler).toHaveBeenCalledWith('PLAYER_ACTION');
        });

        it('should handle rapid tile clicks', () => {
            const sendMessageSpy = mockNetworkManager.sendMessage;
            mockGameStateManager.validateAction.mockReturnValue({ isValid: true, errors: [] });
            mockGameStateManager.predictAction.mockReturnValue({ isValid: true });

            // Rapid clicks should be debounced
            for (let i = 0; i < 5; i++) {
                (gameSceneController as any).onTileClick('1W');
            }

            expect(sendMessageSpy.mock.calls.length).toBeLessThan(5);
        });
    });

    describe('Accessibility', () => {
        beforeEach(() => {
            mockGameStateManager.getCurrentState.mockReturnValue(mockGameState);
            mockGameStateManager.getLocalPlayer.mockReturnValue(mockGameState.players[0]);
            gameSceneController.onLoad();
        });

        it('should provide audio feedback for actions', () => {
            const playAudioSpy = vi.spyOn(gameSceneController as any, 'playAudio');
            mockGameStateManager.validateAction.mockReturnValue({ isValid: true, errors: [] });
            mockGameStateManager.predictAction.mockReturnValue({ isValid: true });

            (gameSceneController as any).onTileClick('1W');

            expect(playAudioSpy).toHaveBeenCalledWith('tile_click');
        });

        it('should provide visual feedback for invalid actions', () => {
            const showFeedbackSpy = vi.spyOn(gameSceneController as any, 'showVisualFeedback');
            mockGameStateManager.validateAction.mockReturnValue({ 
                isValid: false, 
                errors: ['Invalid action'] 
            });

            (gameSceneController as any).onTileClick('1W');

            expect(showFeedbackSpy).toHaveBeenCalledWith('error', 'Invalid action');
        });

        it('should support keyboard navigation', () => {
            const keyboardHandler = vi.spyOn(gameSceneController as any, 'onKeyDown');
            
            // Simulate keyboard event
            const mockEvent = { keyCode: 32 }; // Space key
            (gameSceneController as any).onKeyDown(mockEvent);

            expect(keyboardHandler).toHaveBeenCalledWith(mockEvent);
        });
    });
});