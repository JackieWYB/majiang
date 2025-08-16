#!/bin/bash

# Monitoring script for Mahjong Game
set -e

# Configuration
ALERT_EMAIL=${ALERT_EMAIL:-admin@example.com}
SLACK_WEBHOOK=${SLACK_WEBHOOK:-}
CHECK_INTERVAL=${CHECK_INTERVAL:-60}

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to log messages
log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1"
}

# Function to send alert
send_alert() {
    local severity=$1
    local message=$2
    local color=$RED
    
    case $severity in
        "INFO") color=$GREEN ;;
        "WARNING") color=$YELLOW ;;
        "CRITICAL") color=$RED ;;
    esac
    
    echo -e "${color}[$severity] $message${NC}"
    
    # Send Slack notification if webhook is configured
    if [ -n "$SLACK_WEBHOOK" ]; then
        curl -X POST -H 'Content-type: application/json' \
            --data "{\"text\":\"[$severity] Mahjong Game: $message\"}" \
            "$SLACK_WEBHOOK" > /dev/null 2>&1 || true
    fi
    
    # Log to file
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] [$severity] $message" >> ./logs/monitoring.log
}

# Function to check service health
check_service_health() {
    local service_name=$1
    local health_url=$2
    
    if curl -f -s "$health_url" > /dev/null 2>&1; then
        return 0
    else
        return 1
    fi
}

# Function to check container status
check_container_status() {
    local container_name=$1
    
    if docker ps --format "table {{.Names}}" | grep -q "^$container_name$"; then
        return 0
    else
        return 1
    fi
}

# Function to check database connectivity
check_database() {
    if check_container_status "mahjong-mysql"; then
        if docker exec mahjong-mysql mysqladmin ping -h localhost > /dev/null 2>&1; then
            send_alert "INFO" "Database is healthy"
            return 0
        else
            send_alert "CRITICAL" "Database is not responding"
            return 1
        fi
    else
        send_alert "CRITICAL" "Database container is not running"
        return 1
    fi
}

# Function to check Redis
check_redis() {
    if check_container_status "mahjong-redis"; then
        if docker exec mahjong-redis redis-cli ping > /dev/null 2>&1; then
            send_alert "INFO" "Redis is healthy"
            return 0
        else
            send_alert "CRITICAL" "Redis is not responding"
            return 1
        fi
    else
        send_alert "CRITICAL" "Redis container is not running"
        return 1
    fi
}

# Function to check application
check_application() {
    if check_container_status "mahjong-backend"; then
        if check_service_health "mahjong-backend" "http://localhost:8080/api/actuator/health"; then
            send_alert "INFO" "Application is healthy"
            return 0
        else
            send_alert "CRITICAL" "Application health check failed"
            return 1
        fi
    else
        send_alert "CRITICAL" "Application container is not running"
        return 1
    fi
}

# Function to check nginx
check_nginx() {
    if check_container_status "mahjong-nginx"; then
        if curl -f -s http://localhost > /dev/null 2>&1; then
            send_alert "INFO" "Nginx is healthy"
            return 0
        else
            send_alert "WARNING" "Nginx is not responding properly"
            return 1
        fi
    else
        send_alert "CRITICAL" "Nginx container is not running"
        return 1
    fi
}

# Function to check disk space
check_disk_space() {
    local usage=$(df / | awk 'NR==2 {print $5}' | sed 's/%//')
    
    if [ "$usage" -gt 90 ]; then
        send_alert "CRITICAL" "Disk usage is at ${usage}%"
        return 1
    elif [ "$usage" -gt 80 ]; then
        send_alert "WARNING" "Disk usage is at ${usage}%"
        return 1
    else
        send_alert "INFO" "Disk usage is at ${usage}%"
        return 0
    fi
}

# Function to check memory usage
check_memory_usage() {
    local usage=$(free | awk 'NR==2{printf "%.0f", $3*100/$2}')
    
    if [ "$usage" -gt 90 ]; then
        send_alert "CRITICAL" "Memory usage is at ${usage}%"
        return 1
    elif [ "$usage" -gt 80 ]; then
        send_alert "WARNING" "Memory usage is at ${usage}%"
        return 1
    else
        send_alert "INFO" "Memory usage is at ${usage}%"
        return 0
    fi
}

# Function to check application metrics
check_application_metrics() {
    if check_container_status "mahjong-backend"; then
        # Get metrics from actuator endpoint
        local metrics=$(curl -s http://localhost:8080/api/actuator/metrics 2>/dev/null || echo "{}")
        
        # Check active rooms (example metric)
        local active_rooms=$(curl -s http://localhost:8080/api/actuator/metrics/mahjong.active.rooms 2>/dev/null | jq -r '.measurements[0].value // 0' 2>/dev/null || echo "0")
        
        if [ "$active_rooms" -gt 1000 ]; then
            send_alert "WARNING" "High number of active rooms: $active_rooms"
        else
            send_alert "INFO" "Active rooms: $active_rooms"
        fi
        
        # Check WebSocket connections
        local ws_connections=$(curl -s http://localhost:8080/api/actuator/metrics/mahjong.websocket.connections 2>/dev/null | jq -r '.measurements[0].value // 0' 2>/dev/null || echo "0")
        
        if [ "$ws_connections" -gt 5000 ]; then
            send_alert "WARNING" "High number of WebSocket connections: $ws_connections"
        else
            send_alert "INFO" "WebSocket connections: $ws_connections"
        fi
    fi
}

# Function to check log errors
check_log_errors() {
    local log_file="./logs/mahjong-game.log"
    
    if [ -f "$log_file" ]; then
        # Check for errors in the last 5 minutes
        local error_count=$(grep -c "ERROR" "$log_file" | tail -100 | wc -l)
        
        if [ "$error_count" -gt 10 ]; then
            send_alert "WARNING" "High error rate detected: $error_count errors in recent logs"
        elif [ "$error_count" -gt 0 ]; then
            send_alert "INFO" "Found $error_count errors in recent logs"
        fi
    fi
}

# Function to restart unhealthy services
restart_service() {
    local service_name=$1
    
    send_alert "WARNING" "Attempting to restart $service_name"
    
    case $service_name in
        "database")
            docker restart mahjong-mysql
            sleep 30
            ;;
        "redis")
            docker restart mahjong-redis
            sleep 10
            ;;
        "application")
            docker restart mahjong-backend
            sleep 60
            ;;
        "nginx")
            docker restart mahjong-nginx
            sleep 10
            ;;
    esac
    
    send_alert "INFO" "$service_name restart completed"
}

# Function to generate health report
generate_health_report() {
    local report_file="./logs/health_report_$(date +%Y%m%d_%H%M%S).txt"
    
    cat > "$report_file" << EOF
Mahjong Game Health Report
=========================
Generated: $(date)

Container Status:
$(docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}")

System Resources:
Memory Usage: $(free -h | awk 'NR==2{printf "%.1f%%", $3*100/$2}')
Disk Usage: $(df -h / | awk 'NR==2{print $5}')
CPU Load: $(uptime | awk -F'load average:' '{print $2}')

Application Metrics:
$(curl -s http://localhost:8080/api/actuator/health 2>/dev/null | jq . 2>/dev/null || echo "Application metrics unavailable")

Recent Errors:
$(tail -20 ./logs/mahjong-game.log | grep ERROR || echo "No recent errors found")
EOF

    echo "Health report generated: $report_file"
}

# Main monitoring loop
main() {
    log "Starting Mahjong Game monitoring..."
    
    # Create logs directory if it doesn't exist
    mkdir -p ./logs
    
    while true; do
        log "Running health checks..."
        
        local failed_checks=0
        
        # Check all services
        check_database || ((failed_checks++))
        check_redis || ((failed_checks++))
        check_application || ((failed_checks++))
        check_nginx || ((failed_checks++))
        
        # Check system resources
        check_disk_space || ((failed_checks++))
        check_memory_usage || ((failed_checks++))
        
        # Check application-specific metrics
        check_application_metrics
        check_log_errors
        
        # Auto-restart services if they're down (optional)
        if [ "${AUTO_RESTART:-false}" = "true" ] && [ $failed_checks -gt 0 ]; then
            log "Auto-restart is enabled, attempting to restart failed services..."
            
            if ! check_container_status "mahjong-mysql"; then
                restart_service "database"
            fi
            
            if ! check_container_status "mahjong-redis"; then
                restart_service "redis"
            fi
            
            if ! check_container_status "mahjong-backend"; then
                restart_service "application"
            fi
            
            if ! check_container_status "mahjong-nginx"; then
                restart_service "nginx"
            fi
        fi
        
        # Generate health report every hour
        if [ $(($(date +%M) % 60)) -eq 0 ]; then
            generate_health_report
        fi
        
        log "Health check completed. Failed checks: $failed_checks"
        log "Sleeping for $CHECK_INTERVAL seconds..."
        
        sleep $CHECK_INTERVAL
    done
}

# Handle script arguments
case "${1:-monitor}" in
    "monitor")
        main
        ;;
    "check")
        log "Running one-time health check..."
        check_database
        check_redis
        check_application
        check_nginx
        check_disk_space
        check_memory_usage
        check_application_metrics
        ;;
    "report")
        generate_health_report
        ;;
    *)
        echo "Usage: $0 [monitor|check|report]"
        echo "  monitor - Run continuous monitoring (default)"
        echo "  check   - Run one-time health check"
        echo "  report  - Generate health report"
        exit 1
        ;;
esac