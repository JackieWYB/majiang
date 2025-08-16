import { _decorator, Component } from 'cc';

const { ccclass } = _decorator;

/**
 * WeChat Mini Program API wrapper
 * Provides unified interface for WeChat-specific functionality
 */
@ccclass('WeChatAPI')
export class WeChatAPI {
    private static instance: WeChatAPI;
    private isWeChatEnvironment: boolean = false;

    private constructor() {
        this.detectWeChatEnvironment();
    }

    public static getInstance(): WeChatAPI {
        if (!WeChatAPI.instance) {
            WeChatAPI.instance = new WeChatAPI();
        }
        return WeChatAPI.instance;
    }

    /**
     * Detect if running in WeChat Mini Program environment
     */
    private detectWeChatEnvironment(): void {
        try {
            // @ts-ignore - WeChat global object
            this.isWeChatEnvironment = typeof wx !== 'undefined' && wx.getSystemInfoSync;
        } catch (error) {
            this.isWeChatEnvironment = false;
        }
        console.log('WeChat environment detected:', this.isWeChatEnvironment);
    }

    /**
     * Check if running in WeChat environment
     */
    public isWeChat(): boolean {
        return this.isWeChatEnvironment;
    }

    /**
     * WeChat login with authorization
     */
    public login(): Promise<{ code: string; userInfo?: any }> {
        return new Promise((resolve, reject) => {
            if (!this.isWeChatEnvironment) {
                // Mock login for development
                resolve({
                    code: 'mock_code_' + Date.now(),
                    userInfo: {
                        nickName: 'Test User',
                        avatarUrl: 'https://example.com/avatar.png'
                    }
                });
                return;
            }

            // @ts-ignore - WeChat API
            wx.login({
                success: (loginRes: any) => {
                    if (loginRes.code) {
                        // Get user info if authorized
                        this.getUserInfo().then(userInfo => {
                            resolve({
                                code: loginRes.code,
                                userInfo: userInfo
                            });
                        }).catch(() => {
                            // User info not available, return code only
                            resolve({ code: loginRes.code });
                        });
                    } else {
                        reject(new Error('WeChat login failed: ' + loginRes.errMsg));
                    }
                },
                fail: (error: any) => {
                    reject(new Error('WeChat login error: ' + error.errMsg));
                }
            });
        });
    }

    /**
     * Get user profile information
     */
    public getUserProfile(): Promise<any> {
        return new Promise((resolve, reject) => {
            if (!this.isWeChatEnvironment) {
                resolve({
                    nickName: 'Test User',
                    avatarUrl: 'https://example.com/avatar.png',
                    gender: 1,
                    country: 'China',
                    province: 'Beijing',
                    city: 'Beijing'
                });
                return;
            }

            // @ts-ignore - WeChat API
            wx.getUserProfile({
                desc: '用于完善用户资料',
                success: (res: any) => {
                    resolve(res.userInfo);
                },
                fail: (error: any) => {
                    reject(new Error('Get user profile failed: ' + error.errMsg));
                }
            });
        });
    }

    /**
     * Get user info (deprecated but fallback)
     */
    private getUserInfo(): Promise<any> {
        return new Promise((resolve, reject) => {
            // @ts-ignore - WeChat API
            wx.getSetting({
                success: (settingRes: any) => {
                    if (settingRes.authSetting['scope.userInfo']) {
                        // @ts-ignore - WeChat API
                        wx.getUserInfo({
                            success: (userRes: any) => {
                                resolve(userRes.userInfo);
                            },
                            fail: reject
                        });
                    } else {
                        reject(new Error('User info not authorized'));
                    }
                },
                fail: reject
            });
        });
    }

    /**
     * Share to WeChat
     */
    public shareAppMessage(options: {
        title: string;
        desc?: string;
        path?: string;
        imageUrl?: string;
    }): void {
        if (!this.isWeChatEnvironment) {
            console.log('Mock share:', options);
            return;
        }

        // @ts-ignore - WeChat API
        wx.shareAppMessage({
            title: options.title,
            desc: options.desc || '',
            path: options.path || '/pages/index/index',
            imageUrl: options.imageUrl || '',
            success: () => {
                console.log('Share success');
            },
            fail: (error: any) => {
                console.error('Share failed:', error);
            }
        });
    }

    /**
     * Show share menu
     */
    public showShareMenu(): void {
        if (!this.isWeChatEnvironment) {
            return;
        }

        // @ts-ignore - WeChat API
        wx.showShareMenu({
            withShareTicket: true,
            success: () => {
                console.log('Show share menu success');
            }
        });
    }

    /**
     * Get system info
     */
    public getSystemInfo(): Promise<any> {
        return new Promise((resolve, reject) => {
            if (!this.isWeChatEnvironment) {
                resolve({
                    brand: 'mock',
                    model: 'mock',
                    pixelRatio: 2,
                    screenWidth: 375,
                    screenHeight: 667,
                    windowWidth: 375,
                    windowHeight: 667,
                    statusBarHeight: 20,
                    language: 'zh_CN',
                    version: '8.0.0',
                    system: 'iOS 15.0',
                    platform: 'ios',
                    SDKVersion: '2.19.4'
                });
                return;
            }

            // @ts-ignore - WeChat API
            wx.getSystemInfo({
                success: resolve,
                fail: reject
            });
        });
    }

    /**
     * Vibrate device
     */
    public vibrateShort(): void {
        if (!this.isWeChatEnvironment) {
            return;
        }

        // @ts-ignore - WeChat API
        wx.vibrateShort({
            type: 'medium'
        });
    }

    /**
     * Set storage data
     */
    public setStorageSync(key: string, data: any): void {
        if (!this.isWeChatEnvironment) {
            localStorage.setItem(key, JSON.stringify(data));
            return;
        }

        // @ts-ignore - WeChat API
        wx.setStorageSync(key, data);
    }

    /**
     * Get storage data
     */
    public getStorageSync(key: string): any {
        if (!this.isWeChatEnvironment) {
            const data = localStorage.getItem(key);
            return data ? JSON.parse(data) : null;
        }

        // @ts-ignore - WeChat API
        return wx.getStorageSync(key);
    }

    /**
     * Remove storage data
     */
    public removeStorageSync(key: string): void {
        if (!this.isWeChatEnvironment) {
            localStorage.removeItem(key);
            return;
        }

        // @ts-ignore - WeChat API
        wx.removeStorageSync(key);
    }

    /**
     * Show toast message
     */
    public showToast(options: {
        title: string;
        icon?: 'success' | 'error' | 'loading' | 'none';
        duration?: number;
    }): void {
        if (!this.isWeChatEnvironment) {
            console.log('Toast:', options.title);
            return;
        }

        // @ts-ignore - WeChat API
        wx.showToast({
            title: options.title,
            icon: options.icon || 'none',
            duration: options.duration || 2000
        });
    }

    /**
     * Show loading
     */
    public showLoading(title: string = '加载中...'): void {
        if (!this.isWeChatEnvironment) {
            console.log('Loading:', title);
            return;
        }

        // @ts-ignore - WeChat API
        wx.showLoading({
            title: title,
            mask: true
        });
    }

    /**
     * Hide loading
     */
    public hideLoading(): void {
        if (!this.isWeChatEnvironment) {
            return;
        }

        // @ts-ignore - WeChat API
        wx.hideLoading();
    }
}