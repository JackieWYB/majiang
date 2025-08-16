# Comprehensive Testing Suite Documentation

This document describes the comprehensive testing suite implemented for the 3-player Mahjong online battle game.

## Overview

The testing suite covers all aspects of the application including:
- Unit tests for game logic components
- Integration tests for WebSocket communication flows
- End-to-end tests for complete game scenarios
- Performance tests for concurrent gameplay
- Load testing for server scalability validation
- Automated UI tests for frontend components

## Test Structure

### Backend Tests (`backend/src/test/`)

#### Unit Tests
- **Game Logic Tests**: `com.mahjong.model.game.*Test`
  - `GameStateTest`: Core game state management
  - `TileTest`: Tile operations and validation
  - `PlayerStateTest`: Player state management
  - `MeldSetTest`: Meld combinations and validation
  - `TileShufflerTest`: Tile shuffling algorithms

- **Service Tests**: `com.mahjong.service.*Test`
  - `GameServiceTest`: Game flow and action handling
  - `WinValidationServiceTest`: Winning condition validation
  - `ScoreCalculationServiceTest`: Score calculation logic
  - `UserServiceTest`: User management operations
  - `RoomServiceTest`: Room lifecycle management

- **Controller Tests**: `com.mahjong.controller.*Test`
  - `AuthControllerTest`: Authentication endpoints
  - `GameRecordControllerTest`: Game history endpoints
  - `RoomControllerTest`: Room management endpoints
  - `UserControllerTest`: User profile endpoints

#### Integration Tests
- **WebSocket Integration**: `com.mahjong.websocket.WebSocketIntegrationTest`
  - Connection establishment and authentication
  - Message routing and broadcasting
  - Heartbeat mechanism
  - Connection recovery

- **Game Flow Integration**: `com.mahjong.integration.GameFlowIntegrationTest`
  - Complete game scenarios from room creation to settlement
  - Multi-round gameplay
  - Player reconnection scenarios
  - Error recovery and consistency

- **Database Integration**: `com.mahjong.repository.*Test`
  - Data persistence and retrieval
  - Transaction management
  - Query optimization

#### Performance Tests
- **Performance Test Suite**: `com.mahjong.performance.PerformanceTestSuite`
  - Concurrent user operations (1000+ users)
  - Concurrent room operations (300+ rooms)
  - Redis performance under load
  - Database query performance
  - Memory usage monitoring

#### Load Tests
- **Load Test Suite**: `com.mahjong.load.LoadTestSuite`
  - WebSocket connection load (500+ connections)
  - Message throughput testing (5000+ messages/second)
  - Database connection pool testing
  - Memory usage under sustained load
  - System scalability validation

### Frontend Tests (`frontend/test/`)

#### Unit Tests
- **Manager Tests**: `test/managers/`
  - `GameStateManager.test.ts`: Client-side game state management
  - `LocalCacheManager.test.ts`: Local data caching
  - `StateSynchronizer.test.ts`: State synchronization logic

- **Network Tests**: `test/network/`
  - `NetworkManager.test.ts`: WebSocket communication
  - `HttpClient.test.ts`: HTTP request handling
  - `MessageSerializer.test.ts`: Message serialization

- **UI Tests**: `test/ui/`
  - `GameSceneController.test.ts`: Game scene interactions
  - `LobbySceneController.test.ts`: Lobby scene functionality

#### Integration Tests
- **Manager Integration**: `test/managers/*.integration.test.ts`
  - Cross-manager communication
  - State synchronization between components

#### End-to-End Tests
- **Game Flow E2E**: `test/e2e/GameFlow.e2e.test.ts`
  - Complete user journey from login to game completion
  - Error scenario handling
  - Performance under user interactions

#### WeChat Integration Tests
- **WeChat Tests**: `test/wechat/`
  - `WeChatBasic.test.ts`: Basic WeChat API integration
  - `WeChatCompatibility.test.ts`: Cross-version compatibility
  - `WeChatIntegration.test.ts`: Full WeChat integration

## Test Execution

### Backend Test Execution

#### Run All Tests
```bash
cd backend
./run-performance-tests.sh all
```

#### Run Specific Test Categories
```bash
# Unit tests only
./run-performance-tests.sh unit

# Integration tests
./run-performance-tests.sh integration

# Performance tests
./run-performance-tests.sh performance

# Load tests (requires explicit enabling)
./run-performance-tests.sh load true

# WebSocket tests
./run-performance-tests.sh websocket

# Security tests
./run-performance-tests.sh security
```

#### Maven Commands
```bash
# All tests
mvn test

# Specific test class
mvn test -Dtest=GameServiceTest

# Performance tests with specific profile
mvn test -Dtest=PerformanceTestSuite -Dspring.profiles.active=performance

# Load tests (when enabled)
mvn test -Dtest=LoadTestSuite -Dspring.profiles.active=performance -Xmx4g
```

### Frontend Test Execution

#### Run All Tests
```bash
cd frontend
npm run test:all
```

#### Run Specific Test Categories
```bash
# Unit tests
npm run test:unit

# Integration tests
npm run test:integration

# End-to-end tests
npm run test:e2e

# WeChat tests
npm run test:wechat

# With coverage
npm run test:coverage

# Watch mode
npm run test:watch
```

## Performance Benchmarks

### Backend Performance Targets

#### User Operations
- **Target**: 1000 concurrent users
- **Response Time**: < 500ms average
- **Throughput**: > 50 operations/second
- **Error Rate**: < 5%

#### Room Operations
- **Target**: 300 concurrent rooms
- **Response Time**: < 1000ms average
- **Throughput**: > 10 rooms/second
- **Error Rate**: < 10%

#### WebSocket Connections
- **Target**: 500+ concurrent connections
- **Connection Success Rate**: > 95%
- **Message Success Rate**: > 90%

#### Message Throughput
- **Target**: 5000 messages/second
- **Processing Time**: < 10ms average
- **Actual Throughput**: > 80% of target

#### Database Performance
- **Concurrent Queries**: 200+
- **Query Time**: < 500ms average
- **Error Rate**: < 5%

#### Memory Usage
- **Heap Usage**: < 80% of maximum
- **Memory Increase**: Reasonable under load
- **Garbage Collection**: Minimal impact

### Frontend Performance Targets

#### UI Responsiveness
- **Action Response**: < 100ms
- **Scene Transitions**: < 500ms
- **Animation Smoothness**: 60 FPS

#### Network Performance
- **Connection Time**: < 2 seconds
- **Message Latency**: < 200ms
- **Reconnection Time**: < 5 seconds

#### Memory Usage
- **Memory Growth**: Linear with game state
- **Cache Efficiency**: > 90% hit rate
- **Cleanup**: Proper resource disposal

## Test Infrastructure

### Test Containers
The test suite uses Testcontainers for integration testing:
- **MySQL Container**: Isolated database for each test run
- **Redis Container**: Isolated cache for each test run
- **Automatic Cleanup**: Containers are automatically destroyed after tests

### Test Profiles
- **test**: Default test profile with optimized settings
- **performance**: Performance testing with increased resources
- **load**: Load testing with maximum resource allocation

### Test Data Management
- **Test Users**: Automatically created and cleaned up
- **Test Rooms**: Isolated per test scenario
- **Test Games**: Complete game state simulation

## Continuous Integration

### Test Automation
- **Pre-commit Hooks**: Run unit tests before commits
- **Pull Request Validation**: Full test suite on PR creation
- **Nightly Builds**: Performance and load tests
- **Release Validation**: Complete test suite before deployment

### Test Reports
- **Coverage Reports**: Generated with JaCoCo (backend) and Vitest (frontend)
- **Performance Reports**: HTML reports with metrics and graphs
- **Test Results**: JUnit XML format for CI integration

### Quality Gates
- **Unit Test Coverage**: > 80%
- **Integration Test Coverage**: > 70%
- **Performance Benchmarks**: Must meet defined targets
- **Load Test Stability**: No memory leaks or crashes

## Troubleshooting

### Common Issues

#### Backend Tests
1. **Database Connection Issues**
   - Ensure MySQL container is running
   - Check port conflicts (3307)
   - Verify test database credentials

2. **Redis Connection Issues**
   - Ensure Redis container is running
   - Check port conflicts (6380)
   - Verify Redis configuration

3. **Performance Test Failures**
   - Increase JVM heap size (-Xmx4g)
   - Check system resources
   - Adjust performance targets if needed

#### Frontend Tests
1. **Mock Issues**
   - Verify Cocos Creator mocks are properly configured
   - Check WeChat API mocks
   - Ensure test setup is complete

2. **Async Test Issues**
   - Use proper async/await patterns
   - Set appropriate timeouts
   - Handle promise rejections

### Debug Mode
Enable debug logging for detailed test execution information:

```bash
# Backend
mvn test -Dlogging.level.com.mahjong=DEBUG

# Frontend
npm run test -- --reporter=verbose
```

## Best Practices

### Test Writing Guidelines
1. **Isolation**: Each test should be independent
2. **Clarity**: Test names should describe the scenario
3. **Coverage**: Test both happy path and error scenarios
4. **Performance**: Keep tests fast and efficient
5. **Maintainability**: Use helper methods and fixtures

### Test Organization
1. **Structure**: Follow consistent directory structure
2. **Naming**: Use descriptive test class and method names
3. **Grouping**: Group related tests together
4. **Documentation**: Document complex test scenarios

### Performance Testing
1. **Realistic Load**: Use realistic user patterns
2. **Gradual Increase**: Gradually increase load to find limits
3. **Monitoring**: Monitor system resources during tests
4. **Baseline**: Establish performance baselines
5. **Regression**: Detect performance regressions

## Maintenance

### Regular Tasks
1. **Update Dependencies**: Keep test frameworks updated
2. **Review Coverage**: Regularly review test coverage
3. **Performance Baselines**: Update performance targets
4. **Test Data**: Clean up test data regularly
5. **Documentation**: Keep test documentation current

### Monitoring
1. **Test Execution Time**: Monitor for slow tests
2. **Flaky Tests**: Identify and fix unstable tests
3. **Coverage Trends**: Track coverage over time
4. **Performance Trends**: Monitor performance metrics

This comprehensive testing suite ensures the reliability, performance, and quality of the Mahjong game system across all components and scenarios.