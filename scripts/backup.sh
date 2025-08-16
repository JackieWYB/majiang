#!/bin/bash

# Backup script for Mahjong Game
set -e

# Configuration
BACKUP_DIR="./backups/$(date +%Y%m%d_%H%M%S)"
RETENTION_DAYS=${BACKUP_RETENTION_DAYS:-30}
S3_BUCKET=${BACKUP_S3_BUCKET:-}

echo "Starting backup process..."
echo "Backup directory: $BACKUP_DIR"

# Create backup directory
mkdir -p "$BACKUP_DIR"

# Function to backup database
backup_database() {
    echo "Backing up MySQL database..."
    
    if docker ps | grep -q mahjong-mysql; then
        # Create database dump
        docker exec mahjong-mysql mysqldump \
            -u root \
            -p"${MYSQL_ROOT_PASSWORD:-rootpassword}" \
            --single-transaction \
            --routines \
            --triggers \
            "${MYSQL_DATABASE:-mahjong_game}" > "$BACKUP_DIR/database_backup.sql"
        
        # Compress the backup
        gzip "$BACKUP_DIR/database_backup.sql"
        
        echo "Database backup completed: $BACKUP_DIR/database_backup.sql.gz"
    else
        echo "Warning: MySQL container not running, skipping database backup"
    fi
}

# Function to backup Redis data
backup_redis() {
    echo "Backing up Redis data..."
    
    if docker ps | grep -q mahjong-redis; then
        # Trigger Redis background save
        docker exec mahjong-redis redis-cli BGSAVE
        
        # Wait for background save to complete
        while [ "$(docker exec mahjong-redis redis-cli LASTSAVE)" = "$(docker exec mahjong-redis redis-cli LASTSAVE)" ]; do
            sleep 1
        done
        
        # Copy the RDB file
        docker cp mahjong-redis:/data/dump.rdb "$BACKUP_DIR/redis_backup.rdb"
        
        # Compress the backup
        gzip "$BACKUP_DIR/redis_backup.rdb"
        
        echo "Redis backup completed: $BACKUP_DIR/redis_backup.rdb.gz"
    else
        echo "Warning: Redis container not running, skipping Redis backup"
    fi
}

# Function to backup application logs
backup_logs() {
    echo "Backing up application logs..."
    
    if [ -d "./logs" ]; then
        tar -czf "$BACKUP_DIR/logs_backup.tar.gz" -C ./logs .
        echo "Logs backup completed: $BACKUP_DIR/logs_backup.tar.gz"
    else
        echo "Warning: Logs directory not found, skipping logs backup"
    fi
}

# Function to backup configuration files
backup_config() {
    echo "Backing up configuration files..."
    
    # Create config backup directory
    mkdir -p "$BACKUP_DIR/config"
    
    # Backup important configuration files
    files_to_backup=(
        "docker-compose.yml"
        "nginx.conf"
        "redis.conf"
        ".env.production"
        "monitoring/prometheus.yml"
        "monitoring/alert_rules.yml"
    )
    
    for file in "${files_to_backup[@]}"; do
        if [ -f "$file" ]; then
            cp "$file" "$BACKUP_DIR/config/"
            echo "Backed up: $file"
        fi
    done
    
    # Compress config backup
    tar -czf "$BACKUP_DIR/config_backup.tar.gz" -C "$BACKUP_DIR" config
    rm -rf "$BACKUP_DIR/config"
    
    echo "Configuration backup completed: $BACKUP_DIR/config_backup.tar.gz"
}

# Function to upload to S3 (if configured)
upload_to_s3() {
    if [ -n "$S3_BUCKET" ] && command -v aws &> /dev/null; then
        echo "Uploading backup to S3..."
        
        # Upload entire backup directory
        aws s3 sync "$BACKUP_DIR" "s3://$S3_BUCKET/backups/$(basename $BACKUP_DIR)/" \
            --storage-class STANDARD_IA
        
        echo "Backup uploaded to S3: s3://$S3_BUCKET/backups/$(basename $BACKUP_DIR)/"
    else
        echo "S3 upload skipped (bucket not configured or AWS CLI not available)"
    fi
}

# Function to cleanup old backups
cleanup_old_backups() {
    echo "Cleaning up backups older than $RETENTION_DAYS days..."
    
    # Local cleanup
    find ./backups -type d -name "20*" -mtime +$RETENTION_DAYS -exec rm -rf {} + 2>/dev/null || true
    
    # S3 cleanup (if configured)
    if [ -n "$S3_BUCKET" ] && command -v aws &> /dev/null; then
        aws s3 ls "s3://$S3_BUCKET/backups/" | while read -r line; do
            backup_date=$(echo $line | awk '{print $2}' | cut -d'/' -f1)
            if [ -n "$backup_date" ]; then
                backup_timestamp=$(date -d "$backup_date" +%s 2>/dev/null || echo 0)
                current_timestamp=$(date +%s)
                age_days=$(( (current_timestamp - backup_timestamp) / 86400 ))
                
                if [ $age_days -gt $RETENTION_DAYS ]; then
                    echo "Deleting old S3 backup: $backup_date"
                    aws s3 rm "s3://$S3_BUCKET/backups/$backup_date/" --recursive
                fi
            fi
        done
    fi
    
    echo "Cleanup completed"
}

# Function to verify backup integrity
verify_backup() {
    echo "Verifying backup integrity..."
    
    # Check if backup files exist and are not empty
    backup_files=(
        "$BACKUP_DIR/database_backup.sql.gz"
        "$BACKUP_DIR/redis_backup.rdb.gz"
        "$BACKUP_DIR/logs_backup.tar.gz"
        "$BACKUP_DIR/config_backup.tar.gz"
    )
    
    for file in "${backup_files[@]}"; do
        if [ -f "$file" ] && [ -s "$file" ]; then
            echo "✓ $file - OK"
        else
            echo "✗ $file - Missing or empty"
        fi
    done
    
    # Test gzip integrity
    for file in "$BACKUP_DIR"/*.gz; do
        if [ -f "$file" ]; then
            if gzip -t "$file" 2>/dev/null; then
                echo "✓ $(basename $file) - Compression OK"
            else
                echo "✗ $(basename $file) - Compression corrupted"
            fi
        fi
    done
    
    echo "Backup verification completed"
}

# Main backup process
echo "=== Starting Mahjong Game Backup ==="

# Load environment variables
if [ -f ".env.production" ]; then
    export $(cat .env.production | xargs)
fi

# Perform backups
backup_database
backup_redis
backup_logs
backup_config

# Verify backup
verify_backup

# Upload to S3 if configured
upload_to_s3

# Cleanup old backups
cleanup_old_backups

# Create backup summary
cat > "$BACKUP_DIR/backup_summary.txt" << EOF
Backup Summary
==============
Date: $(date)
Backup Directory: $BACKUP_DIR
Files Created:
$(ls -la "$BACKUP_DIR")

Total Size: $(du -sh "$BACKUP_DIR" | cut -f1)
EOF

echo "=== Backup Process Completed ==="
echo "Backup location: $BACKUP_DIR"
echo "Total backup size: $(du -sh "$BACKUP_DIR" | cut -f1)"

# Send notification (implement as needed)
# ./scripts/notify-backup.sh "SUCCESS" "$BACKUP_DIR"