# WeChat Mini Program Integration

This document outlines the WeChat Mini Program integration implemented for the Mahjong game.

## Overview

The WeChat integration provides comprehensive support for WeChat Mini Program features including authentication, sharing, local storage, lifecycle management, and UI adaptation.

## Components Implemented

### 1. WeChatAPI (`assets/scripts/wechat/WeChatAPI.ts`)

Core WeChat API wrapper that provides:

- **Environment Detection**: Automatically detects if running in WeChat Mini Program
- **Authentication**: WeChat login and user profile management
- **Storage**: Local storage operations with fallback to browser localStorage
- **UI Interactions**: Toast messages, loading indicators, vibration
- **System Info**: Device and system information retrieval
- **Sharing**: Basic sharing functionality

**Key Features:**
- Automatic fallback for non-WeChat environments
- Mock data for development and testing
- Error handling and logging

### 2. WeChatAuthService (`assets/scripts/wechat/WeChatAuthService.ts`)

Authentication service that handles:

- **WeChat Login Flow**: Complete login process with code exchange
- **Token Management**: JWT token storage and validation
- **User Profile**: User information management
- **Auto Login**: Automatic login with stored tokens
- **Session Management**: Login state persistence

**Key Features:**
- Backend integration for authentication
- Secure token storage
- User profile caching
- Logout functionality

### 3. WeChatShareService (`assets/scripts/wechat/WeChatShareService.ts`)

Social sharing features including:

- **Game Invitations**: Share room invitations with friends
- **Game Results**: Share game outcomes and achievements
- **Custom Sharing**: Flexible sharing with custom content
- **Share Menu**: WeChat share menu integration
- **Clipboard**: Copy text to clipboard functionality

**Key Features:**
- Multiple sharing scenarios (invitation, results, achievements)
- Custom share image generation
- Share analytics support
- Action sheet for sharing options

### 4. WeChatStorageService (`assets/scripts/wechat/WeChatStorageService.ts`)

Local storage management for:

- **User Preferences**: Sound, music, theme, language settings
- **Game Settings**: Default room configurations and display preferences
- **Recent Rooms**: Recently joined room history
- **Game History**: Local game record cache
- **Session Data**: Temporary session information

**Key Features:**
- Structured data management
- Default value handling
- Data export/import for backup
- Storage quota management
- Cache optimization

### 5. WeChatLifecycleManager (`assets/scripts/wechat/WeChatLifecycleManager.ts`)

Mini Program lifecycle management:

- **App Show/Hide**: Handle app visibility changes
- **Background/Foreground**: Manage app state transitions
- **Scene Parameters**: Handle sharing and QR code parameters
- **Error Handling**: Global error and rejection handling
- **Memory Management**: Memory warning handling
- **Update Management**: App update detection and handling

**Key Features:**
- Automatic reconnection after background
- Scene parameter processing
- Memory optimization
- Error logging and recovery

### 6. WeChatUIAdapter (`assets/scripts/wechat/WeChatUIAdapter.ts`)

UI adaptation for different devices:

- **Device Detection**: iPhone X series, Android device detection
- **Safe Area**: Safe area handling for notched devices
- **Screen Adaptation**: Responsive design for different screen sizes
- **Resolution Scaling**: Design pixel to actual pixel conversion
- **Performance Optimization**: Device-specific optimizations

**Key Features:**
- iPhone X series support
- Safe area margin calculation
- Responsive layout adjustments
- Performance-based optimizations

## Integration Points

### LoginSceneController Updates

The login scene has been updated to use the new WeChat services:

- WeChat authentication integration
- Lifecycle event handling
- UI adaptation
- Scene parameter processing

### Configuration Files

- `project.wechat.json`: WeChat Mini Program project configuration
- `app.wechat.json`: Mini Program app configuration with pages, permissions, and features

## Testing

### Test Files Created

1. **WeChatBasic.test.ts**: Basic functionality tests
2. **WeChatIntegration.test.ts**: Comprehensive integration tests
3. **WeChatCompatibility.test.ts**: Device and version compatibility tests

### Test Coverage

- Environment detection
- Authentication flows
- Storage operations
- Sharing functionality
- Lifecycle management
- Device compatibility
- Error handling

## Usage Examples

### Basic WeChat Login

```typescript
const authService = WeChatAuthService.getInstance();
const result = await authService.login();

if (result.success) {
    console.log('Login successful:', result.userInfo);
} else {
    console.error('Login failed:', result.error);
}
```

### Sharing Game Invitation

```typescript
const shareService = WeChatShareService.getInstance();
shareService.shareGameInvitation('123456', 'PlayerName');
```

### Managing User Preferences

```typescript
const storageService = WeChatStorageService.getInstance();

// Save preferences
storageService.saveUserPreferences({
    soundEnabled: false,
    musicEnabled: true,
    language: 'zh_CN'
});

// Get preferences
const preferences = storageService.getUserPreferences();
```

### Handling Lifecycle Events

```typescript
const lifecycleManager = WeChatLifecycleManager.getInstance();

lifecycleManager.initialize({
    onShow: (options) => {
        console.log('App shown with options:', options);
    },
    onHide: () => {
        console.log('App hidden');
    },
    onError: (error) => {
        console.error('App error:', error);
    }
});
```

## Development vs Production

The implementation includes automatic environment detection:

- **WeChat Environment**: Uses actual WeChat APIs
- **Development Environment**: Provides mock implementations
- **Fallback Behavior**: Graceful degradation for unsupported features

## Performance Considerations

- **Lazy Loading**: Services are instantiated only when needed
- **Memory Management**: Automatic cache cleanup on memory warnings
- **Storage Optimization**: Efficient data structures and cleanup
- **Network Optimization**: Reduced API calls through caching

## Security Features

- **Token Security**: Secure JWT token storage and validation
- **Input Validation**: All user inputs are validated
- **Error Handling**: Comprehensive error handling without exposing sensitive data
- **Session Management**: Secure session handling with automatic cleanup

## Future Enhancements

Potential improvements for future versions:

1. **Advanced Analytics**: Detailed user behavior tracking
2. **Push Notifications**: WeChat template message integration
3. **Social Features**: Friend system and leaderboards
4. **Payment Integration**: WeChat Pay for in-app purchases
5. **Live Streaming**: Integration with WeChat live streaming
6. **Mini Game Ranking**: WeChat game ranking system

## Requirements Fulfilled

This implementation fulfills all requirements from task 22:

- ✅ **WeChat login and user authorization**: Complete authentication flow
- ✅ **WeChat sharing and social features**: Comprehensive sharing system
- ✅ **Local storage for user preferences**: Full preference management
- ✅ **Mini program lifecycle management**: Complete lifecycle handling
- ✅ **WeChat-specific UI adaptations**: Device-specific optimizations
- ✅ **Compatibility testing**: Cross-device and version testing

The implementation provides a robust foundation for WeChat Mini Program deployment while maintaining compatibility with development environments and other platforms.