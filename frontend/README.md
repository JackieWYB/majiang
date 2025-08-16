# Mahjong Game Frontend

This is the frontend client for the 3-player Mahjong online battle game, built with Cocos Creator for WeChat Mini Program deployment.

## Project Structure

```
frontend/
├── assets/                     # Game assets and resources
│   ├── scenes/                # Game scenes
│   │   ├── LoginScene.scene   # Login and authentication
│   │   ├── LobbyScene.scene   # Room creation and joining
│   │   ├── GameScene.scene    # Main gameplay
│   │   ├── SettlementScene.scene # Game results
│   │   └── HistoryScene.scene # Game history
│   ├── scripts/               # TypeScript source code
│   │   ├── managers/          # Core managers
│   │   ├── network/           # Network communication
│   │   ├── scenes/            # Scene controllers
│   │   └── ui/                # UI components
│   ├── resources/             # Game resources
│   │   ├── textures/          # Images and sprites
│   │   ├── audio/             # Sound effects and music
│   │   ├── fonts/             # Font files
│   │   └── data/              # Configuration files
│   └── prefabs/               # Reusable game objects
│       ├── ui/                # UI prefabs
│       └── game/              # Game object prefabs
├── settings/                  # Cocos Creator project settings
├── build/                     # Build output directory
└── temp/                      # Temporary build files
```

## Key Features

### Scene Management
- **LoginScene**: WeChat authentication and user onboarding
- **LobbyScene**: Room creation, joining, and player dashboard
- **GameScene**: Real-time 3-player Mahjong gameplay
- **SettlementScene**: Game results and score display
- **HistoryScene**: Game records and replay functionality

### Core Systems
- **GameStateManager**: Client-side game state tracking and validation
- **NetworkManager**: WebSocket communication with automatic reconnection
- **SceneManager**: Scene transition and loading management

### WeChat Integration
- WeChat Mini Program API integration
- WeChat login and user authentication
- Local storage for user preferences and game data
- WeChat-specific UI adaptations

## Development Setup

### Prerequisites
- Cocos Creator 3.8.0 or later
- Node.js 14.0.0 or later
- WeChat Developer Tools (for Mini Program testing)

### Installation
1. Open the project in Cocos Creator
2. Install dependencies:
   ```bash
   npm install
   ```
3. Configure WeChat Mini Program settings in `project.config.json`
4. Set up backend server URL in NetworkManager

### Building
- **WeChat Mini Game**: `npm run build`
- **Web Mobile**: `npm run build-web`
- **Preview**: `npm run preview`

## Configuration

### WeChat Mini Program
1. Update `project.config.json` with your WeChat App ID
2. Configure server domains in WeChat Mini Program console
3. Set up authentication endpoints in backend

### Network Configuration
Update server URL in `NetworkManager.ts`:
```typescript
this._config = {
    serverUrl: 'ws://your-server-url:8080/game',
    // ... other config
};
```

## Game Flow

1. **Login**: User authenticates via WeChat
2. **Lobby**: Create or join game rooms
3. **Game**: Real-time 3-player Mahjong gameplay
4. **Settlement**: View game results and scores
5. **History**: Browse past games and replays

## Network Protocol

The client communicates with the backend via WebSocket using a structured message format:

```typescript
interface GameMessage {
    type: 'EVENT' | 'REQ' | 'RESP' | 'ERROR';
    cmd: string;
    roomId?: string;
    data?: any;
    timestamp: number;
}
```

## Asset Organization

### Textures
- `textures/ui/`: User interface elements
- `textures/tiles/`: Mahjong tile sprites
- `textures/backgrounds/`: Scene backgrounds

### Audio
- `audio/bgm/`: Background music
- `audio/sfx/`: Sound effects

### Prefabs
- `prefabs/ui/`: Reusable UI components
- `prefabs/game/`: Game object prefabs

## Performance Considerations

- Optimized for WeChat Mini Program constraints
- Efficient asset loading and memory management
- Minimal network traffic with state synchronization
- Responsive UI for various screen sizes

## Testing

### WeChat Developer Tools
1. Import project into WeChat Developer Tools
2. Test on simulator and real devices
3. Verify network connectivity and authentication

### Web Testing
Use Cocos Creator's preview feature for rapid development and testing.

## Deployment

1. Build for WeChat Mini Game platform
2. Upload to WeChat Mini Program console
3. Submit for review and approval
4. Configure server domains and permissions

## Troubleshooting

### Common Issues
- **Network Connection**: Verify server URL and WebSocket connectivity
- **WeChat Authentication**: Check App ID and server configuration
- **Asset Loading**: Ensure all resources are properly referenced
- **Performance**: Monitor memory usage and optimize assets

### Debug Mode
Enable debug logging in development:
```typescript
// In NetworkManager or GameStateManager
console.log('Debug info:', data);
```

## Contributing

1. Follow TypeScript coding standards
2. Use meaningful component and variable names
3. Add comments for complex game logic
4. Test on both simulator and real devices
5. Optimize for performance and memory usage