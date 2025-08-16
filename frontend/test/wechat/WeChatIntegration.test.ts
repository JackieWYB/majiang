import { describe, it, expect, beforeEach, vi } from 'vitest';
import { WeChatAPI } from '../../assets/scripts/wechat/WeChatAPI';
import { WeChatAuthService } from '../../assets/scripts/wechat/WeChatAuthService';
import { WeChatShareService } from '../../assets/scripts/wechat/WeChatShareService';
import { WeChatStorageService } from '../../assets/scripts/wechat/WeChatStorageService';
import { WeChatLifecycleManager } from '../../assets/scripts/wechat/WeChatLifecycleManager';

// Mock WeChat global object
const mockWx = {
    login: vi.fn(),
    getUserProfile: vi.fn(),
    getUserInfo: vi.fn(),
    getSetting: vi.fn(),
    getSystemInfo: vi.fn(),
    setStorageSync: vi.fn(),
    getStorageSync: vi.fn(),
    removeStorageSync: vi.fn(),
    showToast: vi.fn(),
    showLoading: vi.fn(),
    hideLoading: vi.fn(),
    shareAppMessage: vi.fn(),
    showShareMenu: vi.fn(),
    onAppShow: vi.fn(),
    onAppHide: vi.fn(),
    onError: vi.fn(),
    onUnhandledRejection: vi.fn(),
    onMemoryWarning: vi.fn(),
    vibrateShort: vi.fn(),
    setClipboardData: vi.fn(),
    onPageNotFound: vi.fn(),
    getSystemInfoSync: vi.fn().mockReturnValue({ platform: 'ios' })
};

// Mock HttpClient
vi.mock('../../assets/scripts/network/HttpClient', () => ({
    HttpClient: {
        getInstance: () => ({
            post: vi.fn().mockResolvedValue({
                success: true,
                data: {
                    token: 'test_jwt_token',
                    user: {
                        id: 'user123',
                        nickname: 'Test User',
                        avatar: 'https://example.com/avatar.png',
                        coins: 1000,
                        roomCards: 10
                    }
                }
            }),
            get: vi.fn().mockResolvedValue({ success: true })
        })
    }
}));

// Mock NetworkManager
vi.mock('../../assets/scripts/network/NetworkManager', () => ({
    NetworkManager: {
        getInstance: () => ({
            isConnected: vi.fn().mockReturnValue(true),
            reconnect: vi.fn().mockResolvedValue(undefined)
        })
    }
}));

// @ts-ignore
global.wx = mockWx;

describe('WeChat API Integration', () => {
    let wechatAPI: WeChatAPI;

    beforeEach(() => {
        vi.clearAllMocks();
        wechatAPI = WeChatAPI.getInstance();
    });

    describe('WeChatAPI', () => {
        it('should detect WeChat environment', () => {
            expect(wechatAPI.isWeChat()).toBe(true);
        });

        it('should perform login successfully', async () => {
            mockWx.login.mockImplementation((options: any) => {
                options.success({ code: 'test_code' });
            });

            mockWx.getSetting.mockImplementation((options: any) => {
                options.success({ authSetting: { 'scope.userInfo': true } });
            });

            mockWx.getUserInfo.mockImplementation((options: any) => {
                options.success({
                    userInfo: {
                        nickName: 'Test User',
                        avatarUrl: 'https://example.com/avatar.png'
                    }
                });
            });

            const result = await wechatAPI.login();
            
            expect(result.code).toBe('test_code');
            expect(result.userInfo).toBeDefined();
            expect(result.userInfo.nickName).toBe('Test User');
        });

        it('should handle login failure', async () => {
            mockWx.login.mockImplementation((options: any) => {
                options.fail({ errMsg: 'login failed' });
            });

            await expect(wechatAPI.login()).rejects.toThrow('WeChat login error: login failed');
        });

        it('should get system info', async () => {
            const mockSystemInfo = {
                brand: 'iPhone',
                model: 'iPhone 12',
                pixelRatio: 3,
                screenWidth: 390,
                screenHeight: 844,
                windowWidth: 390,
                windowHeight: 844,
                statusBarHeight: 47,
                platform: 'ios'
            };

            mockWx.getSystemInfo.mockImplementation((options: any) => {
                options.success(mockSystemInfo);
            });

            const result = await wechatAPI.getSystemInfo();
            expect(result).toEqual(mockSystemInfo);
        });

        it('should handle storage operations', () => {
            const testData = { key: 'value', number: 123 };
            
            wechatAPI.setStorageSync('test_key', testData);
            expect(mockWx.setStorageSync).toHaveBeenCalledWith('test_key', testData);

            mockWx.getStorageSync.mockReturnValue(testData);
            const result = wechatAPI.getStorageSync('test_key');
            expect(result).toEqual(testData);

            wechatAPI.removeStorageSync('test_key');
            expect(mockWx.removeStorageSync).toHaveBeenCalledWith('test_key');
        });
    });

    describe('WeChatAuthService', () => {
        let authService: WeChatAuthService;

        beforeEach(() => {
            authService = WeChatAuthService.getInstance();
        });

        it('should perform login flow', async () => {
            // Mock WeChat login
            mockWx.login.mockImplementation((options: any) => {
                options.success({ code: 'test_code' });
            });

            mockWx.getUserProfile.mockImplementation((options: any) => {
                options.success({
                    userInfo: {
                        nickName: 'Test User',
                        avatarUrl: 'https://example.com/avatar.png'
                    }
                });
            });

            // Mock HTTP response
            const mockHttpResponse = {
                success: true,
                data: {
                    token: 'test_jwt_token',
                    user: {
                        id: 'user123',
                        nickname: 'Test User',
                        avatar: 'https://example.com/avatar.png',
                        coins: 1000,
                        roomCards: 10
                    }
                }
            };

            // Mock HttpClient
            vi.doMock('../../assets/scripts/network/HttpClient', () => ({
                HttpClient: {
                    getInstance: () => ({
                        post: vi.fn().mockResolvedValue(mockHttpResponse)
                    })
                }
            }));

            const result = await authService.login();
            
            expect(result.success).toBe(true);
            expect(result.token).toBe('test_jwt_token');
            expect(result.userInfo).toBeDefined();
        });

        it('should check login status', () => {
            mockWx.getStorageSync.mockReturnValue('stored_token');
            expect(authService.isLoggedIn()).toBe(true);

            mockWx.getStorageSync.mockReturnValue(null);
            expect(authService.isLoggedIn()).toBe(false);
        });

        it('should logout user', () => {
            authService.logout();
            
            expect(mockWx.removeStorageSync).toHaveBeenCalledWith('auth_token');
            expect(mockWx.removeStorageSync).toHaveBeenCalledWith('user_info');
        });
    });

    describe('WeChatShareService', () => {
        let shareService: WeChatShareService;

        beforeEach(() => {
            shareService = WeChatShareService.getInstance();
        });

        it('should share game invitation', () => {
            shareService.shareGameInvitation('123456', 'TestPlayer');
            
            expect(mockWx.shareAppMessage).toHaveBeenCalledWith({
                title: 'TestPlayer邀请你来玩麻将',
                desc: '快来一起玩卡五星麻将吧！',
                path: '/pages/index/index?roomId=123456&action=join',
                imageUrl: '/images/share-game.png'
            });
        });

        it('should share game result', () => {
            const gameResult = {
                winner: 'TestPlayer',
                score: 24,
                gameType: '卡五星'
            };

            shareService.shareGameResult(gameResult);
            
            expect(mockWx.shareAppMessage).toHaveBeenCalledWith({
                title: 'TestPlayer在麻将中获胜了！',
                desc: '得分：24分 | 卡五星',
                path: '/pages/index/index?action=play',
                imageUrl: '/images/share-result.png'
            });
        });

        it('should copy text to clipboard', async () => {
            mockWx.setClipboardData.mockImplementation((options: any) => {
                options.success();
            });

            await expect(shareService.copyToClipboard('test text')).resolves.toBeUndefined();
            expect(mockWx.setClipboardData).toHaveBeenCalledWith({
                data: 'test text',
                success: expect.any(Function),
                fail: expect.any(Function)
            });
        });
    });

    describe('WeChatStorageService', () => {
        let storageService: WeChatStorageService;

        beforeEach(() => {
            storageService = WeChatStorageService.getInstance();
        });

        it('should manage user preferences', () => {
            const preferences = {
                soundEnabled: false,
                musicEnabled: true,
                vibrationEnabled: false,
                language: 'en_US' as const
            };

            storageService.saveUserPreferences(preferences);
            
            const saved = storageService.getUserPreferences();
            expect(saved.soundEnabled).toBe(false);
            expect(saved.musicEnabled).toBe(true);
            expect(saved.language).toBe('en_US');
        });

        it('should manage recent rooms', () => {
            storageService.addRecentRoom('123456');
            storageService.addRecentRoom('789012');
            storageService.addRecentRoom('123456'); // Duplicate

            const recent = storageService.getRecentRooms();
            expect(recent).toEqual(['123456', '789012']);
        });

        it('should manage game history', () => {
            const gameRecord = {
                gameId: 'game123',
                roomId: '123456',
                players: ['player1', 'player2', 'player3'],
                winner: 'player1',
                score: 24,
                timestamp: Date.now()
            };

            storageService.addGameHistory(gameRecord);
            
            const history = storageService.getGameHistory();
            expect(history).toHaveLength(1);
            expect(history[0]).toEqual(gameRecord);
        });

        it('should export and import data', () => {
            // Add some data
            storageService.saveUserPreferences({ soundEnabled: false });
            storageService.addRecentRoom('123456');

            // Export data
            const exported = storageService.exportData();
            expect(exported).toBeTruthy();

            // Clear and import
            storageService.clearAllData();
            const imported = storageService.importData(exported);
            expect(imported).toBe(true);

            // Verify data restored
            const preferences = storageService.getUserPreferences();
            expect(preferences.soundEnabled).toBe(false);
        });
    });

    describe('WeChatLifecycleManager', () => {
        let lifecycleManager: WeChatLifecycleManager;

        beforeEach(() => {
            lifecycleManager = WeChatLifecycleManager.getInstance();
        });

        it('should initialize lifecycle handlers', () => {
            const callbacks = {
                onShow: vi.fn(),
                onHide: vi.fn(),
                onError: vi.fn()
            };

            lifecycleManager.initialize(callbacks);

            expect(mockWx.onAppShow).toHaveBeenCalled();
            expect(mockWx.onAppHide).toHaveBeenCalled();
            expect(mockWx.onError).toHaveBeenCalled();
        });

        it('should track app visibility', () => {
            expect(lifecycleManager.isAppVisible()).toBe(true);
        });

        it('should handle memory warnings', () => {
            const callback = vi.fn();
            lifecycleManager.initialize({ onMemoryWarning: callback });

            expect(mockWx.onMemoryWarning).toHaveBeenCalled();
        });
    });
});

describe('WeChat Integration - Non-WeChat Environment', () => {
    beforeEach(() => {
        // @ts-ignore
        delete global.wx;
        vi.clearAllMocks();
    });

    it('should work in non-WeChat environment', () => {
        const wechatAPI = WeChatAPI.getInstance();
        expect(wechatAPI.isWeChat()).toBe(false);
    });

    it('should provide mock data in non-WeChat environment', async () => {
        const wechatAPI = WeChatAPI.getInstance();
        const result = await wechatAPI.login();
        
        expect(result.code).toContain('mock_code_');
        expect(result.userInfo).toBeDefined();
        expect(result.userInfo.nickName).toBe('Test User');
    });

    it('should use localStorage fallback', () => {
        const wechatAPI = WeChatAPI.getInstance();
        const testData = { test: 'data' };
        
        wechatAPI.setStorageSync('test_key', testData);
        const result = wechatAPI.getStorageSync('test_key');
        
        expect(result).toEqual(testData);
    });
});