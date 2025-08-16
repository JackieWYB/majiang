# Requirements Document

## Introduction

This document outlines the requirements for a 3-player Mahjong online battle game (卡五星麻将) built with Cocos Creator for WeChat Mini Program frontend and Spring Boot backend. The game features real-time multiplayer gameplay, configurable rules, reconnection capabilities, and comprehensive game management systems.

## Requirements

### Requirement 1: User Authentication and Management

**User Story:** As a player, I want to log in using WeChat authentication so that I can access the game with my WeChat identity and maintain my game progress.

#### Acceptance Criteria

1. WHEN a user opens the mini program THEN the system SHALL present WeChat login authorization
2. WHEN a user authorizes WeChat login THEN the system SHALL create or retrieve user profile with openId
3. WHEN a user logs in successfully THEN the system SHALL issue a JWT token for session management
4. IF a user is banned THEN the system SHALL prevent login and display ban reason
5. WHEN a user requests profile information THEN the system SHALL return nickname, avatar, coins, and room cards

### Requirement 2: Room Creation and Management

**User Story:** As a room owner, I want to create custom game rooms with configurable rules so that I can play with specific game settings and invite friends.

#### Acceptance Criteria

1. WHEN a user creates a room THEN the system SHALL generate a unique 6-digit room ID
2. WHEN creating a room THEN the user SHALL be able to configure game rules including scoring, dealer rotation, and special hands
3. WHEN a room is created THEN the system SHALL set the creator as room owner with administrative privileges
4. WHEN a room has 3 players THEN the system SHALL automatically start the game preparation phase
5. IF a room owner leaves THEN the system SHALL transfer ownership to another player or dissolve the room
6. WHEN a room is inactive for 30 minutes THEN the system SHALL automatically dissolve it

### Requirement 3: Real-time Gameplay Engine

**User Story:** As a player, I want to play Mahjong with real-time interactions so that I can enjoy smooth multiplayer gameplay with immediate responses.

#### Acceptance Criteria

1. WHEN a game starts THEN the system SHALL deal 14 tiles to dealer and 13 tiles to other players
2. WHEN it's a player's turn THEN the system SHALL enforce turn order and time limits (15 seconds default)
3. WHEN a player discards a tile THEN other players SHALL have 2 seconds to claim for Peng/Gang/Hu
4. WHEN multiple players claim the same tile THEN the system SHALL prioritize Hu > Gang > Peng
5. WHEN a player forms a winning hand THEN the system SHALL validate the win and calculate scores
6. WHEN a player times out THEN the system SHALL automatically play for them (trustee mode)
7. WHEN the tile wall is exhausted THEN the system SHALL declare a draw and handle scoring

### Requirement 4: Scoring and Settlement System

**User Story:** As a player, I want accurate score calculation based on configurable rules so that I can see fair and transparent game results.

#### Acceptance Criteria

1. WHEN a player wins THEN the system SHALL calculate base score plus multipliers for special hands
2. WHEN calculating scores THEN the system SHALL apply configured multipliers for dealer, self-draw, and hand types
3. WHEN a player has Kong tiles THEN the system SHALL add Kong bonus points to the final score
4. WHEN scores exceed the configured cap THEN the system SHALL limit to maximum allowed points
5. WHEN a game ends THEN the system SHALL update player totals and save settlement records
6. WHEN multiple players win on same discard THEN the system SHALL handle multiple winner scoring if enabled

### Requirement 5: Disconnection and Reconnection

**User Story:** As a player, I want to reconnect to ongoing games after network issues so that I don't lose progress due to connectivity problems.

#### Acceptance Criteria

1. WHEN a player disconnects THEN the system SHALL maintain their game state for 30 seconds
2. WHEN a disconnected player reconnects THEN the system SHALL send complete game snapshot
3. WHEN a player is disconnected for over 30 seconds THEN the system SHALL enable auto-play trustee mode
4. WHEN sending reconnection data THEN the system SHALL include hand tiles, discarded tiles, and current turn state
5. WHEN a player reconnects THEN the system SHALL restore their ability to make moves if it's their turn

### Requirement 6: Game Configuration and Rules

**User Story:** As a room owner, I want to configure various game rules so that I can customize the gameplay experience according to regional preferences.

#### Acceptance Criteria

1. WHEN configuring a room THEN the owner SHALL be able to set dealer rotation rules
2. WHEN configuring scoring THEN the owner SHALL be able to enable/disable special hand types like Seven Pairs
3. WHEN configuring gameplay THEN the owner SHALL be able to set time limits and auto-trustee behavior
4. WHEN configuring winning conditions THEN the owner SHALL be able to enable/disable edge waits and pair waits
5. WHEN saving room configuration THEN the system SHALL validate all rule combinations for consistency

### Requirement 7: Game History and Replay

**User Story:** As a player, I want to view my game history and replay past games so that I can review my performance and learn from previous matches.

#### Acceptance Criteria

1. WHEN a game completes THEN the system SHALL save complete game record with all moves
2. WHEN a player requests game history THEN the system SHALL return paginated list of past games
3. WHEN viewing a specific game record THEN the system SHALL show final scores and game statistics
4. WHEN replaying a game THEN the system SHALL reconstruct game state from saved move sequence
5. WHEN generating replay data THEN the system SHALL use the original random seed for consistency

### Requirement 8: Administrative and Monitoring

**User Story:** As an administrator, I want to monitor and manage the game system so that I can ensure fair play and system stability.

#### Acceptance Criteria

1. WHEN suspicious activity is detected THEN the system SHALL log events and trigger alerts
2. WHEN an administrator reviews user activity THEN the system SHALL provide detailed audit logs
3. WHEN banning a user THEN the system SHALL immediately disconnect them and prevent future logins
4. WHEN monitoring system health THEN the system SHALL track room counts, player counts, and response times
5. WHEN a room needs intervention THEN administrators SHALL be able to force dissolve rooms

### Requirement 9: WeChat Mini Program Integration

**User Story:** As a player, I want to play the game seamlessly within WeChat so that I can enjoy the game without leaving the WeChat ecosystem.

#### Acceptance Criteria

1. WHEN the mini program loads THEN the system SHALL adapt to different screen sizes and orientations
2. WHEN using WeChat features THEN the system SHALL integrate with WeChat's sharing and social features
3. WHEN handling network requests THEN the system SHALL use WeChat's WebSocket and HTTP APIs
4. WHEN storing data locally THEN the system SHALL use WeChat's local storage capabilities
5. WHEN the mini program updates THEN the system SHALL handle version compatibility gracefully

### Requirement 10: Performance and Scalability

**User Story:** As a player, I want responsive gameplay with minimal latency so that I can enjoy smooth real-time interactions.

#### Acceptance Criteria

1. WHEN sending game actions THEN the system SHALL respond within 200ms for same-region players
2. WHEN handling concurrent rooms THEN a single server instance SHALL support at least 1000 concurrent players
3. WHEN processing game logic THEN WebSocket message handling SHALL complete within 2ms per message
4. WHEN a server instance fails THEN players SHALL be able to reconnect within 30 seconds
5. WHEN monitoring performance THEN the system SHALL track and alert on latency and error rates