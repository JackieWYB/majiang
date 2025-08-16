import { _decorator } from 'cc';
import { WeChatAPI } from './WeChatAPI';

const { ccclass } = _decorator;

export interface UserPreferences {
    soundEnabled: boolean;
    musicEnabled: boolean;
    vibrationEnabled: boolean;
    autoPlay: boolean;
    showAnimations: boolean;
    language: 'zh_CN' | 'en_US';
    theme: 'light' | 'dark' | 'auto';
    tileStyle: 'classic' | 'modern' | 'simple';
    backgroundMusic: string;
    soundEffects: {
        tileClick: boolean;
        gameStart: boolean;
        gameEnd: boolean;
        playerAction: boolean;
    };
}

export interface GameSettings {
    defaultRoomConfig: {
        players: number;
        tiles: string;
        allowPeng: boolean;
        allowGang: boolean;
        allowChi: boolean;
        timeLimit: number;
        autoTrustee: boolean;
    };
    quickJoinPreferences: {
        preferredPlayerCount: number;
        preferredGameMode: string;
        avoidPlayers: string[];
    };
    displaySettings: {
        showPlayerNames: boolean;
        showScoreHistory: boolean;
        showTileCount: boolean;
        highlightPossibleMoves: boolean;
    };
}

export interface CacheData {
    userPreferences: UserPreferences;
    gameSettings: GameSettings;
    recentRooms: string[];
    friendsList: any[];
    gameHistory: any[];
    achievements: any[];
    lastLoginTime: number;
    sessionData: any;
}

/**
 * WeChat local storage service for user preferences and game settings
 */
@ccclass('WeChatStorageService')
export class WeChatStorageService {
    private static instance: WeChatStorageService;
    private wechatAPI: WeChatAPI;
    private cache: Partial<CacheData> = {};
    private readonly STORAGE_KEYS = {
        USER_PREFERENCES: 'user_preferences',
        GAME_SETTINGS: 'game_settings',
        RECENT_ROOMS: 'recent_rooms',
        FRIENDS_LIST: 'friends_list',
        GAME_HISTORY: 'game_history',
        ACHIEVEMENTS: 'achievements',
        LAST_LOGIN: 'last_login_time',
        SESSION_DATA: 'session_data'
    };

    private constructor() {
        this.wechatAPI = WeChatAPI.getInstance();
        this.loadAllData();
    }

    public static getInstance(): WeChatStorageService {
        if (!WeChatStorageService.instance) {
            WeChatStorageService.instance = new WeChatStorageService();
        }
        return WeChatStorageService.instance;
    }

    /**
     * Load all cached data from storage
     */
    private loadAllData(): void {
        try {
            this.cache.userPreferences = this.loadUserPreferences();
            this.cache.gameSettings = this.loadGameSettings();
            this.cache.recentRooms = this.loadRecentRooms();
            this.cache.friendsList = this.loadFriendsList();
            this.cache.gameHistory = this.loadGameHistory();
            this.cache.achievements = this.loadAchievements();
            this.cache.lastLoginTime = this.loadLastLoginTime();
            this.cache.sessionData = this.loadSessionData();
        } catch (error) {
            console.error('Failed to load cached data:', error);
        }
    }

    /**
     * Get default user preferences
     */
    private getDefaultUserPreferences(): UserPreferences {
        return {
            soundEnabled: true,
            musicEnabled: true,
            vibrationEnabled: true,
            autoPlay: false,
            showAnimations: true,
            language: 'zh_CN',
            theme: 'auto',
            tileStyle: 'classic',
            backgroundMusic: 'default',
            soundEffects: {
                tileClick: true,
                gameStart: true,
                gameEnd: true,
                playerAction: true
            }
        };
    }

    /**
     * Get default game settings
     */
    private getDefaultGameSettings(): GameSettings {
        return {
            defaultRoomConfig: {
                players: 3,
                tiles: 'WAN_ONLY',
                allowPeng: true,
                allowGang: true,
                allowChi: false,
                timeLimit: 15,
                autoTrustee: true
            },
            quickJoinPreferences: {
                preferredPlayerCount: 3,
                preferredGameMode: 'classic',
                avoidPlayers: []
            },
            displaySettings: {
                showPlayerNames: true,
                showScoreHistory: true,
                showTileCount: true,
                highlightPossibleMoves: true
            }
        };
    }

    /**
     * User Preferences Management
     */
    public getUserPreferences(): UserPreferences {
        return this.cache.userPreferences || this.getDefaultUserPreferences();
    }

    public saveUserPreferences(preferences: Partial<UserPreferences>): void {
        const current = this.getUserPreferences();
        const updated = { ...current, ...preferences };
        this.cache.userPreferences = updated;
        this.wechatAPI.setStorageSync(this.STORAGE_KEYS.USER_PREFERENCES, updated);
    }

    private loadUserPreferences(): UserPreferences {
        const stored = this.wechatAPI.getStorageSync(this.STORAGE_KEYS.USER_PREFERENCES);
        return stored ? { ...this.getDefaultUserPreferences(), ...stored } : this.getDefaultUserPreferences();
    }

    /**
     * Game Settings Management
     */
    public getGameSettings(): GameSettings {
        return this.cache.gameSettings || this.getDefaultGameSettings();
    }

    public saveGameSettings(settings: Partial<GameSettings>): void {
        const current = this.getGameSettings();
        const updated = { ...current, ...settings };
        this.cache.gameSettings = updated;
        this.wechatAPI.setStorageSync(this.STORAGE_KEYS.GAME_SETTINGS, updated);
    }

    private loadGameSettings(): GameSettings {
        const stored = this.wechatAPI.getStorageSync(this.STORAGE_KEYS.GAME_SETTINGS);
        return stored ? { ...this.getDefaultGameSettings(), ...stored } : this.getDefaultGameSettings();
    }

    /**
     * Recent Rooms Management
     */
    public getRecentRooms(): string[] {
        return this.cache.recentRooms || [];
    }

    public addRecentRoom(roomId: string): void {
        const recent = this.getRecentRooms();
        const updated = [roomId, ...recent.filter(id => id !== roomId)].slice(0, 10);
        this.cache.recentRooms = updated;
        this.wechatAPI.setStorageSync(this.STORAGE_KEYS.RECENT_ROOMS, updated);
    }

    public removeRecentRoom(roomId: string): void {
        const recent = this.getRecentRooms();
        const updated = recent.filter(id => id !== roomId);
        this.cache.recentRooms = updated;
        this.wechatAPI.setStorageSync(this.STORAGE_KEYS.RECENT_ROOMS, updated);
    }

    private loadRecentRooms(): string[] {
        return this.wechatAPI.getStorageSync(this.STORAGE_KEYS.RECENT_ROOMS) || [];
    }

    /**
     * Friends List Management
     */
    public getFriendsList(): any[] {
        return this.cache.friendsList || [];
    }

    public saveFriendsList(friends: any[]): void {
        this.cache.friendsList = friends;
        this.wechatAPI.setStorageSync(this.STORAGE_KEYS.FRIENDS_LIST, friends);
    }

    private loadFriendsList(): any[] {
        return this.wechatAPI.getStorageSync(this.STORAGE_KEYS.FRIENDS_LIST) || [];
    }

    /**
     * Game History Management
     */
    public getGameHistory(): any[] {
        return this.cache.gameHistory || [];
    }

    public addGameHistory(gameRecord: any): void {
        const history = this.getGameHistory();
        const updated = [gameRecord, ...history].slice(0, 100); // Keep last 100 games
        this.cache.gameHistory = updated;
        this.wechatAPI.setStorageSync(this.STORAGE_KEYS.GAME_HISTORY, updated);
    }

    private loadGameHistory(): any[] {
        return this.wechatAPI.getStorageSync(this.STORAGE_KEYS.GAME_HISTORY) || [];
    }

    /**
     * Achievements Management
     */
    public getAchievements(): any[] {
        return this.cache.achievements || [];
    }

    public saveAchievements(achievements: any[]): void {
        this.cache.achievements = achievements;
        this.wechatAPI.setStorageSync(this.STORAGE_KEYS.ACHIEVEMENTS, achievements);
    }

    private loadAchievements(): any[] {
        return this.wechatAPI.getStorageSync(this.STORAGE_KEYS.ACHIEVEMENTS) || [];
    }

    /**
     * Session Management
     */
    public getLastLoginTime(): number {
        return this.cache.lastLoginTime || 0;
    }

    public updateLastLoginTime(): void {
        const now = Date.now();
        this.cache.lastLoginTime = now;
        this.wechatAPI.setStorageSync(this.STORAGE_KEYS.LAST_LOGIN, now);
    }

    private loadLastLoginTime(): number {
        return this.wechatAPI.getStorageSync(this.STORAGE_KEYS.LAST_LOGIN) || 0;
    }

    public getSessionData(): any {
        return this.cache.sessionData || {};
    }

    public saveSessionData(data: any): void {
        this.cache.sessionData = data;
        this.wechatAPI.setStorageSync(this.STORAGE_KEYS.SESSION_DATA, data);
    }

    private loadSessionData(): any {
        return this.wechatAPI.getStorageSync(this.STORAGE_KEYS.SESSION_DATA) || {};
    }

    /**
     * Clear all data
     */
    public clearAllData(): void {
        Object.values(this.STORAGE_KEYS).forEach(key => {
            this.wechatAPI.removeStorageSync(key);
        });
        this.cache = {};
    }

    /**
     * Get storage usage info
     */
    public getStorageInfo(): Promise<any> {
        return new Promise((resolve, reject) => {
            if (!this.wechatAPI.isWeChat()) {
                resolve({
                    keys: Object.values(this.STORAGE_KEYS),
                    currentSize: 0,
                    limitSize: 10240 // 10MB mock limit
                });
                return;
            }

            // @ts-ignore - WeChat API
            wx.getStorageInfo({
                success: resolve,
                fail: reject
            });
        });
    }

    /**
     * Export data for backup
     */
    public exportData(): string {
        return JSON.stringify(this.cache);
    }

    /**
     * Import data from backup
     */
    public importData(dataString: string): boolean {
        try {
            const data = JSON.parse(dataString);
            
            // Validate data structure
            if (typeof data === 'object' && data !== null) {
                // Save each valid section
                if (data.userPreferences) {
                    this.saveUserPreferences(data.userPreferences);
                }
                if (data.gameSettings) {
                    this.saveGameSettings(data.gameSettings);
                }
                if (data.recentRooms && Array.isArray(data.recentRooms)) {
                    this.cache.recentRooms = data.recentRooms;
                    this.wechatAPI.setStorageSync(this.STORAGE_KEYS.RECENT_ROOMS, data.recentRooms);
                }
                
                return true;
            }
        } catch (error) {
            console.error('Failed to import data:', error);
        }
        
        return false;
    }
}