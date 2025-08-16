# Implementation Plan

- [x] 1. Set up Spring Boot project foundation
  - Create Spring Boot project with Maven/Gradle configuration
  - Add required dependencies (spring-boot-starter-web, spring-boot-starter-websocket, spring-boot-starter-data-jpa, spring-boot-starter-data-redis, spring-boot-starter-security)
  - Configure application.yml with database and Redis connection settings
  - Set up basic project structure with packages (controller, service, repository, model, config)
  - Create main application class and basic configuration
  - _Requirements: 1.1, 1.2, 1.3_

- [x] 2. Create core domain models and database schema
  - Implement User entity with JPA annotations (id, openId, unionId, nickname, avatar, coins, roomCards, status)
  - Create Room entity with relationship mappings (id, ownerId, status, ruleId, players)
  - Build RoomRule entity with JSON configuration field for game rules
  - Implement GameRecord entity for game history persistence
  - Create database migration scripts using Flyway or Liquibase
  - Write unit tests for entity validation and relationships
  - _Requirements: 1.1, 1.2, 2.1, 6.1, 7.1_

- [x] 3. Implement WeChat authentication system
  - Create WeChat login service with code2Session API integration
  - Build JWT token service for token generation and validation
  - Implement UserService with user creation and profile management
  - Create authentication controller with login and profile endpoints
  - Add Spring Security configuration for JWT authentication
  - Write unit tests for authentication flows
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5_

- [x] 4. Build room management system
  - Create RoomService with room creation, joining, and leaving logic
  - Implement room ID generation (6-digit unique numbers)
  - Build room ownership transfer and dissolution functionality
  - Add room status management and automatic cleanup
  - Create RoomController with REST endpoints for room operations
  - Write unit tests for room lifecycle management
  - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6_

- [x] 5. Create game configuration system
  - Design RoomConfig data structure with all rule options (players, tiles, scoring, timing)
  - Implement configuration validation service for rule combinations
  - Create default configuration templates for different game variants
  - Build configuration persistence and retrieval in RoomRule entity
  - Add configuration endpoints for retrieving and updating rules
  - Write unit tests for configuration validation logic
  - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5_

- [x] 6. Implement core game models and tile system
  - Create Tile class with suit (WAN, TIAO, TONG) and rank properties
  - Implement MeldSet class for Peng, Gang, and Chi combinations
  - Build PlayerState class with hand tiles, melds, and game status
  - Create GameState class with all players, tile wall, discard pile, and turn tracking
  - Implement tile shuffling algorithm with reproducible random seeds
  - Write comprehensive unit tests for tile operations and game state
  - _Requirements: 3.1, 3.2_

- [x] 7. Build winning condition validation engine
  - Implement basic winning hand validation (4 sets + 1 pair pattern)
  - Create special hand type validation (Seven Pairs, All Pungs, All Honors)
  - Build edge wait and pair wait detection algorithms
  - Implement Kong robbery (Qiang Gang Hu) validation logic
  - Add configurable winning condition toggles based on room rules
  - Write comprehensive unit tests for all winning scenarios
  - _Requirements: 3.5, 6.4_

- [x] 8. Create scoring and settlement system
  - Implement base score calculation with fan (multiplier) system
  - Build special hand scoring (self-draw bonus, dealer bonus, hand types)
  - Create Kong bonus point calculation and distribution logic
  - Implement score capping and multiple winner handling
  - Add SettlementService for calculating and persisting game results
  - Write unit tests for all scoring scenarios and edge cases
  - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6_

- [x] 9. Implement game engine and turn management
  - Create GameService with game initialization and state management
  - Build turn-based flow with time limits and automatic progression
  - Implement action validation for player moves (discard, peng, gang, hu)
  - Add action priority handling (hu > gang > peng) with timing windows
  - Create automatic trustee mode for timed-out players
  - Write unit tests for turn management and action validation
  - _Requirements: 3.2, 3.3, 3.6, 5.1, 5.3_

- [x] 10. Set up Redis for game state management
  - Configure Redis connection and session management
  - Implement game state persistence in Redis with TTL expiration
  - Create game state snapshot generation and restoration
  - Build game state validation and consistency checks
  - Add Redis-based session management for WebSocket connections
  - Write integration tests for Redis operations
  - _Requirements: 3.1, 3.3, 5.2, 5.4_

- [x] 11. Implement WebSocket communication layer
  - Set up WebSocket configuration with STOMP protocol support
  - Create GameWebSocketHandler for connection management and authentication
  - Build message routing system for different game actions
  - Implement connection heartbeat and timeout detection
  - Add graceful connection cleanup on player disconnect
  - Write integration tests for WebSocket connectivity and message handling
  - _Requirements: 3.1, 5.1_

- [x] 12. Build real-time message protocol and broadcasting
  - Define GameMessage structure for all game actions and events
  - Implement message serialization/deserialization with JSON
  - Create action message handlers (PlayAction, PengAction, GangAction, HuAction)
  - Build room-based message broadcasting system
  - Add game snapshot broadcasting for state synchronization
  - Write unit tests for message processing and broadcasting
  - _Requirements: 3.2, 3.3, 3.4, 3.5, 4.5, 5.4_

- [x] 13. Implement reconnection and recovery system
  - Build player disconnection detection and handling
  - Create game state snapshot generation for reconnecting players
  - Implement reconnection authentication and room rejoining
  - Add automatic trustee mode activation for disconnected players
  - Build game state recovery from Redis with fallback to database
  - Write integration tests for disconnection and reconnection scenarios
  - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5_

- [x] 14. Create game history and replay system
  - Implement GameAction logging with timestamps and sequence numbers
  - Build game record persistence with complete move history
  - Create game history retrieval with pagination support
  - Implement replay reconstruction from saved action sequences
  - Add game statistics calculation and storage
  - Write unit tests for history persistence and replay functionality
  - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5_

- [x] 15. Build administrative and monitoring features
  - Create admin authentication and role-based authorization
  - Implement user management endpoints (ban, unban, profile updates)
  - Build room monitoring and forced dissolution capabilities
  - Add system health monitoring with metrics collection (Micrometer)
  - Create audit logging for administrative actions
  - Write unit tests for administrative functions
  - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5_

- [x] 16. Implement REST API endpoints
  - Create UserController with profile management endpoints
  - Build RoomController with room creation and management endpoints
  - Implement GameRecordController for game history retrieval
  - Add ConfigController for rule configuration endpoints
  - Create AdminController for administrative operations
  - Write integration tests for all REST endpoints with proper authentication
  - _Requirements: 1.5, 2.1, 2.2, 7.2, 6.1_

- [x] 17. Add security and validation layers
  - Implement rate limiting for API endpoints and WebSocket messages
  - Add comprehensive input validation and sanitization
  - Create suspicious activity detection and logging
  - Implement game seed preservation for audit purposes
  - Add request/response logging and monitoring
  - Write security tests for common attack vectors
  - _Requirements: 8.1, 8.2, 8.4_

- [x] 18. Create Cocos Creator frontend project
  - Set up new Cocos Creator project with WeChat Mini Game template
  - Configure project settings for WeChat Mini Program deployment
  - Create scene hierarchy (LoginScene, LobbyScene, GameScene, SettlementScene, HistoryScene)
  - Set up resource management and asset organization structure
  - Create base UI component prefabs and scripts
  - _Requirements: 9.1, 9.2_

- [x] 19. Implement frontend network communication
  - Create WebSocket client with automatic reconnection logic
  - Build message serialization and deserialization system
  - Implement heartbeat mechanism and connection monitoring
  - Add network error handling and retry mechanisms
  - Create HTTP client for REST API calls with authentication
  - Write unit tests for network layer functionality
  - _Requirements: 9.3, 9.4, 5.1_

- [x] 20. Build frontend game state management
  - Create client-side GameStateManager for local state tracking
  - Implement local game state validation and prediction
  - Build state synchronization with server snapshots
  - Add conflict resolution for client-server state differences
  - Create local caching for user preferences and settings
  - Write unit tests for client state management
  - _Requirements: 9.5, 5.4_

- [x] 21. Create game UI and user interactions
  - Build login scene with WeChat authorization integration
  - Create lobby scene with room creation and joining interface
  - Implement game scene with 3-player layout and tile rendering
  - Add action buttons (discard, peng, gang, hu, pass) with validation
  - Create settlement popup with score breakdown display
  - Build game history list with filtering and replay functionality
  - _Requirements: 9.1, 2.1, 2.2, 2.6, 3.2, 3.3, 3.6, 4.5, 7.2, 7.4_

- [x] 22. Add WeChat Mini Program integration
  - Implement WeChat login and user authorization
  - Add WeChat sharing and social features
  - Create local storage for user preferences and game settings
  - Implement mini program lifecycle management
  - Add WeChat-specific UI adaptations and optimizations
  - Test compatibility across different WeChat client versions
  - _Requirements: 9.1, 9.2, 9.3, 9.4, 9.5_

- [x] 23. Implement performance optimization
  - Add Redis caching for frequently accessed data
  - Optimize database queries with proper indexing
  - Create connection pooling for database and Redis
  - Implement message queue for asynchronous processing
  - Add performance monitoring and alerting
  - Write performance tests for concurrent load scenarios
  - _Requirements: 10.1, 10.2, 10.3, 10.4, 10.5_

- [x] 24. Create comprehensive testing suite
  - Write unit tests for all game logic components
  - Create integration tests for WebSocket communication flows
  - Build end-to-end tests for complete game scenarios
  - Add performance tests for concurrent gameplay
  - Create automated UI tests for frontend components
  - Implement load testing for server scalability validation
  - _Requirements: 10.2, 10.3, 10.4_

- [x] 25. Set up deployment and production environment
  - Create Docker containers for Spring Boot application
  - Set up database migration and seeding scripts
  - Configure Redis cluster for production deployment
  - Implement health checks and monitoring endpoints
  - Set up logging aggregation and alerting systems
  - Create deployment scripts and CI/CD pipeline
  - _Requirements: 10.4, 10.5, 8.4, 8.5_