import { _decorator, Component, Node } from 'cc';
import { NetworkManager, GameMessage } from './NetworkManager';
import { HttpClient } from './HttpClient';
import { MessageSerializer } from './MessageSerializer';

const { ccclass, property } = _decorator;

/**
 * Example usage of the Network Layer
 * Demonstrates WebSocket communication, HTTP requests, and message handling
 */
@ccclass('NetworkExample')
export class NetworkExample extends Component {
    private networkManager: NetworkManager;
    private httpClient: HttpClient;

    onLoad() {
        // Get the NetworkManager instance
        this.networkManager = NetworkManager.instance;
        this.httpClient = new HttpClient(this.networkManager);

        // Set up message handlers
        this.setupMessageHandlers();
    }

    /**
     * Example: Connect to server with authentication
     */
    async connectToServer(token: string): Promise<void> {
        try {
            await this.networkManager.connect(token);
            console.log('Connected to server successfully');
            
            // Send initial message after connection
            this.sendJoinRoomMessage('123456');
        } catch (error) {
            console.error('Failed to connect to server:', error);
        }
    }

    /**
     * Example: Send a join room message
     */
    sendJoinRoomMessage(roomId: string): void {
        const message = MessageSerializer.createRequest('JOIN_ROOM', {
            roomId: roomId
        });

        try {
            this.networkManager.sendMessage(message);
            console.log('Join room message sent');
        } catch (error) {
            console.error('Failed to send join room message:', error);
        }
    }

    /**
     * Example: Send a request and wait for response
     */
    async getUserProfile(userId: string): Promise<any> {
        try {
            const message = MessageSerializer.createRequest('GET_USER_PROFILE', {
                userId: userId
            });

            const response = await this.networkManager.sendRequest(message, 5000);
            console.log('User profile received:', response);
            return response;
        } catch (error) {
            console.error('Failed to get user profile:', error);
            throw error;
        }
    }

    /**
     * Example: Make HTTP API calls
     */
    async makeHttpRequests(): Promise<void> {
        try {
            // GET request
            const rooms = await this.httpClient.get('/rooms');
            console.log('Available rooms:', rooms);

            // POST request
            const newRoom = await this.httpClient.post('/rooms', {
                name: 'My Room',
                maxPlayers: 3
            });
            console.log('Created room:', newRoom);

            // PUT request
            const updatedRoom = await this.httpClient.put(`/rooms/${newRoom.id}`, {
                name: 'Updated Room Name'
            });
            console.log('Updated room:', updatedRoom);

            // DELETE request
            await this.httpClient.delete(`/rooms/${newRoom.id}`);
            console.log('Room deleted');

        } catch (error) {
            console.error('HTTP request failed:', error);
        }
    }

    /**
     * Example: Handle file upload (WeChat Mini Program only)
     */
    async uploadAvatar(filePath: string): Promise<void> {
        try {
            const result = await this.httpClient.uploadFile('/upload/avatar', filePath, 'avatar');
            console.log('Avatar uploaded:', result);
        } catch (error) {
            console.error('Failed to upload avatar:', error);
        }
    }

    /**
     * Example: Handle reconnection
     */
    async handleReconnection(): Promise<void> {
        try {
            await this.networkManager.forceReconnect();
            console.log('Reconnected successfully');
        } catch (error) {
            console.error('Reconnection failed:', error);
        }
    }

    /**
     * Set up message handlers for different game events
     */
    private setupMessageHandlers(): void {
        // Handle room joined event
        this.networkManager.onMessage('ROOM_JOINED', (message: GameMessage) => {
            console.log('Joined room:', message.data);
            // Update UI to show room state
        });

        // Handle player joined event
        this.networkManager.onMessage('PLAYER_JOINED', (message: GameMessage) => {
            console.log('Player joined:', message.data);
            // Update player list UI
        });

        // Handle game started event
        this.networkManager.onMessage('GAME_STARTED', (message: GameMessage) => {
            console.log('Game started:', message.data);
            // Switch to game scene
        });

        // Handle game state update
        this.networkManager.onMessage('GAME_STATE_UPDATE', (message: GameMessage) => {
            console.log('Game state updated:', message.data);
            // Update game UI with new state
        });

        // Handle player action
        this.networkManager.onMessage('PLAYER_ACTION', (message: GameMessage) => {
            console.log('Player action:', message.data);
            // Process player action and update UI
        });

        // Handle game ended event
        this.networkManager.onMessage('GAME_ENDED', (message: GameMessage) => {
            console.log('Game ended:', message.data);
            // Show settlement screen
        });

        // Handle connection lost
        this.networkManager.onMessage('CONNECTION_LOST', (message: GameMessage) => {
            console.warn('Connection lost, attempting to reconnect...');
            // Show reconnection UI
        });

        // Handle reconnection success
        this.networkManager.onMessage('RECONNECTED', (message: GameMessage) => {
            console.log('Reconnected successfully');
            // Hide reconnection UI and sync state
        });

        // Handle errors
        this.networkManager.onMessage('ERROR', (message: GameMessage) => {
            console.error('Server error:', message.data);
            // Show error message to user
        });
    }

    /**
     * Example: Send game actions
     */
    sendGameAction(action: string, data: any): void {
        const message = MessageSerializer.createEvent(action, data, this.getCurrentRoomId());
        
        try {
            this.networkManager.sendMessage(message);
            console.log(`Game action sent: ${action}`);
        } catch (error) {
            console.error(`Failed to send game action ${action}:`, error);
        }
    }

    /**
     * Example: Play a tile
     */
    playTile(tile: string): void {
        this.sendGameAction('PLAY_TILE', { tile });
    }

    /**
     * Example: Peng action
     */
    pengTile(tile: string): void {
        this.sendGameAction('PENG', { tile });
    }

    /**
     * Example: Gang action
     */
    gangTile(tile: string, type: 'MING' | 'AN' | 'BU'): void {
        this.sendGameAction('GANG', { tile, type });
    }

    /**
     * Example: Hu (win) action
     */
    huTile(tile?: string): void {
        this.sendGameAction('HU', { tile });
    }

    /**
     * Get current room ID (would be stored in game state)
     */
    private getCurrentRoomId(): string {
        // This would typically come from your game state manager
        return '123456';
    }

    /**
     * Example: Monitor connection state
     */
    monitorConnection(): void {
        setInterval(() => {
            const state = this.networkManager.getConnectionState();
            console.log('Connection state:', {
                isConnected: state.isConnected,
                reconnectAttempts: state.reconnectAttempts,
                lastHeartbeat: new Date(state.lastHeartbeat).toISOString()
            });
        }, 10000); // Check every 10 seconds
    }

    onDestroy() {
        // Clean up when component is destroyed
        this.networkManager.disconnect();
    }
}