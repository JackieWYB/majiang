# Network Layer Documentation

This network layer provides comprehensive WebSocket and HTTP communication capabilities for the Mahjong game frontend, with automatic reconnection, message serialization, and WeChat Mini Program integration.

## Components

### NetworkManager
The core component that handles WebSocket connections, message routing, and connection management.

**Features:**
- Automatic reconnection with exponential backoff
- Heartbeat mechanism for connection monitoring
- Request/response pattern support
- Message serialization and validation
- WeChat Mini Program WebSocket integration
- Connection state monitoring
- Error handling and recovery

**Usage:**
```typescript
import { NetworkManager } from './NetworkManager';

// Get singleton instance
const networkManager = NetworkManager.instance;

// Connect to server
await networkManager.connect('your-auth-token');

// Send a message
networkManager.sendMessage({
    type: 'REQ',
    cmd: 'JOIN_ROOM',
    data: { roomId: '123456' },
    timestamp: Date.now()
});

// Send request and wait for response
const response = await networkManager.sendRequest({
    type: 'REQ',
    cmd: 'GET_USER_PROFILE',
    data: { userId: 'user123' },
    timestamp: Date.now()
}, 5000); // 5 second timeout

// Register message handler
networkManager.onMessage('GAME_STATE_UPDATE', (message) => {
    console.log('Game state updated:', message.data);
});
```

### HttpClient
Utility class for making HTTP requests with authentication and error handling.

**Features:**
- GET, POST, PUT, DELETE methods
- Automatic authentication header injection
- File upload/download for WeChat Mini Program
- Timeout handling
- Error classification

**Usage:**
```typescript
import { HttpClient } from './HttpClient';

const httpClient = new HttpClient(networkManager);

// GET request
const rooms = await httpClient.get('/rooms');

// POST request
const newRoom = await httpClient.post('/rooms', {
    name: 'My Room',
    maxPlayers: 3
});

// File upload (WeChat Mini Program only)
const result = await httpClient.uploadFile('/upload/avatar', filePath, 'avatar');
```

### MessageSerializer
Handles message serialization, validation, and helper methods for creating standard messages.

**Features:**
- JSON serialization with validation
- Message version compatibility
- Size limits and validation
- Helper methods for creating standard message types
- Error handling for malformed messages

**Usage:**
```typescript
import { MessageSerializer } from './MessageSerializer';

// Create request message
const request = MessageSerializer.createRequest('JOIN_ROOM', { roomId: '123456' });

// Create response message
const response = MessageSerializer.createResponse('JOIN_ROOM', 'req_123', { success: true });

// Create event message
const event = MessageSerializer.createEvent('PLAYER_JOINED', { playerId: 'player123' });

// Create error message
const error = MessageSerializer.createError('JOIN_ROOM', 'Room not found', 'req_123');

// Serialize/deserialize
const serialized = MessageSerializer.serialize(message);
const deserialized = MessageSerializer.deserialize(serialized);
```

## Message Protocol

### Message Structure
```typescript
interface GameMessage {
    type: 'EVENT' | 'REQ' | 'RESP' | 'ERROR';
    cmd: string;
    reqId?: string;
    roomId?: string;
    data?: any;
    timestamp: number;
}
```

### Message Types
- **REQ**: Request message that expects a response
- **RESP**: Response to a request message
- **EVENT**: One-way event notification
- **ERROR**: Error message

### Common Commands
- `HEARTBEAT`: Connection heartbeat
- `JOIN_ROOM`: Join a game room
- `LEAVE_ROOM`: Leave a game room
- `GAME_STATE_UPDATE`: Game state synchronization
- `PLAYER_ACTION`: Player game actions
- `PLAY_TILE`: Play a tile
- `PENG`: Peng action
- `GANG`: Gang action
- `HU`: Win action

## Error Handling

### Error Classes
- **NetworkError**: Base network error class
- **ConnectionError**: Connection-related errors
- **TimeoutError**: Request/connection timeout errors
- **AuthenticationError**: Authentication failures

### Error Handling Strategy
```typescript
try {
    await networkManager.connect(token);
} catch (error) {
    if (error instanceof ConnectionError) {
        // Handle connection issues
        console.error('Connection failed:', error.message);
    } else if (error instanceof AuthenticationError) {
        // Handle auth issues
        console.error('Authentication failed:', error.message);
    } else if (error instanceof TimeoutError) {
        // Handle timeouts
        console.error('Request timed out:', error.message);
    }
}
```

## Configuration

### Network Configuration
```typescript
interface NetworkConfig {
    serverUrl: string;           // WebSocket server URL
    httpBaseUrl: string;         // HTTP API base URL
    heartbeatInterval: number;   // Heartbeat interval in ms
    reconnectAttempts: number;   // Max reconnection attempts
    reconnectDelay: number;      // Base reconnection delay in ms
    connectionTimeout: number;   // Connection timeout in ms
    messageTimeout: number;      // Message response timeout in ms
}
```

### Default Configuration
```typescript
{
    serverUrl: 'ws://localhost:8080/game',
    httpBaseUrl: 'http://localhost:8080/api',
    heartbeatInterval: 30000,    // 30 seconds
    reconnectAttempts: 5,
    reconnectDelay: 1000,        // 1 second
    connectionTimeout: 10000,    // 10 seconds
    messageTimeout: 5000         // 5 seconds
}
```

## WeChat Mini Program Integration

### WebSocket
Uses `wx.connectSocket()` when running in WeChat Mini Program environment, falls back to standard WebSocket for web/simulator.

### HTTP Requests
Uses `wx.request()` for HTTP calls in WeChat Mini Program, with automatic header and authentication handling.

### File Operations
- `wx.uploadFile()` for file uploads
- `wx.downloadFile()` for file downloads
- Automatic authentication header injection

## Connection Management

### Automatic Reconnection
- Exponential backoff strategy (1s, 2s, 4s, 8s, 16s)
- Maximum 5 reconnection attempts by default
- Preserves authentication token for reconnection
- Notifies handlers of connection state changes

### Heartbeat Mechanism
- Sends heartbeat every 30 seconds by default
- Monitors heartbeat responses
- Detects connection loss when heartbeat fails
- Triggers reconnection on connection loss

### Connection Monitoring
```typescript
// Monitor connection state
const state = networkManager.getConnectionState();
console.log({
    isConnected: state.isConnected,
    reconnectAttempts: state.reconnectAttempts,
    lastHeartbeat: state.lastHeartbeat,
    connectionStartTime: state.connectionStartTime
});

// Handle connection events
networkManager.onMessage('CONNECTION_LOST', () => {
    console.log('Connection lost, showing reconnection UI...');
});

networkManager.onMessage('RECONNECTED', () => {
    console.log('Reconnected successfully, hiding reconnection UI...');
});
```

## Testing

The network layer includes comprehensive unit tests covering:
- Message serialization and validation
- Error handling and classification
- WeChat API mocking
- Connection state management
- HTTP client functionality

Run tests with:
```bash
npm run test
```

## Best Practices

1. **Always handle errors**: Wrap network calls in try-catch blocks
2. **Use message handlers**: Register handlers for all expected message types
3. **Monitor connection state**: Show appropriate UI for connection issues
4. **Implement timeouts**: Set appropriate timeouts for requests
5. **Clean up resources**: Disconnect when components are destroyed
6. **Validate messages**: Use MessageSerializer for consistent message format
7. **Handle reconnection**: Implement UI feedback for reconnection attempts

## Example Integration

See `NetworkExample.ts` for a complete example of how to integrate the network layer into your game components.