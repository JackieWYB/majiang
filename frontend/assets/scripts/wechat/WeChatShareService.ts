import { _decorator } from 'cc';
import { WeChatAPI } from './WeChatAPI';

const { ccclass } = _decorator;

export interface ShareOptions {
    title: string;
    desc?: string;
    path?: string;
    imageUrl?: string;
    query?: Record<string, string>;
}

/**
 * WeChat sharing and social features service
 */
@ccclass('WeChatShareService')
export class WeChatShareService {
    private static instance: WeChatShareService;
    private wechatAPI: WeChatAPI;

    private constructor() {
        this.wechatAPI = WeChatAPI.getInstance();
        this.initializeShareMenu();
    }

    public static getInstance(): WeChatShareService {
        if (!WeChatShareService.instance) {
            WeChatShareService.instance = new WeChatShareService();
        }
        return WeChatShareService.instance;
    }

    /**
     * Initialize share menu
     */
    private initializeShareMenu(): void {
        this.wechatAPI.showShareMenu();
    }

    /**
     * Share game invitation
     */
    public shareGameInvitation(roomId: string, roomOwner: string): void {
        const shareOptions: ShareOptions = {
            title: `${roomOwner}邀请你来玩麻将`,
            desc: '快来一起玩卡五星麻将吧！',
            path: `/pages/index/index?roomId=${roomId}&action=join`,
            imageUrl: '/images/share-game.png'
        };

        this.shareAppMessage(shareOptions);
    }

    /**
     * Share game result
     */
    public shareGameResult(gameResult: {
        winner: string;
        score: number;
        gameType: string;
    }): void {
        const shareOptions: ShareOptions = {
            title: `${gameResult.winner}在麻将中获胜了！`,
            desc: `得分：${gameResult.score}分 | ${gameResult.gameType}`,
            path: '/pages/index/index?action=play',
            imageUrl: '/images/share-result.png'
        };

        this.shareAppMessage(shareOptions);
    }

    /**
     * Share achievement
     */
    public shareAchievement(achievement: {
        title: string;
        description: string;
        icon: string;
    }): void {
        const shareOptions: ShareOptions = {
            title: `我获得了成就：${achievement.title}`,
            desc: achievement.description,
            path: '/pages/index/index?action=achievements',
            imageUrl: achievement.icon
        };

        this.shareAppMessage(shareOptions);
    }

    /**
     * Share daily check-in
     */
    public shareDailyCheckIn(days: number, reward: string): void {
        const shareOptions: ShareOptions = {
            title: `连续签到${days}天！`,
            desc: `获得奖励：${reward}`,
            path: '/pages/index/index?action=checkin',
            imageUrl: '/images/share-checkin.png'
        };

        this.shareAppMessage(shareOptions);
    }

    /**
     * Generic share app message
     */
    public shareAppMessage(options: ShareOptions): void {
        // Build query string if provided
        let path = options.path || '/pages/index/index';
        if (options.query) {
            const queryString = Object.entries(options.query)
                .map(([key, value]) => `${key}=${encodeURIComponent(value)}`)
                .join('&');
            path += (path.includes('?') ? '&' : '?') + queryString;
        }

        this.wechatAPI.shareAppMessage({
            title: options.title,
            desc: options.desc,
            path: path,
            imageUrl: options.imageUrl
        });
    }

    /**
     * Handle share callback (when user shares)
     */
    public onShareAppMessage(callback: (shareOptions: ShareOptions) => ShareOptions): void {
        if (!this.wechatAPI.isWeChat()) {
            return;
        }

        // @ts-ignore - WeChat API
        wx.onShareAppMessage(() => {
            const defaultOptions: ShareOptions = {
                title: '卡五星麻将',
                desc: '经典三人麻将，快来一起玩吧！',
                path: '/pages/index/index',
                imageUrl: '/images/share-default.png'
            };

            return callback(defaultOptions);
        });
    }

    /**
     * Handle share timeline (moments)
     */
    public onShareTimeline(callback: (shareOptions: ShareOptions) => ShareOptions): void {
        if (!this.wechatAPI.isWeChat()) {
            return;
        }

        // @ts-ignore - WeChat API
        wx.onShareTimeline(() => {
            const defaultOptions: ShareOptions = {
                title: '卡五星麻将 - 经典三人麻将游戏',
                path: '/pages/index/index',
                imageUrl: '/images/share-timeline.png'
            };

            return callback(defaultOptions);
        });
    }

    /**
     * Get share info (for analytics)
     */
    public getShareInfo(): Promise<any> {
        return new Promise((resolve, reject) => {
            if (!this.wechatAPI.isWeChat()) {
                resolve({ shareTickets: [] });
                return;
            }

            // @ts-ignore - WeChat API
            wx.getShareInfo({
                success: resolve,
                fail: reject
            });
        });
    }

    /**
     * Show action sheet for sharing options
     */
    public showShareActionSheet(): Promise<number> {
        return new Promise((resolve, reject) => {
            if (!this.wechatAPI.isWeChat()) {
                resolve(0);
                return;
            }

            // @ts-ignore - WeChat API
            wx.showActionSheet({
                itemList: ['分享给好友', '分享到朋友圈', '复制链接'],
                success: (res: any) => {
                    resolve(res.tapIndex);
                },
                fail: reject
            });
        });
    }

    /**
     * Copy text to clipboard
     */
    public copyToClipboard(text: string): Promise<void> {
        return new Promise((resolve, reject) => {
            if (!this.wechatAPI.isWeChat()) {
                // Fallback for non-WeChat environment
                if (navigator.clipboard) {
                    navigator.clipboard.writeText(text).then(resolve).catch(reject);
                } else {
                    reject(new Error('Clipboard not supported'));
                }
                return;
            }

            // @ts-ignore - WeChat API
            wx.setClipboardData({
                data: text,
                success: () => {
                    this.wechatAPI.showToast({
                        title: '已复制到剪贴板',
                        icon: 'success'
                    });
                    resolve();
                },
                fail: reject
            });
        });
    }

    /**
     * Create share image (for custom sharing)
     */
    public createShareImage(options: {
        title: string;
        subtitle?: string;
        qrCode?: string;
        background?: string;
    }): Promise<string> {
        return new Promise((resolve, reject) => {
            // This would typically involve canvas operations
            // For now, return a placeholder
            setTimeout(() => {
                resolve('/images/generated-share.png');
            }, 100);
        });
    }
}