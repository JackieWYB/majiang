#!/bin/bash

# Deployment script for Mahjong Game
set -e

# Configuration
ENVIRONMENT=${1:-production}
VERSION=${2:-latest}
COMPOSE_FILE="docker-compose.yml"
BACKUP_DIR="./backups/$(date +%Y%m%d_%H%M%S)"

echo "Starting deployment for environment: $ENVIRONMENT"
echo "Version: $VERSION"

# Create backup directory
mkdir -p "$BACKUP_DIR"

# Function to backup database
backup_database() {
    echo "Creating database backup..."
    docker exec mahjong-mysql mysqldump -u root -p"${MYSQL_ROOT_PASSWORD:-rootpassword}" "${MYSQL_DATABASE:-mahjong_game}" > "$BACKUP_DIR/database_backup.sql"
    echo "Database backup created at $BACKUP_DIR/database_backup.sql"
}

# Function to backup Redis data
backup_redis() {
    echo "Creating Redis backup..."
    docker exec mahjong-redis redis-cli BGSAVE
    sleep 5
    docker cp mahjong-redis:/data/dump.rdb "$BACKUP_DIR/redis_backup.rdb"
    echo "Redis backup created at $BACKUP_DIR/redis_backup.rdb"
}

# Function to health check
health_check() {
    echo "Performing health check..."
    local max_attempts=30
    local attempt=1
    
    while [ $attempt -le $max_attempts ]; do
        if curl -f http://localhost:8080/api/actuator/health/readiness > /dev/null 2>&1; then
            echo "Health check passed!"
            return 0
        fi
        
        echo "Health check attempt $attempt/$max_attempts failed, waiting..."
        sleep 10
        ((attempt++))
    done
    
    echo "Health check failed after $max_attempts attempts"
    return 1
}

# Function to rollback
rollback() {
    echo "Rolling back deployment..."
    docker-compose -f $COMPOSE_FILE down
    
    # Restore database
    if [ -f "$BACKUP_DIR/database_backup.sql" ]; then
        echo "Restoring database..."
        docker-compose -f $COMPOSE_FILE up -d mysql
        sleep 30
        docker exec -i mahjong-mysql mysql -u root -p"${MYSQL_ROOT_PASSWORD:-rootpassword}" "${MYSQL_DATABASE:-mahjong_game}" < "$BACKUP_DIR/database_backup.sql"
    fi
    
    # Restore Redis
    if [ -f "$BACKUP_DIR/redis_backup.rdb" ]; then
        echo "Restoring Redis..."
        docker cp "$BACKUP_DIR/redis_backup.rdb" mahjong-redis:/data/dump.rdb
        docker restart mahjong-redis
    fi
    
    echo "Rollback completed"
    exit 1
}

# Trap for cleanup on failure
trap rollback ERR

# Load environment variables
if [ -f ".env.$ENVIRONMENT" ]; then
    echo "Loading environment variables from .env.$ENVIRONMENT"
    export $(cat .env.$ENVIRONMENT | xargs)
fi

# Pre-deployment checks
echo "Running pre-deployment checks..."

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
    echo "Error: Docker is not running"
    exit 1
fi

# Check if required environment variables are set
required_vars=("MYSQL_ROOT_PASSWORD" "JWT_SECRET")
for var in "${required_vars[@]}"; do
    if [ -z "${!var}" ]; then
        echo "Error: Required environment variable $var is not set"
        exit 1
    fi
done

# Create backups before deployment
if docker ps | grep -q mahjong-mysql; then
    backup_database
fi

if docker ps | grep -q mahjong-redis; then
    backup_redis
fi

# Build and deploy
echo "Building application..."
cd backend
./mvnw clean package -DskipTests
cd ..

echo "Stopping existing services..."
docker-compose -f $COMPOSE_FILE down

echo "Starting new deployment..."
docker-compose -f $COMPOSE_FILE up -d --build

# Wait for services to be ready
echo "Waiting for services to start..."
sleep 60

# Run database migrations
echo "Running database migrations..."
docker exec mahjong-backend java -jar app.jar --spring.flyway.migrate=true || true

# Health check
if ! health_check; then
    echo "Deployment failed health check"
    rollback
fi

# Post-deployment verification
echo "Running post-deployment verification..."

# Check if all services are running
services=("mahjong-mysql" "mahjong-redis" "mahjong-backend" "mahjong-nginx")
for service in "${services[@]}"; do
    if ! docker ps | grep -q "$service"; then
        echo "Error: Service $service is not running"
        rollback
    fi
done

# Test API endpoints
echo "Testing API endpoints..."
if ! curl -f http://localhost:8080/api/actuator/health > /dev/null 2>&1; then
    echo "Error: API health check failed"
    rollback
fi

# Clean up old images
echo "Cleaning up old Docker images..."
docker image prune -f

echo "Deployment completed successfully!"
echo "Backup location: $BACKUP_DIR"
echo "Application is available at: http://localhost"

# Send notification (implement as needed)
# ./scripts/notify-deployment.sh "$ENVIRONMENT" "$VERSION" "SUCCESS"