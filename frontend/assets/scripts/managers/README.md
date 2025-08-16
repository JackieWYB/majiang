# Frontend Game State Management System

This document describes the comprehensive frontend game state management system implemented for the 3-player Mahjong game.

## Overview

The frontend game state management system consists of three main components:

1. **GameStateManager** - Core game state tracking and management
2. **StateSynchronizer** - Client-server state synchronization and conflict resolution
3. **LocalCacheManager** - Persistent local data caching and storage

## Components

### GameStateManager

The `GameStateManager` is a singleton class that handles all client-side game state operations.

#### Key Features

- **Local State Tracking**: Maintains current game state, player states, and game history
- **Action Validation**: Validates player actions before sending to server
- **Optimistic Updates**: Predicts action outcomes for responsive UI
- **Conflict Resolution**: Handles conflicts between local and server state
- **User Preferences**: Manages user settings and preferences
- **State History**: Maintains history of game states for debugging and replay
- **Performance Monitoring**: Tracks performance metrics and memory usage

#### Core Methods

```typescript
// State Management
updateGameState(snapshot: GameState): void
getCurrentState(): GameState | null
getLocalPlayer(): PlayerState | null
clearState(): void

// Action Handling
predictAction(action: GameAction): ValidationResult
confirmAction(action: GameAction): void
rejectAction(action: GameAction, reason: string): void
validateAction(action: GameAction, state: GameState): ValidationResult

// User Preferences
getUserPreferences(): UserPreferences
updateUserPreferences(preferences: Partial<UserPreferences>): void

// Performance & Debugging
getPerformanceMetrics(): PerformanceMetrics
exportState(): StateExport
importState(exportedState: StateExport): void
```

#### User Preferences

The system supports comprehensive user preferences:

```typescript
interface UserPreferences {
    soundEnabled: boolean;
    musicEnabled: boolean;
    autoPlay: boolean;
    showHints: boolean;
    animationSpeed: number;
    tileTheme: string;
    backgroundTheme: string;
    language: string;
    notifications: boolean;
    vibration: boolean;
    autoReconnect: boolean;
    dataUsageOptimization: boolean;
}
```

### StateSynchronizer

The `StateSynchronizer` handles complex client-server state synchronization.

#### Key Features

- **Automatic Synchronization**: Periodic sync with configurable intervals
- **Conflict Resolution**: Multiple strategies for handling state conflicts
- **Delta Synchronization**: Efficient state difference calculation and application
- **Checksum Validation**: Ensures state integrity
- **Action Tracking**: Tracks pending actions for confirmation/rejection
- **Backup & Recovery**: State backup and restoration capabilities

#### Configuration

```typescript
interface SyncConfig {
    syncInterval: number;           // Sync frequency in milliseconds
    maxRetries: number;            // Maximum retry attempts
    retryDelay: number;            // Delay between retries
    checksumValidation: boolean;   // Enable checksum validation
    conflictResolutionStrategy: 'server_wins' | 'client_wins' | 'merge' | 'manual';
}
```

#### Core Methods

```typescript
// Synchronization
startSync(): void
stopSync(): void
performSync(): Promise<GameSnapshot | null>
forceResync(): Promise<void>

// Action Tracking
registerAction(action: GameAction): void
confirmAction(action: GameAction): void

// Conflict Resolution
resolveConflict(localState: GameState, serverState: GameState): ConflictResolution
registerConflictResolver(roomId: string, resolver: ConflictResolver): void

// State Management
calculateDiff(oldState: GameState, newState: GameState): StateDiff
applyDiff(baseState: GameState, diff: StateDiff): GameState
validateChecksum(state: GameState, expectedChecksum: string): boolean
```

### LocalCacheManager

The `LocalCacheManager` provides efficient local data caching with TTL support.

#### Key Features

- **TTL Support**: Automatic expiration of cached items
- **Size Management**: LRU eviction when cache size limits are reached
- **Statistics Tracking**: Hit/miss rates and performance metrics
- **Import/Export**: Backup and restore cache data
- **Storage Abstraction**: Works with both WeChat storage and localStorage
- **Automatic Cleanup**: Background cleanup of expired items

#### Configuration

```typescript
interface CacheConfig {
    maxCacheSize: number;      // Maximum cache size in bytes
    defaultTTL: number;        // Default TTL in milliseconds
    compressionEnabled: boolean;
    encryptionEnabled: boolean;
}
```

#### Core Methods

```typescript
// Basic Operations
set<T>(key: string, value: T, ttl?: number): void
get<T>(key: string): T | null
has(key: string): boolean
delete(key: string): boolean
clear(): void

// Management
cleanup(): number
getStats(): CacheStats
updateConfig(config: Partial<CacheConfig>): void

// Import/Export
export(): string
import(data: string): boolean
```

## Integration

### GameStateManager + LocalCacheManager

The `GameStateManager` integrates with `LocalCacheManager` for efficient data persistence:

- User preferences are cached with 7-day TTL
- Game state snapshots are cached with 5-minute TTL
- Common game data is preloaded and cached for 24 hours
- Automatic fallback to direct storage if cache fails

### StateSynchronizer + LocalCacheManager

The `StateSynchronizer` uses `LocalCacheManager` for:

- Caching sync status and metrics
- Storing last known server state
- Persisting conflict resolution strategies

## Storage Strategy

The system uses a multi-layered storage approach:

1. **Memory Cache**: Fast access for frequently used data
2. **LocalCacheManager**: Structured caching with TTL and size management
3. **Direct Storage**: Fallback storage (WeChat storage or localStorage)

### WeChat Mini Program Support

The system automatically detects and uses WeChat-specific storage APIs:

```typescript
// WeChat Mini Program
wx.getStorageSync(key)
wx.setStorageSync(key, value)
wx.removeStorageSync(key)

// Web/Simulator fallback
localStorage.getItem(key)
localStorage.setItem(key, value)
localStorage.removeItem(key)
```

## Performance Optimizations

### Memory Management

- Configurable state history size (default: 50 states)
- Automatic cleanup of expired cache items
- LRU eviction for cache size management
- Memory usage tracking and reporting

### Network Efficiency

- Delta synchronization reduces bandwidth usage
- Optimistic updates provide immediate feedback
- Configurable sync intervals balance freshness and efficiency
- Checksum validation prevents unnecessary updates

### Data Persistence

- Intelligent caching strategies based on data usage patterns
- Automatic fallback mechanisms for storage failures
- Efficient serialization and deserialization
- Background cleanup processes

## Error Handling

The system includes comprehensive error handling:

- Graceful degradation when storage is unavailable
- Automatic retry mechanisms for network operations
- State validation and consistency checks
- Detailed error logging and reporting

## Testing

The implementation includes comprehensive test coverage:

- **Unit Tests**: 48 tests for GameStateManager
- **Integration Tests**: 22 tests for component integration
- **Cache Tests**: 27 tests for LocalCacheManager
- **Performance Tests**: Load testing and memory usage validation

### Test Categories

1. **Basic Operations**: Core functionality testing
2. **State Management**: Game state tracking and updates
3. **Action Handling**: Validation and prediction testing
4. **Synchronization**: Client-server sync testing
5. **Caching**: Cache operations and TTL testing
6. **Error Handling**: Failure scenario testing
7. **Performance**: Load and memory testing

## Usage Examples

### Basic State Management

```typescript
// Initialize
const gameStateManager = GameStateManager.instance;
gameStateManager.initialize('user123');

// Update state from server
gameStateManager.updateGameState(serverSnapshot);

// Predict action
const action = { type: ActionType.DISCARD, playerId: 'user123', tile: '1W' };
const validation = gameStateManager.predictAction(action);

if (validation.isValid) {
    // Send to server
    networkManager.sendAction(action);
}
```

### Synchronization Setup

```typescript
// Initialize synchronizer
const synchronizer = new StateSynchronizer(networkManager, {
    syncInterval: 30000,
    conflictResolutionStrategy: 'server_wins'
});

// Start automatic sync
synchronizer.startSync();

// Handle conflicts
synchronizer.registerConflictResolver('room123', (conflict) => {
    // Custom conflict resolution logic
    return { ...conflict, resolution: 'merge' };
});
```

### Cache Management

```typescript
// Get cache manager
const cacheManager = LocalCacheManager.instance;

// Cache user data
cacheManager.set('user_profile', userProfile, 24 * 60 * 60 * 1000); // 24 hours

// Retrieve cached data
const profile = cacheManager.get('user_profile');

// Check cache statistics
const stats = cacheManager.getStats();
console.log(`Hit rate: ${stats.hitRate}, Cache size: ${stats.totalSize} bytes`);
```

## Configuration

### Default Configuration

```typescript
// GameStateManager
maxHistorySize: 50
syncInterval: 30000ms
validationEnabled: true

// StateSynchronizer
syncInterval: 30000ms
maxRetries: 3
retryDelay: 1000ms
checksumValidation: true
conflictResolutionStrategy: 'server_wins'

// LocalCacheManager
maxCacheSize: 5MB
defaultTTL: 24 hours
compressionEnabled: false
encryptionEnabled: false
```

### Customization

All components support runtime configuration updates:

```typescript
// Update sync configuration
synchronizer.updateConfig({
    syncInterval: 15000,
    maxRetries: 5
});

// Update cache configuration
cacheManager.updateConfig({
    maxCacheSize: 10 * 1024 * 1024, // 10MB
    defaultTTL: 12 * 60 * 60 * 1000  // 12 hours
});
```

## Monitoring and Debugging

### Performance Metrics

```typescript
// GameStateManager metrics
const metrics = gameStateManager.getPerformanceMetrics();
console.log('State updates:', metrics.stateUpdates);
console.log('Memory usage:', metrics.memoryUsage);

// Synchronizer metrics
const syncMetrics = synchronizer.getSyncMetrics();
console.log('Sync efficiency:', syncMetrics.syncEfficiency);

// Cache metrics
const cacheStats = cacheManager.getStats();
console.log('Hit rate:', cacheStats.hitRate);
```

### State Export/Import

```typescript
// Export state for debugging
const exportedState = gameStateManager.exportState();
console.log('Current state:', JSON.stringify(exportedState, null, 2));

// Import state for testing
gameStateManager.importState(testState);
```

### Cache Backup

```typescript
// Export cache for backup
const cacheBackup = cacheManager.export();
localStorage.setItem('cache_backup', cacheBackup);

// Restore from backup
const backup = localStorage.getItem('cache_backup');
if (backup) {
    cacheManager.import(backup);
}
```

## Best Practices

1. **Initialize Early**: Initialize GameStateManager as soon as user ID is available
2. **Handle Conflicts**: Always register conflict resolvers for custom game logic
3. **Monitor Performance**: Regularly check performance metrics in production
4. **Cache Strategically**: Use appropriate TTL values based on data volatility
5. **Test Thoroughly**: Include edge cases and failure scenarios in tests
6. **Clean Up**: Properly destroy components when no longer needed

## Future Enhancements

Potential areas for future improvement:

1. **Compression**: Implement data compression for large state objects
2. **Encryption**: Add encryption for sensitive cached data
3. **Offline Support**: Enhanced offline mode with local state persistence
4. **Analytics**: Built-in analytics for user behavior tracking
5. **A/B Testing**: Framework for testing different state management strategies