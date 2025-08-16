import { _decorator, Component, Button, EditBox, Label, Node, ScrollView, Prefab, instantiate, Layout } from 'cc';
import { BaseUIController } from '../ui/BaseUIController';
import { SceneManager } from '../managers/SceneManager';
import { NetworkManager } from '../network/NetworkManager';
import { LocalCacheManager } from '../managers/LocalCacheManager';
import { HttpClient } from '../network/HttpClient';
const { ccclass, property } = _decorator;

interface RoomInfo {
    id: string;
    ownerId: string;
    ownerName: string;
    playerCount: number;
    maxPlayers: number;
    status: string;
    config: any;
}

interface UserProfile {
    openId: string;
    nickname: string;
    avatar: string;
    coins: number;
    roomCards: number;
    level: number;
    winRate: number;
}

/**
 * Lobby Scene Controller with room creation and joining interface
 */
@ccclass('LobbySceneController')
export class LobbySceneController extends BaseUIController {
    @property(Button)
    private createRoomButton: Button = null!;

    @property(Button)
    private joinRoomButton: Button = null!;

    @property(Button)
    private quickJoinButton: Button = null!;

    @property(Button)
    private historyButton: Button = null!;

    @property(Button)
    private settingsButton: Button = null!;

    @property(Button)
    private logoutButton: Button = null!;

    @property(EditBox)
    private roomIdInput: EditBox = null!;

    @property(Label)
    private playerInfoLabel: Label = null!;

    @property(Label)
    private coinsLabel: Label = null!;

    @property(Label)
    private roomCardsLabel: Label = null!;

    @property(Node)
    private playerAvatarNode: Node = null!;

    @property(ScrollView)
    private roomListScrollView: ScrollView = null!;

    @property(Node)
    private roomListContent: Node = null!;

    @property(Prefab)
    private roomItemPrefab: Prefab = null!;

    @property(Node)
    private createRoomDialog: Node = null!;

    @property(Node)
    private roomConfigPanel: Node = null!;

    private _userProfile: UserProfile | null = null;
    private _roomList: RoomInfo[] = [];
    private _refreshTimer: number = 0;

    protected setupComponents(): void {
        this.loadUserProfile();
        this.updatePlayerInfo();
        this.loadRoomList();
        this.startRefreshTimer();
        
        // Hide dialogs initially
        if (this.createRoomDialog) {
            this.createRoomDialog.active = false;
        }
    }

    protected bindEvents(): void {
        if (this.createRoomButton) {
            this.createRoomButton.node.on(Button.EventType.CLICK, this.onCreateRoomClick, this);
        }
        if (this.joinRoomButton) {
            this.joinRoomButton.node.on(Button.EventType.CLICK, this.onJoinRoomClick, this);
        }
        if (this.quickJoinButton) {
            this.quickJoinButton.node.on(Button.EventType.CLICK, this.onQuickJoinClick, this);
        }
        if (this.historyButton) {
            this.historyButton.node.on(Button.EventType.CLICK, this.onHistoryClick, this);
        }
        if (this.settingsButton) {
            this.settingsButton.node.on(Button.EventType.CLICK, this.onSettingsClick, this);
        }
        if (this.logoutButton) {
            this.logoutButton.node.on(Button.EventType.CLICK, this.onLogoutClick, this);
        }

        // Register network message handlers
        NetworkManager.instance.onMessage('RoomCreated', this.onRoomCreated.bind(this));
        NetworkManager.instance.onMessage('RoomJoined', this.onRoomJoined.bind(this));
        NetworkManager.instance.onMessage('RoomError', this.onRoomError.bind(this));
        NetworkManager.instance.onMessage('RoomListUpdate', this.onRoomListUpdate.bind(this));
    }

    protected unbindEvents(): void {
        if (this.createRoomButton) {
            this.createRoomButton.node.off(Button.EventType.CLICK, this.onCreateRoomClick, this);
        }
        if (this.joinRoomButton) {
            this.joinRoomButton.node.off(Button.EventType.CLICK, this.onJoinRoomClick, this);
        }
        if (this.quickJoinButton) {
            this.quickJoinButton.node.off(Button.EventType.CLICK, this.onQuickJoinClick, this);
        }
        if (this.historyButton) {
            this.historyButton.node.off(Button.EventType.CLICK, this.onHistoryClick, this);
        }
        if (this.settingsButton) {
            this.settingsButton.node.off(Button.EventType.CLICK, this.onSettingsClick, this);
        }
        if (this.logoutButton) {
            this.logoutButton.node.off(Button.EventType.CLICK, this.onLogoutClick, this);
        }

        // Unregister network message handlers
        NetworkManager.instance.offMessage('RoomCreated', this.onRoomCreated);
        NetworkManager.instance.offMessage('RoomJoined', this.onRoomJoined);
        NetworkManager.instance.offMessage('RoomError', this.onRoomError);
        NetworkManager.instance.offMessage('RoomListUpdate', this.onRoomListUpdate);
        
        // Clear refresh timer
        if (this._refreshTimer > 0) {
            clearInterval(this._refreshTimer);
        }
    }

    private onCreateRoomClick(): void {
        // Show create room dialog
        if (this.createRoomDialog) {
            this.createRoomDialog.active = true;
        } else {
            // Direct create with default config
            this.createRoomWithConfig(this.getDefaultRoomConfig());
        }
    }

    private createRoomWithConfig(config: any): void {
        // Check if user has enough room cards
        if (this._userProfile && this._userProfile.roomCards <= 0) {
            this.showMessage('房卡不足，无法创建房间');
            return;
        }

        // Send create room request
        NetworkManager.instance.sendMessage({
            type: 'REQ',
            cmd: 'CreateRoom',
            data: {
                config: config
            },
            timestamp: Date.now()
        });
    }

    private onJoinRoomClick(): void {
        const roomId = this.roomIdInput?.string?.trim();
        if (!roomId) {
            this.showMessage('请输入房间号');
            return;
        }

        if (roomId.length !== 6 || !/^\d+$/.test(roomId)) {
            this.showMessage('房间号格式错误，请输入6位数字');
            return;
        }

        this.joinRoom(roomId);
    }

    private onQuickJoinClick(): void {
        // Find an available room to join
        const availableRoom = this._roomList.find(room => 
            room.playerCount < room.maxPlayers && room.status === 'WAITING'
        );

        if (availableRoom) {
            this.joinRoom(availableRoom.id);
        } else {
            this.showMessage('暂无可加入的房间');
        }
    }

    private joinRoom(roomId: string): void {
        // Send join room request
        NetworkManager.instance.sendMessage({
            type: 'REQ',
            cmd: 'JoinRoom',
            data: {
                roomId: roomId
            },
            timestamp: Date.now()
        });
    }

    private onHistoryClick(): void {
        SceneManager.instance.loadScene('HistoryScene');
    }

    private onSettingsClick(): void {
        // Show settings dialog or navigate to settings scene
        this.showMessage('设置功能开发中...');
    }

    private onLogoutClick(): void {
        // Clear auth data and return to login
        LocalCacheManager.instance.clearAuthToken();
        LocalCacheManager.instance.clearUserInfo();
        SceneManager.instance.loadScene('LoginScene');
    }

    private onRoomCreated(message: any): void {
        const roomId = message.data.roomId;
        console.log('Room created:', roomId);
        this.hideCreateRoomDialog();
        this.enterRoom(roomId);
    }

    private onRoomJoined(message: any): void {
        const roomId = message.data.roomId;
        console.log('Room joined:', roomId);
        this.enterRoom(roomId);
    }

    private onRoomError(message: any): void {
        const error = message.data.error;
        this.showMessage('房间操作失败: ' + error);
    }

    private onRoomListUpdate(message: any): void {
        this._roomList = message.data.rooms || [];
        this.updateRoomListUI();
    }

    private enterRoom(roomId: string): void {
        // Store room ID for game scene
        if (typeof wx !== 'undefined') {
            wx.setStorageSync('current_room_id', roomId);
        } else {
            localStorage.setItem('current_room_id', roomId);
        }

        // Navigate to game scene
        SceneManager.instance.loadScene('GameScene');
    }

    private async loadUserProfile(): Promise<void> {
        try {
            // Try to get from cache first
            const cachedUserInfo = LocalCacheManager.instance.getUserInfo();
            if (cachedUserInfo) {
                this._userProfile = {
                    ...cachedUserInfo,
                    coins: 1000, // These would come from API
                    roomCards: 10,
                    level: 1,
                    winRate: 0.65
                };
            }

            // Fetch latest from server
            const response = await HttpClient.instance.get('/api/user/profile');
            if (response.success) {
                this._userProfile = response.data;
                LocalCacheManager.instance.setUserInfo(this._userProfile);
            }
        } catch (error) {
            console.error('Failed to load user profile:', error);
            // Use cached data or defaults
        }
    }

    private updatePlayerInfo(): void {
        if (!this._userProfile) return;

        if (this.playerInfoLabel) {
            this.playerInfoLabel.string = `${this._userProfile.nickname} | Lv.${this._userProfile.level}`;
        }
        if (this.coinsLabel) {
            this.coinsLabel.string = this._userProfile.coins.toString();
        }
        if (this.roomCardsLabel) {
            this.roomCardsLabel.string = this._userProfile.roomCards.toString();
        }
        
        // Load avatar if available
        if (this.playerAvatarNode && this._userProfile.avatar) {
            this.loadPlayerAvatar(this._userProfile.avatar);
        }
    }

    private loadPlayerAvatar(avatarUrl: string): void {
        // Load avatar image - implementation depends on Cocos Creator version
        console.log('Loading avatar:', avatarUrl);
    }

    private async loadRoomList(): Promise<void> {
        try {
            // Request room list from server
            NetworkManager.instance.sendMessage({
                type: 'REQ',
                cmd: 'GetRoomList',
                timestamp: Date.now()
            });
        } catch (error) {
            console.error('Failed to load room list:', error);
        }
    }

    private updateRoomListUI(): void {
        if (!this.roomListContent) return;

        // Clear existing room items
        this.roomListContent.removeAllChildren();

        // Create room items
        this._roomList.forEach(room => {
            if (this.roomItemPrefab) {
                const roomItem = instantiate(this.roomItemPrefab);
                this.setupRoomItem(roomItem, room);
                this.roomListContent.addChild(roomItem);
            }
        });

        // Update layout
        const layout = this.roomListContent.getComponent(Layout);
        if (layout) {
            layout.updateLayout();
        }
    }

    private setupRoomItem(roomItem: Node, room: RoomInfo): void {
        // Setup room item UI with room data
        const roomIdLabel = roomItem.getChildByName('RoomIdLabel')?.getComponent(Label);
        const ownerLabel = roomItem.getChildByName('OwnerLabel')?.getComponent(Label);
        const playerCountLabel = roomItem.getChildByName('PlayerCountLabel')?.getComponent(Label);
        const joinButton = roomItem.getChildByName('JoinButton')?.getComponent(Button);

        if (roomIdLabel) {
            roomIdLabel.string = room.id;
        }
        if (ownerLabel) {
            ownerLabel.string = room.ownerName;
        }
        if (playerCountLabel) {
            playerCountLabel.string = `${room.playerCount}/${room.maxPlayers}`;
        }
        if (joinButton) {
            joinButton.node.on(Button.EventType.CLICK, () => {
                this.joinRoom(room.id);
            });
            
            // Disable if room is full
            joinButton.interactable = room.playerCount < room.maxPlayers;
        }
    }

    private startRefreshTimer(): void {
        // Refresh room list every 10 seconds
        this._refreshTimer = setInterval(() => {
            this.loadRoomList();
        }, 10000);
    }

    private hideCreateRoomDialog(): void {
        if (this.createRoomDialog) {
            this.createRoomDialog.active = false;
        }
    }

    private getDefaultRoomConfig(): any {
        return {
            players: 3,
            tiles: 'WAN_ONLY',
            allowPeng: true,
            allowGang: true,
            allowChi: false,
            huTypes: {
                basicWin: true,
                sevenPairs: true,
                allPungs: true
            },
            score: {
                baseFan: 1,
                maxFan: 8,
                dealerMultiplier: 2
            },
            turn: {
                timeLimit: 15,
                autoTrustee: true
            }
        };
    }

    private showMessage(message: string): void {
        console.log('Message:', message);
        // In real implementation, show toast or dialog
    }
}