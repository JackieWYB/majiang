import { _decorator, Component } from 'cc';
const { ccclass, property } = _decorator;

export interface GameMessage {
    type: 'EVENT' | 'REQ' | 'RESP' | 'ERROR';
    cmd: string;
    reqId?: string;
    roomId?: string;
    data?: any;
    timestamp: number;
}

export interface NetworkConfig {
    serverUrl: string;
    httpBaseUrl: string;
    heartbeatInterval: number;
    reconnectAttempts: number;
    reconnectDelay: number;
    connectionTimeout: number;
    messageTimeout: number;
}

export interface ConnectionState {
    isConnected: boolean;
    reconnectAttempts: number;
    lastHeartbeat: number;
    connectionStartTime: number;
}

export interface HttpRequestOptions {
    method: 'GET' | 'POST' | 'PUT' | 'DELETE';
    headers?: Record<string, string>;
    body?: any;
    timeout?: number;
}

export interface HttpResponse<T = any> {
    data: T;
    status: number;
    statusText: string;
    headers: Record<string, string>;
}

export class NetworkError extends Error {
    public code: string;
    public details?: any;

    constructor(message: string, code: string, details?: any) {
        super(message);
        this.name = 'NetworkError';
        this.code = code;
        this.details = details;
    }
}

export class ConnectionError extends NetworkError {
    constructor(message: string, details?: any) {
        super(message, 'CONNECTION_ERROR', details);
    }
}

export class TimeoutError extends NetworkError {
    constructor(message: string, details?: any) {
        super(message, 'TIMEOUT_ERROR', details);
    }
}

export class AuthenticationError extends NetworkError {
    constructor(message: string, details?: any) {
        super(message, 'AUTH_ERROR', details);
    }
}

/**
 * Network Manager for WebSocket and HTTP communication
 * Handles automatic reconnection, heartbeat, message serialization, and error handling
 */
@ccclass('NetworkManager')
export class NetworkManager extends Component {
    private static _instance: NetworkManager;
    private _socket: WebSocket | null = null;
    private _config: NetworkConfig;
    private _connectionState: ConnectionState;
    private _heartbeatTimer: number = 0;
    private _connectionTimer: number = 0;
    private _messageHandlers: Map<string, Function[]> = new Map();
    private _pendingRequests: Map<string, { resolve: Function; reject: Function; timer: number }> = new Map();
    private _authToken: string = '';
    private _reconnectTimer: number = 0;
    private _isReconnecting: boolean = false;

    public static get instance(): NetworkManager {
        return this._instance;
    }

    onLoad() {
        if (NetworkManager._instance) {
            this.node.destroy();
            return;
        }
        NetworkManager._instance = this;

        // Default configuration
        this._config = {
            serverUrl: 'ws://localhost:8080/game',
            httpBaseUrl: 'http://localhost:8080/api',
            heartbeatInterval: 30000, // 30 seconds
            reconnectAttempts: 5,
            reconnectDelay: 1000,
            connectionTimeout: 10000, // 10 seconds
            messageTimeout: 5000 // 5 seconds
        };

        // Initialize connection state
        this._connectionState = {
            isConnected: false,
            reconnectAttempts: 0,
            lastHeartbeat: 0,
            connectionStartTime: 0
        };
    }

    /**
     * Connect to WebSocket server with enhanced error handling and timeout
     */
    public async connect(token: string): Promise<void> {
        if (this._isReconnecting) {
            throw new ConnectionError('Already attempting to reconnect');
        }

        this._authToken = token;
        this._connectionState.connectionStartTime = Date.now();

        return new Promise((resolve, reject) => {
            // Set connection timeout
            const timeoutTimer = setTimeout(() => {
                this.cleanup();
                reject(new TimeoutError('Connection timeout'));
            }, this._config.connectionTimeout);

            try {
                // For WeChat Mini Program, use wx.connectSocket
                if (typeof wx !== 'undefined') {
                    this._socket = wx.connectSocket({
                        url: `${this._config.serverUrl}?token=${token}`,
                        protocols: ['websocket']
                    }) as any;
                } else {
                    // For web/simulator
                    this._socket = new WebSocket(`${this._config.serverUrl}?token=${token}`);
                }

                this._socket.onopen = () => {
                    clearTimeout(timeoutTimer);
                    this._connectionState.isConnected = true;
                    this._connectionState.reconnectAttempts = 0;
                    this._connectionState.lastHeartbeat = Date.now();
                    this._isReconnecting = false;
                    this.startHeartbeat();
                    this.startConnectionMonitoring();
                    console.log('WebSocket connected successfully');
                    resolve();
                };

                this._socket.onmessage = (event) => {
                    this._connectionState.lastHeartbeat = Date.now();
                    this.handleMessage(event.data);
                };

                this._socket.onclose = (event) => {
                    clearTimeout(timeoutTimer);
                    this._connectionState.isConnected = false;
                    this.stopHeartbeat();
                    this.stopConnectionMonitoring();
                    console.log('WebSocket connection closed:', event.code, event.reason);
                    
                    if (!this._isReconnecting) {
                        this.handleDisconnection();
                    }
                };

                this._socket.onerror = (error) => {
                    clearTimeout(timeoutTimer);
                    console.error('WebSocket error:', error);
                    this.cleanup();
                    reject(new ConnectionError('WebSocket connection failed', error));
                };

            } catch (error) {
                clearTimeout(timeoutTimer);
                reject(new ConnectionError('Failed to create WebSocket connection', error));
            }
        });
    }

    /**
     * Send message to server with enhanced error handling
     */
    public sendMessage(message: GameMessage): void {
        if (!this._connectionState.isConnected || !this._socket) {
            throw new ConnectionError('Cannot send message: not connected');
        }

        try {
            // Add timestamp if not present
            if (!message.timestamp) {
                message.timestamp = Date.now();
            }

            const messageStr = JSON.stringify(message);
            
            if (typeof wx !== 'undefined' && this._socket) {
                (this._socket as any).send({
                    data: messageStr
                });
            } else if (this._socket instanceof WebSocket) {
                this._socket.send(messageStr);
            }

            console.log('Message sent:', message.cmd, message.reqId);
        } catch (error) {
            console.error('Failed to send message:', error);
            throw new NetworkError('Failed to send message', 'SEND_ERROR', error);
        }
    }

    /**
     * Send request and wait for response
     */
    public async sendRequest<T = any>(message: GameMessage, timeout?: number): Promise<T> {
        return new Promise((resolve, reject) => {
            // Generate request ID if not provided
            if (!message.reqId) {
                message.reqId = this.generateRequestId();
            }

            const requestTimeout = timeout || this._config.messageTimeout;
            
            // Set up timeout timer
            const timer = setTimeout(() => {
                this._pendingRequests.delete(message.reqId!);
                reject(new TimeoutError(`Request timeout: ${message.cmd}`));
            }, requestTimeout);

            // Store pending request
            this._pendingRequests.set(message.reqId, {
                resolve: (data: T) => {
                    clearTimeout(timer);
                    resolve(data);
                },
                reject: (error: any) => {
                    clearTimeout(timer);
                    reject(error);
                },
                timer
            });

            // Send the message
            try {
                this.sendMessage(message);
            } catch (error) {
                this._pendingRequests.delete(message.reqId);
                clearTimeout(timer);
                reject(error);
            }
        });
    }

    /**
     * Register message handler
     */
    public onMessage(cmd: string, handler: Function): void {
        if (!this._messageHandlers.has(cmd)) {
            this._messageHandlers.set(cmd, []);
        }
        this._messageHandlers.get(cmd)!.push(handler);
    }

    /**
     * Unregister message handler
     */
    public offMessage(cmd: string, handler: Function): void {
        const handlers = this._messageHandlers.get(cmd);
        if (handlers) {
            const index = handlers.indexOf(handler);
            if (index > -1) {
                handlers.splice(index, 1);
            }
        }
    }

    /**
     * Disconnect from server
     */
    public disconnect(): void {
        if (this._socket) {
            this._socket.close();
            this._socket = null;
        }
        this._isConnected = false;
        this.stopHeartbeat();
    }

    /**
     * HTTP request with authentication and error handling
     */
    public async httpRequest<T = any>(endpoint: string, options: HttpRequestOptions = { method: 'GET' }): Promise<HttpResponse<T>> {
        const url = `${this._config.httpBaseUrl}${endpoint}`;
        const timeout = options.timeout || this._config.messageTimeout;

        return new Promise((resolve, reject) => {
            const timeoutTimer = setTimeout(() => {
                reject(new TimeoutError(`HTTP request timeout: ${endpoint}`));
            }, timeout);

            try {
                // Prepare headers
                const headers: Record<string, string> = {
                    'Content-Type': 'application/json',
                    ...options.headers
                };

                // Add authentication token if available
                if (this._authToken) {
                    headers['Authorization'] = `Bearer ${this._authToken}`;
                }

                // For WeChat Mini Program
                if (typeof wx !== 'undefined') {
                    wx.request({
                        url,
                        method: options.method,
                        header: headers,
                        data: options.body,
                        success: (res: any) => {
                            clearTimeout(timeoutTimer);
                            if (res.statusCode >= 200 && res.statusCode < 300) {
                                resolve({
                                    data: res.data,
                                    status: res.statusCode,
                                    statusText: 'OK',
                                    headers: res.header || {}
                                });
                            } else if (res.statusCode === 401) {
                                reject(new AuthenticationError('Authentication failed'));
                            } else {
                                reject(new NetworkError(`HTTP ${res.statusCode}`, 'HTTP_ERROR', res.data));
                            }
                        },
                        fail: (error: any) => {
                            clearTimeout(timeoutTimer);
                            reject(new NetworkError('HTTP request failed', 'HTTP_ERROR', error));
                        }
                    });
                } else {
                    // For web/simulator - using fetch
                    const fetchOptions: RequestInit = {
                        method: options.method,
                        headers,
                        body: options.body ? JSON.stringify(options.body) : undefined
                    };

                    fetch(url, fetchOptions)
                        .then(async (response) => {
                            clearTimeout(timeoutTimer);
                            const data = await response.json();
                            
                            if (response.ok) {
                                resolve({
                                    data,
                                    status: response.status,
                                    statusText: response.statusText,
                                    headers: this.parseHeaders(response.headers)
                                });
                            } else if (response.status === 401) {
                                reject(new AuthenticationError('Authentication failed'));
                            } else {
                                reject(new NetworkError(`HTTP ${response.status}`, 'HTTP_ERROR', data));
                            }
                        })
                        .catch((error) => {
                            clearTimeout(timeoutTimer);
                            reject(new NetworkError('HTTP request failed', 'HTTP_ERROR', error));
                        });
                }
            } catch (error) {
                clearTimeout(timeoutTimer);
                reject(new NetworkError('Failed to make HTTP request', 'HTTP_ERROR', error));
            }
        });
    }

    /**
     * Check if connected
     */
    public isConnected(): boolean {
        return this._connectionState.isConnected;
    }

    /**
     * Get connection state
     */
    public getConnectionState(): ConnectionState {
        return { ...this._connectionState };
    }

    /**
     * Set authentication token
     */
    public setAuthToken(token: string): void {
        this._authToken = token;
    }

    /**
     * Get authentication token
     */
    public getAuthToken(): string {
        return this._authToken;
    }

    /**
     * Get network configuration
     */
    public getConfig(): NetworkConfig {
        return { ...this._config };
    }

    private handleMessage(data: string): void {
        try {
            const message: GameMessage = JSON.parse(data);
            
            // Handle response messages for pending requests
            if (message.type === 'RESP' && message.reqId) {
                const pendingRequest = this._pendingRequests.get(message.reqId);
                if (pendingRequest) {
                    this._pendingRequests.delete(message.reqId);
                    if (message.type === 'ERROR') {
                        pendingRequest.reject(new NetworkError(message.data?.message || 'Server error', 'SERVER_ERROR', message.data));
                    } else {
                        pendingRequest.resolve(message.data);
                    }
                    return;
                }
            }

            // Handle heartbeat response
            if (message.cmd === 'HEARTBEAT' && message.type === 'RESP') {
                console.log('Heartbeat response received');
                return;
            }

            // Handle regular message handlers
            const handlers = this._messageHandlers.get(message.cmd);
            if (handlers) {
                handlers.forEach(handler => {
                    try {
                        handler(message);
                    } catch (error) {
                        console.error(`Error in message handler for ${message.cmd}:`, error);
                    }
                });
            } else {
                console.warn('No handler registered for message:', message.cmd);
            }
        } catch (error) {
            console.error('Failed to parse message:', error);
        }
    }

    private handleDisconnection(): void {
        if (this._connectionState.reconnectAttempts < this._config.reconnectAttempts && !this._isReconnecting) {
            const delay = this._config.reconnectDelay * Math.pow(2, this._connectionState.reconnectAttempts); // Exponential backoff
            console.log(`Attempting reconnection in ${delay}ms (attempt ${this._connectionState.reconnectAttempts + 1}/${this._config.reconnectAttempts})`);
            
            this._reconnectTimer = setTimeout(() => {
                this.reconnect();
            }, delay) as any;
        } else {
            console.error('Max reconnection attempts reached or already reconnecting');
            this.notifyConnectionLost();
        }
    }

    private async reconnect(): Promise<void> {
        if (this._isReconnecting) {
            return;
        }

        this._isReconnecting = true;
        this._connectionState.reconnectAttempts++;

        try {
            if (!this._authToken) {
                throw new AuthenticationError('No authentication token available for reconnection');
            }

            await this.connect(this._authToken);
            console.log('Reconnected successfully');
            this.notifyReconnected();
        } catch (error) {
            console.error('Reconnection failed:', error);
            this._isReconnecting = false;
            this.handleDisconnection();
        }
    }

    /**
     * Force reconnection
     */
    public async forceReconnect(): Promise<void> {
        this.disconnect();
        this._connectionState.reconnectAttempts = 0;
        this._isReconnecting = false;
        
        if (this._authToken) {
            await this.connect(this._authToken);
        } else {
            throw new AuthenticationError('No authentication token available');
        }
    }

    private startHeartbeat(): void {
        this.stopHeartbeat(); // Clear any existing heartbeat
        this._heartbeatTimer = setInterval(() => {
            if (this._connectionState.isConnected) {
                try {
                    this.sendMessage({
                        type: 'REQ',
                        cmd: 'HEARTBEAT',
                        timestamp: Date.now()
                    });
                } catch (error) {
                    console.error('Failed to send heartbeat:', error);
                }
            }
        }, this._config.heartbeatInterval) as any;
    }

    private stopHeartbeat(): void {
        if (this._heartbeatTimer) {
            clearInterval(this._heartbeatTimer);
            this._heartbeatTimer = 0;
        }
    }

    private startConnectionMonitoring(): void {
        this.stopConnectionMonitoring(); // Clear any existing monitoring
        this._connectionTimer = setInterval(() => {
            const now = Date.now();
            const timeSinceLastHeartbeat = now - this._connectionState.lastHeartbeat;
            
            // If no heartbeat response for too long, consider connection lost
            if (timeSinceLastHeartbeat > this._config.heartbeatInterval * 2) {
                console.warn('Connection appears to be lost (no heartbeat response)');
                this.disconnect();
            }
        }, this._config.heartbeatInterval) as any;
    }

    private stopConnectionMonitoring(): void {
        if (this._connectionTimer) {
            clearInterval(this._connectionTimer);
            this._connectionTimer = 0;
        }
    }

    private cleanup(): void {
        this.stopHeartbeat();
        this.stopConnectionMonitoring();
        
        if (this._reconnectTimer) {
            clearTimeout(this._reconnectTimer);
            this._reconnectTimer = 0;
        }

        // Reject all pending requests
        this._pendingRequests.forEach((request) => {
            clearTimeout(request.timer);
            request.reject(new ConnectionError('Connection lost'));
        });
        this._pendingRequests.clear();

        if (this._socket) {
            this._socket.close();
            this._socket = null;
        }

        this._connectionState.isConnected = false;
        this._isReconnecting = false;
    }

    private generateRequestId(): string {
        return `req_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
    }

    private parseHeaders(headers: Headers): Record<string, string> {
        const result: Record<string, string> = {};
        headers.forEach((value, key) => {
            result[key] = value;
        });
        return result;
    }

    private notifyConnectionLost(): void {
        // Notify all handlers about connection loss
        const handlers = this._messageHandlers.get('CONNECTION_LOST');
        if (handlers) {
            handlers.forEach(handler => {
                try {
                    handler({ type: 'EVENT', cmd: 'CONNECTION_LOST', timestamp: Date.now() });
                } catch (error) {
                    console.error('Error in connection lost handler:', error);
                }
            });
        }
    }

    private notifyReconnected(): void {
        // Notify all handlers about successful reconnection
        const handlers = this._messageHandlers.get('RECONNECTED');
        if (handlers) {
            handlers.forEach(handler => {
                try {
                    handler({ type: 'EVENT', cmd: 'RECONNECTED', timestamp: Date.now() });
                } catch (error) {
                    console.error('Error in reconnected handler:', error);
                }
            });
        }
    }

    onDestroy(): void {
        this.cleanup();
        if (NetworkManager._instance === this) {
            NetworkManager._instance = null as any;
        }
    }
}