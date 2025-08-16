package com.mahjong.service;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * Connection Pool Monitoring Service
 * 
 * Monitors database and Redis connection pools for performance optimization
 * and provides metrics for system health monitoring.
 */
@Service
public class ConnectionPoolMonitoringService {

    private static final Logger logger = LoggerFactory.getLogger(ConnectionPoolMonitoringService.class);

    @Autowired
    private DataSource dataSource;

    @Autowired
    private RedisConnectionFactory redisConnectionFactory;

    /**
     * Monitor connection pools every 5 minutes
     */
    @Scheduled(fixedRate = 300000) // 5 minutes
    public void monitorConnectionPools() {
        try {
            Map<String, Object> metrics = getConnectionPoolMetrics();
            logConnectionPoolMetrics(metrics);
            checkConnectionPoolHealth(metrics);
        } catch (Exception e) {
            logger.error("Error monitoring connection pools", e);
        }
    }

    /**
     * Get comprehensive connection pool metrics
     */
    public Map<String, Object> getConnectionPoolMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        
        // Database connection pool metrics
        if (dataSource instanceof HikariDataSource) {
            HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
            HikariPoolMXBean poolBean = hikariDataSource.getHikariPoolMXBean();
            
            Map<String, Object> dbMetrics = new HashMap<>();
            dbMetrics.put("activeConnections", poolBean.getActiveConnections());
            dbMetrics.put("idleConnections", poolBean.getIdleConnections());
            dbMetrics.put("totalConnections", poolBean.getTotalConnections());
            dbMetrics.put("threadsAwaitingConnection", poolBean.getThreadsAwaitingConnection());
            dbMetrics.put("maximumPoolSize", hikariDataSource.getMaximumPoolSize());
            dbMetrics.put("minimumIdle", hikariDataSource.getMinimumIdle());
            
            metrics.put("database", dbMetrics);
        }
        
        // Redis connection pool metrics
        if (redisConnectionFactory instanceof LettuceConnectionFactory) {
            LettuceConnectionFactory lettuceFactory = (LettuceConnectionFactory) redisConnectionFactory;
            Map<String, Object> redisMetrics = new HashMap<>();
            
            // Note: Lettuce doesn't expose detailed pool metrics by default
            // This is a simplified implementation
            redisMetrics.put("factoryType", "Lettuce");
            redisMetrics.put("database", lettuceFactory.getDatabase());
            redisMetrics.put("hostName", lettuceFactory.getHostName());
            redisMetrics.put("port", lettuceFactory.getPort());
            
            metrics.put("redis", redisMetrics);
        }
        
        metrics.put("timestamp", System.currentTimeMillis());
        return metrics;
    }

    /**
     * Log connection pool metrics
     */
    private void logConnectionPoolMetrics(Map<String, Object> metrics) {
        @SuppressWarnings("unchecked")
        Map<String, Object> dbMetrics = (Map<String, Object>) metrics.get("database");
        
        if (dbMetrics != null) {
            logger.info("Database Connection Pool - Active: {}, Idle: {}, Total: {}, Waiting: {}, Max: {}",
                    dbMetrics.get("activeConnections"),
                    dbMetrics.get("idleConnections"),
                    dbMetrics.get("totalConnections"),
                    dbMetrics.get("threadsAwaitingConnection"),
                    dbMetrics.get("maximumPoolSize"));
        }
        
        @SuppressWarnings("unchecked")
        Map<String, Object> redisMetrics = (Map<String, Object>) metrics.get("redis");
        if (redisMetrics != null) {
            logger.info("Redis Connection - Host: {}:{}, Database: {}",
                    redisMetrics.get("hostName"),
                    redisMetrics.get("port"),
                    redisMetrics.get("database"));
        }
    }

    /**
     * Check connection pool health and alert if issues detected
     */
    private void checkConnectionPoolHealth(Map<String, Object> metrics) {
        @SuppressWarnings("unchecked")
        Map<String, Object> dbMetrics = (Map<String, Object>) metrics.get("database");
        
        if (dbMetrics != null) {
            int activeConnections = (Integer) dbMetrics.get("activeConnections");
            int totalConnections = (Integer) dbMetrics.get("totalConnections");
            int threadsAwaitingConnection = (Integer) dbMetrics.get("threadsAwaitingConnection");
            int maximumPoolSize = (Integer) dbMetrics.get("maximumPoolSize");
            
            // Check for potential issues
            double utilizationRate = (double) totalConnections / maximumPoolSize;
            if (utilizationRate > 0.8) {
                logger.warn("Database connection pool utilization is high: {:.2f}%", utilizationRate * 100);
            }
            
            if (threadsAwaitingConnection > 0) {
                logger.warn("Threads waiting for database connections: {}", threadsAwaitingConnection);
            }
            
            if (activeConnections == maximumPoolSize) {
                logger.error("Database connection pool is at maximum capacity!");
            }
        }
    }

    /**
     * Get database connection pool utilization percentage
     */
    public double getDatabasePoolUtilization() {
        if (dataSource instanceof HikariDataSource) {
            HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
            HikariPoolMXBean poolBean = hikariDataSource.getHikariPoolMXBean();
            
            return (double) poolBean.getTotalConnections() / hikariDataSource.getMaximumPoolSize();
        }
        return 0.0;
    }

    /**
     * Get current active database connections
     */
    public int getActiveDatabaseConnections() {
        if (dataSource instanceof HikariDataSource) {
            HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
            HikariPoolMXBean poolBean = hikariDataSource.getHikariPoolMXBean();
            return poolBean.getActiveConnections();
        }
        return 0;
    }

    /**
     * Get threads waiting for database connections
     */
    public int getThreadsAwaitingConnection() {
        if (dataSource instanceof HikariDataSource) {
            HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
            HikariPoolMXBean poolBean = hikariDataSource.getHikariPoolMXBean();
            return poolBean.getThreadsAwaitingConnection();
        }
        return 0;
    }

    /**
     * Force connection pool health check
     */
    public Map<String, String> performHealthCheck() {
        Map<String, String> healthStatus = new HashMap<>();
        
        try {
            // Test database connection
            if (dataSource instanceof HikariDataSource) {
                HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
                hikariDataSource.getConnection().close();
                healthStatus.put("database", "healthy");
            }
        } catch (Exception e) {
            logger.error("Database health check failed", e);
            healthStatus.put("database", "unhealthy: " + e.getMessage());
        }
        
        try {
            // Test Redis connection
            redisConnectionFactory.getConnection().ping();
            healthStatus.put("redis", "healthy");
        } catch (Exception e) {
            logger.error("Redis health check failed", e);
            healthStatus.put("redis", "unhealthy: " + e.getMessage());
        }
        
        return healthStatus;
    }
}