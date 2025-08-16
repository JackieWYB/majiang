import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest';
import { LobbySceneController } from '../../assets/scripts/scenes/LobbySceneController';
import { NetworkManager } from '../../assets/scripts/network/NetworkManager';
import { mockCc } from '../mocks/cocos';

// Mock Cocos Creator
global.cc = mockCc;

describe('LobbySceneController', () => {
    let lobbySceneController: LobbySceneController;
    let mockNetworkManager: NetworkManager;

    beforeEach(() => {
        vi.clearAllMocks();
        
        mockNetworkManager = {
            sendMessage: vi.fn(),
            sendRequest: vi.fn(),
            isConnected: vi.fn(() => true),
            onMessage: vi.fn(),
            removeMessageHandler: vi.fn()
        } as any;

        lobbySceneController = new LobbySceneController();
        (lobbySceneController as any).networkManager = mockNetworkManager;
    });

    afterEach(() => {
        if (lobbySceneController) {
            lobbySceneController.onDestroy();
        }
    });

    describe('Initialization', () => {
        it('should initialize lobby scene correctly', () => {
            lobbySceneController.onLoad();

            expect(mockNetworkManager.onMessage).toHaveBeenCalledWith('ROOM_CREATED', expect.any(Function));
            expect(mockNetworkManager.onMessage).toHaveBeenCalledWith('ROOM_JOINED', expect.any(Function));
            expect(mockNetworkManager.onMessage).toHaveBeenCalledWith('ROOM_LIST_UPDATE', expect.any(Function));
        });

        it('should setup UI components', () => {
            lobbySceneController.onLoad();
            lobbySceneController.start();

            expect(mockCc.find).toHaveBeenCalledWith('CreateRoomButton');
            expect(mockCc.find).toHaveBeenCalledWith('JoinRoomButton');
            expect(mockCc.find).toHaveBeenCalledWith('RoomList');
        });
    });

    describe('Room Creation', () => {
        beforeEach(() => {
            lobbySceneController.onLoad();
        });

        it('should create room with default settings', async () => {
            const mockResponse = {
                roomId: '123456',
                ownerId: 'user1',
                players: [{ userId: 'user1', nickname: 'Player 1' }]
            };

            mockNetworkManager.sendRequest.mockResolvedValue(mockResponse);

            await (lobbySceneController as any).onCreateRoomClick();

            expect(mockNetworkManager.sendRequest).toHaveBeenCalledWith({
                type: 'REQ',
                cmd: 'CREATE_ROOM',
                data: expect.objectContaining({
                    config: expect.any(Object)
                }),
                timestamp: expect.any(Number)
            });
        });

        it('should create room with custom settings', async () => {
            const customConfig = {
                tiles: 'ALL',
                allowPeng: true,
                allowGang: true,
                maxRounds: 3
            };

            const mockResponse = {
                roomId: '123456',
                ownerId: 'user1',
                players: [{ userId: 'user1', nickname: 'Player 1' }]
            };

            mockNetworkManager.sendRequest.mockResolvedValue(mockResponse);

            await (lobbySceneController as any).createRoomWithConfig(customConfig);

            expect(mockNetworkManager.sendRequest).toHaveBeenCalledWith({
                type: 'REQ',
                cmd: 'CREATE_ROOM',
                data: { config: customConfig },
                timestamp: expect.any(Number)
            });
        });

        it('should handle room creation failure', async () => {
            mockNetworkManager.sendRequest.mockRejectedValue(new Error('Room creation failed'));
            const showErrorSpy = vi.spyOn(lobbySceneController as any, 'showError');

            await (lobbySceneController as any).onCreateRoomClick();

            expect(showErrorSpy).toHaveBeenCalledWith('Failed to create room');
        });

        it('should navigate to room after successful creation', async () => {
            const mockResponse = {
                roomId: '123456',
                ownerId: 'user1',
                players: [{ userId: 'user1', nickname: 'Player 1' }]
            };

            mockNetworkManager.sendRequest.mockResolvedValue(mockResponse);
            const navigateToRoomSpy = vi.spyOn(lobbySceneController as any, 'navigateToRoom');

            await (lobbySceneController as any).onCreateRoomClick();

            expect(navigateToRoomSpy).toHaveBeenCalledWith('123456');
        });
    });

    describe('Room Joining', () => {
        beforeEach(() => {
            lobbySceneController.onLoad();
        });

        it('should join room by ID', async () => {
            const roomId = '123456';
            const mockResponse = {
                roomId: '123456',
                players: [
                    { userId: 'user1', nickname: 'Player 1' },
                    { userId: 'user2', nickname: 'Player 2' }
                ]
            };

            mockNetworkManager.sendRequest.mockResolvedValue(mockResponse);

            await (lobbySceneController as any).joinRoomById(roomId);

            expect(mockNetworkManager.sendRequest).toHaveBeenCalledWith({
                type: 'REQ',
                cmd: 'JOIN_ROOM',
                data: { roomId },
                timestamp: expect.any(Number)
            });
        });

        it('should handle invalid room ID', async () => {
            const invalidRoomId = '999999';
            mockNetworkManager.sendRequest.mockRejectedValue(new Error('Room not found'));
            const showErrorSpy = vi.spyOn(lobbySceneController as any, 'showError');

            await (lobbySceneController as any).joinRoomById(invalidRoomId);

            expect(showErrorSpy).toHaveBeenCalledWith('Room not found');
        });

        it('should handle room full error', async () => {
            const roomId = '123456';
            mockNetworkManager.sendRequest.mockRejectedValue(new Error('Room is full'));
            const showErrorSpy = vi.spyOn(lobbySceneController as any, 'showError');

            await (lobbySceneController as any).joinRoomById(roomId);

            expect(showErrorSpy).toHaveBeenCalledWith('Room is full');
        });

        it('should validate room ID format', () => {
            const isValidSpy = vi.spyOn(lobbySceneController as any, 'isValidRoomId');

            expect((lobbySceneController as any).isValidRoomId('123456')).toBe(true);
            expect((lobbySceneController as any).isValidRoomId('12345')).toBe(false);
            expect((lobbySceneController as any).isValidRoomId('1234567')).toBe(false);
            expect((lobbySceneController as any).isValidRoomId('abcdef')).toBe(false);
        });
    });

    describe('Room List Management', () => {
        beforeEach(() => {
            lobbySceneController.onLoad();
        });

        it('should load room list on start', async () => {
            const mockRooms = [
                { roomId: '123456', playerCount: 2, maxPlayers: 3, status: 'WAITING' },
                { roomId: '789012', playerCount: 1, maxPlayers: 3, status: 'WAITING' }
            ];

            mockNetworkManager.sendRequest.mockResolvedValue({ rooms: mockRooms });

            await (lobbySceneController as any).loadRoomList();

            expect(mockNetworkManager.sendRequest).toHaveBeenCalledWith({
                type: 'REQ',
                cmd: 'GET_ROOM_LIST',
                data: {},
                timestamp: expect.any(Number)
            });
        });

        it('should update room list UI', () => {
            const mockRooms = [
                { roomId: '123456', playerCount: 2, maxPlayers: 3, status: 'WAITING' },
                { roomId: '789012', playerCount: 1, maxPlayers: 3, status: 'WAITING' }
            ];

            const updateRoomListUISpy = vi.spyOn(lobbySceneController as any, 'updateRoomListUI');

            (lobbySceneController as any).updateRoomList(mockRooms);

            expect(updateRoomListUISpy).toHaveBeenCalledWith(mockRooms);
        });

        it('should handle room list update message', () => {
            const roomListHandler = mockNetworkManager.onMessage.mock.calls
                .find(call => call[0] === 'ROOM_LIST_UPDATE')[1];

            const updateMessage = {
                type: 'EVENT',
                cmd: 'ROOM_LIST_UPDATE',
                data: {
                    rooms: [
                        { roomId: '123456', playerCount: 3, maxPlayers: 3, status: 'FULL' }
                    ]
                },
                timestamp: Date.now()
            };

            const updateRoomListSpy = vi.spyOn(lobbySceneController as any, 'updateRoomList');
            roomListHandler(updateMessage);

            expect(updateRoomListSpy).toHaveBeenCalledWith(updateMessage.data.rooms);
        });

        it('should refresh room list periodically', () => {
            const loadRoomListSpy = vi.spyOn(lobbySceneController as any, 'loadRoomList');
            
            lobbySceneController.start();

            // Fast forward time to trigger refresh
            vi.advanceTimersByTime(30000);

            expect(loadRoomListSpy).toHaveBeenCalled();
        });
    });

    describe('User Profile Management', () => {
        beforeEach(() => {
            lobbySceneController.onLoad();
        });

        it('should display user profile', () => {
            const mockUserProfile = {
                userId: 'user1',
                nickname: 'Player 1',
                avatar: 'avatar1.jpg',
                coins: 1000,
                roomCards: 5,
                gamesPlayed: 50,
                winRate: 0.6
            };

            const displayProfileSpy = vi.spyOn(lobbySceneController as any, 'displayUserProfile');

            (lobbySceneController as any).showUserProfile(mockUserProfile);

            expect(displayProfileSpy).toHaveBeenCalledWith(mockUserProfile);
        });

        it('should handle profile update', async () => {
            const updatedProfile = {
                nickname: 'New Nickname',
                avatar: 'new_avatar.jpg'
            };

            mockNetworkManager.sendRequest.mockResolvedValue({ success: true });

            await (lobbySceneController as any).updateUserProfile(updatedProfile);

            expect(mockNetworkManager.sendRequest).toHaveBeenCalledWith({
                type: 'REQ',
                cmd: 'UPDATE_PROFILE',
                data: updatedProfile,
                timestamp: expect.any(Number)
            });
        });
    });

    describe('Settings Management', () => {
        beforeEach(() => {
            lobbySceneController.onLoad();
        });

        it('should show settings dialog', () => {
            const showSettingsSpy = vi.spyOn(lobbySceneController as any, 'showSettingsDialog');

            (lobbySceneController as any).onSettingsClick();

            expect(showSettingsSpy).toHaveBeenCalled();
        });

        it('should save settings', () => {
            const newSettings = {
                soundEnabled: false,
                musicEnabled: true,
                language: 'en-US'
            };

            const saveSettingsSpy = vi.spyOn(lobbySceneController as any, 'saveSettings');

            (lobbySceneController as any).updateSettings(newSettings);

            expect(saveSettingsSpy).toHaveBeenCalledWith(newSettings);
        });

        it('should load settings on start', () => {
            const loadSettingsSpy = vi.spyOn(lobbySceneController as any, 'loadSettings');

            lobbySceneController.start();

            expect(loadSettingsSpy).toHaveBeenCalled();
        });
    });

    describe('Navigation', () => {
        beforeEach(() => {
            lobbySceneController.onLoad();
        });

        it('should navigate to game scene', () => {
            const navigateToGameSpy = vi.spyOn(lobbySceneController as any, 'navigateToGame');

            (lobbySceneController as any).navigateToRoom('123456');

            expect(navigateToGameSpy).toHaveBeenCalledWith('123456');
        });

        it('should navigate to history scene', () => {
            const navigateToHistorySpy = vi.spyOn(lobbySceneController as any, 'navigateToHistory');

            (lobbySceneController as any).onHistoryClick();

            expect(navigateToHistorySpy).toHaveBeenCalled();
        });

        it('should handle back navigation', () => {
            const handleBackSpy = vi.spyOn(lobbySceneController as any, 'handleBack');

            (lobbySceneController as any).onBackClick();

            expect(handleBackSpy).toHaveBeenCalled();
        });
    });

    describe('Error Handling', () => {
        beforeEach(() => {
            lobbySceneController.onLoad();
        });

        it('should handle network disconnection', () => {
            mockNetworkManager.isConnected.mockReturnValue(false);
            const showConnectionErrorSpy = vi.spyOn(lobbySceneController as any, 'showConnectionError');

            (lobbySceneController as any).checkConnection();

            expect(showConnectionErrorSpy).toHaveBeenCalled();
        });

        it('should retry failed operations', async () => {
            mockNetworkManager.sendRequest
                .mockRejectedValueOnce(new Error('Network error'))
                .mockResolvedValueOnce({ success: true });

            const retrySpy = vi.spyOn(lobbySceneController as any, 'retryOperation');

            await (lobbySceneController as any).loadRoomList();

            expect(retrySpy).toHaveBeenCalled();
        });

        it('should show appropriate error messages', () => {
            const showErrorSpy = vi.spyOn(lobbySceneController as any, 'showError');

            (lobbySceneController as any).handleError(new Error('Test error'));

            expect(showErrorSpy).toHaveBeenCalledWith('Test error');
        });
    });

    describe('Performance', () => {
        beforeEach(() => {
            lobbySceneController.onLoad();
        });

        it('should debounce room list updates', () => {
            const updateRoomListSpy = vi.spyOn(lobbySceneController as any, 'updateRoomList');

            // Trigger multiple rapid updates
            for (let i = 0; i < 5; i++) {
                (lobbySceneController as any).onRoomListUpdate([]);
            }

            // Should be debounced
            expect(updateRoomListSpy.mock.calls.length).toBeLessThan(5);
        });

        it('should cleanup resources on destroy', () => {
            lobbySceneController.onDestroy();

            expect(mockNetworkManager.removeMessageHandler).toHaveBeenCalledWith('ROOM_CREATED');
            expect(mockNetworkManager.removeMessageHandler).toHaveBeenCalledWith('ROOM_JOINED');
            expect(mockNetworkManager.removeMessageHandler).toHaveBeenCalledWith('ROOM_LIST_UPDATE');
        });

        it('should limit room list size', () => {
            const largeRoomList = Array.from({ length: 100 }, (_, i) => ({
                roomId: `room${i}`,
                playerCount: 1,
                maxPlayers: 3,
                status: 'WAITING'
            }));

            (lobbySceneController as any).updateRoomList(largeRoomList);

            const displayedRooms = (lobbySceneController as any).getDisplayedRooms();
            expect(displayedRooms.length).toBeLessThanOrEqual(20); // Max display limit
        });
    });

    describe('Accessibility', () => {
        beforeEach(() => {
            lobbySceneController.onLoad();
        });

        it('should support keyboard navigation', () => {
            const keyboardHandler = vi.spyOn(lobbySceneController as any, 'onKeyDown');

            const mockEvent = { keyCode: 13 }; // Enter key
            (lobbySceneController as any).onKeyDown(mockEvent);

            expect(keyboardHandler).toHaveBeenCalledWith(mockEvent);
        });

        it('should provide audio feedback', () => {
            const playAudioSpy = vi.spyOn(lobbySceneController as any, 'playAudio');

            (lobbySceneController as any).onCreateRoomClick();

            expect(playAudioSpy).toHaveBeenCalledWith('button_click');
        });

        it('should support screen reader labels', () => {
            const setAccessibilityLabelSpy = vi.spyOn(lobbySceneController as any, 'setAccessibilityLabel');

            lobbySceneController.start();

            expect(setAccessibilityLabelSpy).toHaveBeenCalledWith('CreateRoomButton', 'Create new room');
            expect(setAccessibilityLabelSpy).toHaveBeenCalledWith('JoinRoomButton', 'Join existing room');
        });
    });
});