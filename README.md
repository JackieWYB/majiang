# 3-Player Mahjong Game (卡五星麻将)

A real-time multiplayer Mahjong game built with Spring Boot backend and Cocos Creator frontend for WeChat Mini Program.

## Project Structure

```
├── backend/                 # Spring Boot Backend
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/mahjong/
│   │   │   │   ├── config/          # Configuration classes
│   │   │   │   ├── controller/      # REST controllers and WebSocket handlers
│   │   │   │   ├── service/         # Business logic services
│   │   │   │   ├── repository/      # Data access layer
│   │   │   │   ├── model/           # Domain entities and DTOs
│   │   │   │   ├── util/            # Utility classes
│   │   │   │   └── exception/       # Custom exceptions
│   │   │   └── resources/
│   │   │       ├── application.yml  # Application configuration
│   │   │       └── db/migration/    # Database migration scripts
│   │   └── test/                    # Test classes
│   ├── target/                      # Maven build output
│   ├── logs/                        # Application logs
│   └── pom.xml                      # Maven configuration
├── frontend/                        # Cocos Creator Frontend (WeChat Mini Program)
│   └── (to be created)
├── .kiro/                          # Kiro IDE configuration
└── README.md                       # Project documentation
```

## Technology Stack

### Backend
- **Framework**: Spring Boot 3.2, Spring Security, Spring WebSocket
- **Database**: MySQL 8.0, Redis
- **Authentication**: JWT, WeChat Mini Program Login
- **Build Tool**: Maven
- **Java Version**: 17

### Frontend
- **Framework**: Cocos Creator
- **Platform**: WeChat Mini Program
- **Language**: TypeScript/JavaScript

## Getting Started

### Prerequisites

- Java 17+
- Maven 3.6+
- MySQL 8.0+
- Redis 6.0+
- Cocos Creator (for frontend development)

### Running the Backend

1. Navigate to the backend directory:
   ```bash
   cd backend
   ```

2. Configure database and Redis connections in `src/main/resources/application.yml`

3. Run the application:
   ```bash
   mvn spring-boot:run
   ```

### Health Checks

- Application health: `GET /api/health`
- Database health: `GET /api/health/database`
- Redis health: `GET /api/health/redis`

## Features

- Real-time 3-player Mahjong gameplay
- WeChat Mini Program integration
- Configurable game rules
- Room management system
- Game history and replay
- Administrative monitoring
- Reconnection support

## Development

The project follows a modular architecture with clear separation of concerns:

### Backend Architecture
- **Controllers**: Handle HTTP requests and WebSocket connections
- **Services**: Implement business logic
- **Repositories**: Manage data persistence
- **Models**: Define domain entities and data transfer objects
- **Configuration**: Spring configuration and beans

### Frontend Architecture
- **Scenes**: Different game screens (Login, Lobby, Game, etc.)
- **Components**: Reusable UI components
- **Services**: Network communication and game state management
- **Models**: Client-side data models
