import { _decorator, Component, Node, Button, Label, ScrollView, EditBox, Prefab, instantiate, Layout, Toggle } from 'cc';
import { BaseUIController } from '../ui/BaseUIController';
import { SceneManager } from '../managers/SceneManager';
import { HttpClient } from '../network/HttpClient';
import { LocalCacheManager } from '../managers/LocalCacheManager';
const { ccclass, property } = _decorator;

interface GameHistoryItem {
    gameId: string;
    roomId: string;
    startTime: number;
    endTime: number;
    duration: number;
    playerCount: number;
    result: 'WIN' | 'LOSE' | 'DRAW';
    score: number;
    winnerName?: string;
    winType?: string;
    canReplay: boolean;
}

interface HistoryFilter {
    dateRange: 'ALL' | 'TODAY' | 'WEEK' | 'MONTH';
    result: 'ALL' | 'WIN' | 'LOSE' | 'DRAW';
    roomId?: string;
}

/**
 * History Scene Controller with game history list and replay functionality
 */
@ccclass('HistorySceneController')
export class HistorySceneController extends BaseUIController {
    // Header
    @property(Label)
    private titleLabel: Label = null!;

    @property(Button)
    private backButton: Button = null!;

    @property(Button)
    private refreshButton: Button = null!;

    // Filter controls
    @property(Node)
    private filterPanel: Node = null!;

    @property(Toggle)
    private filterAllToggle: Toggle = null!;

    @property(Toggle)
    private filterTodayToggle: Toggle = null!;

    @property(Toggle)
    private filterWeekToggle: Toggle = null!;

    @property(Toggle)
    private filterMonthToggle: Toggle = null!;

    @property(Toggle)
    private resultAllToggle: Toggle = null!;

    @property(Toggle)
    private resultWinToggle: Toggle = null!;

    @property(Toggle)
    private resultLoseToggle: Toggle = null!;

    @property(EditBox)
    private roomIdFilterInput: EditBox = null!;

    @property(Button)
    private applyFilterButton: Button = null!;

    // Statistics
    @property(Node)
    private statsPanel: Node = null!;

    @property(Label)
    private totalGamesLabel: Label = null!;

    @property(Label)
    private winRateLabel: Label = null!;

    @property(Label)
    private totalScoreLabel: Label = null!;

    @property(Label)
    private avgScoreLabel: Label = null!;

    // History list
    @property(ScrollView)
    private historyScrollView: ScrollView = null!;

    @property(Node)
    private historyContent: Node = null!;

    @property(Prefab)
    private historyItemPrefab: Prefab = null!;

    @property(Node)
    private loadingIndicator: Node = null!;

    @property(Node)
    private emptyStateNode: Node = null!;

    // Pagination
    @property(Button)
    private loadMoreButton: Button = null!;

    private _historyItems: GameHistoryItem[] = [];
    private _currentFilter: HistoryFilter = {
        dateRange: 'ALL',
        result: 'ALL'
    };
    private _currentPage: number = 0;
    private _pageSize: number = 20;
    private _hasMoreData: boolean = true;
    private _isLoading: boolean = false;

    protected setupComponents(): void {
        this.initializeFilters();
        this.loadGameHistory();
        this.updateStatistics();
    }

    protected bindEvents(): void {
        if (this.backButton) {
            this.backButton.node.on(Button.EventType.CLICK, this.onBackClick, this);
        }
        if (this.refreshButton) {
            this.refreshButton.node.on(Button.EventType.CLICK, this.onRefreshClick, this);
        }
        if (this.applyFilterButton) {
            this.applyFilterButton.node.on(Button.EventType.CLICK, this.onApplyFilterClick, this);
        }
        if (this.loadMoreButton) {
            this.loadMoreButton.node.on(Button.EventType.CLICK, this.onLoadMoreClick, this);
        }

        // Bind filter toggles
        this.bindFilterToggles();
    }

    protected unbindEvents(): void {
        if (this.backButton) {
            this.backButton.node.off(Button.EventType.CLICK, this.onBackClick, this);
        }
        if (this.refreshButton) {
            this.refreshButton.node.off(Button.EventType.CLICK, this.onRefreshClick, this);
        }
        if (this.applyFilterButton) {
            this.applyFilterButton.node.off(Button.EventType.CLICK, this.onApplyFilterClick, this);
        }
        if (this.loadMoreButton) {
            this.loadMoreButton.node.off(Button.EventType.CLICK, this.onLoadMoreClick, this);
        }

        this.unbindFilterToggles();
    }

    private bindFilterToggles(): void {
        // Date range toggles
        if (this.filterAllToggle) {
            this.filterAllToggle.node.on(Toggle.EventType.TOGGLE, () => this.onDateFilterChange('ALL'), this);
        }
        if (this.filterTodayToggle) {
            this.filterTodayToggle.node.on(Toggle.EventType.TOGGLE, () => this.onDateFilterChange('TODAY'), this);
        }
        if (this.filterWeekToggle) {
            this.filterWeekToggle.node.on(Toggle.EventType.TOGGLE, () => this.onDateFilterChange('WEEK'), this);
        }
        if (this.filterMonthToggle) {
            this.filterMonthToggle.node.on(Toggle.EventType.TOGGLE, () => this.onDateFilterChange('MONTH'), this);
        }

        // Result toggles
        if (this.resultAllToggle) {
            this.resultAllToggle.node.on(Toggle.EventType.TOGGLE, () => this.onResultFilterChange('ALL'), this);
        }
        if (this.resultWinToggle) {
            this.resultWinToggle.node.on(Toggle.EventType.TOGGLE, () => this.onResultFilterChange('WIN'), this);
        }
        if (this.resultLoseToggle) {
            this.resultLoseToggle.node.on(Toggle.EventType.TOGGLE, () => this.onResultFilterChange('LOSE'), this);
        }
    }

    private unbindFilterToggles(): void {
        if (this.filterAllToggle) {
            this.filterAllToggle.node.off(Toggle.EventType.TOGGLE);
        }
        if (this.filterTodayToggle) {
            this.filterTodayToggle.node.off(Toggle.EventType.TOGGLE);
        }
        if (this.filterWeekToggle) {
            this.filterWeekToggle.node.off(Toggle.EventType.TOGGLE);
        }
        if (this.filterMonthToggle) {
            this.filterMonthToggle.node.off(Toggle.EventType.TOGGLE);
        }
        if (this.resultAllToggle) {
            this.resultAllToggle.node.off(Toggle.EventType.TOGGLE);
        }
        if (this.resultWinToggle) {
            this.resultWinToggle.node.off(Toggle.EventType.TOGGLE);
        }
        if (this.resultLoseToggle) {
            this.resultLoseToggle.node.off(Toggle.EventType.TOGGLE);
        }
    }

    private initializeFilters(): void {
        // Set default filter states
        if (this.filterAllToggle) {
            this.filterAllToggle.isChecked = true;
        }
        if (this.resultAllToggle) {
            this.resultAllToggle.isChecked = true;
        }
    }

    private onBackClick(): void {
        SceneManager.instance.loadScene('LobbyScene');
    }

    private onRefreshClick(): void {
        this._currentPage = 0;
        this._historyItems = [];
        this._hasMoreData = true;
        this.loadGameHistory();
    }

    private onApplyFilterClick(): void {
        // Apply current filter settings
        this.updateFilterFromUI();
        this.onRefreshClick();
    }

    private onLoadMoreClick(): void {
        if (!this._isLoading && this._hasMoreData) {
            this._currentPage++;
            this.loadGameHistory(false);
        }
    }

    private onDateFilterChange(dateRange: string): void {
        this._currentFilter.dateRange = dateRange as any;
    }

    private onResultFilterChange(result: string): void {
        this._currentFilter.result = result as any;
    }

    private updateFilterFromUI(): void {
        // Update filter from room ID input
        if (this.roomIdFilterInput) {
            const roomId = this.roomIdFilterInput.string.trim();
            this._currentFilter.roomId = roomId || undefined;
        }
    }

    private async loadGameHistory(clearExisting: boolean = true): Promise<void> {
        if (this._isLoading) return;

        this._isLoading = true;
        this.showLoading(true);

        try {
            const response = await HttpClient.instance.get('/api/game/history', {
                params: {
                    page: this._currentPage,
                    size: this._pageSize,
                    dateRange: this._currentFilter.dateRange,
                    result: this._currentFilter.result,
                    roomId: this._currentFilter.roomId
                }
            });

            if (response.success) {
                const newItems: GameHistoryItem[] = response.data.items || [];
                
                if (clearExisting) {
                    this._historyItems = newItems;
                } else {
                    this._historyItems.push(...newItems);
                }

                this._hasMoreData = newItems.length === this._pageSize;
                this.updateHistoryList();
                this.updateStatistics();
            } else {
                this.showMessage('加载历史记录失败: ' + response.message);
            }
        } catch (error) {
            console.error('Failed to load game history:', error);
            // Use mock data for testing
            if (this._historyItems.length === 0) {
                this._historyItems = this.getMockHistoryData();
                this.updateHistoryList();
                this.updateStatistics();
            }
        } finally {
            this._isLoading = false;
            this.showLoading(false);
        }
    }

    private updateHistoryList(): void {
        if (!this.historyContent || !this.historyItemPrefab) return;

        // Clear existing items if this is a refresh
        if (this._currentPage === 0) {
            this.historyContent.removeAllChildren();
        }

        // Show empty state if no items
        if (this._historyItems.length === 0) {
            this.showEmptyState(true);
            return;
        }

        this.showEmptyState(false);

        // Create history item nodes
        const startIndex = this._currentPage * this._pageSize;
        const endIndex = Math.min(startIndex + this._pageSize, this._historyItems.length);

        for (let i = startIndex; i < endIndex; i++) {
            const item = this._historyItems[i];
            const itemNode = instantiate(this.historyItemPrefab);
            this.setupHistoryItem(itemNode, item);
            this.historyContent.addChild(itemNode);
        }

        // Update layout
        const layout = this.historyContent.getComponent(Layout);
        if (layout) {
            layout.updateLayout();
        }

        // Update load more button
        if (this.loadMoreButton) {
            this.loadMoreButton.node.active = this._hasMoreData;
        }
    }

    private setupHistoryItem(itemNode: Node, item: GameHistoryItem): void {
        // Setup history item UI
        const dateLabel = itemNode.getChildByName('DateLabel')?.getComponent(Label);
        const roomIdLabel = itemNode.getChildByName('RoomIdLabel')?.getComponent(Label);
        const durationLabel = itemNode.getChildByName('DurationLabel')?.getComponent(Label);
        const resultLabel = itemNode.getChildByName('ResultLabel')?.getComponent(Label);
        const scoreLabel = itemNode.getChildByName('ScoreLabel')?.getComponent(Label);
        const winnerLabel = itemNode.getChildByName('WinnerLabel')?.getComponent(Label);
        const replayButton = itemNode.getChildByName('ReplayButton')?.getComponent(Button);
        const detailButton = itemNode.getChildByName('DetailButton')?.getComponent(Button);

        if (dateLabel) {
            const date = new Date(item.startTime);
            dateLabel.string = this.formatDate(date);
        }

        if (roomIdLabel) {
            roomIdLabel.string = `房间: ${item.roomId}`;
        }

        if (durationLabel) {
            const minutes = Math.floor(item.duration / 60);
            const seconds = item.duration % 60;
            durationLabel.string = `${minutes}:${seconds.toString().padStart(2, '0')}`;
        }

        if (resultLabel) {
            const resultText = this.getResultText(item.result);
            resultLabel.string = resultText;
            
            // Color code results
            switch (item.result) {
                case 'WIN':
                    resultLabel.node.color = new Color(0, 255, 0); // Green
                    break;
                case 'LOSE':
                    resultLabel.node.color = new Color(255, 0, 0); // Red
                    break;
                case 'DRAW':
                    resultLabel.node.color = new Color(128, 128, 128); // Gray
                    break;
            }
        }

        if (scoreLabel) {
            const scoreText = item.score >= 0 ? `+${item.score}` : `${item.score}`;
            scoreLabel.string = scoreText;
            scoreLabel.node.color = item.score >= 0 ? new Color(0, 255, 0) : new Color(255, 0, 0);
        }

        if (winnerLabel) {
            if (item.winnerName && item.result !== 'WIN') {
                winnerLabel.string = `胜者: ${item.winnerName}`;
                winnerLabel.node.active = true;
            } else {
                winnerLabel.node.active = false;
            }
        }

        // Bind buttons
        if (replayButton) {
            replayButton.interactable = item.canReplay;
            replayButton.node.on(Button.EventType.CLICK, () => {
                this.onReplayClick(item);
            });
        }

        if (detailButton) {
            detailButton.node.on(Button.EventType.CLICK, () => {
                this.onDetailClick(item);
            });
        }
    }

    private updateStatistics(): void {
        if (this._historyItems.length === 0) return;

        const totalGames = this._historyItems.length;
        const wins = this._historyItems.filter(item => item.result === 'WIN').length;
        const winRate = totalGames > 0 ? (wins / totalGames * 100).toFixed(1) : '0.0';
        const totalScore = this._historyItems.reduce((sum, item) => sum + item.score, 0);
        const avgScore = totalGames > 0 ? (totalScore / totalGames).toFixed(1) : '0.0';

        if (this.totalGamesLabel) {
            this.totalGamesLabel.string = totalGames.toString();
        }
        if (this.winRateLabel) {
            this.winRateLabel.string = `${winRate}%`;
        }
        if (this.totalScoreLabel) {
            this.totalScoreLabel.string = totalScore.toString();
        }
        if (this.avgScoreLabel) {
            this.avgScoreLabel.string = avgScore;
        }
    }

    private onReplayClick(item: GameHistoryItem): void {
        console.log('Starting replay for game:', item.gameId);
        
        // Store replay data and navigate to replay scene
        const replayData = {
            gameId: item.gameId,
            roomId: item.roomId,
            isReplay: true
        };

        if (typeof wx !== 'undefined') {
            wx.setStorageSync('replay_data', replayData);
        } else {
            localStorage.setItem('replay_data', JSON.stringify(replayData));
        }

        // Navigate to game scene in replay mode
        SceneManager.instance.loadScene('GameScene');
    }

    private onDetailClick(item: GameHistoryItem): void {
        console.log('Showing details for game:', item.gameId);
        
        // Show detailed game information popup
        this.showGameDetails(item);
    }

    private showGameDetails(item: GameHistoryItem): void {
        // Implementation would show a detailed popup with game information
        console.log('Game details:', item);
        this.showMessage('详细信息功能开发中...');
    }

    private formatDate(date: Date): string {
        const now = new Date();
        const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
        const itemDate = new Date(date.getFullYear(), date.getMonth(), date.getDate());

        if (itemDate.getTime() === today.getTime()) {
            return `今天 ${date.getHours().toString().padStart(2, '0')}:${date.getMinutes().toString().padStart(2, '0')}`;
        } else if (itemDate.getTime() === today.getTime() - 24 * 60 * 60 * 1000) {
            return `昨天 ${date.getHours().toString().padStart(2, '0')}:${date.getMinutes().toString().padStart(2, '0')}`;
        } else {
            return `${date.getMonth() + 1}/${date.getDate()} ${date.getHours().toString().padStart(2, '0')}:${date.getMinutes().toString().padStart(2, '0')}`;
        }
    }

    private getResultText(result: string): string {
        switch (result) {
            case 'WIN': return '胜利';
            case 'LOSE': return '失败';
            case 'DRAW': return '平局';
            default: return '未知';
        }
    }

    private showLoading(show: boolean): void {
        if (this.loadingIndicator) {
            this.loadingIndicator.active = show;
        }
    }

    private showEmptyState(show: boolean): void {
        if (this.emptyStateNode) {
            this.emptyStateNode.active = show;
        }
    }

    private getMockHistoryData(): GameHistoryItem[] {
        // Mock history data for testing
        const now = Date.now();
        return [
            {
                gameId: 'game-001',
                roomId: '123456',
                startTime: now - 3600000, // 1 hour ago
                endTime: now - 3000000,   // 50 minutes ago
                duration: 600, // 10 minutes
                playerCount: 3,
                result: 'WIN',
                score: 24,
                winType: '平胡',
                canReplay: true
            },
            {
                gameId: 'game-002',
                roomId: '789012',
                startTime: now - 7200000, // 2 hours ago
                endTime: now - 6600000,   // 1h50m ago
                duration: 600,
                playerCount: 3,
                result: 'LOSE',
                score: -12,
                winnerName: '玩家2',
                canReplay: true
            },
            {
                gameId: 'game-003',
                roomId: '345678',
                startTime: now - 86400000, // 1 day ago
                endTime: now - 86400000 + 900000, // 1 day ago + 15 minutes
                duration: 900,
                playerCount: 3,
                result: 'DRAW',
                score: 0,
                canReplay: false
            }
        ];
    }

    private showMessage(message: string): void {
        console.log('History message:', message);
        // Implementation would show toast or temporary message
    }
}