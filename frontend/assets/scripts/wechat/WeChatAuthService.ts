import { _decorator } from 'cc';
import { WeChatAPI } from './WeChatAPI';
import { HttpClient } from '../network/HttpClient';

const { ccclass } = _decorator;

export interface WeChatLoginResult {
    success: boolean;
    token?: string;
    userInfo?: {
        id: string;
        nickname: string;
        avatar: string;
        coins: number;
        roomCards: number;
    };
    error?: string;
}

/**
 * WeChat authentication service
 * Handles WeChat login flow and user authentication
 */
@ccclass('WeChatAuthService')
export class WeChatAuthService {
    private static instance: WeChatAuthService;
    private wechatAPI: WeChatAPI;
    private httpClient: HttpClient;
    private currentToken: string | null = null;
    private currentUser: any = null;

    private constructor() {
        this.wechatAPI = WeChatAPI.getInstance();
        this.httpClient = HttpClient.getInstance();
    }

    public static getInstance(): WeChatAuthService {
        if (!WeChatAuthService.instance) {
            WeChatAuthService.instance = new WeChatAuthService();
        }
        return WeChatAuthService.instance;
    }

    /**
     * Perform WeChat login
     */
    public async login(): Promise<WeChatLoginResult> {
        try {
            this.wechatAPI.showLoading('登录中...');

            // Step 1: Get WeChat login code
            const wechatLoginData = await this.wechatAPI.login();
            
            // Step 2: Get user profile if needed
            let userProfile = wechatLoginData.userInfo;
            if (!userProfile) {
                try {
                    userProfile = await this.wechatAPI.getUserProfile();
                } catch (error) {
                    console.warn('Failed to get user profile:', error);
                    // Continue without user profile
                }
            }

            // Step 3: Send to backend for authentication
            const loginResult = await this.authenticateWithBackend(
                wechatLoginData.code,
                userProfile
            );

            this.wechatAPI.hideLoading();

            if (loginResult.success) {
                this.currentToken = loginResult.token!;
                this.currentUser = loginResult.userInfo;
                
                // Save token to local storage
                this.wechatAPI.setStorageSync('auth_token', this.currentToken);
                this.wechatAPI.setStorageSync('user_info', this.currentUser);

                this.wechatAPI.showToast({
                    title: '登录成功',
                    icon: 'success'
                });
            }

            return loginResult;

        } catch (error) {
            this.wechatAPI.hideLoading();
            console.error('WeChat login error:', error);
            
            return {
                success: false,
                error: error instanceof Error ? error.message : '登录失败'
            };
        }
    }

    /**
     * Authenticate with backend server
     */
    private async authenticateWithBackend(
        code: string, 
        userProfile?: any
    ): Promise<WeChatLoginResult> {
        try {
            const response = await this.httpClient.post('/api/auth/wechat/login', {
                code: code,
                userInfo: userProfile
            });

            if (response.success && response.data) {
                return {
                    success: true,
                    token: response.data.token,
                    userInfo: response.data.user
                };
            } else {
                return {
                    success: false,
                    error: response.message || '服务器认证失败'
                };
            }
        } catch (error) {
            console.error('Backend authentication error:', error);
            return {
                success: false,
                error: '网络连接失败'
            };
        }
    }

    /**
     * Check if user is logged in
     */
    public isLoggedIn(): boolean {
        if (this.currentToken) {
            return true;
        }

        // Check stored token
        const storedToken = this.wechatAPI.getStorageSync('auth_token');
        if (storedToken) {
            this.currentToken = storedToken;
            this.currentUser = this.wechatAPI.getStorageSync('user_info');
            return true;
        }

        return false;
    }

    /**
     * Get current authentication token
     */
    public getToken(): string | null {
        return this.currentToken;
    }

    /**
     * Get current user info
     */
    public getCurrentUser(): any {
        return this.currentUser;
    }

    /**
     * Logout user
     */
    public logout(): void {
        this.currentToken = null;
        this.currentUser = null;
        this.wechatAPI.removeStorageSync('auth_token');
        this.wechatAPI.removeStorageSync('user_info');
    }

    /**
     * Refresh user info from server
     */
    public async refreshUserInfo(): Promise<boolean> {
        if (!this.currentToken) {
            return false;
        }

        try {
            const response = await this.httpClient.get('/api/user/profile', {
                headers: {
                    'Authorization': `Bearer ${this.currentToken}`
                }
            });

            if (response.success && response.data) {
                this.currentUser = response.data;
                this.wechatAPI.setStorageSync('user_info', this.currentUser);
                return true;
            }
        } catch (error) {
            console.error('Failed to refresh user info:', error);
        }

        return false;
    }

    /**
     * Auto login with stored token
     */
    public async autoLogin(): Promise<boolean> {
        const storedToken = this.wechatAPI.getStorageSync('auth_token');
        if (!storedToken) {
            return false;
        }

        try {
            // Verify token with server
            const response = await this.httpClient.get('/api/auth/verify', {
                headers: {
                    'Authorization': `Bearer ${storedToken}`
                }
            });

            if (response.success) {
                this.currentToken = storedToken;
                this.currentUser = this.wechatAPI.getStorageSync('user_info');
                return true;
            } else {
                // Token invalid, clear storage
                this.logout();
                return false;
            }
        } catch (error) {
            console.error('Auto login failed:', error);
            this.logout();
            return false;
        }
    }
}