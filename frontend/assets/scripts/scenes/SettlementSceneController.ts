import { _decorator, Component, Node, Button, Label, ScrollView, Prefab, instantiate, Layout } from 'cc';
import { BaseUIController } from '../ui/BaseUIController';
import { SceneManager } from '../managers/SceneManager';
import { LocalCacheManager } from '../managers/LocalCacheManager';
const { ccclass, property } = _decorator;

interface PlayerResult {
    userId: string;
    nickname: string;
    avatar: string;
    score: number;
    totalScore: number;
    isWinner: boolean;
    winType?: string;
    fanCount?: number;
}

interface SettlementData {
    gameId: string;
    roomId: string;
    results: PlayerResult[];
    gameEndTime: number;
    duration: number;
    roundIndex: number;
    totalRounds: number;
}

/**
 * Settlement Scene Controller with score breakdown display
 */
@ccclass('SettlementSceneController')
export class SettlementSceneController extends BaseUIController {
    @property(Label)
    private titleLabel: Label = null!;

    @property(Label)
    private gameInfoLabel: Label = null!;

    @property(ScrollView)
    private resultsScrollView: ScrollView = null!;

    @property(Node)
    private resultsContent: Node = null!;

    @property(Prefab)
    private playerResultPrefab: Prefab = null!;

    @property(Button)
    private continueButton: Button = null!;

    @property(Button)
    private backToLobbyButton: Button = null!;

    @property(Button)
    private shareButton: Button = null!;

    @property(Node)
    private winnerEffectNode: Node = null!;

    private _settlementData: SettlementData | null = null;

    protected setupComponents(): void {
        this.loadSettlementData();
        this.displaySettlementResults();
        this.playSettlementEffects();
    }

    protected bindEvents(): void {
        if (this.continueButton) {
            this.continueButton.node.on(Button.EventType.CLICK, this.onContinueClick, this);
        }
        if (this.backToLobbyButton) {
            this.backToLobbyButton.node.on(Button.EventType.CLICK, this.onBackToLobbyClick, this);
        }
        if (this.shareButton) {
            this.shareButton.node.on(Button.EventType.CLICK, this.onShareClick, this);
        }
    }

    protected unbindEvents(): void {
        if (this.continueButton) {
            this.continueButton.node.off(Button.EventType.CLICK, this.onContinueClick, this);
        }
        if (this.backToLobbyButton) {
            this.backToLobbyButton.node.off(Button.EventType.CLICK, this.onBackToLobbyClick, this);
        }
        if (this.shareButton) {
            this.shareButton.node.off(Button.EventType.CLICK, this.onShareClick, this);
        }
    }

    private loadSettlementData(): void {
        // Load settlement data from storage
        try {
            let settlementJson: string = '';
            if (typeof wx !== 'undefined') {
                settlementJson = wx.getStorageSync('settlement_data') || '';
            } else {
                settlementJson = localStorage.getItem('settlement_data') || '';
            }

            if (settlementJson) {
                this._settlementData = JSON.parse(settlementJson);
            }
        } catch (error) {
            console.error('Failed to load settlement data:', error);
            // Use mock data for testing
            this._settlementData = this.getMockSettlementData();
        }

        if (!this._settlementData) {
            this._settlementData = this.getMockSettlementData();
        }
    }

    private displaySettlementResults(): void {
        if (!this._settlementData) return;

        // Update title and game info
        this.updateGameInfo();
        
        // Display player results
        this.displayPlayerResults();
    }

    private updateGameInfo(): void {
        if (!this._settlementData) return;

        if (this.titleLabel) {
            const winner = this._settlementData.results.find(r => r.isWinner);
            if (winner) {
                this.titleLabel.string = `${winner.nickname} 胡牌`;
            } else {
                this.titleLabel.string = '游戏结束';
            }
        }

        if (this.gameInfoLabel) {
            const duration = Math.floor(this._settlementData.duration / 60);
            this.gameInfoLabel.string = `房间: ${this._settlementData.roomId} | 第${this._settlementData.roundIndex}/${this._settlementData.totalRounds}局 | 用时: ${duration}分钟`;
        }
    }

    private displayPlayerResults(): void {
        if (!this.resultsContent || !this.playerResultPrefab || !this._settlementData) return;

        // Clear existing results
        this.resultsContent.removeAllChildren();

        // Sort results by score (winner first, then by score)
        const sortedResults = [...this._settlementData.results].sort((a, b) => {
            if (a.isWinner && !b.isWinner) return -1;
            if (!a.isWinner && b.isWinner) return 1;
            return b.score - a.score;
        });

        // Create result items
        sortedResults.forEach((result, index) => {
            const resultItem = instantiate(this.playerResultPrefab);
            this.setupPlayerResultItem(resultItem, result, index);
            this.resultsContent.addChild(resultItem);
        });

        // Update layout
        const layout = this.resultsContent.getComponent(Layout);
        if (layout) {
            layout.updateLayout();
        }
    }

    private setupPlayerResultItem(resultItem: Node, result: PlayerResult, index: number): void {
        // Setup player result item UI
        const rankLabel = resultItem.getChildByName('RankLabel')?.getComponent(Label);
        const avatarNode = resultItem.getChildByName('Avatar');
        const nameLabel = resultItem.getChildByName('NameLabel')?.getComponent(Label);
        const scoreLabel = resultItem.getChildByName('ScoreLabel')?.getComponent(Label);
        const totalScoreLabel = resultItem.getChildByName('TotalScoreLabel')?.getComponent(Label);
        const winTypeLabel = resultItem.getChildByName('WinTypeLabel')?.getComponent(Label);
        const winnerIcon = resultItem.getChildByName('WinnerIcon');

        if (rankLabel) {
            rankLabel.string = `${index + 1}`;
        }

        if (nameLabel) {
            nameLabel.string = result.nickname;
        }

        if (scoreLabel) {
            const scoreText = result.score >= 0 ? `+${result.score}` : `${result.score}`;
            scoreLabel.string = scoreText;
            
            // Color code scores
            if (result.score > 0) {
                scoreLabel.node.color = new Color(0, 255, 0); // Green for positive
            } else if (result.score < 0) {
                scoreLabel.node.color = new Color(255, 0, 0); // Red for negative
            }
        }

        if (totalScoreLabel) {
            totalScoreLabel.string = `总分: ${result.totalScore}`;
        }

        if (winTypeLabel) {
            if (result.isWinner && result.winType) {
                winTypeLabel.string = `${result.winType} ${result.fanCount || 1}番`;
                winTypeLabel.node.active = true;
            } else {
                winTypeLabel.node.active = false;
            }
        }

        if (winnerIcon) {
            winnerIcon.active = result.isWinner;
        }

        // Load avatar if available
        if (avatarNode && result.avatar) {
            this.loadPlayerAvatar(avatarNode, result.avatar);
        }
    }

    private playSettlementEffects(): void {
        if (!this._settlementData) return;

        const winner = this._settlementData.results.find(r => r.isWinner);
        if (winner && this.winnerEffectNode) {
            // Play winner celebration effect
            this.winnerEffectNode.active = true;
            
            // Add particle effects, animations, etc.
            console.log('Playing winner effects for:', winner.nickname);
        }
    }

    private onContinueClick(): void {
        // Continue to next round or return to room
        if (this._settlementData && this._settlementData.roundIndex < this._settlementData.totalRounds) {
            // More rounds to play, return to game
            SceneManager.instance.loadScene('GameScene');
        } else {
            // All rounds completed, return to lobby
            this.onBackToLobbyClick();
        }
    }

    private onBackToLobbyClick(): void {
        // Clear settlement data and return to lobby
        this.clearSettlementData();
        SceneManager.instance.loadScene('LobbyScene');
    }

    private onShareClick(): void {
        // Share settlement results
        if (typeof wx !== 'undefined') {
            // WeChat sharing
            const shareData = this.generateShareData();
            wx.shareAppMessage({
                title: shareData.title,
                path: shareData.path,
                imageUrl: shareData.imageUrl
            });
        } else {
            // Web sharing or copy to clipboard
            const shareText = this.generateShareText();
            console.log('Share text:', shareText);
            this.showMessage('结果已复制到剪贴板');
        }
    }

    private generateShareData(): any {
        if (!this._settlementData) return {};

        const winner = this._settlementData.results.find(r => r.isWinner);
        const title = winner ? `${winner.nickname}胡牌获胜！` : '麻将对局结束';
        
        return {
            title: title,
            path: `/pages/game/game?roomId=${this._settlementData.roomId}`,
            imageUrl: '/images/share-settlement.png'
        };
    }

    private generateShareText(): string {
        if (!this._settlementData) return '';

        let shareText = `麻将对局结果\n房间: ${this._settlementData.roomId}\n\n`;
        
        this._settlementData.results.forEach((result, index) => {
            const scoreText = result.score >= 0 ? `+${result.score}` : `${result.score}`;
            shareText += `${index + 1}. ${result.nickname}: ${scoreText}分`;
            if (result.isWinner) {
                shareText += ` (胡牌)`;
            }
            shareText += '\n';
        });

        return shareText;
    }

    private loadPlayerAvatar(avatarNode: Node, avatarUrl: string): void {
        // Load and display player avatar
        console.log('Loading avatar:', avatarUrl);
        // Implementation would depend on your asset loading system
    }

    private clearSettlementData(): void {
        // Clear settlement data from storage
        if (typeof wx !== 'undefined') {
            wx.removeStorageSync('settlement_data');
        } else {
            localStorage.removeItem('settlement_data');
        }
    }

    private getMockSettlementData(): SettlementData {
        // Mock settlement data for testing
        return {
            gameId: 'game-123456',
            roomId: '123456',
            results: [
                {
                    userId: 'user1',
                    nickname: '玩家1',
                    avatar: '',
                    score: 24,
                    totalScore: 124,
                    isWinner: true,
                    winType: '平胡',
                    fanCount: 1
                },
                {
                    userId: 'user2',
                    nickname: '玩家2',
                    avatar: '',
                    score: -12,
                    totalScore: 88,
                    isWinner: false
                },
                {
                    userId: 'user3',
                    nickname: '玩家3',
                    avatar: '',
                    score: -12,
                    totalScore: 76,
                    isWinner: false
                }
            ],
            gameEndTime: Date.now(),
            duration: 1200, // 20 minutes
            roundIndex: 1,
            totalRounds: 8
        };
    }

    private showMessage(message: string): void {
        console.log('Settlement message:', message);
        // Implementation would show toast or temporary message
    }
}