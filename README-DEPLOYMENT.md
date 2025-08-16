# Mahjong Game Deployment Guide

This guide covers the deployment and production setup for the Mahjong Game application.

## Prerequisites

- Docker and Docker Compose installed
- At least 4GB RAM and 20GB disk space
- SSL certificates (for HTTPS in production)
- Domain name configured (for production)

## Quick Start

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd mahjong-game
   ```

2. **Set up environment variables**
   ```bash
   cp .env.example .env.production
   # Edit .env.production with your actual values
   ```

3. **Deploy the application**
   ```bash
   ./scripts/deploy.sh production
   ```

## Environment Configuration

### Required Environment Variables

Copy `.env.example` to `.env.production` and configure:

- `MYSQL_ROOT_PASSWORD`: Secure password for MySQL root user
- `MYSQL_PASSWORD`: Password for application database user
- `JWT_SECRET`: Long, secure secret for JWT token signing
- `WECHAT_APP_ID`: WeChat Mini Program App ID
- `WECHAT_APP_SECRET`: WeChat Mini Program App Secret
- `REDIS_PASSWORD`: Password for Redis (optional but recommended)

### Optional Configuration

- `GRAFANA_PASSWORD`: Password for Grafana admin user
- `BACKUP_S3_BUCKET`: S3 bucket for automated backups
- `SLACK_WEBHOOK`: Slack webhook URL for monitoring alerts

## Deployment Options

### Single Server Deployment

For small to medium deployments:

```bash
# Standard deployment
docker-compose up -d

# With custom environment
docker-compose --env-file .env.production up -d
```

### Redis Cluster Deployment

For high availability Redis:

```bash
# Deploy Redis cluster
docker-compose -f docker-compose.redis-cluster.yml up -d

# Wait for cluster initialization
sleep 30

# Deploy main application with cluster config
SPRING_REDIS_CLUSTER_NODES=redis-node-1:7001,redis-node-2:7002,redis-node-3:7003 \
docker-compose up -d mahjong-backend
```

### Load Balanced Deployment

For high traffic scenarios:

```bash
# Scale backend instances
docker-compose up -d --scale mahjong-backend=3

# Update nginx upstream configuration
# Edit nginx.conf to include all backend instances
```

## Database Setup

### Initial Setup

The database is automatically initialized with:
- Schema creation via Flyway migrations
- Default room rules
- Performance indexes

### Manual Database Setup

If needed, run database setup manually:

```bash
./scripts/db-setup.sh
```

### Database Migrations

Migrations run automatically on startup. To run manually:

```bash
docker exec mahjong-backend java -jar app.jar --spring.flyway.migrate=true
```

## Monitoring and Alerting

### Prometheus and Grafana

Access monitoring dashboards:
- Prometheus: http://localhost:9090
- Grafana: http://localhost:3000 (admin/admin)

### Health Checks

The application provides several health check endpoints:

- `/api/actuator/health` - Overall health status
- `/api/actuator/health/liveness` - Kubernetes liveness probe
- `/api/actuator/health/readiness` - Kubernetes readiness probe
- `/api/actuator/metrics` - Application metrics

### Monitoring Script

Run continuous monitoring:

```bash
# Continuous monitoring
./scripts/monitor.sh monitor

# One-time health check
./scripts/monitor.sh check

# Generate health report
./scripts/monitor.sh report
```

## Backup and Recovery

### Automated Backups

Set up automated backups:

```bash
# Run backup manually
./scripts/backup.sh

# Set up cron job for daily backups
echo "0 2 * * * /path/to/mahjong-game/scripts/backup.sh" | crontab -
```

### Backup Configuration

Configure backup settings in environment:

```bash
BACKUP_RETENTION_DAYS=30
BACKUP_S3_BUCKET=your-backup-bucket
AWS_ACCESS_KEY_ID=your-access-key
AWS_SECRET_ACCESS_KEY=your-secret-key
```

### Recovery Process

To restore from backup:

```bash
# Stop services
docker-compose down

# Restore database
gunzip -c backups/20240101_120000/database_backup.sql.gz | \
docker exec -i mahjong-mysql mysql -u root -p mahjong_game

# Restore Redis
gunzip -c backups/20240101_120000/redis_backup.rdb.gz > dump.rdb
docker cp dump.rdb mahjong-redis:/data/dump.rdb
docker restart mahjong-redis

# Start services
docker-compose up -d
```

## Security Considerations

### SSL/TLS Configuration

For production, configure HTTPS:

1. Obtain SSL certificates
2. Update nginx.conf with SSL configuration
3. Redirect HTTP to HTTPS

### Firewall Configuration

Recommended firewall rules:

```bash
# Allow HTTP/HTTPS
ufw allow 80/tcp
ufw allow 443/tcp

# Allow SSH (adjust port as needed)
ufw allow 22/tcp

# Block direct access to services
ufw deny 3306/tcp  # MySQL
ufw deny 6379/tcp  # Redis
ufw deny 8080/tcp  # Backend (behind nginx)
```

### Security Headers

The nginx configuration includes security headers:
- X-Frame-Options: DENY
- X-Content-Type-Options: nosniff
- X-XSS-Protection: 1; mode=block
- Strict-Transport-Security (HTTPS only)

## Performance Tuning

### Database Optimization

MySQL configuration in docker-compose.yml:

```yaml
environment:
  MYSQL_INNODB_BUFFER_POOL_SIZE: 1G
  MYSQL_INNODB_LOG_FILE_SIZE: 256M
  MYSQL_MAX_CONNECTIONS: 200
```

### Redis Optimization

Redis configuration in redis.conf:

```
maxmemory 2gb
maxmemory-policy allkeys-lru
save 900 1
save 300 10
save 60 10000
```

### Application Tuning

JVM options in Dockerfile:

```
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC"
```

## Troubleshooting

### Common Issues

1. **Application won't start**
   - Check database connectivity
   - Verify environment variables
   - Check logs: `docker logs mahjong-backend`

2. **High memory usage**
   - Monitor with: `docker stats`
   - Adjust JVM heap size
   - Check for memory leaks in logs

3. **Database connection errors**
   - Verify MySQL is running: `docker ps`
   - Check connection pool settings
   - Review database logs: `docker logs mahjong-mysql`

4. **Redis connection issues**
   - Check Redis status: `docker exec mahjong-redis redis-cli ping`
   - Verify Redis configuration
   - Check network connectivity

### Log Locations

- Application logs: `./logs/mahjong-game.log`
- Nginx logs: `docker logs mahjong-nginx`
- Database logs: `docker logs mahjong-mysql`
- Redis logs: `docker logs mahjong-redis`

### Performance Issues

1. **Slow response times**
   - Check database query performance
   - Monitor connection pool usage
   - Review application metrics

2. **High CPU usage**
   - Profile application with JProfiler
   - Check for inefficient algorithms
   - Monitor garbage collection

3. **Memory leaks**
   - Use heap dumps for analysis
   - Monitor memory usage over time
   - Check for unclosed resources

## Scaling

### Horizontal Scaling

Scale backend instances:

```bash
docker-compose up -d --scale mahjong-backend=3
```

Update nginx upstream configuration to include all instances.

### Database Scaling

For read replicas:

```yaml
mysql-slave:
  image: mysql:8.0
  environment:
    MYSQL_MASTER_HOST: mysql
    MYSQL_REPLICATION_USER: repl
    MYSQL_REPLICATION_PASSWORD: replpass
```

### Redis Scaling

Use Redis Cluster for horizontal scaling:

```bash
docker-compose -f docker-compose.redis-cluster.yml up -d
```

## CI/CD Pipeline

The project includes GitHub Actions workflow for:

- Automated testing
- Security scanning
- Docker image building
- Deployment to staging/production

Configure secrets in GitHub repository:
- `DOCKER_REGISTRY_TOKEN`
- `PRODUCTION_SERVER_SSH_KEY`
- `STAGING_SERVER_SSH_KEY`

## Support

For deployment issues:

1. Check the troubleshooting section
2. Review application logs
3. Run health checks: `./scripts/monitor.sh check`
4. Generate health report: `./scripts/monitor.sh report`

## Maintenance

### Regular Tasks

- Monitor disk space and clean up old logs
- Update Docker images regularly
- Review and rotate secrets
- Test backup and recovery procedures
- Monitor security advisories

### Updates

To update the application:

```bash
# Pull latest changes
git pull origin main

# Backup current state
./scripts/backup.sh

# Deploy new version
./scripts/deploy.sh production
```