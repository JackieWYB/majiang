import { _decorator, Component, Button, Label, Node, Sprite, Animation } from 'cc';
import { BaseUIController } from '../ui/BaseUIController';
import { SceneManager } from '../managers/SceneManager';
import { HttpClient } from '../network/HttpClient';
import { LocalCacheManager } from '../managers/LocalCacheManager';
import { WeChatAuthService } from '../wechat/WeChatAuthService';
import { WeChatAPI } from '../wechat/WeChatAPI';
import { WeChatLifecycleManager } from '../wechat/WeChatLifecycleManager';
import { WeChatStorageService } from '../wechat/WeChatStorageService';
const { ccclass, property } = _decorator;

interface WeChatUserInfo {
    openId: string;
    unionId?: string;
    nickname: string;
    avatar: string;
}

interface LoginResponse {
    success: boolean;
    token?: string;
    userInfo?: WeChatUserInfo;
    error?: string;
}

/**
 * Login Scene Controller with WeChat authorization integration
 */
@ccclass('LoginSceneController')
export class LoginSceneController extends BaseUIController {
    @property(Button)
    private loginButton: Button = null!;

    @property(Button)
    private guestLoginButton: Button = null!;

    @property(Label)
    private statusLabel: Label = null!;

    @property(Label)
    private versionLabel: Label = null!;

    @property(Node)
    private loadingNode: Node = null!;

    @property(Node)
    private logoNode: Node = null!;

    @property(Animation)
    private logoAnimation: Animation = null!;

    private _isLoggingIn: boolean = false;
    private authService: WeChatAuthService;
    private wechatAPI: WeChatAPI;
    private lifecycleManager: WeChatLifecycleManager;
    private storageService: WeChatStorageService;

    protected setupComponents(): void {
        // Initialize WeChat services
        this.authService = WeChatAuthService.getInstance();
        this.wechatAPI = WeChatAPI.getInstance();
        this.lifecycleManager = WeChatLifecycleManager.getInstance();
        this.storageService = WeChatStorageService.getInstance();

        // Initialize lifecycle management
        this.lifecycleManager.initialize({
            onShow: (options) => {
                this.handleSceneShow(options);
            },
            onError: (error) => {
                this.updateStatus('系统错误: ' + error);
            }
        });
        // Find components if not assigned
        if (!this.loginButton) {
            this.loginButton = this.node.getComponentInChildren(Button);
        }
        
        // Set version info
        if (this.versionLabel) {
            this.versionLabel.string = 'v1.0.0';
        }

        // Start logo animation
        if (this.logoAnimation) {
            this.logoAnimation.play();
        }

        // Check for existing login
        this.checkExistingLogin();
    }

    protected bindEvents(): void {
        if (this.loginButton) {
            this.loginButton.node.on(Button.EventType.CLICK, this.onWeChatLoginClick, this);
        }
        if (this.guestLoginButton) {
            this.guestLoginButton.node.on(Button.EventType.CLICK, this.onGuestLoginClick, this);
        }
    }

    protected unbindEvents(): void {
        if (this.loginButton) {
            this.loginButton.node.off(Button.EventType.CLICK, this.onWeChatLoginClick, this);
        }
        if (this.guestLoginButton) {
            this.guestLoginButton.node.off(Button.EventType.CLICK, this.onGuestLoginClick, this);
        }
    }

    private async checkExistingLogin(): Promise<void> {
        if (this.authService.isLoggedIn()) {
            this.updateStatus('检查登录状态...');
            try {
                const success = await this.authService.autoLogin();
                if (success) {
                    this.updateStatus('自动登录成功');
                    this.navigateToLobby();
                    return;
                }
            } catch (error) {
                console.log('Auto login failed:', error);
            }
        }
        this.updateStatus('请选择登录方式');
    }

    private async onWeChatLoginClick(): Promise<void> {
        if (this._isLoggingIn) {
            return;
        }

        this._isLoggingIn = true;
        this.showLoading(true);
        this.updateStatus('正在获取微信授权...');

        try {
            // WeChat login logic
            const loginResult = await this.performWeChatLogin();
            
            if (loginResult.success && loginResult.token) {
                this.updateStatus('登录成功');
                LocalCacheManager.instance.setAuthToken(loginResult.token);
                if (loginResult.userInfo) {
                    LocalCacheManager.instance.setUserInfo(loginResult.userInfo);
                }
                this.navigateToLobby();
            } else {
                this.updateStatus('登录失败: ' + (loginResult.error || '未知错误'));
            }
        } catch (error) {
            console.error('WeChat login error:', error);
            this.updateStatus('微信登录失败，请重试');
        } finally {
            this._isLoggingIn = false;
            this.showLoading(false);
        }
    }

    private async onGuestLoginClick(): Promise<void> {
        if (this._isLoggingIn) {
            return;
        }

        this._isLoggingIn = true;
        this.showLoading(true);
        this.updateStatus('正在以游客身份登录...');

        try {
            const loginResult = await this.performGuestLogin();
            
            if (loginResult.success && loginResult.token) {
                this.updateStatus('游客登录成功');
                LocalCacheManager.instance.setAuthToken(loginResult.token);
                if (loginResult.userInfo) {
                    LocalCacheManager.instance.setUserInfo(loginResult.userInfo);
                }
                this.navigateToLobby();
            } else {
                this.updateStatus('游客登录失败: ' + (loginResult.error || '未知错误'));
            }
        } catch (error) {
            console.error('Guest login error:', error);
            this.updateStatus('游客登录失败，请重试');
        } finally {
            this._isLoggingIn = false;
            this.showLoading(false);
        }
    }

    private async performWeChatLogin(): Promise<LoginResponse> {
        try {
            const result = await this.authService.login();
            
            if (result.success && result.token && result.userInfo) {
                return {
                    success: true,
                    token: result.token,
                    userInfo: {
                        openId: result.userInfo.id,
                        nickname: result.userInfo.nickname,
                        avatar: result.userInfo.avatar
                    }
                };
            } else {
                return {
                    success: false,
                    error: result.error || '微信登录失败'
                };
            }
        } catch (error) {
            console.error('WeChat login error:', error);
            return {
                success: false,
                error: error instanceof Error ? error.message : '微信登录失败'
            };
        }
    }

    private async performGuestLogin(): Promise<LoginResponse> {
        try {
            const response = await HttpClient.instance.post('/api/auth/guest-login', {
                deviceId: this.getDeviceId()
            });

            if (response.success) {
                return {
                    success: true,
                    token: response.data.token,
                    userInfo: response.data.userInfo
                };
            } else {
                return {
                    success: false,
                    error: response.message || '游客登录失败'
                };
            }
        } catch (error) {
            console.error('Guest login request failed:', error);
            // Fallback for testing
            return {
                success: true,
                token: 'mock-jwt-token-guest',
                userInfo: {
                    openId: 'guest-' + Date.now(),
                    nickname: '游客' + Math.floor(Math.random() * 1000),
                    avatar: ''
                }
            };
        }
    }

    private async authenticateWithBackend(code: string, userInfo?: any): Promise<LoginResponse> {
        try {
            const response = await HttpClient.instance.post('/api/auth/wechat-login', {
                code: code,
                userInfo: userInfo
            });

            if (response.success) {
                return {
                    success: true,
                    token: response.data.token,
                    userInfo: response.data.userInfo
                };
            } else {
                return {
                    success: false,
                    error: response.message || '认证失败'
                };
            }
        } catch (error) {
            console.error('Backend authentication failed:', error);
            throw error;
        }
    }

    private async validateToken(token: string): Promise<boolean> {
        try {
            const response = await HttpClient.instance.get('/api/auth/validate', {
                headers: {
                    'Authorization': `Bearer ${token}`
                }
            });
            return response.success;
        } catch (error) {
            return false;
        }
    }

    private getDeviceId(): string {
        // Generate or retrieve device ID
        let deviceId = LocalCacheManager.instance.getItem('device_id');
        if (!deviceId) {
            deviceId = 'device-' + Date.now() + '-' + Math.random().toString(36).substr(2, 9);
            LocalCacheManager.instance.setItem('device_id', deviceId);
        }
        return deviceId;
    }

    private navigateToLobby(): void {
        setTimeout(() => {
            SceneManager.instance.loadScene('LobbyScene');
        }, 1000);
    }

    private showLoading(show: boolean): void {
        if (this.loadingNode) {
            this.loadingNode.active = show;
        }
        if (this.loginButton) {
            this.loginButton.interactable = !show;
        }
    }

    private updateStatus(message: string): void {
        if (this.statusLabel) {
            this.statusLabel.string = message;
        }
    }

    private handleSceneShow(options: any): void {
        // Handle scene parameters from WeChat sharing or QR codes
        if (options.query) {
            const { roomId, action, inviteCode } = options.query;
            
            if (roomId && action === 'join') {
                // Store room invitation for after login
                this.updateStatus('检测到房间邀请，请先登录');
                this.storageService.saveSessionData({
                    pendingRoomInvitation: roomId,
                    invitationTime: Date.now()
                });
            } else if (inviteCode) {
                // Store invite code for after login
                this.updateStatus('检测到邀请码，请先登录');
                this.storageService.saveSessionData({
                    pendingInviteCode: inviteCode,
                    inviteTime: Date.now()
                });
            } else if (action === 'play') {
                this.updateStatus('准备开始游戏，请先登录');
            }
        }
    }
}