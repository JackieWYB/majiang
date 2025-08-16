import { _decorator, Component } from 'cc';
import { WeChatAPI } from './WeChatAPI';
import { WeChatStorageService } from './WeChatStorageService';
import { NetworkManager } from '../network/NetworkManager';

const { ccclass } = _decorator;

export interface LifecycleCallbacks {
    onShow?: (options: any) => void;
    onHide?: () => void;
    onError?: (error: string) => void;
    onPageNotFound?: (options: any) => void;
    onUnhandledRejection?: (reason: any) => void;
    onMemoryWarning?: () => void;
}

/**
 * WeChat Mini Program lifecycle management
 */
@ccclass('WeChatLifecycleManager')
export class WeChatLifecycleManager extends Component {
    private static instance: WeChatLifecycleManager;
    private wechatAPI: WeChatAPI;
    private storageService: WeChatStorageService;
    private networkManager: NetworkManager;
    private callbacks: LifecycleCallbacks = {};
    private isVisible: boolean = true;
    private backgroundTime: number = 0;
    private foregroundTime: number = 0;

    private constructor() {
        super();
        this.wechatAPI = WeChatAPI.getInstance();
        this.storageService = WeChatStorageService.getInstance();
        this.networkManager = NetworkManager.getInstance();
    }

    public static getInstance(): WeChatLifecycleManager {
        if (!WeChatLifecycleManager.instance) {
            WeChatLifecycleManager.instance = new WeChatLifecycleManager();
        }
        return WeChatLifecycleManager.instance;
    }

    /**
     * Initialize lifecycle management
     */
    public initialize(callbacks: LifecycleCallbacks = {}): void {
        this.callbacks = callbacks;
        this.setupLifecycleHandlers();
        this.setupErrorHandlers();
        this.setupMemoryWarningHandler();
    }

    /**
     * Setup WeChat lifecycle handlers
     */
    private setupLifecycleHandlers(): void {
        if (!this.wechatAPI.isWeChat()) {
            // Setup browser lifecycle events for development
            this.setupBrowserLifecycle();
            return;
        }

        // @ts-ignore - WeChat API
        wx.onAppShow((options: any) => {
            this.handleAppShow(options);
        });

        // @ts-ignore - WeChat API
        wx.onAppHide(() => {
            this.handleAppHide();
        });

        // @ts-ignore - WeChat API
        wx.onPageNotFound((options: any) => {
            this.handlePageNotFound(options);
        });
    }

    /**
     * Setup browser lifecycle for development
     */
    private setupBrowserLifecycle(): void {
        document.addEventListener('visibilitychange', () => {
            if (document.hidden) {
                this.handleAppHide();
            } else {
                this.handleAppShow({ path: '/', query: {} });
            }
        });

        window.addEventListener('beforeunload', () => {
            this.handleAppHide();
        });

        window.addEventListener('focus', () => {
            if (!this.isVisible) {
                this.handleAppShow({ path: '/', query: {} });
            }
        });

        window.addEventListener('blur', () => {
            if (this.isVisible) {
                this.handleAppHide();
            }
        });
    }

    /**
     * Handle app show event
     */
    private handleAppShow(options: any): void {
        console.log('App show:', options);
        
        this.isVisible = true;
        this.foregroundTime = Date.now();
        
        // Calculate background duration
        if (this.backgroundTime > 0) {
            const backgroundDuration = this.foregroundTime - this.backgroundTime;
            console.log('Background duration:', backgroundDuration, 'ms');
            
            // Handle long background duration (e.g., reconnect network)
            if (backgroundDuration > 30000) { // 30 seconds
                this.handleLongBackgroundReturn();
            }
        }

        // Update last login time
        this.storageService.updateLastLoginTime();

        // Handle scene parameters
        this.handleSceneParameters(options);

        // Callback
        if (this.callbacks.onShow) {
            this.callbacks.onShow(options);
        }
    }

    /**
     * Handle app hide event
     */
    private handleAppHide(): void {
        console.log('App hide');
        
        this.isVisible = false;
        this.backgroundTime = Date.now();

        // Save current session data
        this.saveSessionState();

        // Disconnect network if needed
        this.handleNetworkOnBackground();

        // Callback
        if (this.callbacks.onHide) {
            this.callbacks.onHide();
        }
    }

    /**
     * Handle long background return
     */
    private handleLongBackgroundReturn(): void {
        console.log('Handling long background return');
        
        // Reconnect network
        if (this.networkManager.isConnected()) {
            this.networkManager.reconnect().catch(error => {
                console.error('Failed to reconnect after background:', error);
            });
        }

        // Refresh user data
        // This would typically refresh user info, game state, etc.
    }

    /**
     * Handle scene parameters (from sharing, QR codes, etc.)
     */
    private handleSceneParameters(options: any): void {
        if (!options.query) {
            return;
        }

        const { roomId, action, inviteCode } = options.query;

        if (roomId && action === 'join') {
            // Handle room invitation
            this.handleRoomInvitation(roomId);
        } else if (action === 'play') {
            // Handle direct play action
            this.handleDirectPlay();
        } else if (action === 'checkin') {
            // Handle check-in action
            this.handleCheckInAction();
        } else if (inviteCode) {
            // Handle invite code
            this.handleInviteCode(inviteCode);
        }
    }

    /**
     * Handle room invitation
     */
    private handleRoomInvitation(roomId: string): void {
        console.log('Handling room invitation:', roomId);
        
        // Store invitation for later processing
        this.storageService.saveSessionData({
            pendingRoomInvitation: roomId,
            invitationTime: Date.now()
        });

        // Show invitation dialog or navigate to room
        this.wechatAPI.showToast({
            title: '正在加入房间...',
            icon: 'loading'
        });
    }

    /**
     * Handle direct play action
     */
    private handleDirectPlay(): void {
        console.log('Handling direct play action');
        
        // Navigate to lobby or quick match
        this.storageService.saveSessionData({
            pendingAction: 'quickPlay',
            actionTime: Date.now()
        });
    }

    /**
     * Handle check-in action
     */
    private handleCheckInAction(): void {
        console.log('Handling check-in action');
        
        // Navigate to check-in page
        this.storageService.saveSessionData({
            pendingAction: 'checkIn',
            actionTime: Date.now()
        });
    }

    /**
     * Handle invite code
     */
    private handleInviteCode(inviteCode: string): void {
        console.log('Handling invite code:', inviteCode);
        
        // Process invite code
        this.storageService.saveSessionData({
            pendingInviteCode: inviteCode,
            inviteTime: Date.now()
        });
    }

    /**
     * Save current session state
     */
    private saveSessionState(): void {
        const sessionData = {
            lastActiveTime: Date.now(),
            isVisible: this.isVisible,
            // Add current game state, scene info, etc.
        };
        
        this.storageService.saveSessionData(sessionData);
    }

    /**
     * Handle network on background
     */
    private handleNetworkOnBackground(): void {
        // Optionally disconnect WebSocket to save resources
        // The network manager should handle reconnection on foreground
        if (this.networkManager.isConnected()) {
            // Don't disconnect immediately, let it timeout naturally
            // This allows for quick app switches
        }
    }

    /**
     * Handle page not found
     */
    private handlePageNotFound(options: any): void {
        console.error('Page not found:', options);
        
        if (this.callbacks.onPageNotFound) {
            this.callbacks.onPageNotFound(options);
        }

        // Navigate to home page
        this.wechatAPI.showToast({
            title: '页面不存在',
            icon: 'error'
        });
    }

    /**
     * Setup error handlers
     */
    private setupErrorHandlers(): void {
        if (!this.wechatAPI.isWeChat()) {
            // Setup browser error handlers
            window.addEventListener('error', (event) => {
                this.handleError(event.error?.message || 'Unknown error');
            });

            window.addEventListener('unhandledrejection', (event) => {
                this.handleUnhandledRejection(event.reason);
            });
            return;
        }

        // @ts-ignore - WeChat API
        wx.onError((error: string) => {
            this.handleError(error);
        });

        // @ts-ignore - WeChat API
        wx.onUnhandledRejection((reason: any) => {
            this.handleUnhandledRejection(reason);
        });
    }

    /**
     * Handle error
     */
    private handleError(error: string): void {
        console.error('App error:', error);
        
        // Log error for analytics
        this.logError('app_error', error);

        if (this.callbacks.onError) {
            this.callbacks.onError(error);
        }
    }

    /**
     * Handle unhandled rejection
     */
    private handleUnhandledRejection(reason: any): void {
        console.error('Unhandled rejection:', reason);
        
        // Log error for analytics
        this.logError('unhandled_rejection', reason);

        if (this.callbacks.onUnhandledRejection) {
            this.callbacks.onUnhandledRejection(reason);
        }
    }

    /**
     * Setup memory warning handler
     */
    private setupMemoryWarningHandler(): void {
        if (!this.wechatAPI.isWeChat()) {
            return;
        }

        // @ts-ignore - WeChat API
        wx.onMemoryWarning((res: any) => {
            this.handleMemoryWarning(res.level);
        });
    }

    /**
     * Handle memory warning
     */
    private handleMemoryWarning(level: number): void {
        console.warn('Memory warning, level:', level);
        
        // Clear caches, reduce memory usage
        this.clearMemoryCache();

        if (this.callbacks.onMemoryWarning) {
            this.callbacks.onMemoryWarning();
        }
    }

    /**
     * Clear memory cache
     */
    private clearMemoryCache(): void {
        // Clear image cache, audio cache, etc.
        // This would be implemented based on the specific caching system used
        console.log('Clearing memory cache');
    }

    /**
     * Log error for analytics
     */
    private logError(type: string, error: any): void {
        // This would typically send to analytics service
        console.log('Logging error:', type, error);
    }

    /**
     * Get app visibility state
     */
    public isAppVisible(): boolean {
        return this.isVisible;
    }

    /**
     * Get background duration
     */
    public getBackgroundDuration(): number {
        if (this.backgroundTime === 0 || this.isVisible) {
            return 0;
        }
        return Date.now() - this.backgroundTime;
    }

    /**
     * Get foreground duration
     */
    public getForegroundDuration(): number {
        if (this.foregroundTime === 0 || !this.isVisible) {
            return 0;
        }
        return Date.now() - this.foregroundTime;
    }

    /**
     * Force update check
     */
    public checkForUpdates(): void {
        if (!this.wechatAPI.isWeChat()) {
            return;
        }

        // @ts-ignore - WeChat API
        const updateManager = wx.getUpdateManager();

        updateManager.onCheckForUpdate((res: any) => {
            console.log('Update check result:', res.hasUpdate);
        });

        updateManager.onUpdateReady(() => {
            this.wechatAPI.showToast({
                title: '新版本已准备好',
                icon: 'success'
            });
            
            // Apply update
            updateManager.applyUpdate();
        });

        updateManager.onUpdateFailed(() => {
            this.wechatAPI.showToast({
                title: '更新失败',
                icon: 'error'
            });
        });
    }
}