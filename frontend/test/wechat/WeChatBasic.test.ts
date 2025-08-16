import { describe, it, expect, beforeEach, vi } from 'vitest';

// Mock WeChat global object
const mockWx = {
    getSystemInfoSync: vi.fn().mockReturnValue({ platform: 'ios' }),
    login: vi.fn(),
    getUserProfile: vi.fn(),
    setStorageSync: vi.fn(),
    getStorageSync: vi.fn(),
    removeStorageSync: vi.fn(),
    showToast: vi.fn(),
    shareAppMessage: vi.fn()
};

// @ts-ignore
global.wx = mockWx;

describe('WeChat Basic Integration', () => {
    beforeEach(() => {
        vi.clearAllMocks();
    });

    it('should detect WeChat environment when wx is available', async () => {
        const { WeChatAPI } = await import('../../assets/scripts/wechat/WeChatAPI');
        const wechatAPI = WeChatAPI.getInstance();
        expect(wechatAPI.isWeChat()).toBe(true);
    });

    it('should handle storage operations', async () => {
        const { WeChatAPI } = await import('../../assets/scripts/wechat/WeChatAPI');
        const wechatAPI = WeChatAPI.getInstance();
        
        const testData = { key: 'value', number: 123 };
        
        wechatAPI.setStorageSync('test_key', testData);
        expect(mockWx.setStorageSync).toHaveBeenCalledWith('test_key', testData);

        mockWx.getStorageSync.mockReturnValue(testData);
        const result = wechatAPI.getStorageSync('test_key');
        expect(result).toEqual(testData);

        wechatAPI.removeStorageSync('test_key');
        expect(mockWx.removeStorageSync).toHaveBeenCalledWith('test_key');
    });

    it('should show toast messages', async () => {
        const { WeChatAPI } = await import('../../assets/scripts/wechat/WeChatAPI');
        const wechatAPI = WeChatAPI.getInstance();
        
        wechatAPI.showToast({
            title: 'Test Message',
            icon: 'success'
        });

        expect(mockWx.showToast).toHaveBeenCalledWith({
            title: 'Test Message',
            icon: 'success',
            duration: 2000
        });
    });

    it('should handle sharing', async () => {
        const { WeChatAPI } = await import('../../assets/scripts/wechat/WeChatAPI');
        const wechatAPI = WeChatAPI.getInstance();
        
        wechatAPI.shareAppMessage({
            title: 'Test Share',
            desc: 'Test Description',
            path: '/pages/test',
            imageUrl: '/images/test.png'
        });

        expect(mockWx.shareAppMessage).toHaveBeenCalledWith({
            title: 'Test Share',
            desc: 'Test Description',
            path: '/pages/test',
            imageUrl: '/images/test.png'
        });
    });

    it('should manage user preferences', async () => {
        const { WeChatStorageService } = await import('../../assets/scripts/wechat/WeChatStorageService');
        const storageService = WeChatStorageService.getInstance();
        
        const preferences = {
            soundEnabled: false,
            musicEnabled: true,
            vibrationEnabled: false
        };

        storageService.saveUserPreferences(preferences);
        
        const saved = storageService.getUserPreferences();
        expect(saved.soundEnabled).toBe(false);
        expect(saved.musicEnabled).toBe(true);
        expect(saved.vibrationEnabled).toBe(false);
    });

    it('should manage recent rooms', async () => {
        const { WeChatStorageService } = await import('../../assets/scripts/wechat/WeChatStorageService');
        const storageService = WeChatStorageService.getInstance();
        
        storageService.addRecentRoom('123456');
        storageService.addRecentRoom('789012');
        storageService.addRecentRoom('123456'); // Duplicate should be moved to front

        const recent = storageService.getRecentRooms();
        expect(recent[0]).toBe('123456');
        expect(recent[1]).toBe('789012');
        expect(recent.length).toBe(2);
    });
});

describe('WeChat Non-Environment Fallback', () => {
    beforeEach(() => {
        // @ts-ignore
        delete global.wx;
        vi.clearAllMocks();
    });

    it('should work without WeChat environment', async () => {
        const { WeChatAPI } = await import('../../assets/scripts/wechat/WeChatAPI');
        const wechatAPI = WeChatAPI.getInstance();
        expect(wechatAPI.isWeChat()).toBe(false);
    });

    it('should provide mock login in non-WeChat environment', async () => {
        const { WeChatAPI } = await import('../../assets/scripts/wechat/WeChatAPI');
        const wechatAPI = WeChatAPI.getInstance();
        
        const result = await wechatAPI.login();
        expect(result.code).toContain('mock_code_');
        expect(result.userInfo).toBeDefined();
        expect(result.userInfo.nickName).toBe('Test User');
    });

    it('should use localStorage fallback for storage', async () => {
        const { WeChatAPI } = await import('../../assets/scripts/wechat/WeChatAPI');
        const wechatAPI = WeChatAPI.getInstance();
        
        const testData = { test: 'data' };
        
        wechatAPI.setStorageSync('test_key', testData);
        const result = wechatAPI.getStorageSync('test_key');
        
        expect(result).toEqual(testData);
    });
});